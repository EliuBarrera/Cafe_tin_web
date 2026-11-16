package pe.edu.ucv.cafetin.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mismos tonos café que el panel web (panel-web/src/estilos.css).
private val Claro = lightColorScheme(
    primary = Color(0xFF7A4A21),
    onPrimary = Color.White,
    secondary = Color(0xFF5E3816),
    background = Color(0xFFF7F4EF),
    surface = Color.White,
    onBackground = Color(0xFF2B211A),
    onSurface = Color(0xFF2B211A),
    error = Color(0xFFA4262C),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFE0B48A),
    onPrimary = Color(0xFF3A2410),
    secondary = Color(0xFFC9A27E),
    error = Color(0xFFFFB4AB),
)

@Composable
fun TemaCafetin(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = contenido)
}
