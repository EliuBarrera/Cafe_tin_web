import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'

import { api } from '../api/cliente'
import { MarcoPublico } from '../componentes/MarcoPublico'
import { MensajeError } from '../componentes/MensajeError'

export function Restablecer() {
  const [parametros] = useSearchParams()
  const token = parametros.get('token')
  const [nueva, setNueva] = useState('')
  const [repetida, setRepetida] = useState('')

  const guardar = useMutation({
    mutationFn: () =>
      api<void>('/api/auth/restablecer', { metodo: 'POST', cuerpo: { token, nuevaContrasena: nueva } }),
  })

  function enviar(evento: FormEvent) {
    evento.preventDefault()
    guardar.mutate()
  }

  if (!token) {
    return (
      <MarcoPublico titulo="Enlace no válido">
        <p>Este enlace está incompleto. Pide uno nuevo.</p>
        <Link to="/recuperar" className="enlace-secundario">
          Pedir otro enlace
        </Link>
      </MarcoPublico>
    )
  }

  if (guardar.isSuccess) {
    return (
      <MarcoPublico titulo="Contraseña actualizada">
        <p role="status">Ya puedes entrar con tu contraseña nueva.</p>
        <Link to="/login" className="enlace-secundario">
          Iniciar sesión
        </Link>
      </MarcoPublico>
    )
  }

  const noCoinciden = repetida !== '' && repetida !== nueva

  return (
    <MarcoPublico titulo="Crear contraseña nueva">
      <form className="formulario" onSubmit={enviar}>
        <label>
          Contraseña nueva
          <input
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
            value={nueva}
            onChange={(e) => setNueva(e.target.value)}
          />
        </label>
        <label>
          Repite la contraseña
          <input
            type="password"
            autoComplete="new-password"
            required
            value={repetida}
            onChange={(e) => setRepetida(e.target.value)}
          />
        </label>
        {noCoinciden && (
          <p className="mensaje-error" role="alert">
            Las contraseñas no coinciden.
          </p>
        )}
        <MensajeError error={guardar.error} />
        <button type="submit" className="boton" disabled={guardar.isPending || noCoinciden}>
          {guardar.isPending ? 'Guardando…' : 'Guardar contraseña'}
        </button>
      </form>
      <Link to="/recuperar" className="enlace-secundario">
        Pedir otro enlace
      </Link>
    </MarcoPublico>
  )
}
