package pe.edu.ucv.cafetin.app.datos

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Rutas, cuerpos y respuestas de Pedidos tal como los define docs/openapi.yaml (respuestas reales de la API). */
class PedidosRepositorioTest {

    private val servidor = MockWebServer()
    private lateinit var pedidos: PedidosRepositorio

    @Before
    fun preparar() {
        servidor.start()
        pedidos = PedidosRepositorio(ClienteApi.crear(servidor.url("/").toString(), { "jwt-de-ana" }))
    }

    @After
    fun cerrar() = servidor.close()

    @Test
    fun catalogoLeeLaDisponibilidad() = runTest {
        servidor.enqueue(json(200, """[{"idProducto":6,"nombre":"Jugo de piña 500 ml","descripcion":"Natural","precio":4.50,"categoria":"Bebidas","cantidadDisponible":3,"estadoStock":"POCAS UNIDADES"},
            {"idProducto":9,"nombre":"Galleta","precio":1.00,"categoria":"Snacks","cantidadDisponible":0,"estadoStock":"AGOTADO"}]"""))

        val catalogo = pedidos.catalogo()

        val peticion = servidor.takeRequest()
        assertEquals("/api/catalogo", peticion.url.encodedPath)
        assertEquals("Bearer jwt-de-ana", peticion.headers["Authorization"])
        assertEquals(4.5, catalogo[0].precio, 0.0)
        assertTrue(catalogo[1].agotado)
    }

    @Test
    fun crearPedidoEnviaElCarritoSinObservacionVacia() = runTest {
        servidor.enqueue(json(201, PEDIDO_SIN_PAGAR))

        val pedido = pedidos.crear(CrearPedidoRequest("12:50", listOf(ItemPedidoRequest(7, 2))))

        assertEquals("""{"horaRecojo":"12:50","items":[{"idProducto":7,"cantidad":2}]}""", servidor.takeRequest().body?.utf8())
        assertEquals("PED-430", pedido.codigoPedido)
        assertNull(pedido.pago)
    }

    @Test
    fun pagarDevuelveElRecibo() = runTest {
        servidor.enqueue(json(200, PEDIDO_PAGADO))

        val pedido = pedidos.pagar(30, "YAPE")

        val peticion = servidor.takeRequest()
        assertEquals("/api/pedidos/30/pago", peticion.url.encodedPath)
        assertEquals("""{"metodoPago":"YAPE"}""", peticion.body?.utf8())
        assertTrue(pedido.pagado)
        assertEquals("DR-430-0042", pedido.recibo?.codigoVerificacion)
    }

    @Test
    fun pagoRechazadoLlegaComo402ConSuCodigo() = runTest {
        servidor.enqueue(json(402, """{"codigo":"PAGO_RECHAZADO","mensaje":"Fondos insuficientes","detalles":[]}"""))
        try {
            pedidos.pagar(30, "TARJETA")
            fail("Debía fallar")
        } catch (e: ErrorApi) {
            assertEquals(402, e.estado)
            assertEquals("PAGO_RECHAZADO", e.codigo)
        }
    }

    @Test
    fun historialPideLaPaginaYElTamano() = runTest {
        servidor.enqueue(json(200, """{"contenido":[{"id":5,"codigoPedido":"PED-0424","estado":"ENTREGADO","estadoPago":"PAGADO","fechaHora":"2026-11-12T19:51:20.772032","horaRecojo":"16:30","total":9.00,"cantidadProductos":4}],"pagina":1,"tamano":20,"totalElementos":21,"totalPaginas":2}"""))

        val pagina = pedidos.historial(1)

        assertEquals("/api/pedidos/mios?pagina=1&tamano=20", servidor.takeRequest().url.encodedPathAndQuery())
        assertEquals(4, pagina.contenido.single().cantidadProductos)
        assertEquals(2, pagina.totalPaginas)
    }

    @Test
    fun marcarAvisoLeidoAceptaEl204() = runTest {
        servidor.enqueue(MockResponse.Builder().code(204).build())
        pedidos.marcarLeida(3)
        val peticion = servidor.takeRequest()
        assertEquals("PATCH", peticion.method)
        assertEquals("/api/usuarios/me/notificaciones/3/leida", peticion.url.encodedPath)
    }

    private fun okhttp3.HttpUrl.encodedPathAndQuery() = encodedPath + (encodedQuery?.let { "?$it" } ?: "")

    private fun json(codigo: Int, cuerpo: String) =
        MockResponse.Builder().code(codigo).addHeader("Content-Type", "application/json").body(cuerpo).build()

    private companion object {
        const val PEDIDO_SIN_PAGAR =
            """{"id":30,"codigoPedido":"PED-430","estado":"PENDIENTE","fechaHora":"2026-11-15T20:10:00.1","horaRecojo":"12:50","total":5.00,"observacion":null,"items":[{"idProducto":7,"nombre":"Café pasado","cantidad":2,"precioUnitario":2.50,"subtotal":5.00}],"pago":null,"recibo":null}"""
        const val PEDIDO_PAGADO =
            """{"id":30,"codigoPedido":"PED-430","estado":"PENDIENTE","total":5.00,"items":[],"pago":{"monto":5.00,"metodoPago":"YAPE","estado":"PAGADO","fecha":"2026-11-15T20:11:00","referencia":"YP-123456"},"recibo":{"codigoVerificacion":"DR-430-0042","fechaEmision":"2026-11-15T20:11:00","verificado":false}}"""
    }
}
