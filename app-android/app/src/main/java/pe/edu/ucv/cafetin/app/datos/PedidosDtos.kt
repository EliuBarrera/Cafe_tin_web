package pe.edu.ucv.cafetin.app.datos

import kotlinx.serialization.Serializable

// Copias de los DTOs de docs/openapi.yaml (paquete Pedidos). Los nombres de campo son los del contrato.
// Las fechas llegan como texto ISO sin zona (hora de Lima, la pone la base): se muestran tal cual.

/** RF-04. [estadoStock]: DISPONIBLE, "POCAS UNIDADES" o AGOTADO (la tarjeta se deshabilita). */
@Serializable
data class ProductoCatalogo(
    val idProducto: Int,
    val nombre: String,
    val descripcion: String? = null,
    val precio: Double,
    val categoria: String? = null,
    val cantidadDisponible: Int,
    val estadoStock: String,
) {
    val agotado: Boolean get() = estadoStock == "AGOTADO" || cantidadDisponible <= 0
}

@Serializable
data class ItemPedidoRequest(val idProducto: Int, val cantidad: Int)

/** RF-05. [horaRecojo] en formato HH:mm. */
@Serializable
data class CrearPedidoRequest(
    val horaRecojo: String,
    val items: List<ItemPedidoRequest>,
    val observacion: String? = null,
)

/** RF-06. Solo YAPE, PLIN o TARJETA en la v1. */
@Serializable
data class PagarRequest(val metodoPago: String)

@Serializable
data class LineaPedido(
    val idProducto: Int,
    val nombre: String,
    val cantidad: Int,
    val precioUnitario: Double,
    val subtotal: Double,
)

@Serializable
data class Pago(
    val monto: Double,
    val metodoPago: String,
    val estado: String,
    val fecha: String? = null,
    val referencia: String? = null,
)

/** RF-07: el estudiante muestra [codigoVerificacion] al recoger. */
@Serializable
data class Recibo(
    val codigoVerificacion: String,
    val fechaEmision: String? = null,
    val verificado: Boolean = false,
)

@Serializable
data class PedidoResponse(
    val id: Int,
    val codigoPedido: String,
    val estado: String,
    val fechaHora: String? = null,
    val horaRecojo: String? = null,
    val total: Double,
    val observacion: String? = null,
    val items: List<LineaPedido> = emptyList(),
    /** Nulo si aún no se intentó pagar. */
    val pago: Pago? = null,
    /** Nulo hasta que el pago queda PAGADO. */
    val recibo: Recibo? = null,
) {
    val pagado: Boolean get() = pago?.estado == "PAGADO"
}

/** RF-09: un pedido del historial. */
@Serializable
data class PedidoResumen(
    val id: Int,
    val codigoPedido: String,
    val estado: String,
    val estadoPago: String? = null,
    val fechaHora: String? = null,
    val horaRecojo: String? = null,
    val total: Double,
    val cantidadProductos: Int = 0,
)

@Serializable
data class PaginaPedidos(
    val contenido: List<PedidoResumen> = emptyList(),
    val pagina: Int = 0,
    val tamano: Int = 20,
    val totalElementos: Long = 0,
    val totalPaginas: Int = 0,
)

/** RF-08: aviso guardado por la API; la app los consulta por si el push no llega. */
@Serializable
data class Notificacion(
    val id: Int,
    val tipo: String,
    val mensaje: String,
    val idPedido: Int? = null,
    val fecha: String? = null,
    val leida: Boolean = false,
)
