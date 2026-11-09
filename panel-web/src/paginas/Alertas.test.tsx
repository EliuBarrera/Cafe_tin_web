import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import type { Alerta, Usuario } from '../api/tipos'
import { INTERVALO_ALERTAS_MS } from '../componentes/ContadorAlertas'
import { borrarToken, guardarToken } from '../sesion/almacen'
import { SesionProvider } from '../sesion/SesionProvider'

const dueno: Usuario = { id: 1, nombre: 'Rosa', apellidos: 'Quispe', correo: 'rosa@cafetin.pe', rol: 'DUENO' }

function alerta(id: number, cambios: Partial<Alerta> = {}): Alerta {
  return {
    id,
    idProducto: id,
    producto: `Producto ${id}`,
    tipo: 'STOCK_BAJO',
    mensaje: `Quedan 3 unidades de "Producto ${id}" (mínimo 5). Contacta a tu proveedor.`,
    nivelDetectado: 3,
    cantidadDisponible: 3,
    stockMinimo: 5,
    proveedor: 'Distribuidora Norte',
    telefonoProveedor: '944123456',
    fecha: '2026-11-09T10:00:00',
    atendida: false,
    ...cambios,
  }
}

function json(estado: number, cuerpo?: unknown) {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json' },
  })
}

type Estado = { pendientes: Alerta[]; atendidas: Alerta[] }

/** API falsa con sesión de dueño. Las listas se leen en cada GET: el test las cambia entre consultas. */
function apiFalsa(estado: Estado, marcar?: (id: number) => Response) {
  const espia = vi.fn(async (url: string, opciones?: RequestInit) => {
    const metodo = opciones?.method ?? 'GET'
    const direccion = new URL(url)
    const ruta = direccion.pathname
    if (metodo === 'GET' && ruta === '/api/usuarios/me') return json(200, dueno)
    if (metodo === 'GET' && ruta === '/api/alertas') {
      return json(200, direccion.searchParams.get('atendida') === 'true' ? estado.atendidas : estado.pendientes)
    }
    const marcado = ruta.match(/^\/api\/alertas\/(\d+)\/atendida$/)
    if (metodo === 'PATCH' && marcado && marcar) return marcar(Number(marcado[1]))
    throw new Error(`Petición no esperada: ${metodo} ${ruta}`)
  })
  vi.stubGlobal('fetch', espia)
  return espia
}

function abrirAlertas() {
  guardarToken('token-de-prueba')
  const clienteQuery = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={clienteQuery}>
      <SesionProvider>
        <MemoryRouter initialEntries={['/alertas']}>
          <App />
        </MemoryRouter>
      </SesionProvider>
    </QueryClientProvider>,
  )
}

const tarjeta = (producto: string) => screen.findByRole('article', { name: producto })
const menu = () => screen.getByRole('navigation', { name: 'Principal' })

beforeEach(() => {
  borrarToken()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('Alertas de stock (RF-17)', () => {
  it('lista las pendientes con el stock actual y el contacto del proveedor', async () => {
    apiFalsa({
      pendientes: [
        alerta(1, { tipo: 'STOCK_AGOTADO', producto: 'Tamal criollo', mensaje: 'El producto "Tamal criollo" se agotó. Recarga el stock.', nivelDetectado: 0, cantidadDisponible: 0 }),
        alerta(2, { producto: 'Jugo de naranja', proveedor: null, telefonoProveedor: null }),
      ],
      atendidas: [],
    })
    abrirAlertas()

    const tamal = await tarjeta('Tamal criollo')
    expect(within(tamal).getByText('Agotado')).toBeInTheDocument()
    expect(within(tamal).getByText('El producto "Tamal criollo" se agotó. Recarga el stock.')).toBeInTheDocument()
    expect(within(tamal).getByText('Stock actual: 0 (mínimo 5)')).toBeInTheDocument()
    expect(within(tamal).getByRole('link', { name: '944123456' })).toHaveAttribute('href', 'tel:944123456')
    expect(within(tamal).getByRole('link', { name: 'Recargar en Productos' })).toHaveAttribute('href', '/productos')

    const jugo = await tarjeta('Jugo de naranja')
    expect(within(jugo).getByText('Stock bajo')).toBeInTheDocument()
    expect(within(jugo).getByText('Sin proveedor (preparación propia)')).toBeInTheDocument()
  })

  it('marcar como atendida la quita de pendientes y baja el contador del menú', async () => {
    const estado: Estado = { pendientes: [alerta(1), alerta(2)], atendidas: [] }
    const marcar = vi.fn((id: number) => {
      const marcada = { ...estado.pendientes.find((a) => a.id === id)!, atendida: true }
      estado.pendientes = estado.pendientes.filter((a) => a.id !== id)
      estado.atendidas = [marcada]
      return json(200, marcada)
    })
    apiFalsa(estado, marcar)
    abrirAlertas()
    await waitFor(() => expect(within(menu()).getByRole('link', { name: /Alertas/ })).toHaveTextContent('2'))

    await userEvent.click(within(await tarjeta('Producto 1')).getByRole('button', { name: 'Marcar como atendida' }))

    expect(marcar).toHaveBeenCalledWith(1)
    await waitFor(() => expect(screen.queryByRole('article', { name: 'Producto 1' })).not.toBeInTheDocument())
    expect(await tarjeta('Producto 2')).toBeInTheDocument()
    await waitFor(() => expect(within(menu()).getByRole('link', { name: /Alertas/ })).toHaveTextContent('1'))
  })

  it('muestra las atendidas sin el botón de marcar', async () => {
    const espia = apiFalsa({ pendientes: [alerta(1)], atendidas: [alerta(7, { producto: 'Gaseosa', atendida: true })] })
    abrirAlertas()
    await tarjeta('Producto 1')

    await userEvent.selectOptions(screen.getByLabelText('Mostrar'), 'Atendidas')

    const gaseosa = await tarjeta('Gaseosa')
    expect(within(gaseosa).getByText('Atendida')).toBeInTheDocument()
    expect(within(gaseosa).queryByRole('button', { name: 'Marcar como atendida' })).not.toBeInTheDocument()
    expect(screen.queryByRole('article', { name: 'Producto 1' })).not.toBeInTheDocument()
    expect(espia.mock.calls.some(([url]) => new URL(url).searchParams.get('atendida') === 'true')).toBe(true)
  })

  it('sin alertas lo dice', async () => {
    apiFalsa({ pendientes: [], atendidas: [] })
    abrirAlertas()

    expect(await screen.findByText('No hay alertas pendientes. El stock está sobre el mínimo.')).toBeInTheDocument()
  })

  it('si no se puede marcar, muestra el error en la tarjeta', async () => {
    apiFalsa({ pendientes: [alerta(1)], atendidas: [] }, () =>
      json(404, { codigo: 'NO_ENCONTRADO', mensaje: 'No existe la alerta 1', detalles: [] }),
    )
    abrirAlertas()

    const tarjeta1 = await tarjeta('Producto 1')
    await userEvent.click(within(tarjeta1).getByRole('button', { name: 'Marcar como atendida' }))

    expect(await within(tarjeta1).findByRole('alert')).toHaveTextContent('No existe la alerta 1')
  })

  it(`las pendientes se actualizan solas cada ${INTERVALO_ALERTAS_MS / 1000} s`, async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const estado: Estado = { pendientes: [alerta(1)], atendidas: [] }
    apiFalsa(estado)
    abrirAlertas()
    await tarjeta('Producto 1')

    estado.pendientes = [alerta(9, { producto: 'Chifles' }), ...estado.pendientes]
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_ALERTAS_MS))

    expect(await tarjeta('Chifles')).toBeInTheDocument()
  })
})
