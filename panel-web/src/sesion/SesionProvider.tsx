import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

import { api, ErrorApi, registrarAlRechazarToken } from '../api/cliente'
import type { TokenRespuesta, Usuario } from '../api/tipos'
import { borrarToken, guardarToken, leerToken } from './almacen'

const CLAVE_PERFIL = ['usuarios', 'me']

interface Sesion {
  usuario: Usuario | null
  cargando: boolean
  /** La sesión existe pero no se pudo comprobar (p. ej. la API está despertando). */
  errorDeCarga: ErrorApi | null
  reintentar: () => void
  iniciarSesion: (correo: string, contrasena: string) => Promise<void>
  cerrarSesion: () => void
}

const ContextoSesion = createContext<Sesion | null>(null)

export function SesionProvider({ children }: { children: ReactNode }) {
  const clienteQuery = useQueryClient()
  const [token, setToken] = useState(leerToken)

  const perfil = useQuery({
    queryKey: CLAVE_PERFIL,
    queryFn: () => api<Usuario>('/api/usuarios/me'),
    enabled: token !== null,
    staleTime: Infinity,
  })

  const cerrarSesion = useCallback(() => {
    borrarToken()
    setToken(null)
    clienteQuery.clear()
  }, [clienteQuery])

  useEffect(() => registrarAlRechazarToken(cerrarSesion), [cerrarSesion])

  const iniciarSesion = useCallback(
    async (correo: string, contrasena: string) => {
      const respuesta = await api<TokenRespuesta>('/api/auth/login', {
        metodo: 'POST',
        cuerpo: { correo, contrasena },
      })
      if (respuesta.usuario.rol === 'ESTUDIANTE') {
        throw new ErrorApi(403, 'ACCESO_DENEGADO', 'Las cuentas de estudiante usan la app móvil.')
      }
      guardarToken(respuesta.token)
      clienteQuery.setQueryData(CLAVE_PERFIL, respuesta.usuario)
      setToken(respuesta.token)
    },
    [clienteQuery],
  )

  const valor = useMemo<Sesion>(
    () => ({
      usuario: token !== null ? (perfil.data ?? null) : null,
      cargando: token !== null && perfil.isPending,
      errorDeCarga: token !== null && perfil.error instanceof ErrorApi ? perfil.error : null,
      reintentar: () => void perfil.refetch(),
      iniciarSesion,
      cerrarSesion,
    }),
    [token, perfil.data, perfil.isPending, perfil.error, perfil.refetch, iniciarSesion, cerrarSesion],
  )

  return <ContextoSesion.Provider value={valor}>{children}</ContextoSesion.Provider>
}

export function useSesion(): Sesion {
  const sesion = useContext(ContextoSesion)
  if (!sesion) throw new Error('useSesion debe usarse dentro de SesionProvider')
  return sesion
}
