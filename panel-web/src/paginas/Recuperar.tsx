import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'

import { api } from '../api/cliente'
import { MarcoPublico } from '../componentes/MarcoPublico'
import { MensajeError } from '../componentes/MensajeError'

export function Recuperar() {
  const [correo, setCorreo] = useState('')
  const pedir = useMutation({
    mutationFn: () => api<void>('/api/auth/recuperar', { metodo: 'POST', cuerpo: { correo: correo.trim() } }),
  })

  function enviar(evento: FormEvent) {
    evento.preventDefault()
    pedir.mutate()
  }

  return (
    <MarcoPublico titulo="Recuperar contraseña">
      {pedir.isSuccess ? (
        <p role="status">Si el correo está registrado, recibirás un enlace para crear una contraseña nueva.</p>
      ) : (
        <form className="formulario" onSubmit={enviar}>
          <label>
            Correo
            <input type="email" autoComplete="username" required value={correo} onChange={(e) => setCorreo(e.target.value)} />
          </label>
          <MensajeError error={pedir.error} />
          <button type="submit" className="boton" disabled={pedir.isPending}>
            {pedir.isPending ? 'Enviando…' : 'Enviar enlace'}
          </button>
        </form>
      )}
      <Link to="/login" className="enlace-secundario">
        Volver a iniciar sesión
      </Link>
    </MarcoPublico>
  )
}
