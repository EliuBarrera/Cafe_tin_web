package pe.edu.ucv.cafetin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import pe.edu.ucv.cafetin.app.ui.AppCafetin
import pe.edu.ucv.cafetin.app.ui.TemaCafetin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CafetinApp
        setContent {
            TemaCafetin {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    AppCafetin(app.cuentas)
                }
            }
        }
    }
}
