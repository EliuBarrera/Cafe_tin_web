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
    // Contenedores en tonos café: sin esto Material usa sus lilas por defecto (tarjetas, chips, barra).
    primaryContainer = Color(0xFFF3E1CE),
    onPrimaryContainer = Color(0xFF3A2410),
    secondaryContainer = Color(0xFFEBDDCF),
    onSecondaryContainer = Color(0xFF3A2410),
    surfaceVariant = Color(0xFFEDE5DC),
    onSurfaceVariant = Color(0xFF5C5048),
    outline = Color(0xFF8B7E74),
    outlineVariant = Color(0xFFD8CCC1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF8F4),
    surfaceContainer = Color(0xFFF3EEE8),
    surfaceContainerHigh = Color(0xFFEEE8E1),
    surfaceContainerHighest = Color(0xFFEFE8E0),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFE0B48A),
    onPrimary = Color(0xFF3A2410),
    secondary = Color(0xFFC9A27E),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF1C1612),
    onBackground = Color(0xFFEDE0D4),
    surface = Color(0xFF1C1612),
    onSurface = Color(0xFFEDE0D4),
    primaryContainer = Color(0xFF5E3816),
    onPrimaryContainer = Color(0xFFF3E1CE),
    secondaryContainer = Color(0xFF4A3828),
    onSecondaryContainer = Color(0xFFEBDDCF),
    surfaceVariant = Color(0xFF4F453D),
    onSurfaceVariant = Color(0xFFD3C4B8),
    outline = Color(0xFF9C8E83),
    outlineVariant = Color(0xFF4F453D),
    surfaceContainerLowest = Color(0xFF16110D),
    surfaceContainerLow = Color(0xFF241D18),
    surfaceContainer = Color(0xFF29211B),
    surfaceContainerHigh = Color(0xFF332A23),
    surfaceContainerHighest = Color(0xFF3E342C),
)

@Composable
fun TemaCafetin(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = contenido)
}
