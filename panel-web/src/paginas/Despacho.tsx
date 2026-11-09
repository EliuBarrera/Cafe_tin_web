import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'

import { api, ErrorApi } from '../api/cliente'
import type { EstadoPedido, PedidoDespacho } from '../api/tipos'
import { MensajeError } from '../componentes/MensajeError'

/** El panel consulta el tablero cada 5 s (decisión del 02/11/2026: polling en vez de SSE). */
export const INTERVALO_REFRESCO_MS = 5000

const CLAVE = ['despacho', 'pedidos'] as const
const FORMATO_CODIGO = /^DR-\d+-\d{4}$/

const COLUMNAS: { estado: EstadoPedido; titulo: string }[] = [
  { estado: 'PENDIENTE', titulo: 'Pendientes' },
  { estado: 'CONFIRMADO', titulo: 'Confirmados' },
  { estado: 'EN_PREPARACION', titulo: 'En preparación' },
  { estado: 'LISTO', titulo: 'Listos' },
]

/** Acción del botón principal de cada columna (LISTO usa verificar + entregar). */
const SIGUIENTE: Partial<Record<EstadoPedido, { ruta: string; texto: string }>> = {
  PENDIENTE: { ruta: 'confirmar', texto: 'Confirmar' },
  CONFIRMADO: { ruta: 'preparar', texto: 'Empezar a preparar' },
  EN_PREPARACION: { ruta: 'listo', texto: 'Marcar listo' },
}

interface Aviso {
  codigoPedido: string
  mensaje: string
}

/**
 * Tablero de despacho (RF-10 a RF-14): pedidos pagados en columnas por estado. La API solo envía
 * pedidos pagados y nunca el código del recibo: el trabajador se lo pide al estudiante (RF-13).
 */
export function Despacho() {
  const consulta = useQuery({
    queryKey: CLAVE,
    queryFn: () => api<PedidoDespacho[]>('/api/despacho/pedidos'),
    refetchInterval: INTERVALO_REFRESCO_MS,
  })
  // Un error de transición se muestra arriba: la tarjeta puede haber cambiado de columna al refrescar.
  const [aviso, setAviso] = useState<Aviso | null>(null)

  const pedidos = consulta.data

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Despacho</h1>
        {consulta.dataUpdatedAt > 0 && (
          <p className="texto-suave">
            Actualizado a las {new Date(consulta.dataUpdatedAt).toLocaleTimeString('es-PE')}
          </p>
        )}
      </div>

      {aviso && (
        <div className="mensaje-error aviso" role="alert">
          <p>
            {aviso.codigoPedido}: {aviso.mensaje}
          </p>
          <button type="button" className="boton-texto" onClick={() => setAviso(null)}>
            Ocultar
          </button>
        </div>
      )}

      {consulta.isPending ? (
        <p className="estado-pagina texto-suave">Cargando pedidos…</p>
      ) : !pedidos ? (
        <div className="estado-pagina">
          <MensajeError error={consulta.error} />
          <button type="button" className="boton" onClick={() => consulta.refetch()}>
            Reintentar
          </button>
        </div>
      ) : (
        <>
          {consulta.isError && <MensajeError error={consulta.error} />}
          <div className="tablero">
            {COLUMNAS.map((c) => (
              <Columna
                key={c.estado}
                titulo={c.titulo}
                pedidos={pedidos.filter((p) => p.estado === c.estado)}
                alFallar={setAviso}
                alAvanzar={() => setAviso(null)}
              />
            ))}
          </div>
        </>
      )}
    </section>
  )
}

function Columna({
  titulo,
  pedidos,
  alFallar,
  alAvanzar,
}: {
  titulo: string
  pedidos: PedidoDespacho[]
  alFallar: (aviso: Aviso) => void
  alAvanzar: () => void
}) {
  const id = useId()
  return (
    <section className="columna" aria-labelledby={id}>
      <h2 id={id}>
        {titulo} <span className="contador">{pedidos.length}</span>
      </h2>
      {pedidos.length === 0 ? (
        <p className="texto-suave columna-vacia">Sin pedidos</p>
      ) : (
        pedidos.map((p) => <Tarjeta key={p.idPedido} pedido={p} alFallar={alFallar} alAvanzar={alAvanzar} />)
      )}
    </section>
  )
}

function Tarjeta({
  pedido,
  alFallar,
  alAvanzar,
}: {
  pedido: PedidoDespacho
  alFallar: (aviso: Aviso) => void
  alAvanzar: () => void
}) {
  const cliente = useQueryClient()
  const refrescar = () => cliente.invalidateQueries({ queryKey: CLAVE })
  const ruta = `/api/despacho/pedidos/${pedido.idPedido}`

  const avanzar = useMutation({
    mutationFn: (accion: string) => api<PedidoDespacho>(`${ruta}/${accion}`, { metodo: 'POST' }),
    onSuccess: alAvanzar,
    onError: (error) => alFallar({ codigoPedido: pedido.codigoPedido, mensaje: mensajeDe(error) }),
    onSettled: refrescar,
  })

  const siguiente = SIGUIENTE[pedido.estado]

  return (
    <article className="tarjeta-pedido" aria-label={pedido.codigoPedido}>
      <header className="tarjeta-pedido-cabecera">
        <strong>{pedido.codigoPedido}</strong>
        <span className="hora" title="Hora de recojo">
          {pedido.horaRecojo}
        </span>
      </header>
      <p className="estudiante">{pedido.estudiante}</p>
      <ul className="items">
        {pedido.items.map((l) => (
          <li key={l.producto}>{`${l.cantidad} × ${l.producto}`}</li>
        ))}
      </ul>
      <p className="texto-suave detalle">
        S/ {pedido.total.toFixed(2)} · {pedido.metodoPago}
        {pedido.trabajador && <> · Atiende: {pedido.trabajador}</>}
      </p>

      {siguiente && (
        <button
          type="button"
          className="boton"
          disabled={avanzar.isPending}
          onClick={() => avanzar.mutate(siguiente.ruta)}
        >
          {siguiente.texto}
        </button>
      )}

      {pedido.estado === 'LISTO' && (
        <>
          {pedido.reciboVerificado ? (
            <p className="verificado">Recibo verificado</p>
          ) : (
            <VerificarRecibo ruta={ruta} alVerificar={refrescar} />
          )}
          <button
            type="button"
            className="boton"
            disabled={!pedido.reciboVerificado || avanzar.isPending}
            onClick={() => avanzar.mutate('entregar')}
          >
            Entregar
          </button>
        </>
      )}
    </article>
  )
}

/** RF-13: el estudiante dicta el código de su recibo y la API lo compara. */
function VerificarRecibo({ ruta, alVerificar }: { ruta: string; alVerificar: () => void }) {
  const [codigo, setCodigo] = useState('')
  const verificar = useMutation({
    mutationFn: () => api<PedidoDespacho>(`${ruta}/verificar`, { metodo: 'POST', cuerpo: { codigoVerificacion: codigo } }),
    onSuccess: alVerificar,
  })
  const valido = FORMATO_CODIGO.test(codigo)

  return (
    <form
      className="verificar"
      onSubmit={(e) => {
        e.preventDefault()
        if (valido) verificar.mutate()
      }}
    >
      <label>
        Código del recibo
        <input
          value={codigo}
          onChange={(e) => setCodigo(e.target.value.toUpperCase().trim())}
          placeholder="DR-0000-0000"
          autoComplete="off"
          spellCheck={false}
        />
      </label>
      <button type="submit" className="boton boton-secundario" disabled={!valido || verificar.isPending}>
        Verificar
      </button>
      <MensajeError error={verificar.error} />
    </form>
  )
}

function mensajeDe(error: unknown) {
  return error instanceof ErrorApi ? error.message : 'Ocurrió un error inesperado. Intenta de nuevo.'
}
