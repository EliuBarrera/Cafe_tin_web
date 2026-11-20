import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import type { EstadoCobros, Usuario } from '../api/tipos'
import { irA } from '../navegador'
import { guardarToken } from '../sesion/almacen'
import { SesionProvider } from '../sesion/SesionProvider'

vi.mock('../navegador', () => ({ irA: vi.fn() }))

const dueno: Usuario = { id: 1, nombre: 'Rosa', apellidos: 'Quispe', correo: 'rosa@cafetin.pe', rol: 'DUENO', cafeteria: { id: 1, nombre: 'Cafetín principal', ubicacion: null } }
const RUTA = '/api/cafeterias/mia/mercadopago'

const sinConectar: EstadoCobros = { pasarela: 'MERCADOPAGO', conectado: false, idUsuarioMp: null, conectadoEn: null }
const conectado: EstadoCobros = { pasarela: 'MERCADOPAGO', conectado: true, idUsuarioMp: 3862056228, conectadoEn: '2026-11-19T10:00:00' }

function json(estado: number, cuerpo?: unknown) {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** API falsa con sesión de dueño. `estado.cobros` se lee en cada GET: el test lo cambia para simular el refresco. */
function apiFalsa(estado: { cobros: EstadoCobros }, acciones: Record<string, () => Response> = {}) {
  const espia = vi.fn(async (url: string, opciones?: RequestInit) => {
    const metodo = opciones?.method ?? 'GET'
    const ruta = new URL(url).pathname
    if (metodo === 'GET' && ruta === '/api/usuarios/me') return json(200, dueno)
    if (metodo === 'GET' && ruta === '/api/alertas') return json(200, [])
    if (metodo === 'GET' && ruta === RUTA) return json(200, estado.cobros)
    const manejador = acciones[`${metodo} ${ruta}`]
    if (!manejador) throw new Error(`Petición no esperada: ${metodo} ${ruta}`)
    return manejador()
  })
  vi.stubGlobal('fetch', espia)
  return espia
}

function abrir(ruta = '/cobros') {
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

describe('Cobros', () => {
  it('sin cuenta conectada, Conectar lleva a la página de autorización de Mercado Pago', async () => {
    const url = 'https://auth.mercadopago.com/authorization?client_id=1&state=abc'
    apiFalsa({ cobros: sinConectar }, { [`POST ${RUTA}/conectar`]: () => json(200, { urlAutorizacion: url }) })
    abrir()

    expect(await screen.findByText('Sin conectar')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Conectar Mercado Pago' }))

    await vi.waitFor(() => expect(irA).toHaveBeenCalledWith(url))
    expect(screen.getByRole('button', { name: 'Abriendo Mercado Pago…' })).toBeDisabled()
  })

  it('muestra la cuenta conectada y desconecta tras confirmar', async () => {
    const estado = { cobros: conectado }
    const espia = apiFalsa(estado, {
      [`DELETE ${RUTA}`]: () => {
        estado.cobros = sinConectar
        return json(204)
      },
    })
    abrir()

    expect(await screen.findByText('Conectado')).toBeInTheDocument()
    expect(screen.getByText('3862056228')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Desconectar' }))
    expect(espia).not.toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ method: 'DELETE' }))
    await userEvent.click(screen.getByRole('button', { name: 'Sí, desconectar' }))

    expect(await screen.findByText('Sin conectar')).toBeInTheDocument()
  })

  it('avisa el resultado del OAuth al volver de Mercado Pago', async () => {
    apiFalsa({ cobros: conectado })
    abrir('/cobros?mercadopago=conectado')
    expect(await screen.findByRole('status')).toHaveTextContent('quedó conectada')
  })

  it('avisa si el OAuth falló', async () => {
    apiFalsa({ cobros: sinConectar })
    abrir('/cobros?mercadopago=error')
    expect(await screen.findByText(/No se pudo conectar la cuenta/)).toBeInTheDocument()
  })

  it('con la pasarela simulada no ofrece conectar', async () => {
    apiFalsa({ cobros: { ...sinConectar, pasarela: 'SIMULADA' } })
    abrir()
    expect(await screen.findByText('Pasarela simulada')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Conectar Mercado Pago' })).not.toBeInTheDocument()
  })

  it('el conflicto de la API se muestra con su mensaje', async () => {
    apiFalsa(
      { cobros: sinConectar },
      {
        [`POST ${RUTA}/conectar`]: () =>
          json(409, { codigo: 'REGLA_NEGOCIO', mensaje: 'La API cobra con la pasarela simulada', detalles: [] }),
      },
    )
    abrir()
    await userEvent.click(await screen.findByRole('button', { name: 'Conectar Mercado Pago' }))
    expect(await screen.findByText('La API cobra con la pasarela simulada')).toBeInTheDocument()
  })
})
