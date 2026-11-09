import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { App } from '../App'
import type { PedidoDespacho, Usuario } from '../api/tipos'
import { borrarToken, guardarToken } from '../sesion/almacen'
import { SesionProvider } from '../sesion/SesionProvider'
import { INTERVALO_REFRESCO_MS } from './Despacho'

const trabajador: Usuario = { id: 2, nombre: 'Carmen', apellidos: 'Díaz', correo: 'carmen@cafetin.pe', rol: 'TRABAJADOR' }

function pedido(cambios: Partial<PedidoDespacho> & Pick<PedidoDespacho, 'idPedido'>): PedidoDespacho {
  return {
    codigoPedido: `PED-${cambios.idPedido}`,
    estado: 'PENDIENTE',
    horaRecojo: '12:50',
    total: 10.5,
    estudiante: 'María Quiroz Vásquez',
    metodoPago: 'YAPE',
    items: [{ producto: 'Tamal criollo', cantidad: 2 }],
    trabajador: null,
    reciboVerificado: false,
    ...cambios,
  }
}

function json(estado: number, cuerpo?: unknown) {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json' },
  })
}

const error409 = (codigo: string, mensaje: string) => json(409, { codigo, mensaje, detalles: [] })

type Manejador = (cuerpo: unknown) => Response

/** API falsa con sesión de trabajador. `pedidos` se lee en cada GET, así el test puede cambiarlo entre refrescos. */
function apiFalsa(estado: { pedidos: PedidoDespacho[] }, acciones: Record<string, Manejador> = {}) {
  const espia = vi.fn(async (url: string, opciones?: RequestInit) => {
    const metodo = opciones?.method ?? 'GET'
    const ruta = new URL(url).pathname
    if (metodo === 'GET' && ruta === '/api/usuarios/me') return json(200, trabajador)
    if (metodo === 'GET' && ruta === '/api/despacho/pedidos') return json(200, estado.pedidos)
    const manejador = acciones[`${metodo} ${ruta}`]
    if (!manejador) throw new Error(`Petición no esperada: ${metodo} ${ruta}`)
    return manejador(opciones?.body ? JSON.parse(String(opciones.body)) : undefined)
  })
  vi.stubGlobal('fetch', espia)
  return espia
}

const llamadasA = (espia: ReturnType<typeof apiFalsa>, ruta: string) =>
  espia.mock.calls.filter(([url]) => new URL(url).pathname === ruta).length

function abrirDespacho() {
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

const columna = (nombre: string) => screen.getByRole('region', { name: new RegExp(`^${nombre}`) })
const tarjeta = async (codigo: string) => screen.findByRole('article', { name: codigo })

beforeEach(() => {
  borrarToken()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('tablero de despacho', () => {
  it('reparte los pedidos en columnas por estado con lo necesario para prepararlos', async () => {
    apiFalsa({
      pedidos: [
        pedido({ idPedido: 1, horaRecojo: '11:10', items: [{ producto: 'Tamal criollo', cantidad: 2 }, { producto: 'Café', cantidad: 1 }] }),
        pedido({ idPedido: 2, estado: 'CONFIRMADO', trabajador: 'Carmen Díaz Morales' }),
        pedido({ idPedido: 3, estado: 'EN_PREPARACION' }),
        pedido({ idPedido: 4, estado: 'LISTO' }),
      ],
    })
    abrirDespacho()

    const uno = await tarjeta('PED-1')
    expect(within(columna('Pendientes')).getByRole('article', { name: 'PED-1' })).toBe(uno)
    expect(within(uno).getByText('11:10')).toBeInTheDocument()
    expect(within(uno).getByText('María Quiroz Vásquez')).toBeInTheDocument()
    expect(within(uno).getByText('2 × Tamal criollo')).toBeInTheDocument()
    expect(within(uno).getByText('1 × Café')).toBeInTheDocument()

    expect(within(columna('Confirmados')).getByRole('article', { name: 'PED-2' })).toHaveTextContent('Carmen Díaz Morales')
    expect(within(columna('En preparación')).getByRole('article', { name: 'PED-3' })).toBeInTheDocument()
    expect(within(columna('Listos')).getByRole('article', { name: 'PED-4' })).toBeInTheDocument()
  })

  it('indica las columnas vacías', async () => {
    apiFalsa({ pedidos: [pedido({ idPedido: 1 })] })
    abrirDespacho()

    await tarjeta('PED-1')
    expect(within(columna('Listos')).getByText('Sin pedidos')).toBeInTheDocument()
  })

  it(`se actualiza solo cada ${INTERVALO_REFRESCO_MS / 1000} s`, async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const estado = { pedidos: [pedido({ idPedido: 1 })] }
    const espia = apiFalsa(estado)
    abrirDespacho()
    await tarjeta('PED-1')

    estado.pedidos = [...estado.pedidos, pedido({ idPedido: 9 })]
    await act(() => vi.advanceTimersByTimeAsync(INTERVALO_REFRESCO_MS))

    expect(await tarjeta('PED-9')).toBeInTheDocument()
    expect(llamadasA(espia, '/api/despacho/pedidos')).toBeGreaterThanOrEqual(2)
  })

  it('confirma, prepara y marca listo moviendo la tarjeta de columna', async () => {
    const estado = { pedidos: [pedido({ idPedido: 1 })] }
    const mover = (nuevo: PedidoDespacho['estado']) => () => {
      estado.pedidos = [pedido({ idPedido: 1, estado: nuevo, trabajador: 'Carmen Díaz Morales' })]
      return json(200, estado.pedidos[0])
    }
    const espia = apiFalsa(estado, {
      'POST /api/despacho/pedidos/1/confirmar': mover('CONFIRMADO'),
      'POST /api/despacho/pedidos/1/preparar': mover('EN_PREPARACION'),
      'POST /api/despacho/pedidos/1/listo': mover('LISTO'),
    })
    abrirDespacho()

    await userEvent.click(within(await tarjeta('PED-1')).getByRole('button', { name: 'Confirmar' }))
    await userEvent.click(within(await tarjeta('PED-1')).getByRole('button', { name: 'Empezar a preparar' }))
    await userEvent.click(within(await tarjeta('PED-1')).getByRole('button', { name: 'Marcar listo' }))

    expect(await within(columna('Listos')).findByRole('article', { name: 'PED-1' })).toBeInTheDocument()
    expect(llamadasA(espia, '/api/despacho/pedidos/1/confirmar')).toBe(1)
    expect(llamadasA(espia, '/api/despacho/pedidos/1/listo')).toBe(1)
  })

  it('verifica el código del recibo y solo entonces deja entregar', async () => {
    const estado = { pedidos: [pedido({ idPedido: 4, estado: 'LISTO' })] }
    const espia = apiFalsa(estado, {
      'POST /api/despacho/pedidos/4/verificar': (cuerpo) => {
        expect(cuerpo).toEqual({ codigoVerificacion: 'DR-4-3390' })
        estado.pedidos = [pedido({ idPedido: 4, estado: 'LISTO', reciboVerificado: true })]
        return json(200, estado.pedidos[0])
      },
      'POST /api/despacho/pedidos/4/entregar': () => {
        const entregado = pedido({ idPedido: 4, estado: 'ENTREGADO', reciboVerificado: true })
        estado.pedidos = []
        return json(200, entregado)
      },
    })
    abrirDespacho()

    const listo = await tarjeta('PED-4')
    expect(within(listo).getByRole('button', { name: 'Entregar' })).toBeDisabled()

    await userEvent.type(within(listo).getByLabelText('Código del recibo'), 'dr-4-3390')
    await userEvent.click(within(listo).getByRole('button', { name: 'Verificar' }))

    expect(await within(await tarjeta('PED-4')).findByText('Recibo verificado')).toBeInTheDocument()
    await userEvent.click(within(await tarjeta('PED-4')).getByRole('button', { name: 'Entregar' }))

    await vi.waitFor(() => expect(screen.queryByRole('article', { name: 'PED-4' })).not.toBeInTheDocument())
    expect(llamadasA(espia, '/api/despacho/pedidos/4/entregar')).toBe(1)
  })

  it('muestra en la tarjeta el error de la API si el código no coincide', async () => {
    apiFalsa({ pedidos: [pedido({ idPedido: 4, estado: 'LISTO' })] }, {
      'POST /api/despacho/pedidos/4/verificar': () =>
        error409('REGLA_NEGOCIO', 'El código no coincide con el recibo del pedido'),
    })
    abrirDespacho()

    const listo = await tarjeta('PED-4')
    await userEvent.type(within(listo).getByLabelText('Código del recibo'), 'DR-4-0000')
    await userEvent.click(within(listo).getByRole('button', { name: 'Verificar' }))

    expect(await within(listo).findByRole('alert')).toHaveTextContent('El código no coincide')
    expect(within(listo).getByRole('button', { name: 'Entregar' })).toBeDisabled()
  })

  it('no envía un código con formato inválido', async () => {
    const espia = apiFalsa({ pedidos: [pedido({ idPedido: 4, estado: 'LISTO' })] })
    abrirDespacho()

    const listo = await tarjeta('PED-4')
    await userEvent.type(within(listo).getByLabelText('Código del recibo'), '12345')

    expect(within(listo).getByRole('button', { name: 'Verificar' })).toBeDisabled()
    expect(llamadasA(espia, '/api/despacho/pedidos/4/verificar')).toBe(0)
  })

  it('si otro trabajador ya movió el pedido, avisa y refresca el tablero', async () => {
    const estado = { pedidos: [pedido({ idPedido: 1 })] }
    const espia = apiFalsa(estado, {
      'POST /api/despacho/pedidos/1/confirmar': () => {
        estado.pedidos = [pedido({ idPedido: 1, estado: 'CONFIRMADO', trabajador: 'Luis Ramos' })]
        return error409('TRANSICION_INVALIDA', 'No se puede pasar de CONFIRMADO a CONFIRMADO')
      },
    })
    abrirDespacho()

    await userEvent.click(within(await tarjeta('PED-1')).getByRole('button', { name: 'Confirmar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se puede pasar de CONFIRMADO a CONFIRMADO')
    expect(await within(columna('Confirmados')).findByRole('article', { name: 'PED-1' })).toHaveTextContent('Luis Ramos')
    expect(llamadasA(espia, '/api/despacho/pedidos')).toBeGreaterThanOrEqual(2)
  })

  it('si la API no responde, muestra el error y permite reintentar', async () => {
    let falla = true
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        const ruta = new URL(url).pathname
        if (ruta === '/api/usuarios/me') return json(200, trabajador)
        if (falla) throw new TypeError('Failed to fetch')
        return json(200, [pedido({ idPedido: 1 })])
      }),
    )
    abrirDespacho()

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo conectar con el servidor')
    falla = false
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await tarjeta('PED-1')).toBeInTheDocument()
  })
})
