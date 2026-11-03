import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from './App'
import type { Rol, Usuario } from './api/tipos'
import { borrarToken, guardarToken, leerToken } from './sesion/almacen'
import { SesionProvider } from './sesion/SesionProvider'

const dueno: Usuario = { id: 1, nombre: 'Rosa', apellidos: 'Quispe', correo: 'rosa@cafetin.pe', rol: 'DUENO' }
const trabajador: Usuario = { id: 2, nombre: 'Carmen', apellidos: 'Díaz', correo: 'carmen@cafetin.pe', rol: 'TRABAJADOR' }

function json(estado: number, cuerpo?: unknown) {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json' },
  })
}

type Ruta = `${'GET' | 'POST'} ${string}`

/** Sustituye fetch: cada clave "MÉTODO /ruta" da la respuesta de la API falsa. */
function apiFalsa(rutas: Record<Ruta, () => Response>) {
  const espia = vi.fn(async (url: string, opciones?: RequestInit) => {
    const clave = `${opciones?.method ?? 'GET'} ${new URL(url).pathname}` as Ruta
    const respuesta = rutas[clave]
    if (!respuesta) throw new Error(`Petición no esperada: ${clave}`)
    return respuesta()
  })
  vi.stubGlobal('fetch', espia)
  return espia
}

function cuerpoDe(espia: ReturnType<typeof apiFalsa>, ruta: string) {
  const llamada = espia.mock.calls.find(([url]) => url.endsWith(ruta))
  return JSON.parse(String(llamada?.[1]?.body))
}

function abrir(ruta: string) {
  const clienteQuery = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={clienteQuery}>
      <SesionProvider>
        <MemoryRouter initialEntries={[ruta]}>
          <App />
        </MemoryRouter>
      </SesionProvider>
    </QueryClientProvider>,
  )
}

function conSesion(usuario: Usuario) {
  guardarToken('token-de-prueba')
  return { 'GET /api/usuarios/me': () => json(200, usuario) } as Record<Ruta, () => Response>
}

async function entrar(correo: string, contrasena = 'Cafetin2026') {
  await userEvent.type(screen.getByLabelText('Correo'), correo)
  await userEvent.type(screen.getByLabelText('Contraseña'), contrasena)
  await userEvent.click(screen.getByRole('button', { name: 'Entrar' }))
}

const respuestaLogin = (usuario: { rol: Rol } & Partial<Usuario>) =>
  json(200, { token: 'token-nuevo', expiraEn: 28800, usuario })

beforeEach(() => {
  borrarToken()
})

describe('login', () => {
  it('entra al panel y guarda el token', async () => {
    const espia = apiFalsa({ 'POST /api/auth/login': () => respuestaLogin(trabajador) })
    abrir('/login')

    await entrar('carmen@cafetin.pe')

    expect(await screen.findByRole('heading', { name: 'Despacho' })).toBeInTheDocument()
    expect(leerToken()).toBe('token-nuevo')
    expect(cuerpoDe(espia, '/api/auth/login')).toEqual({ correo: 'carmen@cafetin.pe', contrasena: 'Cafetin2026' })
  })

  it('muestra el mensaje de la API si las credenciales son incorrectas', async () => {
    apiFalsa({
      'POST /api/auth/login': () =>
        json(401, { codigo: 'NO_AUTENTICADO', mensaje: 'Correo o contraseña incorrectos', detalles: [] }),
    })
    abrir('/login')

    await entrar('carmen@cafetin.pe', 'mala-clave')

    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos')
    expect(leerToken()).toBeNull()
  })

  it('no deja entrar a un estudiante ni guarda su token', async () => {
    apiFalsa({ 'POST /api/auth/login': () => respuestaLogin({ ...trabajador, rol: 'ESTUDIANTE' }) })
    abrir('/login')

    await entrar('ana@ucvvirtual.edu.pe')

    expect(await screen.findByRole('alert')).toHaveTextContent('Las cuentas de estudiante usan la app móvil.')
    expect(leerToken()).toBeNull()
  })

  it('avisa si no hay conexión con la API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    abrir('/login')

    await entrar('carmen@cafetin.pe')

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo conectar con el servidor')
  })

  it('vuelve a la página que se pidió antes de iniciar sesión', async () => {
    apiFalsa({ 'POST /api/auth/login': () => respuestaLogin(dueno) })
    abrir('/reportes')

    await screen.findByRole('heading', { name: 'Iniciar sesión' })
    await entrar('rosa@cafetin.pe')

    expect(await screen.findByRole('heading', { name: 'Reportes' })).toBeInTheDocument()
  })
})

describe('rutas protegidas y menú por rol', () => {
  it('sin sesión envía al login', async () => {
    apiFalsa({} as Record<Ruta, () => Response>)
    abrir('/despacho')

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
  })

  it('el trabajador solo ve Despacho en el menú', async () => {
    apiFalsa(conSesion(trabajador))
    abrir('/')

    const menu = await screen.findByRole('navigation', { name: 'Principal' })
    expect(within(menu).getAllByRole('link').map((a) => a.textContent)).toEqual(['Despacho'])
  })

  it('el dueño ve todas las secciones', async () => {
    apiFalsa(conSesion(dueno))
    abrir('/')

    const menu = await screen.findByRole('navigation', { name: 'Principal' })
    expect(within(menu).getAllByRole('link').map((a) => a.textContent)).toEqual([
      'Despacho',
      'Productos',
      'Categorías',
      'Proveedores',
      'Alertas',
      'Reportes',
      'Trabajadores',
    ])
  })

  it('el trabajador que abre una ruta del dueño vuelve a Despacho', async () => {
    apiFalsa(conSesion(trabajador))
    abrir('/reportes')

    expect(await screen.findByRole('heading', { name: 'Despacho' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Reportes' })).not.toBeInTheDocument()
  })

  it('cerrar sesión borra el token y vuelve al login', async () => {
    apiFalsa(conSesion(dueno))
    abrir('/')

    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar sesión' }))

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(leerToken()).toBeNull()
  })

  it('si la API rechaza el token guardado, cierra la sesión', async () => {
    guardarToken('token-vencido')
    apiFalsa({
      'GET /api/usuarios/me': () => json(401, { codigo: 'NO_AUTENTICADO', mensaje: 'Inicia sesión', detalles: [] }),
    })
    abrir('/despacho')

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    await waitFor(() => expect(leerToken()).toBeNull())
  })

  it('un fallo del servidor conserva la sesión y permite reintentar', async () => {
    guardarToken('token-de-prueba')
    let intentos = 0
    apiFalsa({
      'GET /api/usuarios/me': () => {
        intentos += 1
        return intentos === 1
          ? json(500, { codigo: 'ERROR_INTERNO', mensaje: 'Ocurrió un error inesperado', detalles: [] })
          : json(200, trabajador)
      },
    })
    abrir('/despacho')

    await userEvent.click(await screen.findByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByRole('heading', { name: 'Despacho' })).toBeInTheDocument()
    expect(leerToken()).toBe('token-de-prueba')
  })
})

describe('recuperar contraseña', () => {
  it('confirma sin revelar si el correo existe', async () => {
    const espia = apiFalsa({ 'POST /api/auth/recuperar': () => json(202) })
    abrir('/recuperar')

    await userEvent.type(screen.getByLabelText('Correo'), 'carmen@cafetin.pe')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar enlace' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Si el correo está registrado')
    expect(cuerpoDe(espia, '/api/auth/recuperar')).toEqual({ correo: 'carmen@cafetin.pe' })
  })

  it('restablece con el token del enlace', async () => {
    const espia = apiFalsa({ 'POST /api/auth/restablecer': () => json(204) })
    abrir('/restablecer?token=abc123')

    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'NuevaClave2026')
    await userEvent.type(screen.getByLabelText('Repite la contraseña'), 'NuevaClave2026')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Ya puedes entrar')
    expect(cuerpoDe(espia, '/api/auth/restablecer')).toEqual({ token: 'abc123', nuevaContrasena: 'NuevaClave2026' })
  })

  it('no envía si las contraseñas no coinciden', async () => {
    apiFalsa({} as Record<Ruta, () => Response>)
    abrir('/restablecer?token=abc123')

    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'NuevaClave2026')
    await userEvent.type(screen.getByLabelText('Repite la contraseña'), 'OtraClave2026')

    expect(screen.getByRole('alert')).toHaveTextContent('Las contraseñas no coinciden')
    expect(screen.getByRole('button', { name: 'Guardar contraseña' })).toBeDisabled()
  })

  it('muestra el error de la API si el enlace expiró', async () => {
    apiFalsa({
      'POST /api/auth/restablecer': () =>
        json(409, { codigo: 'REGLA_NEGOCIO', mensaje: 'El enlace de recuperación no es válido o ya expiró', detalles: [] }),
    })
    abrir('/restablecer?token=viejo')

    await userEvent.type(screen.getByLabelText('Contraseña nueva'), 'NuevaClave2026')
    await userEvent.type(screen.getByLabelText('Repite la contraseña'), 'NuevaClave2026')
    await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('no es válido o ya expiró')
  })

  it('sin token en el enlace ofrece pedir otro', async () => {
    apiFalsa({} as Record<Ruta, () => Response>)
    abrir('/restablecer')

    expect(screen.getByRole('heading', { name: 'Enlace no válido' })).toBeInTheDocument()
  })
})
