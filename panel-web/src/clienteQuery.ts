import { QueryClient } from '@tanstack/react-query'

import { ErrorApi } from './api/cliente'

/** Reintenta fallos de red o del servidor; un 4xx no va a cambiar por repetir la petición. */
export function crearClienteQuery() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (intentos, error) => {
          const esDelCliente = error instanceof ErrorApi && error.estado >= 400 && error.estado < 500
          return !esDelCliente && intentos < 2
        },
      },
    },
  })
}
