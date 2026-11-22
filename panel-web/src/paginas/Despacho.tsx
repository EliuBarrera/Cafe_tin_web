import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { useSearchParams } from 'react-router'

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

/** Valor del filtro "Atiende" para los pedidos que nadie confirmó todavía (o que confirmó el dueño). */
const SIN_ASIGNAR = '-'

/** Minúsculas y sin tildes: "maria" encuentra a "María". */
function normalizar(texto: string) {
  return texto.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
}

interface Filtros {
  texto: string
  pago: string
  atiende: string
}

function cumple(p: PedidoDespacho, f: Filtros) {
  if (f.pago && p.metodoPago !== f.pago) return false
  if (f.atiende && (p.trabajador ?? SIN_ASIGNAR) !== f.atiende) return false
  if (!f.texto) return true
  const buscado = normalizar(f.texto.trim())
  return [p.codigoPedido, p.estudiante, ...p.items.map((l) => l.producto)].some((t) => normalizar(t).includes(buscado))
}

const unicos = (valores: string[]) => [...new Set(valores)].sort((a, b) => a.localeCompare(b, 'es'))

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

  // Filtros en la URL (?q=&pago=&atiende=): sobreviven a recargar la página y al polling.
  const [parametros, setParametros] = useSearchParams()
  const filtros: Filtros = {
    texto: parametros.get('q') ?? '',
    pago: parametros.get('pago') ?? '',
    atiende: parametros.get('atiende') ?? '',
  }
  const hayFiltros = Boolean(filtros.texto || filtros.pago || filtros.atiende)
  const cambiarFiltro = (clave: string, valor: string) =>
    setParametros(
      (actuales) => {
        const nuevos = new URLSearchParams(actuales)
        if (valor) nuevos.set(clave, valor)
        else nuevos.delete(clave)
        return nuevos
      },
      { replace: true },
    )

  const todos = consulta.data
  const pedidos = todos?.filter((p) => cumple(p, filtros))

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
          <BarraFiltros
            filtros={filtros}
            metodos={unicos((todos ?? []).map((p) => p.metodoPago))}
            trabajadores={unicos((todos ?? []).flatMap((p) => (p.trabajador ? [p.trabajador] : [])))}
            alCambiar={cambiarFiltro}
            alLimpiar={() => setParametros(new URLSearchParams(), { replace: true })}
          />
          {hayFiltros && (
            <p className="resultado-filtros texto-suave" role="status">
              Mostrando {pedidos.length} de {todos?.length ?? 0} pedidos
            </p>
          )}
          <div className="tablero">
            {COLUMNAS.map((c) => (
              <Columna
                key={c.estado}
                estado={c.estado}
                titulo={c.titulo}
                pedidos={pedidos.filter((p) => p.estado === c.estado)}
                filtrado={hayFiltros}
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

function BarraFiltros({
  filtros,
  metodos,
  trabajadores,
  alCambiar,
  alLimpiar,
}: {
  filtros: Filtros
  metodos: string[]
  trabajadores: string[]
  alCambiar: (clave: string, valor: string) => void
  alLimpiar: () => void
}) {
  const id = useId()
  // Un valor elegido sigue en la lista aunque ya no quede ningún pedido con él
  const opcionesPago = unicos([...metodos, ...(filtros.pago ? [filtros.pago] : [])])
  const opcionesAtiende = unicos([
    ...trabajadores,
    ...(filtros.atiende && filtros.atiende !== SIN_ASIGNAR ? [filtros.atiende] : []),
  ])

  return (
    <search role="search" className="filtros filtros-despacho" aria-label="Filtrar pedidos">
      <label className="filtro-busqueda" htmlFor={`${id}-q`}>
        Buscar
        <input
          id={`${id}-q`}
          type="search"
          value={filtros.texto}
          onChange={(e) => alCambiar('q', e.target.value)}
          placeholder="Código, estudiante o producto"
          autoComplete="off"
        />
      </label>
      <label htmlFor={`${id}-pago`}>
        Pago
        <select id={`${id}-pago`} value={filtros.pago} onChange={(e) => alCambiar('pago', e.target.value)}>
          <option value="">Todos</option>
          {opcionesPago.map((m) => (
            <option key={m} value={m}>
              {m}
            </option>
          ))}
        </select>
      </label>
      <label htmlFor={`${id}-atiende`}>
        Atiende
        <select id={`${id}-atiende`} value={filtros.atiende} onChange={(e) => alCambiar('atiende', e.target.value)}>
          <option value="">Todos</option>
          <option value={SIN_ASIGNAR}>Sin asignar</option>
          {opcionesAtiende.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </label>
      {(filtros.texto || filtros.pago || filtros.atiende) && (
        <button type="button" className="boton-texto" onClick={alLimpiar}>
          Limpiar filtros
        </button>
      )}
    </search>
  )
}

function Columna({
  estado,
  titulo,
  pedidos,
  filtrado,
  alFallar,
  alAvanzar,
}: {
  estado: EstadoPedido
  titulo: string
  pedidos: PedidoDespacho[]
  filtrado: boolean
  alFallar: (aviso: Aviso) => void
  alAvanzar: () => void
}) {
  const id = useId()
  return (
    <section className="columna" data-estado={estado} aria-labelledby={id}>
      <h2 id={id}>
        {titulo} <span className="contador">{pedidos.length}</span>
      </h2>
      {pedidos.length === 0 ? (
        <p className="texto-suave columna-vacia">{filtrado ? 'Ninguno con estos filtros' : 'Sin pedidos'}</p>
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
