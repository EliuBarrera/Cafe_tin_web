import { Navigate, Outlet, useLocation } from 'react-router'

import type { Rol } from '../api/tipos'
import { useSesion } from './SesionProvider'

/** Solo decide qué se muestra: quien realmente valida el acceso es la API (403). */
export function RutaProtegida({ roles }: { roles?: Rol[] }) {
  const { usuario, cargando, errorDeCarga, reintentar } = useSesion()
  const ubicacion = useLocation()

  if (cargando) return <p className="estado-pagina">Cargando…</p>

  if (errorDeCarga) {
    return (
      <div className="estado-pagina" role="alert">
        <p>{errorDeCarga.message}</p>
        <button type="button" className="boton" onClick={reintentar}>
          Reintentar
        </button>
      </div>
    )
  }

  if (!usuario) return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />
  if (roles && !roles.includes(usuario.rol)) return <Navigate to="/" replace />

  return <Outlet />
}
