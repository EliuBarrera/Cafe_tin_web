import { NavLink, Outlet } from 'react-router'

import { SECCIONES } from '../navegacion'
import { useSesion } from '../sesion/SesionProvider'
import { ContadorAlertas } from './ContadorAlertas'

export function Shell() {
  const { usuario, cerrarSesion } = useSesion()
  if (!usuario) return null

  return (
    <div className="shell">
      <header className="shell-barra">
        <span className="marca">Cafetín</span>
        <nav aria-label="Principal" className="shell-menu">
          {SECCIONES.filter((s) => s.roles.includes(usuario.rol)).map((s) => (
            <NavLink key={s.ruta} to={`/${s.ruta}`}>
              {s.titulo}
              {s.ruta === 'alertas' && <ContadorAlertas />}
            </NavLink>
          ))}
        </nav>
        <div className="shell-usuario">
          <span>{usuario.nombre}</span>
          <button type="button" className="boton-texto" onClick={cerrarSesion}>
            Cerrar sesión
          </button>
        </div>
      </header>
      <main className="shell-contenido">
        <Outlet />
      </main>
    </div>
  )
}
