package pe.edu.ucv.cafetin.app.ui.cuentas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import pe.edu.ucv.cafetin.app.ui.BotonAmarillo
import pe.edu.ucv.cafetin.app.ui.LogoCafetin
import pe.edu.ucv.cafetin.app.ui.Paleta
import pe.edu.ucv.cafetin.app.ui.TextoBoton
import pe.edu.ucv.cafetin.app.ui.TituloPantalla
import pe.edu.ucv.cafetin.app.ui.topografia

/** Marco de registro y recuperación: papel con curvas de nivel, marca arriba y el formulario en una tarjeta. */
@Composable
fun MarcoCuenta(titulo: String, contenido: @Composable () -> Unit) {
    val fondo = MaterialTheme.colorScheme.background
    Box(
        Modifier.fillMaxSize().background(fondo).topografia(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.075f)),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Marca()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TituloPantalla(titulo, color = MaterialTheme.colorScheme.onSurface)
                contenido()
            }
        }
    }
}

/** Logo y nombre, como la marca del panel. */
@Composable
fun Marca() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoCafetin(52.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("CAFETÍN UCV", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "PIDE, PAGA Y RECOGE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CampoTexto(
    etiqueta: String,
    valor: String,
    alCambiar: (String) -> Unit,
    error: String? = null,
    teclado: KeyboardType = KeyboardType.Text,
    secreto: Boolean = false,
    ultimo: Boolean = false,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedBorderColor = Paleta.naranja,
            focusedLabelColor = Paleta.naranjaOscuro,
            cursorColor = Paleta.naranja,
        ),
        visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = if (ultimo) ImeAction.Done else ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Acción principal de la pantalla: el botón amarillo del estilo, con un círculo mientras espera. */
@Composable
fun BotonPrincipal(texto: String, cargando: Boolean, alPulsar: () -> Unit) {
    BotonAmarillo(alPulsar, Modifier.fillMaxWidth(), habilitado = !cargando) {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Paleta.tierra)
            Spacer(Modifier.width(10.dp))
        }
        TextoBoton(texto)
    }
}

/** Error general de la pantalla; los lectores de pantalla lo anuncian al aparecer. */
@Composable
fun MensajeError(mensaje: String?) {
    if (mensaje != null) {
        Text(
            mensaje,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
