package pe.edu.ucv.cafetin.app.ui.pedidos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.edu.ucv.cafetin.app.datos.CrearPedidoRequest
import pe.edu.ucv.cafetin.app.datos.ErrorApi
import pe.edu.ucv.cafetin.app.datos.ItemPedidoRequest
import pe.edu.ucv.cafetin.app.datos.Notificacion
import pe.edu.ucv.cafetin.app.datos.Pago
import pe.edu.ucv.cafetin.app.datos.PaginaPedidos
import pe.edu.ucv.cafetin.app.datos.PedidoResponse
import pe.edu.ucv.cafetin.app.datos.PedidoResumen
import pe.edu.ucv.cafetin.app.datos.Pedidos
import pe.edu.ucv.cafetin.app.datos.ProductoCatalogo
import pe.edu.ucv.cafetin.app.datos.Recibo
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class PedidosViewModelsTest {

    private val api = PedidosFalsos()
    private val carrito = Carrito()

    @Before
    fun preparar() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun limpiar() = Dispatchers.resetMain()

    @Test
    fun catalogoCargaYFiltraPorCategoria() {
        api.catalogo = listOf(producto(1).copy(categoria = "Bebidas"), producto(2).copy(categoria = "Snacks"))
        val vm = CatalogoViewModel(api, carrito)

        assertEquals(listOf("Bebidas", "Snacks"), vm.estado.value.categorias)
        vm.filtrar("Snacks")
        assertEquals(listOf(2), vm.estado.value.visibles.map { it.idProducto })
    }

    @Test
    fun catalogoAvisaSiNoQuedanMasUnidades() {
        val vm = CatalogoViewModel(api, carrito)
        val p = producto(1, disponible = 1)

        vm.agregar(p)
        vm.agregar(p)

        assertEquals("No hay más unidades de Producto 1", vm.estado.value.aviso)
        assertEquals(1, carrito.cantidadDe(1))
    }

    @Test
    fun carritoCreaElPedidoConLaHoraElegidaYSeVacia() {
        carrito.agregar(producto(7))
        carrito.agregar(producto(7))
        val vm = CarritoViewModel(api, carrito) { LocalTime.of(10, 2) }

        vm.elegirHora(LocalTime.of(10, 45))
        vm.cambiarObservacion("  Sin ají  ")
        vm.confirmar()

        assertEquals(CrearPedidoRequest("10:45", listOf(ItemPedidoRequest(7, 2)), "Sin ají"), api.creado)
        assertEquals(30, vm.estado.value.pedidoCreado)
        assertTrue(carrito.lineas.value.isEmpty())
    }

    @Test
    fun carritoVacioNoLlamaALaApi() {
        val vm = CarritoViewModel(api, carrito) { LocalTime.of(10, 0) }
        vm.confirmar()
        assertEquals("Tu carrito está vacío", vm.estado.value.error)
        assertNull(api.creado)
    }

    @Test
    fun sinStockAlCrearConservaElCarritoYMuestraElMensajeDeLaApi() {
        carrito.agregar(producto(7))
        api.falla = ErrorApi(409, "STOCK_INSUFICIENTE", "Solo quedan 0 unidades de Café pasado")
        val vm = CarritoViewModel(api, carrito) { LocalTime.of(10, 0) }

        vm.confirmar()

        assertEquals("Solo quedan 0 unidades de Café pasado", vm.estado.value.error)
        assertEquals(1, carrito.unidades())
        assertNull(vm.estado.value.pedidoCreado)
    }

    @Test
    fun pagoRechazadoPermiteReintentarYLuegoMuestraElRecibo() {
        val vm = PagoViewModel(api, 30)
        api.falla = ErrorApi(402, "PAGO_RECHAZADO", "Rechazado")

        vm.pagar()
        assertTrue(vm.estado.value.error!!.startsWith("El pago fue rechazado"))
        assertFalse(vm.estado.value.pagado)

        api.falla = null
        vm.elegirMetodo("PLIN")
        vm.pagar()

        assertEquals("PLIN", api.metodoPagado)
        assertTrue(vm.estado.value.pagado)
        assertEquals("DR-0430-1234", vm.estado.value.pedido?.recibo?.codigoVerificacion)
    }

    @Test
    fun pagoSinStockExplicaQueOtroCompraLasUltimasUnidades() {
        val vm = PagoViewModel(api, 30)
        api.falla = ErrorApi(409, "STOCK_INSUFICIENTE", "stock insuficiente")
        vm.pagar()
        assertTrue(vm.estado.value.error!!.startsWith("Otro estudiante compró"))
    }

    @Test
    fun cancelarUnPedidoPagadoMuestraQueYaNoSePuede() {
        val vm = PedidoViewModel(api, 30)
        api.falla = ErrorApi(409, "TRANSICION_INVALIDA", "No se puede")
        vm.cancelar()
        assertEquals("Este pedido ya no se puede cancelar.", vm.estado.value.error)
    }

    @Test
    fun historialPideLaSiguientePaginaSoloSiHayMas() {
        api.paginas = 2
        val vm = HistorialViewModel(api)
        assertTrue(vm.estado.value.hayMas)

        vm.cargarMas()
        assertEquals(listOf(0, 1), api.paginasPedidas)
        assertEquals(2, vm.estado.value.pedidos.size)
        assertFalse(vm.estado.value.hayMas)

        vm.cargarMas()
        assertEquals(listOf(0, 1), api.paginasPedidas)
    }

    @Test
    fun abrirUnAvisoLoMarcaLeido() {
        api.avisos = listOf(Notificacion(1, "PEDIDO_LISTO", "Tu pedido está listo", 30))
        val vm = AvisosViewModel(api)
        assertEquals(1, vm.estado.value.noLeidos)

        vm.abrir(vm.estado.value.avisos.first())

        assertEquals(0, vm.estado.value.noLeidos)
        assertEquals(listOf(1), api.leidas)
    }
}

/** API de pedidos en memoria para los ViewModels. */
class PedidosFalsos : Pedidos {
    var catalogo: List<ProductoCatalogo> = emptyList()
    var falla: ErrorApi? = null
    var creado: CrearPedidoRequest? = null
    var metodoPagado: String? = null
    var paginas = 1
    val paginasPedidas = mutableListOf<Int>()
    var avisos: List<Notificacion> = emptyList()
    val leidas = mutableListOf<Int>()

    private val sinPagar = PedidoResponse(30, "PED-430", "PENDIENTE", total = 5.0)

    private fun fallarSiToca() = falla?.let { throw it }

    override suspend fun catalogo() = catalogo.also { fallarSiToca() }

    override suspend fun crear(solicitud: CrearPedidoRequest): PedidoResponse {
        fallarSiToca()
        creado = solicitud
        return sinPagar
    }

    override suspend fun pagar(idPedido: Int, metodoPago: String): PedidoResponse {
        fallarSiToca()
        metodoPagado = metodoPago
        return sinPagar.copy(pago = Pago(5.0, metodoPago, "PAGADO"), recibo = Recibo("DR-0430-1234"))
    }

    override suspend fun cancelar(idPedido: Int) = sinPagar.copy(estado = "CANCELADO").also { fallarSiToca() }

    override suspend fun pedido(idPedido: Int) = sinPagar

    override suspend fun historial(pagina: Int, tamano: Int): PaginaPedidos {
        paginasPedidas += pagina
        val resumen = PedidoResumen(pagina, "PED-$pagina", "ENTREGADO", "PAGADO", total = 5.0)
        return PaginaPedidos(listOf(resumen), pagina, tamano, paginas.toLong(), paginas)
    }

    override suspend fun notificaciones() = avisos

    override suspend fun marcarLeida(idNotificacion: Int) {
        leidas += idNotificacion
    }
}
