import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'

import { api } from '../api/cliente'
import type { EstadoCobros, UrlAutorizacion } from '../api/tipos'
import { MensajeError } from '../componentes/MensajeError'
import { irA } from '../navegador'

const CLAVE = ['cobros'] as const
const RUTA = '/api/cafeterias/mia/mercadopago'

/** Lo que Mercado Pago deja en la URL al volver del OAuth (`/cobros?mercadopago=…`). */
const RESULTADO_OAUTH: Record<string, { ok: boolean; texto: string }> = {
  conectado: { ok: true, texto: 'Listo: tu cuenta de Mercado Pago quedó conectada. Ya puedes recibir pagos.' },
  error: { ok: false, texto: 'No se pudo conectar la cuenta de Mercado Pago. Vuelve a intentarlo.' },
}

/**
 * Cobros (RF-06). El dueño conecta la cuenta de Mercado Pago del cafetín: el dinero de cada venta llega
 * directo a esa cuenta. Sin conexión, los estudiantes ven el catálogo pero no pueden pagar.
 * Con la pasarela simulada no hay nada que conectar.
 */
export function Cobros() {
  const cliente = useQueryClient()
  const [parametros, setParametros] = useSearchParams()
  const [resultado] = useState(() => RESULTADO_OAUTH[parametros.get('mercadopago') ?? ''])
  const [confirmando, setConfirmando] = useState(false)

  // Quita ?mercadopago=… para que el aviso no reaparezca al recargar.
  useEffect(() => {
    if (parametros.has('mercadopago')) setParametros({}, { replace: true })
  }, [parametros, setParametros])

  const consulta = useQuery({ queryKey: CLAVE, queryFn: () => api<EstadoCobros>(RUTA) })
  const estado = consulta.data

  const conectar = useMutation({
    mutationFn: () => api<UrlAutorizacion>(`${RUTA}/conectar`, { metodo: 'POST' }),
    onSuccess: ({ urlAutorizacion }) => irA(urlAutorizacion),
  })
  const desconectar = useMutation({
    mutationFn: () => api<void>(RUTA, { metodo: 'DELETE' }),
    onSuccess: () => setConfirmando(false),
    onSettled: () => cliente.invalidateQueries({ queryKey: CLAVE }),
  })

  return (
    <section>
      <div className="encabezado-pagina">
        <h1>Cobros</h1>
      </div>

      {resultado &&
        (resultado.ok ? (
          <p className="aviso-ok" role="status">
            {resultado.texto}
          </p>
        ) : (
          <p className="mensaje-error" role="alert">
            {resultado.texto}
          </p>
        ))}

      {consulta.isPending ? (
        <p className="estado-pagina texto-suave">Cargando…</p>
      ) : !estado ? (
        <div className="estado-pagina">
          <MensajeError error={consulta.error} />
          <button type="button" className="boton" onClick={() => consulta.refetch()}>
            Reintentar
          </button>
        </div>
      ) : estado.pasarela === 'SIMULADA' ? (
        <div className="panel-formulario formulario">
          <h2>Pasarela simulada</h2>
          <p className="texto-suave">
            La API está en modo de demostración: los pagos se aprueban al instante y no se mueve dinero.
            Cuando se active Mercado Pago, aquí podrás conectar la cuenta del cafetín.
          </p>
        </div>
      ) : estado.conectado ? (
        <div className="panel-formulario formulario">
          <h2>
            Mercado Pago <span className="insignia insignia-ok">Conectado</span>
          </h2>
          <p>
            Cuenta de Mercado Pago nº <strong>{estado.idUsuarioMp}</strong>
            {estado.conectadoEn && (
              <span className="texto-suave">
                {' '}
                · conectada el {new Date(estado.conectadoEn).toLocaleDateString('es-PE')}
              </span>
            )}
          </p>
          <p className="texto-suave">
            Los pagos de los estudiantes llegan a esta cuenta. Desde Mercado Pago puedes retirarlos a tu banco.
          </p>
          <MensajeError error={desconectar.error} />
          {confirmando ? (
            <div className="confirmacion">
              <p>Los estudiantes no podrán pagar hasta que vuelvas a conectar una cuenta.</p>
              <div className="acciones">
                <button
                  type="button"
                  className="boton-texto"
                  disabled={desconectar.isPending}
                  onClick={() => desconectar.mutate()}
                >
                  Sí, desconectar
                </button>
                <button type="button" className="boton-texto" onClick={() => setConfirmando(false)}>
                  Cancelar
                </button>
              </div>
            </div>
          ) : (
            <div className="acciones-formulario">
              <button type="button" className="boton boton-secundario" onClick={() => setConfirmando(true)}>
                Desconectar
              </button>
            </div>
          )}
        </div>
      ) : (
        <div className="panel-formulario formulario">
          <h2>
            Mercado Pago <span className="insignia insignia-aviso">Sin conectar</span>
          </h2>
          <p>
            Conecta la cuenta de Mercado Pago del cafetín para que los estudiantes puedan pagar desde la app.
            El dinero de cada venta llega directo a esa cuenta.
          </p>
          <p className="texto-suave">Mientras no la conectes, el catálogo se ve pero no se puede pagar.</p>
          <MensajeError error={conectar.error} />
          <div className="acciones-formulario">
            <button
              type="button"
              className="boton"
              disabled={conectar.isPending || conectar.isSuccess}
              onClick={() => conectar.mutate()}
            >
              {conectar.isPending || conectar.isSuccess ? 'Abriendo Mercado Pago…' : 'Conectar Mercado Pago'}
            </button>
          </div>
        </div>
      )}
    </section>
  )
}
