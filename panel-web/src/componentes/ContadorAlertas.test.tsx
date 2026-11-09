import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import type { Alerta, Usuario } from '../api/tipos'
import { borrarToken, guardarToken } from '../sesion/almacen'
import { SesionProvider } from '../sesion/SesionProvider'
import { INTERVALO_ALERTAS_MS } from './ContadorAlertas'

const dueno: Usuario = { id: 1, nombre: 'Rosa', apellidos: 'Quispe', correo: 'rosa@cafetin.pe', rol: 'DUENO' }
const trabajador: Usuario = { id: 2, nombre: 'Carmen', apellidos: 'Díaz', correo: 'carmen@cafetin.pe', rol: 'TRABAJADOR' }

function alerta(id: number): Alerta {
  return {
    id,
    idProducto: id,
    producto: `Producto ${id}`,
    tipo: 'STOCK_BAJO',
    mensaje: `Quedan 3 unidades de "Producto ${id}"`,
    nivelDetectado: 3,
    cantidadDisponible: 3,
    stockMinimo: 5,
    proveedor: 'Distribuidora Norte',
    telefonoProveedor: '944123456',
    fecha: '2026-11-09T10:00:00',
    atendida: false,
  }
}

function json(estado: number, cuerpo?: unknown) {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** API falsa: `alertas` se lee en cada GET, así el test puede cambiarla entre consultas. */
function apiFalsa(usuario: Usuario, estado: { alertas: () => Response }) {
  const espia = vi.fn(async (url: string) => {
    const ruta = new URL(url).pathname
    if (ruta === '/api/usuarios/me') return json(200, usuario)
    if (ruta === '/api/despacho/pedidos') return json(200, [])
    if (ruta === '/api/alertas') return estado.alertas()
    throw new Error(`Petición no esperada: ${ruta}`)
  })
  vi.stubGlobal('fetch', espia)
  return espia
}

const consultasDeAlertas = (espia: ReturnType<typeof apiFalsa>) =>
  espia.mock.calls.filter(([url]) => new URL(url).pathname === '/api/alertas').length

function abrir() {
  guardarToken('token-de-prueba')
  const clienteQuery = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={clienteQuery}>
      <SesionProvider>
        <MemoryRouter initialEntries={['/despacho']}>
          <App />
        </MemoryRouter>
      </SesionProvider>
    </QueryClientProvider>,
  )
}

const enlaceAlertas = async () =>
  within(await screen.findByRole('navigation', { name: 'Principal' })).getByRole('link', { name: /^Alertas/ })

beforeEach(() => {
  borrarToken()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('contador de alertas de stock (RF-17)', () => {
  it('muestra cuántas alertas pendientes tiene el dueño junto a "Alertas"', async () => {
    apiFalsa(dueno, { alertas: () => json(200, [alerta(1), alerta(2)]) })
    abrir()

    const enlace = await enlaceAlertas()
    expect(await within(enlace).findByText('2')).toBeInTheDocument()
    expect(enlace).toHaveAccessibleName('Alertas, 2 pendientes')
  })

  it('sin alertas pendientes no muestra número', async () => {
    const espia = apiFalsa(dueno, { alertas: () => json(200, []) })
    abrir()

    const enlace = await enlaceAlertas()
    await vi.waitFor(() => expect(consultasDeAlertas(espia)).toBe(1))
    expect(enlace).toHaveAccessibleName('Alertas')
  })

  it(`se actualiza solo cada ${INTERVALO_ALERTAS_MS / 1000} s`, async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const estado = { alertas: () => json(200, [alerta(1)]) }
    apiFalsa(dueno, estado)
    abrir()
    const enlace = await enlaceAlertas()
    expect(await within(enlace).findByText('1')).toBeInTheDocument()

    estado.alertas = () => json(200, [alerta(1), alerta(2), alerta(3)])
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_ALERTAS_MS))

    expect(await within(enlace).findByText('3')).toBeInTheDocument()
  })

  it('mientras la API de alertas no esté implementada (501), no muestra nada y deja de consultar', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const espia = apiFalsa(dueno, {
      alertas: () => json(501, { codigo: 'NO_IMPLEMENTADO', mensaje: 'RF-17 aún no implementado', detalles: [] }),
    })
    abrir()
    const enlace = await enlaceAlertas()
    await vi.waitFor(() => expect(consultasDeAlertas(espia)).toBe(1))

    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_ALERTAS_MS * 3))

    expect(consultasDeAlertas(espia)).toBe(1)
    expect(enlace).toHaveAccessibleName('Alertas')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('el trabajador no consulta alertas', async () => {
    const espia = apiFalsa(trabajador, { alertas: () => json(200, [alerta(1)]) })
    abrir()

    expect(await screen.findByRole('heading', { name: 'Despacho' })).toBeInTheDocument()
    expect(consultasDeAlertas(espia)).toBe(0)
  })
})
