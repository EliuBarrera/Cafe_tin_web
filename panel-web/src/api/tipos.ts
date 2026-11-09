import type { components } from './esquema'

type Esquemas = components['schemas']

// openapi-typescript marca como opcionales los campos de respuesta: la API siempre los envía.
export type Usuario = Required<Esquemas['UsuarioResponse']>
export type Rol = Usuario['rol']
export type TokenRespuesta = Required<Omit<Esquemas['TokenResponse'], 'usuario'>> & { usuario: Usuario }
export type CampoError = Esquemas['CampoError']

// Despacho (RF-10 a RF-14). `trabajador` es nulo hasta que alguien confirma el pedido.
export type LineaDespacho = Required<Esquemas['LineaDespachoResponse']>
export type PedidoDespacho = Required<Omit<Esquemas['PedidoDespachoResponse'], 'trabajador' | 'items'>> & {
  trabajador: string | null
  items: LineaDespacho[]
}
export type EstadoPedido = PedidoDespacho['estado']

// Alertas de stock (RF-17): las genera la base al cruzar el mínimo o agotarse.
export type Alerta = Required<Esquemas['AlertaResponse']>
