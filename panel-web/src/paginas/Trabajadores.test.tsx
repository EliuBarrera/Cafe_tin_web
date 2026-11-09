import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import type { Trabajador, Usuario } from '../api/tipos'
import { borrarToken, guardarToken } from '../sesion/almacen'
import { SesionProvider } from '../sesion/SesionProvider'

const dueno: Usuario = { id: 1, nombre: 'Rosa', apellidos: 'Quispe', correo: 'rosa@cafetin.pe', rol: 'DUENO' }

function json(estado: number, cuerpo?: unknown) {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json' },
  })
}

type Manejador = (cuerpo: unknown) => Response

/** API falsa con sesión de dueño. `trabajadores` se lee en cada GET: el test la cambia para simular el refresco. */
function apiFalsa(estado: { trabajadores: Trabajador[] }, acciones: Record<string, Manejador> = {}) {
  const espia = vi.fn(async (url: string, opciones?: RequestInit) => {
    const metodo = opciones?.method ?? 'GET'
    const ruta = new URL(url).pathname
    if (metodo === 'GET' && ruta === '/api/usuarios/me') return json(200, dueno)
    if (metodo === 'GET' && ruta === '/api/alertas') return json(200, [])
    if (metodo === 'GET' && ruta === '/api/trabajadores') return json(200, estado.trabajadores)
    const manejador = acciones[`${metodo} ${ruta}`]
    if (!manejador) throw new Error(`Petición no esperada: ${metodo} ${ruta}`)
    return manejador(opciones?.body ? JSON.parse(String(opciones.body)) : undefined)
  })
  vi.stubGlobal('fetch', espia)
  return espia
}

function abrir() {
  guardarToken('token-de-prueba')
  const clienteQuery = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={clienteQuery}>
      <SesionProvider>
        <MemoryRouter initialEntries={['/trabajadores']}>
          <App />
        </MemoryRouter>
      </SesionProvider>
    </QueryClientProvider>,
  )
}

const trabajador = (id: number, nombre: string, cambios: Partial<Trabajador> = {}): Trabajador => ({
  id,
  nombre,
  apellidos: 'Díaz Morales',
  correo: `${nombre.toLowerCase()}@cafetin.pe`,
  telefono: null,
  codigoEmpleado: `TRB-00${id}`,
  turno: 'MANANA',
  fechaIngreso: '2026-11-09',
  activo: true,
  ...cambios,
})

const fila = (texto: string) => screen.findByRole('row', { name: new RegExp(texto) })
const formulario = () => screen.getByRole('form', { name: 'Nuevo trabajador' })

async function llenarFormulario(datos: Partial<Record<string, string>> = {}) {
  const form = formulario()
  const valores: Record<string, string> = {
    Nombre: 'Luis',
    Apellidos: 'Quispe Rojas',
    Correo: 'luis@cafetin.pe',
    'Código de empleado': 'TRB-003',
    'Contraseña inicial': 'Cafetin2026',
    ...datos,
  }
  for (const [etiqueta, valor] of Object.entries(valores)) {
    if (valor) await userEvent.type(within(form).getByLabelText(etiqueta), valor)
  }
  return form
}

beforeEach(() => {
  borrarToken()
})

describe('Trabajadores (RF-02)', () => {
  it('lista los trabajadores con su turno y marca los inactivos', async () => {
    apiFalsa({
      trabajadores: [
        trabajador(1, 'Carmen', { telefono: '944123456' }),
        trabajador(2, 'Pedro', { turno: 'TARDE', activo: false }),
      ],
    })
    abrir()

    const carmen = await fila('Carmen')
    expect(carmen).toHaveTextContent('TRB-001')
    expect(carmen).toHaveTextContent('Mañana')
    expect(within(carmen).getByRole('link', { name: '944123456' })).toHaveAttribute('href', 'tel:944123456')
    const pedro = await fila('Pedro')
    expect(pedro).toHaveTextContent('Tarde')
    expect(pedro).toHaveTextContent('Inactivo')
    expect(within(pedro).getByRole('button', { name: 'Activar' })).toBeInTheDocument()
  })

  it('registra un trabajador sin enviar el teléfono vacío y refresca la lista', async () => {
    const estado = { trabajadores: [] as Trabajador[] }
    const crear = vi.fn((cuerpo: unknown) => {
      estado.trabajadores = [trabajador(3, 'Luis')]
      return json(201, cuerpo)
    })
    apiFalsa(estado, { 'POST /api/trabajadores': crear })
    abrir()
    expect(await screen.findByText('Todavía no hay trabajadores.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Nuevo trabajador' }))
    const form = await llenarFormulario()
    await userEvent.selectOptions(within(form).getByLabelText('Turno'), 'TARDE')
    await userEvent.click(within(form).getByRole('button', { name: 'Registrar' }))

    await waitFor(() =>
      expect(crear).toHaveBeenCalledWith({
        nombre: 'Luis',
        apellidos: 'Quispe Rojas',
        correo: 'luis@cafetin.pe',
        codigoEmpleado: 'TRB-003',
        turno: 'TARDE',
        contrasena: 'Cafetin2026',
      }),
    )
    expect(await fila('Luis')).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Nuevo trabajador' })).not.toBeInTheDocument()
  })

  it('avisa antes de enviar si el teléfono no tiene 9 dígitos o la contraseña es corta', async () => {
    const crear = vi.fn(() => json(201, {}))
    apiFalsa({ trabajadores: [] }, { 'POST /api/trabajadores': crear })
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo trabajador' }))
    const form = await llenarFormulario({ Teléfono: '04412', 'Contraseña inicial': 'corta' })
    await userEvent.click(within(form).getByRole('button', { name: 'Registrar' }))

    const avisos = await within(form).findAllByRole('alert')
    expect(avisos.map((a) => a.textContent)).toEqual([
      'El teléfono debe tener 9 dígitos.',
      'La contraseña debe tener al menos 8 caracteres.',
    ])
    expect(crear).not.toHaveBeenCalled()
  })

  it('un correo o código repetido muestra el mensaje de la API', async () => {
    apiFalsa(
      { trabajadores: [] },
      {
        'POST /api/trabajadores': () =>
          json(409, { codigo: 'CONFLICTO', mensaje: 'Ya existe una cuenta con el correo luis@cafetin.pe', detalles: [] }),
      },
    )
    abrir()

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo trabajador' }))
    const form = await llenarFormulario()
    await userEvent.click(within(form).getByRole('button', { name: 'Registrar' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('Ya existe una cuenta con el correo')
  })

  it('desactiva y activa: un trabajador inactivo no puede iniciar sesión, y se puede deshacer', async () => {
    const estado = { trabajadores: [trabajador(1, 'Carmen')] }
    const cambiar = vi.fn((cuerpo: unknown) => {
      estado.trabajadores = [trabajador(1, 'Carmen', { activo: (cuerpo as { activo: boolean }).activo })]
      return json(200, estado.trabajadores[0])
    })
    apiFalsa(estado, { 'PATCH /api/trabajadores/1/estado': cambiar })
    abrir()

    await userEvent.click(within(await fila('Carmen')).getByRole('button', { name: 'Desactivar' }))
    await waitFor(() => expect(cambiar).toHaveBeenCalledWith({ activo: false }))
    expect(await within(await fila('Carmen')).findByRole('button', { name: 'Activar' })).toBeInTheDocument()

    await userEvent.click(within(await fila('Carmen')).getByRole('button', { name: 'Activar' }))
    await waitFor(() => expect(cambiar).toHaveBeenLastCalledWith({ activo: true }))
  })
})
