package pe.edu.ucv.cafetin.app.ui.inicio

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Pide el permiso de notificaciones (Android 13+) para los avisos de estado del pedido (RF-08). */
@Composable
fun PedirPermisoNotificaciones() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    // Si lo niega, la app sigue igual: los avisos se ven en la pestaña Avisos.
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { lanzador.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
