package pe.edu.ucv.cafetin.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import pe.edu.ucv.cafetin.app.R
import java.util.Locale

// Curvas de nivel del panel (variable --topografia de estilos.css), en un mosaico de 520 × 520.
private const val LADO_MOSAICO = 520f
private val CURVAS = listOf(
    "M260 70c88 4 170 58 176 148s-62 186-160 196-196-44-206-140 102-208 190-204z",
    "M262 112c66 3 128 44 132 112s-46 140-120 148-148-34-156-106 78-157 144-154z",
    "M264 154c46 2 88 30 90 76s-32 96-82 101-100-23-106-72 52-107 98-105z",
    "M266 196c24 1 46 16 47 40s-17 50-43 53-52-12-55-37 27-57 51-56z",
    "M-20 470c60-30 120-10 170 20s120 40 190 0 140-50 200-10",
    "M-20 500c60-26 120-6 170 22s120 36 190-2 140-46 200-8",
    "M-20 30c70 20 130 0 180-30",
    "M380 -10c20 50 70 70 160 60",
).map { PathParser().parsePathString(it).toPath() }

/**
 * Fondo de papel con curvas de nivel, como el panel. [color] es el trazo (el panel usa tierra al 7,5 %
 * sobre crema y crema al 14 % sobre naranja). [lado] es el tamaño de cada mosaico.
 */
fun Modifier.topografia(color: Color, lado: Dp = 300.dp): Modifier = drawBehind {
    val ladoPx = lado.toPx()
    val factor = ladoPx / LADO_MOSAICO
    val trazo = Stroke(width = 1.dp.toPx() / factor) // 1 dp en pantalla, sea cual sea la escala
    clipRect {
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                translate(x, y) {
                    scale(factor, factor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                        CURVAS.forEach { drawPath(it, color, style = trazo) }
                    }
                }
                x += ladoPx
            }
            y += ladoPx
        }
    }
}

/**
 * Línea de ruta decorativa que cruza una cabecera (la del cartel del login del panel), de abajo a la
 * izquierda hasta arriba a la derecha.
 */
fun Modifier.lineaRuta(color: Color, grosor: Dp = 3.dp): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val camino = Path().apply {
        moveTo(-0.02f * w, 0.8f * h)
        cubicTo(0.14f * w, 0.75f * h, 0.24f * w, 0.68f * h, 0.36f * w, 0.73f * h)
        cubicTo(0.48f * w, 0.78f * h, 0.6f * w, 0.84f * h, 0.7f * w, 0.6f * h)
        cubicTo(0.8f * w, 0.36f * h, 0.86f * w, 0.16f * h, 1.03f * w, 0.07f * h)
    }
    drawPath(camino, color, style = Stroke(width = grosor.toPx(), cap = StrokeCap.Round))
}

/**
 * Papel con el borde de abajo dentado, como el recibo del panel. [diente] es el ancho de cada diente.
 * Las esquinas de arriba quedan rectas: el papel "sale" del borde superior.
 */
class BordeDentado(private val diente: Dp = 12.dp, private val arriba: Boolean = false) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = with(density) { diente.toPx() }
        val n = maxOf(1, (size.width / d).toInt())
        val paso = size.width / n
        val alto = paso / 2
        val camino = Path().apply {
            if (arriba) {
                moveTo(0f, alto)
                for (i in 0 until n) {
                    lineTo(i * paso + paso / 2, 0f)
                    lineTo((i + 1) * paso, alto)
                }
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
            }
            lineTo(size.width, size.height - alto)
            for (i in n - 1 downTo 0) {
                lineTo(i * paso + paso / 2, size.height)
                lineTo(i * paso, size.height - alto)
            }
            close()
        }
        return Outline.Generic(camino)
    }
}

/**
 * Titular de pantalla: Archivo expandido en mayúsculas, como el panel. Los lectores de pantalla leen el
 * texto original (en mayúsculas algunos lo deletrean).
 */
@Composable
fun TituloPantalla(texto: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onBackground) {
    Text(
        texto.uppercase(Locale.forLanguageTag("es")),
        modifier.semantics {
            heading()
            contentDescription = texto
        },
        style = MaterialTheme.typography.headlineMedium,
        color = color,
    )
}

/** El bebé con la taza de Café & Tin. Decorativo: el nombre de la app siempre va escrito al lado. */
@Composable
fun LogoCafetin(tamano: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.logo_cafetin), contentDescription = null, modifier = modifier.size(tamano))
}

/**
 * Botón principal del estilo: píldora amarilla con anillo, como un letrero (igual que `.boton` del
 * panel). Texto tierra en mayúsculas.
 */
@Composable
fun BotonAmarillo(
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    contenido: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val anillo = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
    Box(modifier.border(1.5.dp, anillo, CircleShape).padding(3.dp)) {
        Button(
            onClick = alPulsar,
            enabled = habilitado,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Paleta.amarillo,
                contentColor = Paleta.tierra,
                disabledContainerColor = Paleta.amarillo.copy(alpha = 0.5f),
                disabledContentColor = Paleta.tierra.copy(alpha = 0.6f),
            ),
            content = contenido,
        )
    }
}

/** Texto de un botón amarillo: mayúsculas con espaciado, como el panel. */
@Composable
fun TextoBoton(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(Locale.forLanguageTag("es")),
        modifier,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
    )
}
