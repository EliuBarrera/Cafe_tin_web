import { NavLink, Outlet } from 'react-router'

import { SECCIONES } from '../navegacion'
import { useSesion } from '../sesion/SesionProvider'
import { ContadorAlertas } from './ContadorAlertas'

const NOMBRE_ROL: Record<string, string> = { TRABAJADOR: 'Trabajador', DUENO: 'Dueño', ESTUDIANTE: 'Estudiante' }

function iniciales(nombre: string) {
  return nombre
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join('')
}

export function Shell() {
  const { usuario, cerrarSesion } = useSesion()
  if (!usuario) return null

  return (
    <div className="shell">
      <header className="shell-barra">
        <span className="marca-panel">
          <span className="insignia-marca" aria-hidden="true">
            ☕
          </span>
          <span>
            <span className="marca">Cafetín UCV</span>
            <span className="marca-subtitulo">{usuario.cafeteria?.nombre ?? 'Panel'}</span>
          </span>
        </span>
        <nav aria-label="Principal" className="shell-menu">
          {SECCIONES.filter((s) => s.roles.includes(usuario.rol)).map((s) => (
            <NavLink key={s.ruta} to={`/${s.ruta}`}>
              {s.titulo}
              {s.ruta === 'alertas' && <ContadorAlertas />}
            </NavLink>
          ))}
        </nav>
        <div className="shell-usuario">
          <span className="avatar-usuario" aria-hidden="true">
            {iniciales(usuario.nombre)}
          </span>
          <span className="usuario-datos">
            <span>{usuario.nombre}</span>
            <span className="texto-suave usuario-rol">{NOMBRE_ROL[usuario.rol] ?? usuario.rol}</span>
          </span>
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
