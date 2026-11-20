package pe.edu.ucv.cafetin.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import pe.edu.ucv.cafetin.app.ui.AppCafetin
import pe.edu.ucv.cafetin.app.ui.TemaCafetin

/**
 * `launchMode="singleTask"`: al volver de Checkout Pro, el enlace `cafetin://pago/{id}` llega a esta misma
 * actividad ([onNewIntent]) y no abre otra encima con la sesión y la navegación desde cero.
 */
class MainActivity : ComponentActivity() {

    /** Pedido del último enlace `cafetin://pago/{id}` sin atender; la navegación lo consume. */
    private val enlacePago = MutableStateFlow<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) enlacePago.value = idPedidoDelEnlace(intent)
        val app = application as CafetinApp
        setContent {
            TemaCafetin {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    AppCafetin(app.cuentas, app.pedidos, app.almacen, enlacePago) { enlacePago.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        idPedidoDelEnlace(intent)?.let { enlacePago.value = it }
    }
}

/** Id del pedido de `cafetin://pago/{id}`, o null si el intent no es ese enlace. */
internal fun idPedidoDelEnlace(intent: Intent?): Int? {
    val uri = intent?.data ?: return null
    if (uri.scheme != "cafetin" || uri.host != "pago") return null
    return uri.lastPathSegment?.toIntOrNull()
}
