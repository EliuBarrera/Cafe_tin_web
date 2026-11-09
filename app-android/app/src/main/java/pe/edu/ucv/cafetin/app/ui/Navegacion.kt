package pe.edu.ucv.cafetin.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import pe.edu.ucv.cafetin.app.datos.Cuentas
import pe.edu.ucv.cafetin.app.datos.Sesion
import pe.edu.ucv.cafetin.app.ui.cuentas.LoginViewModel
import pe.edu.ucv.cafetin.app.ui.cuentas.PantallaLogin
import pe.edu.ucv.cafetin.app.ui.cuentas.PantallaRecuperar
import pe.edu.ucv.cafetin.app.ui.cuentas.PantallaRegistro
import pe.edu.ucv.cafetin.app.ui.cuentas.RecuperarViewModel
import pe.edu.ucv.cafetin.app.ui.cuentas.RegistroViewModel
import pe.edu.ucv.cafetin.app.ui.inicio.PantallaInicio

private object Rutas {
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val RECUPERAR = "recuperar"
    const val INICIO = "inicio"
}

/**
 * Al abrir, revisa si hay una sesión guardada y vigente: si la hay va directo al inicio; si la API la
 * rechaza (token vencido o cuenta desactivada), al login.
 */
@Composable
fun AppCafetin(cuentas: Cuentas) {
    var revisando by remember { mutableStateOf(true) }
    var sesion by remember { mutableStateOf<Sesion?>(null) }
    LaunchedEffect(Unit) {
        sesion = cuentas.sesionVigente()
        revisando = false
    }
    if (revisando) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val nav = rememberNavController()
    val alcance = rememberCoroutineScope()
    val alEntrar: (Sesion) -> Unit = { nueva ->
        sesion = nueva
        nav.navigate(Rutas.INICIO) { popUpTo(0) }
    }

    NavHost(nav, startDestination = if (sesion != null) Rutas.INICIO else Rutas.LOGIN) {
        composable(Rutas.LOGIN) {
            PantallaLogin(
                vm = viewModel(factory = viewModelFactory { initializer { LoginViewModel(cuentas) } }),
                alEntrar = alEntrar,
                alRegistrarse = { nav.navigate(Rutas.REGISTRO) },
                alRecuperar = { nav.navigate(Rutas.RECUPERAR) },
            )
        }
        composable(Rutas.REGISTRO) {
            PantallaRegistro(
                vm = viewModel(factory = viewModelFactory { initializer { RegistroViewModel(cuentas) } }),
                alEntrar = alEntrar,
                alVolver = { nav.popBackStack() },
            )
        }
        composable(Rutas.RECUPERAR) {
            PantallaRecuperar(
                vm = viewModel(factory = viewModelFactory { initializer { RecuperarViewModel(cuentas) } }),
                alVolver = { nav.popBackStack() },
            )
        }
        composable(Rutas.INICIO) {
            val actual = sesion
            if (actual != null) {
                PantallaInicio(actual, alCerrarSesion = {
                    alcance.launch {
                        cuentas.cerrarSesion()
                        sesion = null
                        nav.navigate(Rutas.LOGIN) { popUpTo(0) }
                    }
                })
            }
        }
    }
}
