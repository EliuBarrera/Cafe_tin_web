package pe.edu.ucv.cafetin.app.ui.pedidos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pe.edu.ucv.cafetin.app.datos.ItemPedidoRequest
import pe.edu.ucv.cafetin.app.datos.ProductoCatalogo
import kotlin.math.roundToLong

data class LineaCarrito(val producto: ProductoCatalogo, val cantidad: Int) {
    val subtotalCentimos: Long get() = centimos(producto.precio) * cantidad
}

/**
 * Carrito en memoria (RF-05). Vive mientras dura la sesión; se vacía al crear el pedido o al salir.
 * El total aquí es solo una referencia: el que vale es el que calcula la base al crear el pedido.
 */
class Carrito {

    private val _lineas = MutableStateFlow<List<LineaCarrito>>(emptyList())
    val lineas: StateFlow<List<LineaCarrito>> = _lineas.asStateFlow()

    /** Suma una unidad. Devuelve false si no se puede: agotado, sin más stock o en el máximo del contrato. */
    fun agregar(producto: ProductoCatalogo): Boolean {
        val actual = cantidadDe(producto.idProducto)
        if (producto.agotado || actual >= maximoPara(producto)) return false
        if (actual == 0 && _lineas.value.size >= MAX_LINEAS) return false
        _lineas.update { lineas ->
            if (actual == 0) lineas + LineaCarrito(producto, 1)
            else lineas.map { if (it.producto.idProducto == producto.idProducto) LineaCarrito(producto, actual + 1) else it }
        }
        return true
    }

    /** Fija la cantidad de un producto; 0 o menos lo quita. */
    fun cambiar(idProducto: Int, cantidad: Int) = _lineas.update { lineas ->
        lineas.mapNotNull {
            when {
                it.producto.idProducto != idProducto -> it
                cantidad <= 0 -> null
                else -> it.copy(cantidad = minOf(cantidad, maximoPara(it.producto)))
            }
        }
    }

    /** Con el catálogo recién leído: ajusta precio y stock, y quita lo agotado o dado de baja. */
    fun actualizar(catalogo: List<ProductoCatalogo>) {
        val porId = catalogo.associateBy { it.idProducto }
        _lineas.update { lineas ->
            lineas.mapNotNull { linea ->
                val nuevo = porId[linea.producto.idProducto] ?: return@mapNotNull null
                if (nuevo.agotado) null else LineaCarrito(nuevo, minOf(linea.cantidad, maximoPara(nuevo)))
            }
        }
    }

    fun vaciar() = _lineas.update { emptyList() }

    fun cantidadDe(idProducto: Int) = _lineas.value.firstOrNull { it.producto.idProducto == idProducto }?.cantidad ?: 0
    fun unidades() = _lineas.value.sumOf { it.cantidad }
    fun totalCentimos() = _lineas.value.sumOf { it.subtotalCentimos }
    fun items() = _lineas.value.map { ItemPedidoRequest(it.producto.idProducto, it.cantidad) }

    private fun maximoPara(p: ProductoCatalogo) = minOf(p.cantidadDisponible, MAX_POR_PRODUCTO)

    companion object {
        /** Límites de ItemPedidoRequest.cantidad y CrearPedidoRequest.items en el contrato. */
        const val MAX_POR_PRODUCTO = 20
        const val MAX_LINEAS = 20
    }
}

fun centimos(soles: Double): Long = (soles * 100).roundToLong()
