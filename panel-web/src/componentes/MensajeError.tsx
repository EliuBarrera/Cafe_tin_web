import { ErrorApi } from '../api/cliente'

export function MensajeError({ error }: { error: unknown }) {
  if (!error) return null
  const mensaje = error instanceof ErrorApi ? error.message : 'Ocurrió un error inesperado. Intenta de nuevo.'
  const detalles = error instanceof ErrorApi ? error.detalles : []

  return (
    <div className="mensaje-error" role="alert">
      <p>{mensaje}</p>
      {detalles.length > 0 && (
        <ul>
          {detalles.map((d) => (
            <li key={`${d.campo}-${d.mensaje}`}>{d.mensaje}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
