package pe.edu.ucv.cafetin.app.ui.pedidos

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.ucv.cafetin.app.ui.Acentos

@Composable
fun Cargando() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/**
 * Esqueleto de carga: la forma de la lista aparece al instante y la espera se siente más corta que con un
 * círculo girando (velocidad percibida, Nah 2004).
 */
@Composable
fun Esqueleto(filas: Int = 5, alto: Dp = 88.dp) {
    val pulso by rememberInfiniteTransition(label = "esqueleto").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alfa",
    )
    Column(
        Modifier.fillMaxWidth().padding(16.dp).clearAndSetSemantics { contentDescription = "Cargando" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(filas) {
            Box(
                Modifier.fillMaxWidth().height(alto).alpha(pulso)
                    .clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

/** Pantalla entera de error con su botón para reintentar. */
@Composable
fun ErrorConReintento(mensaje: String, alReintentar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(mensaje, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        OutlinedButton(onClick = alReintentar) { Text("Reintentar") }
    }
}

@Composable
fun Vacio(titulo: String, detalle: String? = null, accion: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("☕", fontSize = 32.sp) }
        Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        detalle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
        accion?.invoke()
    }
}

/** Fondo cálido para tarjetas destacadas; en modo oscuro usa el contenedor del tema. */
@Composable
fun fondoCrema(): Color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.primaryContainer else Acentos.crema

private val TONOS_CATEGORIA = listOf(
    Color(0xFFF3E1CE), Color(0xFFEAD7C3), Color(0xFFF6E7D2), Color(0xFFE6D3BE), Color(0xFFF1DCC6),
)

/**
 * Avatar del producto: la API no tiene fotos, así que en vez de imágenes de relleno (que prometerían algo
 * que no es) se muestra la inicial sobre un tono café que identifica la categoría.
 */
@Composable
fun AvatarProducto(nombre: String, categoria: String?, tamano: Dp = 56.dp) {
    val tono = TONOS_CATEGORIA[Math.floorMod((categoria ?: "").hashCode(), TONOS_CATEGORIA.size)]
    Box(
        Modifier.size(tamano).clip(RoundedCornerShape(tamano / 3.5f))
            .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.secondaryContainer else tono)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            nombre.trim().take(1).uppercase(),
            fontSize = (tamano.value * 0.42f).sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Selector −/+ con objetivos de 48 dp (Fitts; mínimo de Android). */
@Composable
fun SelectorCantidad(nombre: String, cantidad: Int, puedeSumar: Boolean, alRestar: () -> Unit, alSumar: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = alRestar,
            modifier = Modifier.size(48.dp).semantics { contentDescription = if (cantidad == 1) "Quitar $nombre" else "Uno menos de $nombre" },
        ) { Text(if (cantidad == 1) "×" else "−", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        Text(
            "$cantidad",
            Modifier.width(32.dp).semantics { contentDescription = "$cantidad en el carrito" },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        FilledTonalIconButton(
            onClick = alSumar,
            enabled = puedeSumar,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Uno más de $nombre" },
        ) { Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

/** Barra fija abajo con la acción principal de la pantalla, siempre al alcance del pulgar. */
@Composable
fun BarraAccion(contenido: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp, tonalElevation = 2.dp) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding()) { contenido() }
    }
}

/** Etiqueta del estado del pedido. El texto dice el estado; el color solo lo refuerza (accesibilidad). */
@Composable
fun EtiquetaEstado(estado: String, estadoPago: String?) {
    val (fondo, texto) = when {
        estado == "LISTO" -> Acentos.exito to Color.White
        estado == "CANCELADO" || estadoPago == "FALLIDO" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        estado == "ENTREGADO" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(color = fondo, contentColor = texto, shape = CircleShape) {
        Text(
            etiquetaEstado(estado, estadoPago),
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Seguimiento del pedido por pasos (estado visible, Nielsen 1994): el estudiante sabe en qué va su pedido
 * sin preguntar en el mostrador.
 */
@Composable
fun SeguimientoPedido(paso: Int) {
    val activo = MaterialTheme.colorScheme.primary
    val inactivo = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.semantics(mergeDescendants = true) {
        contentDescription = "Paso ${paso + 1} de ${PASOS_PEDIDO.size}: ${PASOS_PEDIDO.getOrElse(paso) { "" }}"
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PASOS_PEDIDO.indices.forEach { i ->
                val hecho = i <= paso
                // Paso actual más grande y con anillo; los ya hechos, llenos. El nombre en negrita dice cuál es.
                Box(
                    Modifier.size(if (i == paso) 24.dp else 14.dp).clip(CircleShape)
                        .background(if (i == paso) Acentos.caramelo.copy(alpha = 0.3f) else if (hecho) activo else inactivo),
                    contentAlignment = Alignment.Center,
                ) { if (i == paso) Box(Modifier.size(14.dp).clip(CircleShape).background(Acentos.caramelo)) }
                if (i < PASOS_PEDIDO.lastIndex) {
                    Box(Modifier.weight(1f).height(3.dp).background(if (i < paso) activo else inactivo))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            PASOS_PEDIDO.forEachIndexed { i, nombre ->
                Text(
                    nombre,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = when (i) { 0 -> TextAlign.Start; PASOS_PEDIDO.lastIndex -> TextAlign.End; else -> TextAlign.Center },
                    fontWeight = if (i == paso) FontWeight.Bold else FontWeight.Normal,
                    color = if (i <= paso) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Recibo con forma de ticket: el código es lo que el estudiante muestra al recoger (RF-07, RF-13). */
@Composable
fun TicketRecibo(codigo: String, mensaje: String) {
    val fondo = fondoCrema()
    val linea = MaterialTheme.colorScheme.outline
    val muesca = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(fondo).padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("RECIBO DIGITAL", style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                codigo,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = "Código de recibo ${codigo.toList().joinToString(" ")}" },
            )
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 20.dp)) {
                drawLine(linea, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
            }
            Spacer(Modifier.height(14.dp))
            Text(mensaje, Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
        // Muescas laterales a la altura de la línea punteada.
        Box(Modifier.align(Alignment.CenterStart).padding(top = 56.dp).offset(x = (-9).dp).size(18.dp).clip(CircleShape).background(muesca))
        Box(Modifier.align(Alignment.CenterEnd).padding(top = 56.dp).offset(x = 9.dp).size(18.dp).clip(CircleShape).background(muesca))
    }
}

