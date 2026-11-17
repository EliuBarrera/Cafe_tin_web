package pe.edu.ucv.cafetin.app.ui.pedidos

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import pe.edu.ucv.cafetin.app.datos.ProductoCatalogo

fun soles(monto: Double) = solesDeCentimos(centimos(monto))

fun solesDeCentimos(c: Long) = String.format(Locale.ROOT, "S/ %d.%02d", c / 100, c % 100)

private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM HH:mm")

/** "2026-11-07T19:51:20.77" → "07/11 19:51". La hora ya es la de Lima (la pone la base). */
fun fechaCorta(iso: String?): String =
    iso?.let { runCatching { LocalDateTime.parse(it).format(formatoFecha) }.getOrDefault(it) } ?: ""

/**
 * Horas de recojo para elegir con un toque (RNF-01): cada 15 min, desde el siguiente cuarto de hora
 * con al menos [margenMin] de preparación, sin pasar de medianoche.
 */
fun horasDeRecojo(ahora: LocalTime, cantidad: Int = 8, margenMin: Int = 15): List<LocalTime> {
    val minutos = ahora.hour * 60 + ahora.minute + margenMin
    val primero = (minutos + 14) / 15 * 15
    return (0 until cantidad).map { primero + it * 15 }
        .takeWhile { it < 24 * 60 }
        .map { LocalTime.of(it / 60, it % 60) }
}

/** Estado legible para el estudiante. PENDIENTE cubre pagados y sin pagar: lo distingue el pago. */
fun etiquetaEstado(estado: String, estadoPago: String?): String = when (estado) {
    "PENDIENTE" -> when (estadoPago) {
        "PAGADO" -> "Pagado · por confirmar"
        "FALLIDO" -> "Pago rechazado"
        else -> "Sin pagar"
    }
    "CONFIRMADO" -> "Confirmado"
    "EN_PREPARACION" -> "En preparación"
    "LISTO" -> "Listo para recoger"
    "ENTREGADO" -> "Entregado"
    "CANCELADO" -> "Cancelado"
    else -> estado
}

/** Estados en los que el pedido ya no cambia: no hace falta seguir consultándolo. */
fun esFinal(estado: String) = estado == "ENTREGADO" || estado == "CANCELADO"

val METODOS_PAGO = listOf("YAPE" to "Yape", "PLIN" to "Plin", "TARJETA" to "Tarjeta")

/** Saludo según la hora de Lima del teléfono. */
fun saludo(ahora: LocalTime): String = when (ahora.hour) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

/** Pasos que ve el estudiante en el seguimiento, en orden. */
val PASOS_PEDIDO = listOf("Pagado", "Confirmado", "En preparación", "Listo", "Entregado")

/**
 * Paso alcanzado (índice en [PASOS_PEDIDO]) o -1 si aún no está pagado. CANCELADO también da -1:
 * la pantalla lo muestra aparte, no como un paso más.
 */
fun pasoActual(estado: String, estadoPago: String?): Int = when (estado) {
    "PENDIENTE" -> if (estadoPago == "PAGADO") 0 else -1
    "CONFIRMADO" -> 1
    "EN_PREPARACION" -> 2
    "LISTO" -> 3
    "ENTREGADO" -> 4
    else -> -1
}

/**
 * Una sola sugerencia antes de pagar (impulso, con filtro ético): el producto disponible más económico
 * de una categoría que el carrito todavía no tiene, prefiriendo bebidas. Nada si el carrito está vacío
 * o ya cubre todo. Es una sugerencia: no se agrega sola.
 */
fun sugerencia(lineas: List<LineaCarrito>, catalogo: List<ProductoCatalogo>): ProductoCatalogo? {
    if (lineas.isEmpty()) return null
    val enCarrito = lineas.map { it.producto.idProducto }.toSet()
    val categoriasEnCarrito = lineas.mapNotNull { it.producto.categoria }.toSet()
    val candidatos = catalogo.filter { !it.agotado && it.idProducto !in enCarrito && it.categoria !in categoriasEnCarrito }
    return candidatos.minWithOrNull(compareBy<ProductoCatalogo>({ it.categoria != "Bebidas" }, { it.precio }))
}
