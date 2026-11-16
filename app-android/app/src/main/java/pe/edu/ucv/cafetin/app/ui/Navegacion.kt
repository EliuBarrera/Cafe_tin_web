package pe.edu.ucv.cafetin.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import pe.edu.ucv.cafetin.app.R
import pe.edu.ucv.cafetin.app.datos.Cuentas
import pe.edu.ucv.cafetin.app.datos.Pedidos
import pe.edu.ucv.cafetin.app.datos.Sesion
import pe.edu.ucv.cafetin.app.ui.cuentas.LoginViewModel
import pe.edu.ucv.cafetin.app.ui.cuentas.PantallaLogin
import pe.edu.ucv.cafetin.app.ui.cuentas.PantallaRecuperar
import pe.edu.ucv.cafetin.app.ui.cuentas.PantallaRegistro
import pe.edu.ucv.cafetin.app.ui.cuentas.RecuperarViewModel
import pe.edu.ucv.cafetin.app.ui.cuentas.RegistroViewModel
import pe.edu.ucv.cafetin.app.ui.pedidos.AvisosViewModel
import pe.edu.ucv.cafetin.app.ui.pedidos.Carrito
import pe.edu.ucv.cafetin.app.ui.pedidos.CarritoViewModel
import pe.edu.ucv.cafetin.app.ui.pedidos.CatalogoViewModel
import pe.edu.ucv.cafetin.app.ui.pedidos.HistorialViewModel
import pe.edu.ucv.cafetin.app.ui.pedidos.PagoViewModel
import pe.edu.ucv.cafetin.app.ui.pedidos.PantallaAvisos
import pe.edu.ucv.cafetin.app.ui.pedidos.PantallaCarrito
import pe.edu.ucv.cafetin.app.ui.pedidos.PantallaCatalogo
import pe.edu.ucv.cafetin.app.ui.pedidos.PantallaHistorial
import pe.edu.ucv.cafetin.app.ui.pedidos.PantallaPago
import pe.edu.ucv.cafetin.app.ui.pedidos.PantallaPedido
import pe.edu.ucv.cafetin.app.ui.pedidos.PedidoViewModel

private object Rutas {
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val RECUPERAR = "recuperar"
    const val CATALOGO = "catalogo"
    const val CARRITO = "carrito"
    const val PEDIDOS = "pedidos"
    const val AVISOS = "avisos"
    const val PAGO = "pago/{id}"
    const val PEDIDO = "pedido/{id}"
    fun pago(id: Int) = "pago/$id"
    fun pedido(id: Int) = "pedido/$id"
}

private data class Pestana(val ruta: String, val titulo: String, @DrawableRes val icono: Int)

private val PESTANAS = listOf(
    Pestana(Rutas.CATALOGO, "Catálogo", R.drawable.ic_cafe),
    Pestana(Rutas.CARRITO, "Carrito", R.drawable.ic_carrito),
    Pestana(Rutas.PEDIDOS, "Pedidos", R.drawable.ic_recibo),
    Pestana(Rutas.AVISOS, "Avisos", R.drawable.ic_campana),
)

/**
 * Al abrir, revisa si hay una sesión guardada y vigente: si la hay va directo al catálogo; si la API la
 * rechaza (token vencido o cuenta desactivada), al login. Lo mismo si la rechaza después, en cualquier pantalla.
 */
@Composable
fun AppCafetin(cuentas: Cuentas, pedidos: Pedidos) {
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
    // Un carrito por sesión: al salir o entrar con otra cuenta empieza vacío.
    val carrito = remember(sesion?.idUsuario) { Carrito() }
    val alEntrar: (Sesion) -> Unit = { nueva ->
        sesion = nueva
        nav.navigate(Rutas.CATALOGO) { popUpTo(0) }
    }
    val alSalir: () -> Unit = {
        sesion = null
        nav.navigate(Rutas.LOGIN) { popUpTo(0) }
    }
    LaunchedEffect(cuentas) {
        cuentas.sesionRechazada.collect {
            cuentas.cerrarSesion()
            if (sesion != null) alSalir()
        }
    }

    val entrada by nav.currentBackStackEntryAsState()
    val rutaActual = entrada?.destination?.route
    val lineas by carrito.lineas.collectAsState()

    Scaffold(
        bottomBar = {
            if (sesion != null && PESTANAS.any { it.ruta == rutaActual }) {
                BarraPestanas(nav, rutaActual, unidadesCarrito = lineas.sumOf { it.cantidad })
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { margen ->
        NavHost(nav, startDestination = if (sesion != null) Rutas.CATALOGO else Rutas.LOGIN, modifier = Modifier.padding(margen)) {
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

            // ------------------------------------------------ Estudiante con sesión (RF-04 a RF-09)
            composable(Rutas.CATALOGO) {
                PantallaCatalogo(
                    vm = viewModel(factory = viewModelFactory { initializer { CatalogoViewModel(pedidos, carrito) } }),
                    nombre = sesion?.nombre.orEmpty(),
                    alCerrarSesion = { alcance.launch { cuentas.cerrarSesion(); alSalir() } },
                    alVerCarrito = { irAPestana(nav, Rutas.CARRITO) },
                )
            }
            composable(Rutas.CARRITO) {
                PantallaCarrito(
                    vm = viewModel(factory = viewModelFactory { initializer { CarritoViewModel(pedidos, carrito) } }),
                    // El pedido ya existe: si vuelve atrás sin pagar, lo encuentra en Pedidos.
                    alCrear = { id -> nav.navigate(Rutas.pago(id)) { popUpTo(Rutas.CATALOGO) } },
                    alVolverAlCatalogo = { irAPestana(nav, Rutas.CATALOGO) },
                )
            }
            composable(Rutas.PEDIDOS) {
                PantallaHistorial(
                    vm = viewModel(factory = viewModelFactory { initializer { HistorialViewModel(pedidos, carrito) } }),
                    alAbrir = { id -> nav.navigate(Rutas.pedido(id)) },
                    alIrAlCarrito = { irAPestana(nav, Rutas.CARRITO) },
                    alVerCatalogo = { irAPestana(nav, Rutas.CATALOGO) },
                )
            }
            composable(Rutas.AVISOS) {
                PantallaAvisos(
                    vm = viewModel(factory = viewModelFactory { initializer { AvisosViewModel(pedidos) } }),
                    alAbrirPedido = { id -> nav.navigate(Rutas.pedido(id)) },
                )
            }
            composable(Rutas.PAGO, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                val id = e.arguments!!.getInt("id")
                PantallaPago(
                    vm = viewModel(factory = viewModelFactory { initializer { PagoViewModel(pedidos, id) } }),
                    // Pagado: el recibo reemplaza al pago, así "atrás" no vuelve a una pantalla de cobro.
                    alPagar = { nav.navigate(Rutas.pedido(id)) { popUpTo(Rutas.PAGO) { inclusive = true } } },
                )
            }
            composable(Rutas.PEDIDO, arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                val id = e.arguments!!.getInt("id")
                PantallaPedido(
                    vm = viewModel(factory = viewModelFactory { initializer { PedidoViewModel(pedidos, id) } }),
                    alPagar = { nav.navigate(Rutas.pago(id)) },
                )
            }
        }
    }
}

@Composable
private fun BarraPestanas(nav: NavHostController, rutaActual: String?, unidadesCarrito: Int) {
    NavigationBar {
        PESTANAS.forEach { p ->
            NavigationBarItem(
                selected = rutaActual == p.ruta,
                onClick = { irAPestana(nav, p.ruta) },
                label = { Text(p.titulo) },
                icon = {
                    if (p.ruta == Rutas.CARRITO && unidadesCarrito > 0) {
                        BadgedBox(badge = { Badge { Text("$unidadesCarrito") } }) { Icon(painterResource(p.icono), contentDescription = null) }
                    } else {
                        Icon(painterResource(p.icono), contentDescription = null)
                    }
                },
            )
        }
    }
}

/** Cambia de pestaña sin apilar pantallas y conservando el estado de cada una. */
private fun irAPestana(nav: NavHostController, ruta: String) {
    nav.navigate(ruta) {
        // El catálogo siempre está al fondo de la pila con sesión (se llega a él con popUpTo(0)).
        popUpTo(Rutas.CATALOGO) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
