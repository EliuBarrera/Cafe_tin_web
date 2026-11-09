import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import type { IngresoPeriodo, PaginaVentas, ProductoVendido, Usuario, Venta } from '../api/tipos'
import { borrarToken, guardarToken } from '../sesion/almacen'
import { SesionProvider } from '../sesion/SesionProvider'

const dueno: Usuario = { id: 1, nombre: 'Rosa', apellidos: 'Quispe', correo: 'rosa@cafetin.pe', rol: 'DUENO' }

function json(estado: number, cuerpo?: unknown) {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json' },
  })
}

function venta(idPago: number, cambios: Partial<Venta> = {}): Venta {
  return {
    idPago,
    codigoPedido: `PED-${idPago}`,
    fecha: '2026-11-09T12:30:00',
    monto: 10,
    metodoPago: 'YAPE',
    estudiante: 'María Quiroz Vásquez',
    ...cambios,
  }
}

interface Datos {
  ingresos?: (q: URLSearchParams) => IngresoPeriodo[]
  masVendidos?: ProductoVendido[]
  ventas?: (q: URLSearchParams) => PaginaVentas
}

/** API falsa con sesión de dueño. Devuelve el espía para revisar los parámetros de cada consulta. */
function apiFalsa(datos: Datos = {}) {
  const espia = vi.fn(async (url: string) => {
    const direccion = new URL(url)
    const q = direccion.searchParams
    switch (direccion.pathname) {
      case '/api/usuarios/me':
        return json(200, dueno)
      case '/api/alertas':
        return json(200, [])
      case '/api/reportes/ingresos':
        return json(200, datos.ingresos?.(q) ?? [])
      case '/api/reportes/mas-vendidos':
        return json(200, datos.masVendidos ?? [])
      case '/api/reportes/ventas':
        return json(200, datos.ventas?.(q) ?? { contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 })
      default:
        throw new Error(`Petición no esperada: ${direccion.pathname}`)
    }
  })
  vi.stubGlobal('fetch', espia)
  return espia
}

/** Parámetros de la última consulta a `ruta`. */
const ultimaConsulta = (espia: ReturnType<typeof apiFalsa>, ruta: string) =>
  espia.mock.calls
    .map(([url]) => new URL(url))
    .filter((u) => u.pathname === ruta)
    .at(-1)?.searchParams

function abrirReportes() {
  guardarToken('token-de-prueba')
  const clienteQuery = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={clienteQuery}>
      <SesionProvider>
        <MemoryRouter initialEntries={['/reportes']}>
          <App />
        </MemoryRouter>
      </SesionProvider>
    </QueryClientProvider>,
  )
}


beforeEach(() => {
  borrarToken()
})

describe('Reportes (RF-20, RF-21, RF-22)', () => {
  it('sin fechas pide los ingresos por día de los últimos 30 días y suma los totales', async () => {
    const espia = apiFalsa({
      ingresos: () => [
        { inicio: '2026-11-02', pedidosPagados: 3, ingresoTotal: 30, ticketPromedio: 10 },
        { inicio: '2026-11-03', pedidosPagados: 1, ingresoTotal: 12.5, ticketPromedio: 12.5 },
      ],
    })
    abrirReportes()

    const ingresos = await screen.findByRole('region', { name: 'Ingresos' })
    const filas = await within(ingresos).findAllByRole('row')
    expect(filas).toHaveLength(3) // cabecera + 2 días
    expect(within(filas[1]).getByText(/2 nov/)).toBeInTheDocument()
    expect(within(filas[1]).getByText('S/ 30.00')).toBeInTheDocument()
    expect(within(ingresos).getByText('S/ 42.50')).toBeInTheDocument()
    expect(within(ingresos).getByText('4 pedidos pagados')).toBeInTheDocument()
    expect(within(ingresos).getByText('Ticket promedio S/ 10.63')).toBeInTheDocument()

    const q = ultimaConsulta(espia, '/api/reportes/ingresos')!
    expect(q.get('periodo')).toBe('DIA')
    expect(q.has('desde')).toBe(false)
    expect(q.has('hasta')).toBe(false)
  })

  it('agrupa por semana o mes en el rango elegido', async () => {
    const espia = apiFalsa({
      ingresos: (q) =>
        q.get('periodo') === 'SEMANA'
          ? [{ inicio: '2026-10-26', pedidosPagados: 5, ingresoTotal: 50, ticketPromedio: 10 }]
          : [],
    })
    abrirReportes()
    const ingresos = await screen.findByRole('region', { name: 'Ingresos' })

    await userEvent.selectOptions(within(ingresos).getByLabelText('Agrupar'), 'Por semana')
    await userEvent.type(screen.getByLabelText('Desde'), '2026-10-01')
    await userEvent.type(screen.getByLabelText('Hasta'), '2026-10-31')

    expect(await within(ingresos).findByText(/Semana del 26 oct/)).toBeInTheDocument()
    const q = ultimaConsulta(espia, '/api/reportes/ingresos')!
    expect(q.get('periodo')).toBe('SEMANA')
    expect(q.get('desde')).toBe('2026-10-01')
    expect(q.get('hasta')).toBe('2026-10-31')
  })

  it('con "desde" posterior a "hasta" avisa y no consulta', async () => {
    const espia = apiFalsa()
    abrirReportes()
    await screen.findByRole('region', { name: 'Ingresos' })
    await waitFor(() => expect(ultimaConsulta(espia, '/api/reportes/ingresos')).toBeDefined())

    await userEvent.type(screen.getByLabelText('Hasta'), '2026-11-01')
    await userEvent.type(screen.getByLabelText('Desde'), '2026-11-10')

    expect(await screen.findByRole('alert')).toHaveTextContent('"Desde" no puede ser posterior a "Hasta".')
    expect(ultimaConsulta(espia, '/api/reportes/ingresos')?.get('desde')).not.toBe('2026-11-10')
  })

  it('sin ventas en el rango lo dice', async () => {
    apiFalsa()
    abrirReportes()

    expect(await within(await screen.findByRole('region', { name: 'Ingresos' })).findByText('Sin ventas en este rango.')).toBeInTheDocument()
  })

  it('muestra el ranking de más vendidos', async () => {
    const espia = apiFalsa({
      masVendidos: [
        { idProducto: 3, nombre: 'Menú del día', categoria: 'Menú', unidadesVendidas: 120, ingresoGenerado: 960 },
        { idProducto: 8, nombre: 'Chicha morada', categoria: 'Bebidas', unidadesVendidas: 85, ingresoGenerado: 212.5 },
      ],
    })
    abrirReportes()

    const ranking = await screen.findByRole('region', { name: 'Más vendidos' })
    const filas = await within(ranking).findAllByRole('row')
    expect(within(filas[1]).getByText('Menú del día')).toBeInTheDocument()
    expect(within(filas[1]).getByText('120')).toBeInTheDocument()
    expect(within(filas[2]).getByText('S/ 212.50')).toBeInTheDocument()
    expect(ultimaConsulta(espia, '/api/reportes/mas-vendidos')?.get('limite')).toBe('10')
  })

  it('lista las ventas del rango y pagina', async () => {
    const espia = apiFalsa({
      ventas: (q) =>
        q.get('pagina') === '1'
          ? { contenido: [venta(1)], pagina: 1, tamano: 20, totalElementos: 21, totalPaginas: 2 }
          : {
              contenido: [venta(21, { monto: 8.5, metodoPago: 'PLIN' }), venta(20)],
              pagina: 0,
              tamano: 20,
              totalElementos: 21,
              totalPaginas: 2,
            },
    })
    abrirReportes()

    const ventas = await screen.findByRole('region', { name: 'Ventas' })
    expect(await within(ventas).findByText('PED-21')).toBeInTheDocument()
    expect(within(ventas).getByText('S/ 8.50')).toBeInTheDocument()
    expect(within(ventas).getByText('Página 1 de 2 · 21 ventas')).toBeInTheDocument()
    expect(within(ventas).getByRole('button', { name: 'Anterior' })).toBeDisabled()

    await userEvent.click(within(ventas).getByRole('button', { name: 'Siguiente' }))

    expect(await within(ventas).findByText('PED-1')).toBeInTheDocument()
    expect(ultimaConsulta(espia, '/api/reportes/ventas')?.get('pagina')).toBe('1')
    expect(within(ventas).getByRole('button', { name: 'Siguiente' })).toBeDisabled()
  })

  it('cambiar el rango vuelve a la primera página de ventas', async () => {
    const espia = apiFalsa({
      ventas: (q) => ({
        contenido: [venta(Number(q.get('pagina')) + 100)],
        pagina: Number(q.get('pagina')),
        tamano: 20,
        totalElementos: 40,
        totalPaginas: 2,
      }),
    })
    abrirReportes()
    const ventas = await screen.findByRole('region', { name: 'Ventas' })
    await userEvent.click(await within(ventas).findByRole('button', { name: 'Siguiente' }))
    await within(ventas).findByText('PED-101')

    await userEvent.type(screen.getByLabelText('Desde'), '2026-10-01')

    await waitFor(() => expect(ultimaConsulta(espia, '/api/reportes/ventas')?.get('pagina')).toBe('0'))
    expect(ultimaConsulta(espia, '/api/reportes/ventas')?.get('desde')).toBe('2026-10-01')
  })
})
