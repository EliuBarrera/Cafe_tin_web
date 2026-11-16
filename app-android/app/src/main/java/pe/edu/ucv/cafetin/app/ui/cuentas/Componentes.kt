package pe.edu.ucv.cafetin.app.ui.cuentas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Marco de las pantallas sin sesión: título, contenido desplazable y teclado sin tapar campos. */
@Composable
fun MarcoCuenta(titulo: String, contenido: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Cafetín UCV", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(titulo, style = MaterialTheme.typography.headlineSmall)
            contenido()
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
        visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = if (ultimo) ImeAction.Done else ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun BotonPrincipal(texto: String, cargando: Boolean, alPulsar: () -> Unit) {
    Button(onClick = alPulsar, enabled = !cargando, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        if (cargando) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(texto)
    }
}

/** Error general de la pantalla; los lectores de pantalla lo anuncian al aparecer. */
@Composable
fun MensajeError(mensaje: String?) {
    if (mensaje != null) {
        Text(
            mensaje,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
