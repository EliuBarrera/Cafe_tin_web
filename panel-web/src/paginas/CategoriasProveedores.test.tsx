import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import type { Categoria, Proveedor, Usuario } from '../api/tipos'
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

/** API falsa con sesión de dueño. Las listas se leen en cada GET: el test las cambia para simular el refresco. */
function apiFalsa(estado: { categorias?: Categoria[]; proveedores?: Proveedor[] }, acciones: Record<string, Manejador> = {}) {
  const espia = vi.fn(async (url: string, opciones?: RequestInit) => {
    const metodo = opciones?.method ?? 'GET'
    const ruta = new URL(url).pathname
    if (metodo === 'GET' && ruta === '/api/usuarios/me') return json(200, dueno)
    if (metodo === 'GET' && ruta === '/api/alertas') return json(200, [])
    if (metodo === 'GET' && ruta === '/api/categorias') return json(200, estado.categorias ?? [])
    if (metodo === 'GET' && ruta === '/api/proveedores') return json(200, estado.proveedores ?? [])
    const manejador = acciones[`${metodo} ${ruta}`]
    if (!manejador) throw new Error(`Petición no esperada: ${metodo} ${ruta}`)
    return manejador(opciones?.body ? JSON.parse(String(opciones.body)) : undefined)
  })
  vi.stubGlobal('fetch', espia)
  return espia
}

function abrir(ruta: string) {
  guardarToken('token-de-prueba')
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

const fila = (texto: string) => screen.findByRole('row', { name: new RegExp(texto) })
const formulario = (titulo: string) => screen.getByRole('form', { name: titulo })

const categoria = (id: number, nombre: string, descripcion: string | null = null): Categoria => ({
  id,
  nombre,
  descripcion,
  activo: true,
})

const proveedor = (id: number, nombre: string, cambios: Partial<Proveedor> = {}): Proveedor => ({
  id,
  nombre,
  telefono: '044123456',
  correo: null,
  direccion: null,
  activo: true,
  ...cambios,
})

beforeEach(() => {
  borrarToken()
})

describe('Categorías (RF-15)', () => {
  it('lista las categorías activas', async () => {
    apiFalsa({ categorias: [categoria(1, 'Bebidas', 'Frías y calientes'), categoria(2, 'Snacks')] })
    abrir('/categorias')

    expect(within(await fila('Bebidas')).getByText('Frías y calientes')).toBeInTheDocument()
    expect(await fila('Snacks')).toBeInTheDocument()
  })

  it('crea una categoría y refresca la lista', async () => {
    const estado = { categorias: [categoria(1, 'Bebidas')] }
    const crear = vi.fn((cuerpo: unknown) => {
      estado.categorias = [...estado.categorias, categoria(5, 'Postres')]
      return json(201, cuerpo)
    })
    apiFalsa(estado, { 'POST /api/categorias': crear })
    abrir('/categorias')
    await fila('Bebidas')

    await userEvent.click(screen.getByRole('button', { name: 'Nueva categoría' }))
    const form = formulario('Nueva categoría')
    await userEvent.type(within(form).getByLabelText('Nombre'), 'Postres')
    await userEvent.type(within(form).getByLabelText('Descripción'), 'Dulces del día')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(crear).toHaveBeenCalledWith({ nombre: 'Postres', descripcion: 'Dulces del día' }))
    expect(await fila('Postres')).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Nueva categoría' })).not.toBeInTheDocument()
  })

  it('edita una categoría con sus datos precargados', async () => {
    const editar = vi.fn((cuerpo: unknown) => json(200, cuerpo))
    apiFalsa({ categorias: [categoria(1, 'Bebidas', 'Frías')] }, { 'PUT /api/categorias/1': editar })
    abrir('/categorias')

    await userEvent.click(within(await fila('Bebidas')).getByRole('button', { name: 'Editar' }))
    const form = formulario('Editar Bebidas')
    expect(within(form).getByLabelText('Descripción')).toHaveValue('Frías')
    await userEvent.clear(within(form).getByLabelText('Descripción'))
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(editar).toHaveBeenCalledWith({ nombre: 'Bebidas' }))
  })

  it('un nombre repetido muestra el mensaje de la API', async () => {
    apiFalsa(
      { categorias: [categoria(1, 'Bebidas')] },
      { 'POST /api/categorias': () => json(409, { codigo: 'CONFLICTO', mensaje: 'Ya existe una categoría llamado "Bebidas"', detalles: [] }) },
    )
    abrir('/categorias')
    await fila('Bebidas')

    await userEvent.click(screen.getByRole('button', { name: 'Nueva categoría' }))
    const form = formulario('Nueva categoría')
    await userEvent.type(within(form).getByLabelText('Nombre'), 'Bebidas')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('Ya existe una categoría')
  })

  it('desactivar pide confirmación porque luego no se puede reactivar desde el panel', async () => {
    const estado = { categorias: [categoria(1, 'Bebidas'), categoria(2, 'Snacks')] }
    const cambiar = vi.fn((cuerpo: unknown) => {
      estado.categorias = estado.categorias.filter((c) => c.id !== 2)
      return json(200, cuerpo)
    })
    apiFalsa(estado, { 'PATCH /api/categorias/2/estado': cambiar })
    abrir('/categorias')

    await userEvent.click(within(await fila('Snacks')).getByRole('button', { name: 'Desactivar' }))
    const confirmacion = screen.getByRole('alertdialog', { name: 'Desactivar Snacks' })
    expect(confirmacion).toHaveTextContent('no se puede volver a activar desde el panel')
    expect(cambiar).not.toHaveBeenCalled()

    await userEvent.click(within(confirmacion).getByRole('button', { name: 'Sí, desactivar' }))

    await waitFor(() => expect(cambiar).toHaveBeenCalledWith({ activo: false }))
    await waitFor(() => expect(screen.queryByRole('row', { name: /Snacks/ })).not.toBeInTheDocument())
    expect(await screen.findByRole('status')).toHaveTextContent('Snacks quedó desactivada.')
  })

  it('cancelar la confirmación no desactiva', async () => {
    const cambiar = vi.fn(() => json(200, {}))
    apiFalsa({ categorias: [categoria(2, 'Snacks')] }, { 'PATCH /api/categorias/2/estado': cambiar })
    abrir('/categorias')

    await userEvent.click(within(await fila('Snacks')).getByRole('button', { name: 'Desactivar' }))
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Cancelar' }))

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(cambiar).not.toHaveBeenCalled()
  })
})

describe('Proveedores (RF-15)', () => {
  it('lista todos, con el teléfono para llamar y los inactivos marcados', async () => {
    apiFalsa({
      proveedores: [
        proveedor(1, 'Distribuidora Norte', { correo: 'ventas@norte.pe', direccion: 'Av. España 123' }),
        proveedor(2, 'Panadería Sur', { activo: false }),
      ],
    })
    abrir('/proveedores')

    const norte = await fila('Distribuidora Norte')
    expect(within(norte).getByRole('link', { name: '044123456' })).toHaveAttribute('href', 'tel:044123456')
    expect(within(norte).getByText('ventas@norte.pe')).toBeInTheDocument()
    expect(within(norte).getByText('Av. España 123')).toBeInTheDocument()

    const sur = await fila('Panadería Sur')
    expect(within(sur).getByText('Inactivo')).toBeInTheDocument()
    expect(within(sur).getByRole('button', { name: 'Activar' })).toBeInTheDocument()
  })

  it('crea un proveedor sin enviar los campos opcionales vacíos', async () => {
    const crear = vi.fn((cuerpo: unknown) => json(201, cuerpo))
    apiFalsa({ proveedores: [] }, { 'POST /api/proveedores': crear })
    abrir('/proveedores')
    expect(await screen.findByText('Todavía no hay proveedores.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Nuevo proveedor' }))
    const form = formulario('Nuevo proveedor')
    await userEvent.type(within(form).getByLabelText('Nombre'), 'Distribuidora Norte')
    await userEvent.type(within(form).getByLabelText('Teléfono'), '044123456')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(crear).toHaveBeenCalledWith({ nombre: 'Distribuidora Norte', telefono: '044123456' }))
  })

  it('un teléfono que no son solo dígitos se avisa antes de enviar', async () => {
    const crear = vi.fn(() => json(201, {}))
    apiFalsa({ proveedores: [] }, { 'POST /api/proveedores': crear })
    abrir('/proveedores')

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo proveedor' }))
    const form = formulario('Nuevo proveedor')
    await userEvent.type(within(form).getByLabelText('Nombre'), 'Norte')
    await userEvent.type(within(form).getByLabelText('Teléfono'), '044-123')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('El teléfono debe tener solo dígitos, de 6 a 15.')
    expect(crear).not.toHaveBeenCalled()
  })

  it('edita un proveedor con sus datos precargados', async () => {
    const editar = vi.fn((cuerpo: unknown) => json(200, cuerpo))
    apiFalsa(
      { proveedores: [proveedor(1, 'Distribuidora Norte', { correo: 'ventas@norte.pe' })] },
      { 'PUT /api/proveedores/1': editar },
    )
    abrir('/proveedores')

    await userEvent.click(within(await fila('Distribuidora Norte')).getByRole('button', { name: 'Editar' }))
    const form = formulario('Editar Distribuidora Norte')
    const telefono = within(form).getByLabelText('Teléfono')
    await userEvent.clear(telefono)
    await userEvent.type(telefono, '944000111')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    await waitFor(() =>
      expect(editar).toHaveBeenCalledWith({ nombre: 'Distribuidora Norte', telefono: '944000111', correo: 'ventas@norte.pe' }),
    )
  })

  it('desactiva y activa sin confirmación: se puede deshacer', async () => {
    const cambiar = vi.fn((cuerpo: unknown) => json(200, cuerpo))
    apiFalsa({ proveedores: [proveedor(1, 'Distribuidora Norte')] }, { 'PATCH /api/proveedores/1/estado': cambiar })
    abrir('/proveedores')

    await userEvent.click(within(await fila('Distribuidora Norte')).getByRole('button', { name: 'Desactivar' }))

    await waitFor(() => expect(cambiar).toHaveBeenCalledWith({ activo: false }))
  })
})
