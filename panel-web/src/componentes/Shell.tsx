import { useEffect, useId, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'

import { SECCIONES } from '../navegacion'
import { useSesion } from '../sesion/SesionProvider'
import { ContadorAlertas } from './ContadorAlertas'

const NOMBRE_ROL: Record<string, string> = { TRABAJADOR: 'Trabajador', DUENO: 'Dueño', ESTUDIANTE: 'Estudiante' }

const FECHA = new Intl.DateTimeFormat('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric' })

/**
 * Barra del panel como dos tickets de papel colgados del borde superior: a la izquierda el logo y a la
 * derecha un recibo plegado que, al abrirse, lista las secciones como renglones de una cuenta.
 * El menú queda siempre en el DOM y se oculta con `visibility` (también para lectores de pantalla).
 */
export function Shell() {
  const { usuario, cerrarSesion } = useSesion()
  const [abierto, setAbierto] = useState(false)
  const recibo = useRef<HTMLDivElement>(null)
  const idMenu = useId()
  const { pathname } = useLocation()

  // Al navegar, el recibo se vuelve a plegar
  useEffect(() => setAbierto(false), [pathname])

  // Escape o un clic fuera lo pliegan
  useEffect(() => {
    if (!abierto) return
    const alTeclear = (e: KeyboardEvent) => e.key === 'Escape' && setAbierto(false)
    const alPulsar = (e: PointerEvent) => {
      if (recibo.current && !recibo.current.contains(e.target as Node)) setAbierto(false)
    }
    document.addEventListener('keydown', alTeclear)
    document.addEventListener('pointerdown', alPulsar)
    return () => {
      document.removeEventListener('keydown', alTeclear)
      document.removeEventListener('pointerdown', alPulsar)
    }
  }, [abierto])

  if (!usuario) return null

  const secciones = SECCIONES.filter((s) => s.roles.includes(usuario.rol))
  const actual = secciones.find((s) => pathname.startsWith(`/${s.ruta}`))

  return (
    <div className="shell">
      <header className="shell-barra">
        <NavLink to="/" className="ticket-logo" aria-label="Cafetín UCV, inicio">
          <span className="ticket">
            <img src="/logo-cafetin.png" alt="" width="64" height="61" />
          </span>
        </NavLink>

        <div ref={recibo} className={`recibo${abierto ? ' recibo-abierto' : ''}`}>
          <button
            type="button"
            className="ticket recibo-pestana"
            aria-expanded={abierto}
            aria-controls={idMenu}
            onClick={() => setAbierto((a) => !a)}
          >
            <span className="recibo-pestana-titulo">Menú</span>
            <span className="recibo-pestana-actual">{actual?.titulo ?? 'Panel'}</span>
            {usuario.rol === 'DUENO' && <ContadorAlertas />}
          </button>

          <div id={idMenu} className="recibo-papel">
            <div className="recibo-cabecera">
              <span className="recibo-marca">Cafetín UCV</span>
              <span>{usuario.cafeteria?.nombre ?? 'Panel'}</span>
              <span className="recibo-fecha">{FECHA.format(new Date())}</span>
            </div>

            <nav aria-label="Principal" className="shell-menu">
              {secciones.map((s) => (
                <NavLink key={s.ruta} to={`/${s.ruta}`}>
                  {s.titulo}
                  {s.ruta === 'alertas' && <ContadorAlertas />}
                </NavLink>
              ))}
            </nav>

            <div className="recibo-total">
              <span>Atiende</span>
              <span className="recibo-usuario">
                <strong>{usuario.nombre}</strong>
                <span>{NOMBRE_ROL[usuario.rol] ?? usuario.rol}</span>
              </span>
            </div>

            <button type="button" className="boton boton-secundario recibo-salir" onClick={cerrarSesion}>
              Cerrar sesión
            </button>

            <p className="recibo-gracias">¡Gracias por no hacer fila!</p>
            <span className="recibo-barras" aria-hidden="true" />
          </div>
        </div>
      </header>
      <main className="shell-contenido">
        <Outlet />
      </main>
    </div>
  )
}
