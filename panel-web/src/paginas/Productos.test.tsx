import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import type { Categoria, Producto, Proveedor, Usuario } from '../api/tipos'
import { borrarToken, guardarToken } from '../sesion/almacen'
import { SesionProvider } from '../sesion/SesionProvider'

const dueno: Usuario = { id: 1, nombre: 'Rosa', apellidos: 'Quispe', correo: 'rosa@cafetin.pe', rol: 'DUENO' }

const categorias: Categoria[] = [
  { id: 1, nombre: 'Bebidas', descripcion: null, activo: true },
  { id: 2, nombre: 'Snacks', descripcion: null, activo: true },
]

const proveedores: Proveedor[] = [
  { id: 7, nombre: 'Distribuidora Nor-Sur', telefono: '044123456', correo: null, direccion: null, activo: true },
  { id: 8, nombre: 'Proveedor dado de baja', telefono: '044000000', correo: null, direccion: null, activo: false },
]

function producto(cambios: Partial<Producto> & Pick<Producto, 'id' | 'nombre'>): Producto {
  return {
    descripcion: null,
    precio: 2,
    idCategoria: 2,
    categoria: 'Snacks',
    idProveedor: null,
    proveedor: null,
    imagenUrl: null,
    activo: true,
    cantidadDisponible: 30,
    stockMinimo: 10,
    estadoStock: 'DISPONIBLE',
    fechaActualizacionStock: '2026-11-09T08:00:00',
    ...cambios,
  }
}

function json(estado: number, cuerpo?: unknown) {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json' },
  })
}

type Manejador = (cuerpo: unknown, url: URL) => Response

/** API falsa con sesión de dueño. `productos` se lee en cada GET: el test lo cambia para simular el refresco. */
function apiFalsa(estado: { productos: Producto[] }, acciones: Record<string, Manejador> = {}) {
  const espia = vi.fn(async (url: string, opciones?: RequestInit) => {
    const metodo = opciones?.method ?? 'GET'
    const direccion = new URL(url)
    const ruta = direccion.pathname
    if (metodo === 'GET' && ruta === '/api/usuarios/me') return json(200, dueno)
    if (metodo === 'GET' && ruta === '/api/categorias') return json(200, categorias)
    if (metodo === 'GET' && ruta === '/api/proveedores') return json(200, proveedores)
    if (metodo === 'GET' && ruta === '/api/productos') {
      const activo = direccion.searchParams.get('activo')
      return json(200, estado.productos.filter((p) => activo === null || String(p.activo) === activo))
    }
    const manejador = acciones[`${metodo} ${ruta}`]
    if (!manejador) throw new Error(`Petición no esperada: ${metodo} ${ruta}`)
    return manejador(opciones?.body ? JSON.parse(String(opciones.body)) : undefined, direccion)
  })
  vi.stubGlobal('fetch', espia)
  return espia
}

function abrirProductos() {
  guardarToken('token-de-prueba')
  const clienteQuery = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={clienteQuery}>
      <SesionProvider>
        <MemoryRouter initialEntries={['/productos']}>
          <App />
        </MemoryRouter>
      </SesionProvider>
    </QueryClientProvider>,
  )
}

const fila = async (nombre: string) => screen.findByRole('row', { name: new RegExp(nombre) })
const formulario = (titulo: string | RegExp) => screen.getByRole('form', { name: titulo })

beforeEach(() => {
  borrarToken()
})

describe('Productos (RF-15, RF-18, RF-19)', () => {
  it('lista los productos con su stock, el mínimo y el estado', async () => {
    apiFalsa({
      productos: [
        producto({ id: 1, nombre: 'Chifles 50 g' }),
        producto({ id: 2, nombre: 'Gaseosa 500 ml', cantidadDisponible: 3, stockMinimo: 5, estadoStock: 'POCAS UNIDADES', precio: 3.5 }),
        producto({ id: 3, nombre: 'Tamal', activo: false, cantidadDisponible: 0, estadoStock: 'AGOTADO' }),
      ],
    })
    abrirProductos()

    const gaseosa = await fila('Gaseosa 500 ml')
    expect(within(gaseosa).getByText('S/ 3.50')).toBeInTheDocument()
    expect(within(gaseosa).getByText('3')).toBeInTheDocument()
    expect(within(gaseosa).getByText('mín. 5')).toBeInTheDocument()
    expect(within(gaseosa).getByText('Pocas unidades')).toBeInTheDocument()

    const tamal = await fila('Tamal')
    expect(within(tamal).getByText('Agotado')).toBeInTheDocument()
    expect(within(tamal).getByText('Deshabilitado')).toBeInTheDocument()
    expect(within(tamal).getByRole('button', { name: 'Habilitar' })).toBeInTheDocument()
  })

  it('filtra por activos o deshabilitados con el parámetro de la API', async () => {
    const espia = apiFalsa({
      productos: [producto({ id: 1, nombre: 'Chifles 50 g' }), producto({ id: 3, nombre: 'Tamal', activo: false })],
    })
    abrirProductos()
    await fila('Chifles 50 g')

    await userEvent.selectOptions(screen.getByLabelText('Mostrar'), 'Deshabilitados')

    await waitFor(() => expect(screen.queryByRole('row', { name: /Chifles/ })).not.toBeInTheDocument())
    expect(await fila('Tamal')).toBeInTheDocument()
    const urls = espia.mock.calls.map(([url]) => new URL(url)).filter((u) => u.pathname === '/api/productos')
    expect(urls.at(-1)?.searchParams.get('activo')).toBe('false')
  })

  it('registra un producto con su stock inicial y refresca la lista', async () => {
    const estado = { productos: [] as Producto[] }
    const registrar = vi.fn((cuerpo: unknown) => {
      estado.productos = [producto({ id: 9, nombre: 'Chifles 50 g' })]
      return json(201, { cuerpo })
    })
    apiFalsa(estado, { 'POST /api/productos': registrar })
    abrirProductos()
    expect(await screen.findByText('Todavía no hay productos.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Nuevo producto' }))
    const form = formulario('Nuevo producto')
    await userEvent.type(within(form).getByLabelText('Nombre'), 'Chifles 50 g')
    await userEvent.type(within(form).getByLabelText('Precio (S/)'), '2.50')
    await userEvent.selectOptions(within(form).getByLabelText('Categoría'), 'Snacks')
    await userEvent.selectOptions(within(form).getByLabelText('Proveedor'), 'Distribuidora Nor-Sur')
    await userEvent.type(within(form).getByLabelText('Stock inicial'), '30')
    await userEvent.type(within(form).getByLabelText('Stock mínimo'), '10')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(registrar).toHaveBeenCalledOnce())
    expect(registrar.mock.calls[0][0]).toEqual({
      nombre: 'Chifles 50 g',
      precio: 2.5,
      idCategoria: 2,
      idProveedor: 7,
      stockInicial: 30,
      stockMinimo: 10,
    })
    expect(await fila('Chifles 50 g')).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Nuevo producto' })).not.toBeInTheDocument()
  })

  it('el selector de proveedor ofrece "sin proveedor" y oculta los dados de baja', async () => {
    apiFalsa({ productos: [] })
    abrirProductos()
    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo producto' }))

    const opciones = within(within(formulario('Nuevo producto')).getByLabelText('Proveedor'))
      .getAllByRole('option')
      .map((o) => o.textContent)
    expect(opciones).toEqual(['Sin proveedor (preparación propia)', 'Distribuidora Nor-Sur'])
  })

  it('un nombre repetido muestra el mensaje de la API y deja el formulario abierto', async () => {
    apiFalsa(
      { productos: [] },
      {
        'POST /api/productos': () =>
          json(409, { codigo: 'CONFLICTO', mensaje: 'Ya existe un producto llamado "Tamal"', detalles: [] }),
      },
    )
    abrirProductos()
    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo producto' }))
    const form = formulario('Nuevo producto')
    await userEvent.type(within(form).getByLabelText('Nombre'), 'Tamal')
    await userEvent.type(within(form).getByLabelText('Precio (S/)'), '4')
    await userEvent.selectOptions(within(form).getByLabelText('Categoría'), 'Snacks')
    await userEvent.type(within(form).getByLabelText('Stock inicial'), '10')
    await userEvent.type(within(form).getByLabelText('Stock mínimo'), '2')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('Ya existe un producto llamado "Tamal"')
    expect(formulario('Nuevo producto')).toBeInTheDocument()
  })

  it('editar precarga los datos y envía la edición sin el stock', async () => {
    const actualizar = vi.fn((cuerpo: unknown) => json(200, cuerpo))
    apiFalsa(
      {
        productos: [
          producto({ id: 4, nombre: 'Chifles 50 g', descripcion: 'Plátano', precio: 2, idProveedor: 7, proveedor: 'Distribuidora Nor-Sur' }),
        ],
      },
      { 'PUT /api/productos/4': actualizar },
    )
    abrirProductos()
    await userEvent.click(within(await fila('Chifles 50 g')).getByRole('button', { name: 'Editar' }))

    const form = formulario('Editar Chifles 50 g')
    expect(within(form).getByLabelText('Nombre')).toHaveValue('Chifles 50 g')
    expect(within(form).getByLabelText('Proveedor')).toHaveValue('7')
    expect(within(form).queryByLabelText('Stock inicial')).not.toBeInTheDocument()

    const precio = within(form).getByLabelText('Precio (S/)')
    await userEvent.clear(precio)
    await userEvent.type(precio, '2.20')
    await userEvent.click(within(form).getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(actualizar).toHaveBeenCalledOnce())
    expect(actualizar.mock.calls[0][0]).toEqual({
      nombre: 'Chifles 50 g',
      descripcion: 'Plátano',
      precio: 2.2,
      idCategoria: 2,
      idProveedor: 7,
      stockMinimo: 10,
    })
  })

  it('recarga stock con la cantidad indicada y muestra el stock nuevo', async () => {
    const estado = { productos: [producto({ id: 5, nombre: 'Gaseosa', cantidadDisponible: 3, stockMinimo: 5 })] }
    const recargar = vi.fn(() => {
      estado.productos = [producto({ id: 5, nombre: 'Gaseosa', cantidadDisponible: 27, stockMinimo: 5 })]
      return json(200, { idProducto: 5, producto: 'Gaseosa', cantidadDisponible: 27, stockMinimo: 5, fechaActualizacion: '2026-11-09T10:00:00' })
    })
    apiFalsa(estado, { 'PATCH /api/inventario/5/recarga': recargar })
    abrirProductos()
    await userEvent.click(within(await fila('Gaseosa')).getByRole('button', { name: 'Recargar' }))

    const form = formulario('Recargar Gaseosa')
    const boton = within(form).getByRole('button', { name: 'Recargar stock' })
    expect(boton).toBeDisabled()
    await userEvent.type(within(form).getByLabelText('Unidades a agregar'), '24')
    await userEvent.click(boton)

    await waitFor(() => expect(recargar).toHaveBeenCalledWith({ cantidad: 24 }, expect.anything()))
    expect(await screen.findByRole('status')).toHaveTextContent('Gaseosa: ahora hay 27 unidades.')
    expect(within(await fila('Gaseosa')).getByText('27')).toBeInTheDocument()
  })

  it('deshabilita y habilita un producto', async () => {
    const cambiar = vi.fn((cuerpo: unknown) => json(200, cuerpo))
    apiFalsa({ productos: [producto({ id: 6, nombre: 'Tamal' })] }, { 'PATCH /api/productos/6/estado': cambiar })
    abrirProductos()

    await userEvent.click(within(await fila('Tamal')).getByRole('button', { name: 'Deshabilitar' }))

    await waitFor(() => expect(cambiar).toHaveBeenCalledWith({ activo: false }, expect.anything()))
  })

  it('si la lista no carga, muestra el error y deja reintentar', async () => {
    let falla = true
    const estado = { productos: [producto({ id: 1, nombre: 'Chifles 50 g' })] }
    const espia = apiFalsa(estado)
    const original = espia.getMockImplementation()!
    espia.mockImplementation(async (url: string, opciones?: RequestInit) => {
      if (falla && new URL(url).pathname === '/api/productos') {
        return json(500, { codigo: 'ERROR_INTERNO', mensaje: 'Ocurrió un error inesperado.', detalles: [] })
      }
      return original(url, opciones)
    })
    abrirProductos()

    expect(await screen.findByRole('alert')).toHaveTextContent('Ocurrió un error inesperado.')
    falla = false
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await fila('Chifles 50 g')).toBeInTheDocument()
  })
})
