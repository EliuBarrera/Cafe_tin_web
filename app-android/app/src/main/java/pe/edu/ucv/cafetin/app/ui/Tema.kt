package pe.edu.ucv.cafetin.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.ucv.cafetin.app.R

/**
 * Estilo "de ruta", el mismo del panel web (panel-web/src/estilos.css): papel crema con líneas
 * topográficas, titulares pesados y expandidos (Archivo 125 %), acento script (Yesteryear), naranja
 * quemado, verde azulado y botones amarillos. Textos con contraste ≥ 4,5:1; el amarillo solo lleva
 * texto tierra encima.
 */
object Paleta {
    val crema = Color(0xFFF4EBD3)
    val superficie = Color(0xFFFBF6EA)
    val tierra = Color(0xFF3B2D22)
    val textoSuave = Color(0xFF6E5A48)
    val borde = Color(0xFF8F7A66)
    val linea = Color(0x293B2D22)
    val naranja = Color(0xFFB8421C)
    val naranjaOscuro = Color(0xFF9E3615)
    val verde = Color(0xFF0F5C5E)
    val amarillo = Color(0xFFF7D348)
    val amarilloOscuro = Color(0xFFECC12A)
    /** Texto sobre naranja o verde. */
    val enColor = Color(0xFFF8F0DC)
}

private val Claro = lightColorScheme(
    primary = Paleta.naranja,
    onPrimary = Paleta.enColor,
    secondary = Paleta.verde,
    onSecondary = Paleta.enColor,
    tertiary = Paleta.amarillo,
    onTertiary = Paleta.tierra,
    background = Paleta.crema,
    onBackground = Paleta.tierra,
    surface = Paleta.superficie,
    onSurface = Paleta.tierra,
    error = Color(0xFFA4262C),
    errorContainer = Color(0xFFFBE9E7),
    onErrorContainer = Color(0xFFA4262C),
    // Botones tonales y chips elegidos en amarillo con texto tierra, como los botones del panel.
    primaryContainer = Color(0xFFFCEFC0),
    onPrimaryContainer = Paleta.tierra,
    secondaryContainer = Paleta.amarillo,
    onSecondaryContainer = Paleta.tierra,
    surfaceVariant = Color(0xFFEFE4CB),
    onSurfaceVariant = Paleta.textoSuave,
    outline = Paleta.borde,
    outlineVariant = Color(0xFFDCCFB3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Paleta.superficie,
    surfaceContainer = Paleta.superficie,
    surfaceContainerHigh = Color(0xFFF1E7D0),
    surfaceContainerHighest = Color(0xFFEBDFC4),
)

private val Oscuro = darkColorScheme(
    primary = Paleta.amarillo,
    onPrimary = Paleta.tierra,
    secondary = Color(0xFF7FC4C2),
    onSecondary = Color(0xFF07302F),
    tertiary = Paleta.amarillo,
    onTertiary = Paleta.tierra,
    background = Color(0xFF211912),
    onBackground = Paleta.crema,
    surface = Color(0xFF2C221A),
    onSurface = Paleta.crema,
    error = Color(0xFFFFB4AB),
    primaryContainer = Color(0xFF4A3A1E),
    onPrimaryContainer = Paleta.crema,
    secondaryContainer = Paleta.amarillo,
    onSecondaryContainer = Paleta.tierra,
    surfaceVariant = Color(0xFF45382C),
    onSurfaceVariant = Color(0xFFD8C8B2),
    outline = Color(0xFFA08E7A),
    outlineVariant = Color(0xFF4F4235),
    surfaceContainerLowest = Color(0xFF1A130E),
    surfaceContainerLow = Color(0xFF2C221A),
    surfaceContainer = Color(0xFF2C221A),
    surfaceContainerHigh = Color(0xFF362B21),
    surfaceContainerHighest = Color(0xFF413428),
)

/** Acentos fuera del esquema de Material. */
object Acentos {
    /** Puntos, bordes y fondos. */
    val caramelo = Paleta.naranja
    /** Texto de acento sobre fondo claro (contraste ≥ 4,5:1, WCAG 2.1 AA). */
    val carameloTexto = Paleta.naranjaOscuro
    /** Fondo de tarjetas destacadas: amarillo pálido. */
    val crema = Color(0xFFFCEFC0)
    val exito = Color(0xFF2F6B3A)
}

// Archivo es una fuente variable: el mismo archivo da el ancho normal (cuerpo) y el expandido (titulares).
@OptIn(ExperimentalTextApi::class)
private fun archivo(peso: Int, ancho: Float) = Font(
    R.font.archivo,
    weight = FontWeight(peso),
    variationSettings = FontVariation.Settings(FontVariation.weight(peso), FontVariation.width(ancho)),
)

/** Cuerpo de texto: Archivo a ancho normal. */
val Archivo = FontFamily(archivo(400, 100f), archivo(500, 100f), archivo(600, 100f), archivo(700, 100f), archivo(800, 100f))

/** Titulares: Archivo expandido al 125 %, pesado. Se escriben en mayúsculas (ver [TituloPantalla]). */
val ArchivoExpandida = FontFamily(archivo(700, 125f), archivo(800, 125f), archivo(900, 125f))

/** Acento manuscrito ("sin hacer fila"). */
val Script = FontFamily(Font(R.font.yesteryear))

private val base = Typography()
private fun TextStyle.cuerpo() = copy(fontFamily = Archivo)
private fun TextStyle.titular(peso: FontWeight = FontWeight.Black) =
    copy(fontFamily = ArchivoExpandida, fontWeight = peso, letterSpacing = 0.sp)

private val Tipografia = Typography(
    displayLarge = base.displayLarge.titular(),
    displayMedium = base.displayMedium.titular(),
    displaySmall = base.displaySmall.titular(),
    headlineLarge = base.headlineLarge.titular(),
    headlineMedium = base.headlineMedium.titular().copy(fontSize = 26.sp, lineHeight = 28.sp),
    headlineSmall = base.headlineSmall.titular().copy(fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = base.titleLarge.titular(FontWeight.ExtraBold).copy(fontSize = 19.sp),
    titleMedium = base.titleMedium.cuerpo().copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.cuerpo().copy(fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.cuerpo(),
    bodyMedium = base.bodyMedium.cuerpo(),
    bodySmall = base.bodySmall.cuerpo(),
    labelLarge = base.labelLarge.cuerpo().copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp),
    labelMedium = base.labelMedium.cuerpo().copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
    labelSmall = base.labelSmall.cuerpo().copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/** Estilo de los precios: tabular para que las cifras no bailen al cambiar cantidades. */
val estiloPrecio = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

@Composable
fun TemaCafetin(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        typography = Tipografia,
        shapes = Formas,
        content = contenido,
    )
}
