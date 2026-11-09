import { useQuery } from '@tanstack/react-query'

import { api, ErrorApi } from '../api/cliente'
import type { Alerta } from '../api/tipos'

/** Igual que el despacho: polling cada 5 s (decisión del 02/11/2026). */
export const INTERVALO_ALERTAS_MS = 5000

/**
 * Número de alertas de stock pendientes junto a "Alertas" en el menú del dueño (RF-17). Si la API
 * responde 501 (aún no implementada) o 403, no muestra nada y deja de consultar: no tiene sentido
 * despertar a Neon cada 5 s para recibir el mismo error. Con la pestaña oculta tampoco consulta.
 */
export function ContadorAlertas() {
  const { data } = useQuery({
    queryKey: ['alertas', 'pendientes'],
    queryFn: () => api<Alerta[]>('/api/alertas'),
    refetchInterval: (consulta) => {
      const error = consulta.state.error
      return error instanceof ErrorApi && (error.estado === 501 || error.estado === 403) ? false : INTERVALO_ALERTAS_MS
    },
    retry: false, // el siguiente intento ya llega en 5 s
  })

  const pendientes = data?.length ?? 0
  if (pendientes === 0) return null

  return (
    <>
      <span className="contador" aria-hidden="true">
        {pendientes > 99 ? '99+' : pendientes}
      </span>
      <span className="solo-lector">, {pendientes} pendientes</span>
    </>
  )
}
