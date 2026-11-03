import { leerToken } from '../sesion/almacen'
import type { CampoError } from './tipos'

const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

/** Error de la API. Decide por `codigo`, nunca por el texto de `message`. */
export class ErrorApi extends Error {
  constructor(
    readonly estado: number,
    readonly codigo: string,
    mensaje: string,
    readonly detalles: CampoError[] = [],
  ) {
    super(mensaje)
    this.name = 'ErrorApi'
  }
}

let alRechazarToken: () => void = () => {}

/** Se llama cuando la API rechaza el token guardado (venció o la cuenta se desactivó). */
export function registrarAlRechazarToken(accion: () => void) {
  alRechazarToken = accion
}

interface Opciones {
  metodo?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  cuerpo?: unknown
}

export async function api<T>(ruta: string, { metodo = 'GET', cuerpo }: Opciones = {}): Promise<T> {
  const token = leerToken()
  const encabezados: Record<string, string> = {}
  if (token) encabezados.Authorization = `Bearer ${token}`
  if (cuerpo !== undefined) encabezados['Content-Type'] = 'application/json'

  let respuesta: Response
  try {
    respuesta = await fetch(`${BASE}${ruta}`, {
      method: metodo,
      headers: encabezados,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
  } catch {
    throw new ErrorApi(0, 'SIN_CONEXION', 'No se pudo conectar con el servidor. Intenta de nuevo.')
  }

  if (respuesta.ok) {
    return (respuesta.status === 202 || respuesta.status === 204 ? undefined : await respuesta.json()) as T
  }

  // En /api/auth/** un 401 es "contraseña incorrecta", no una sesión vencida.
  if (respuesta.status === 401 && token && !ruta.startsWith('/api/auth/')) {
    alRechazarToken()
  }

  const error = await respuesta.json().catch(() => null)
  throw new ErrorApi(
    respuesta.status,
    error?.codigo ?? 'ERROR_INTERNO',
    error?.mensaje ?? 'Ocurrió un error inesperado. Intenta de nuevo.',
    error?.detalles ?? [],
  )
}
