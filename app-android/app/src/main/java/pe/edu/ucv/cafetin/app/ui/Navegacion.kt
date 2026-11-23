package pe.edu.ucv.cafetin.app.ui

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pe.edu.ucv.cafetin.app.R
import pe.edu.ucv.cafetin.app.datos.AlmacenSesion
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
fun AppCafetin(
    cuentas: Cuentas,
    pedidos: Pedidos,
    almacen: AlmacenSesion? = null,
    enlacePago: StateFlow<Int?> = MutableStateFlow(null),
    alAtenderEnlace: () -> Unit = {},
) {
    var revisando by remember { mutableStateOf(true) }
    var sesion by remember { mutableStateOf<Sesion?>(null) }
    LaunchedEffect(Unit) {
        sesion = cuentas.sesionVigente()
        revisando = false
    }
    if (revisando) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LogoCafetin(120.dp)
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(color = Paleta.naranja)
            }
        }
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

    // Vuelta de Mercado Pago (cafetin://pago/{id}). Si ya está en el pago de ese pedido, la propia pantalla
    // verifica al volver a primer plano; si no, se abre. Sin sesión espera al login.
    val pendiente by enlacePago.collectAsState()
    LaunchedEffect(pendiente, sesion) {
        val id = pendiente ?: return@LaunchedEffect
        if (sesion == null) return@LaunchedEffect
        val actual = nav.currentBackStackEntry
        val yaAbierto = actual?.destination?.route == Rutas.PAGO && actual.arguments?.getInt("id") == id
        if (!yaAbierto) nav.navigate(Rutas.pago(id))
        alAtenderEnlace()
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
                    vm = viewModel(factory = viewModelFactory { initializer {
                        CatalogoViewModel(
                            pedidos, carrito,
                            preferida = { almacen?.cafeteriaElegida() },
                            recordar = { id -> almacen?.recordarCafeteria(id) },
                        )
                    } }),
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

/**
 * Dock flotante: una cápsula tierra separada de los bordes, con sombra. La pestaña actual tiene el ícono
 * en amarillo, un poco más grande, y un punto debajo (como las apps abiertas en un dock); las demás en crema
 * suave. Solo íconos: el nombre va en la descripción para lectores de pantalla.
 */
@Composable
private fun BarraPestanas(nav: NavHostController, rutaActual: String?, unidadesCarrito: Int) {
    val oscuro = isSystemInDarkTheme()
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(28.dp), ambientColor = Paleta.tierra, spotColor = Paleta.tierra)
                .clip(RoundedCornerShape(28.dp))
                .background(if (oscuro) MaterialTheme.colorScheme.surfaceContainerHighest else Paleta.tierra)
                .selectableGroup()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            PESTANAS.forEach { p ->
                Pestana(
                    pestana = p,
                    elegida = rutaActual == p.ruta,
                    insignia = if (p.ruta == Rutas.CARRITO && unidadesCarrito > 0) unidadesCarrito else null,
                    alPulsar = { irAPestana(nav, p.ruta) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Pestana(pestana: Pestana, elegida: Boolean, insignia: Int?, alPulsar: () -> Unit, modifier: Modifier = Modifier) {
    val reposo = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onSurfaceVariant else Paleta.enColor.copy(alpha = 0.7f)
    val color by animateColorAsState(if (elegida) Paleta.amarillo else reposo, label = "color")
    val crecer = spring<Float>(dampingRatio = 0.5f, stiffness = 500f)
    val escala by animateFloatAsState(if (elegida) 1.15f else 1f, animationSpec = crecer, label = "escala")
    val punto by animateFloatAsState(if (elegida) 1f else 0f, animationSpec = crecer, label = "punto")
    Column(
        modifier
            .heightIn(min = 56.dp)
            .selectable(
                selected = elegida,
                onClick = alPulsar,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 28.dp, color = Paleta.amarillo),
            )
            .semantics { contentDescription = pestana.titulo },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        val icono = @Composable {
            Icon(
                painterResource(pestana.icono),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(26.dp).graphicsLayer { scaleX = escala; scaleY = escala },
            )
        }
        if (insignia != null) {
            BadgedBox(badge = { Badge(containerColor = Paleta.naranja, contentColor = Paleta.enColor) { Text("$insignia") } }) { icono() }
        } else {
            icono()
        }
        Box(
            Modifier
                .size(5.dp)
                .graphicsLayer { scaleX = punto; scaleY = punto; alpha = punto }
                .background(Paleta.amarillo, CircleShape),
        )
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
