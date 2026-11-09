package pe.edu.ucv.cafetin.app.ui.inicio

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.edu.ucv.cafetin.app.datos.Sesion

/**
 * Inicio del estudiante. El catálogo, el carrito y el historial (RF-04 a RF-09) van aquí en la
 * siguiente entrega. Al entrar se piden los permisos de notificación (Android 13+) para RF-08.
 */
@Composable
fun PantallaInicio(sesion: Sesion, alCerrarSesion: () -> Unit) {
    PedirPermisoNotificaciones()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Hola, ${sesion.nombre}", style = MaterialTheme.typography.headlineSmall)
            Text("Aquí verás el catálogo para pedir y pagar sin hacer fila.", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = alCerrarSesion) { Text("Cerrar sesión") }
        }
    }
}

@Composable
private fun PedirPermisoNotificaciones() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    // Si lo niega, la app sigue igual: los avisos se ven en la bandeja al abrirla.
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { lanzador.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
