import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'

import { MarcoPublico } from '../componentes/MarcoPublico'
import { MensajeError } from '../componentes/MensajeError'
import { RUTA_INICIO } from '../navegacion'
import { useSesion } from '../sesion/SesionProvider'

export function Login() {
  const { usuario, iniciarSesion } = useSesion()
  const ubicacion = useLocation()
  const [correo, setCorreo] = useState('')
  const [contrasena, setContrasena] = useState('')

  const entrar = useMutation({ mutationFn: () => iniciarSesion(correo.trim(), contrasena) })

  if (usuario) {
    const desde = (ubicacion.state as { desde?: string } | null)?.desde
    return <Navigate to={desde ?? RUTA_INICIO} replace />
  }

  function enviar(evento: FormEvent) {
    evento.preventDefault()
    entrar.mutate()
  }

  return (
    <MarcoPublico titulo="Iniciar sesión">
      <form className="formulario" onSubmit={enviar}>
        <label>
          Correo
          <input type="email" autoComplete="username" required value={correo} onChange={(e) => setCorreo(e.target.value)} />
        </label>
        <label>
          Contraseña
          <input
            type="password"
            autoComplete="current-password"
            required
            value={contrasena}
            onChange={(e) => setContrasena(e.target.value)}
          />
        </label>
        <MensajeError error={entrar.error} />
        <button type="submit" className="boton" disabled={entrar.isPending}>
          {entrar.isPending ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
      <Link to="/recuperar" className="enlace-secundario">
        ¿Olvidaste tu contraseña?
      </Link>
    </MarcoPublico>
  )
}
