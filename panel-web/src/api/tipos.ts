import type { components } from './esquema'

type Esquemas = components['schemas']

// openapi-typescript marca como opcionales los campos de respuesta: la API siempre los envía.
export type Usuario = Required<Esquemas['UsuarioResponse']>
export type Rol = Usuario['rol']
export type TokenRespuesta = Required<Omit<Esquemas['TokenResponse'], 'usuario'>> & { usuario: Usuario }
export type CampoError = Esquemas['CampoError']

// Productos e inventario del dueño (RF-15, RF-18, RF-19). Los campos opcionales del alta llegan nulos.
export type Producto = Required<
  Omit<Esquemas['ProductoResponse'], 'descripcion' | 'idProveedor' | 'proveedor' | 'imagenUrl'>
> & {
  descripcion: string | null
  idProveedor: number | null
  proveedor: string | null
  imagenUrl: string | null
}
export type EstadoStock = Producto['estadoStock']
export type ProductoAlta = Esquemas['ProductoRequest']
export type ProductoEdicion = Esquemas['ProductoActualizarRequest']
export type StockRecargado = Required<Esquemas['InventarioResponse']>
export type Categoria = Required<Omit<Esquemas['CategoriaResponse'], 'descripcion'>> & { descripcion: string | null }
export type Proveedor = Required<Omit<Esquemas['ProveedorResponse'], 'correo' | 'direccion'>> & {
  correo: string | null
  direccion: string | null
}

// Despacho (RF-10 a RF-14). `trabajador` es nulo hasta que alguien confirma el pedido.
export type LineaDespacho = Required<Esquemas['LineaDespachoResponse']>
export type PedidoDespacho = Required<Omit<Esquemas['PedidoDespachoResponse'], 'trabajador' | 'items'>> & {
  trabajador: string | null
  items: LineaDespacho[]
}
export type EstadoPedido = PedidoDespacho['estado']
