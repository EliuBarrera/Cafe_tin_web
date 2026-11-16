package pe.edu.ucv.cafetin.app.ui.pedidos

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
