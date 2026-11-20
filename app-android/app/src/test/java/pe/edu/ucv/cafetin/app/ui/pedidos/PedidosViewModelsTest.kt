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
import pe.edu.ucv.cafetin.app.datos.Cafeteria
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
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        carrito.elegir(PRINCIPAL)
    }

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

        assertEquals(CrearPedidoRequest(1, "10:45", listOf(ItemPedidoRequest(7, 2)), "Sin ají"), api.creado)
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
    fun conMercadoPagoAbreElCheckoutYConfirmaAlVolver() {
        api.urlCheckout = "https://mp.test/checkout?pref_id=1"
        val vm = PagoViewModel(api, 30)

        vm.pagar()
        assertEquals("https://mp.test/checkout?pref_id=1", vm.estado.value.abrirUrl)
        assertTrue(vm.estado.value.enMercadoPago)
        assertFalse(vm.estado.value.pagado)

        vm.urlAbierta()
        assertNull(vm.estado.value.abrirUrl)

        api.verificado = PedidoResponse(30, "PED-430", "PENDIENTE", total = 5.0, pago = Pago(5.0, "TARJETA", "PAGADO"), recibo = Recibo("DR-0430-1234"))
        vm.verificar(silencioso = true)

        assertTrue(vm.estado.value.pagado)
        assertFalse(vm.estado.value.enMercadoPago)
    }

    @Test
    fun pagoAunNoConfirmadoSoloAvisaSiElEstudianteLoPide() {
        api.urlCheckout = "https://mp.test/checkout"
        val vm = PagoViewModel(api, 30)
        vm.pagar()

        vm.verificar(silencioso = true)
        assertNull(vm.estado.value.aviso)
        assertTrue(vm.estado.value.enMercadoPago)

        vm.verificar()
        assertTrue(vm.estado.value.aviso!!.startsWith("Mercado Pago aún no confirma"))
        assertFalse(vm.estado.value.pagado)
    }

    @Test
    fun pagoRechazadoEnMercadoPagoPermiteReintentar() {
        api.urlCheckout = "https://mp.test/checkout"
        val vm = PagoViewModel(api, 30)
        vm.pagar()

        api.verificado = PedidoResponse(30, "PED-430", "PENDIENTE", total = 5.0, pago = Pago(5.0, "TARJETA", "FALLIDO"))
        vm.verificar(silencioso = true)

        assertFalse(vm.estado.value.enMercadoPago)
        assertTrue(vm.estado.value.error!!.startsWith("El pago fue rechazado"))
    }

    @Test
    fun sinStockAlConfirmarExplicaElReembolso() {
        api.urlCheckout = "https://mp.test/checkout"
        val vm = PagoViewModel(api, 30)
        vm.pagar()

        api.verificado = PedidoResponse(30, "PED-430", "CANCELADO", total = 5.0, pago = Pago(5.0, "TARJETA", "REEMBOLSADO"))
        vm.verificar(silencioso = true)

        assertFalse(vm.estado.value.pagado)
        assertTrue(vm.estado.value.error!!.contains("te devolvimos el dinero"))
    }

    @Test
    fun pagoAMediasSeVerificaAlAbrirLaPantalla() {
        api.leido = PedidoResponse(30, "PED-430", "PENDIENTE", total = 5.0, pago = Pago(5.0, "TARJETA", "PENDIENTE", urlPago = "https://mp.test/checkout"))
        api.verificado = PedidoResponse(30, "PED-430", "PENDIENTE", total = 5.0, pago = Pago(5.0, "TARJETA", "PAGADO"), recibo = Recibo("DR-0430-1234"))

        val vm = PagoViewModel(api, 30)

        assertEquals(1, api.verificaciones)
        assertTrue(vm.estado.value.pagado)
    }

    @Test
    fun conLaPasarelaSimuladaNoSeVerifica() {
        val vm = PagoViewModel(api, 30)
        vm.verificar()
        assertEquals(0, api.verificaciones)
        assertNull(vm.estado.value.abrirUrl)
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
    fun repetirElUltimoPedidoLlenaElCarritoYVaAlCarrito() {
        api.catalogo = listOf(producto(7, disponible = 10))
        api.items = listOf(pe.edu.ucv.cafetin.app.datos.LineaPedido(7, "Café", 2, 2.5, 5.0))
        val vm = CatalogoViewModel(api, carrito)
        assertEquals("PED-0", vm.estado.value.ultimo?.codigoPedido)

        vm.repetirUltimo()

        assertEquals(2, carrito.cantidadDe(7))
        assertTrue(vm.estado.value.irAlCarrito)
    }

    @Test
    fun repetirSinNadaDisponibleAvisaYNoNavega() {
        api.catalogo = listOf(producto(7, disponible = 0, estado = "AGOTADO"))
        api.items = listOf(pe.edu.ucv.cafetin.app.datos.LineaPedido(7, "Café", 1, 2.5, 2.5))
        val vm = CatalogoViewModel(api, carrito)

        vm.repetirUltimo()

        assertFalse(vm.estado.value.irAlCarrito)
        assertEquals("Los productos de tu último pedido no están disponibles ahora", vm.estado.value.aviso)
    }

    @Test
    fun volverAPedirDesdeElHistorialUsaElCatalogoDeHoy() {
        api.catalogo = listOf(producto(7, precio = 3.0, disponible = 10))
        api.items = listOf(pe.edu.ucv.cafetin.app.datos.LineaPedido(7, "Café", 1, 2.5, 2.5))
        val vm = HistorialViewModel(api, carrito)

        vm.volverAPedir(0)

        assertEquals(300L, carrito.totalCentimos())
        assertTrue(vm.estado.value.irAlCarrito)
    }

    // ------------------------------------------------------------ Cafetines

    @Test
    fun conUnSoloCafetinLoEligeSoloYCargaSuCatalogo() {
        api.catalogo = listOf(producto(1))
        val vm = CatalogoViewModel(api, Carrito())

        assertEquals(PRINCIPAL, vm.estado.value.cafeteria)
        assertFalse(vm.estado.value.eligiendo)
        assertEquals(listOf(1), api.catalogosPedidos)
    }

    @Test
    fun conVariosCafetinesPideElegirSalvoQueHayaUnoRecordado() {
        api.cafeterias = listOf(PRINCIPAL, NORTE)
        val sinRecordar = CatalogoViewModel(api, Carrito())
        assertTrue(sinRecordar.estado.value.eligiendo)
        assertNull(sinRecordar.estado.value.cafeteria)
        assertTrue(api.catalogosPedidos.isEmpty())

        var recordado: Int? = null
        val conRecordado = CatalogoViewModel(api, Carrito(), preferida = { 2 }, recordar = { recordado = it })
        assertEquals(NORTE, conRecordado.estado.value.cafeteria)
        assertEquals(listOf(2), api.catalogosPedidos)
        assertEquals(2, recordado)
    }

    @Test
    fun cambiarDeCafetinConElCarritoLlenoPideConfirmarYLoVacia() {
        api.cafeterias = listOf(PRINCIPAL, NORTE)
        api.catalogo = listOf(producto(7)) // si no, al cargar el catálogo el carrito suelta el producto
        carrito.agregar(producto(7))
        val vm = CatalogoViewModel(api, carrito)

        vm.cambiarCafeteria()
        vm.elegir(NORTE)
        assertEquals(NORTE, vm.estado.value.porConfirmar)
        assertEquals(1, carrito.unidades())

        vm.cancelarCambio()
        assertEquals(PRINCIPAL, carrito.cafeteria.value)

        vm.elegir(NORTE)
        vm.confirmarCambio()
        assertEquals(NORTE, carrito.cafeteria.value)
        assertEquals(0, carrito.unidades())
        assertFalse(vm.estado.value.eligiendo)
        assertEquals(2, api.catalogosPedidos.last())
    }

    @Test
    fun carritoSinCafetinNoCreaElPedido() {
        val sinCafetin = Carrito()
        sinCafetin.agregar(producto(7))
        val vm = CarritoViewModel(api, sinCafetin) { LocalTime.of(10, 0) }

        vm.confirmar()

        assertNull(api.creado)
        assertEquals("Elige un cafetín en el catálogo", vm.estado.value.error)
    }

    @Test
    fun volverAPedirDeOtroCafetinConElCarritoLlenoAvisaSinMezclar() {
        api.cafeteriaDelPedido = NORTE
        carrito.agregar(producto(7))
        val vm = HistorialViewModel(api, carrito)

        vm.volverAPedir(0)

        assertEquals(PRINCIPAL, carrito.cafeteria.value)
        assertEquals(1, carrito.unidades())
        assertEquals("Tu carrito es de Cafetín principal. Termina o vacía ese pedido primero", vm.estado.value.aviso)
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

val PRINCIPAL = Cafeteria(1, "Cafetín principal")
val NORTE = Cafeteria(2, "Cafetín Norte", "Pabellón B")

/** API de pedidos en memoria para los ViewModels. */
class PedidosFalsos : Pedidos {
    var cafeterias: List<Cafeteria> = listOf(PRINCIPAL)
    val catalogosPedidos = mutableListOf<Int>()
    var cafeteriaDelPedido: Cafeteria = PRINCIPAL
    var catalogo: List<ProductoCatalogo> = emptyList()
    var falla: ErrorApi? = null
    var creado: CrearPedidoRequest? = null
    var metodoPagado: String? = null
    var paginas = 1
    val paginasPedidas = mutableListOf<Int>()
    var avisos: List<Notificacion> = emptyList()
    var items: List<pe.edu.ucv.cafetin.app.datos.LineaPedido> = emptyList()
    val leidas = mutableListOf<Int>()

    private val sinPagar = PedidoResponse(30, "PED-430", "PENDIENTE", total = 5.0)

    private fun fallarSiToca() = falla?.let { throw it }

    override suspend fun cafeterias() = cafeterias.also { fallarSiToca() }

    override suspend fun catalogo(idCafeteria: Int): List<ProductoCatalogo> {
        catalogosPedidos += idCafeteria
        fallarSiToca()
        return catalogo
    }

    override suspend fun crear(solicitud: CrearPedidoRequest): PedidoResponse {
        fallarSiToca()
        creado = solicitud
        return sinPagar
    }

    /** Con Mercado Pago `pagar` deja el pago PENDIENTE con `urlPago`; null = pasarela simulada. */
    var urlCheckout: String? = null
    /** Lo que responde `verificarPago` (lo que Mercado Pago dice del cobro). */
    var verificado: PedidoResponse? = null
    var verificaciones = 0
    /** Respuesta de `pedido(id)`; por defecto el pedido sin pagar. */
    var leido: PedidoResponse? = null

    override suspend fun pagar(idPedido: Int, metodoPago: String): PedidoResponse {
        fallarSiToca()
        metodoPagado = metodoPago
        urlCheckout?.let { return sinPagar.copy(pago = Pago(5.0, metodoPago, "PENDIENTE", urlPago = it)) }
        return sinPagar.copy(pago = Pago(5.0, metodoPago, "PAGADO"), recibo = Recibo("DR-0430-1234"))
    }

    override suspend fun verificarPago(idPedido: Int): PedidoResponse {
        verificaciones++
        fallarSiToca()
        return verificado ?: sinPagar.copy(pago = Pago(5.0, "TARJETA", "PENDIENTE", urlPago = urlCheckout))
    }

    override suspend fun cancelar(idPedido: Int) = sinPagar.copy(estado = "CANCELADO").also { fallarSiToca() }

    override suspend fun pedido(idPedido: Int) = leido ?: sinPagar.copy(items = items, cafeteria = cafeteriaDelPedido)

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
