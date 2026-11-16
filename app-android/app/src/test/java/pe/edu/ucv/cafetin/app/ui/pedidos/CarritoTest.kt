package pe.edu.ucv.cafetin.app.ui.pedidos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.edu.ucv.cafetin.app.datos.ItemPedidoRequest
import java.time.LocalTime

class CarritoTest {

    private val carrito = Carrito()

    @Test
    fun agregarSumaUnidadesYCalculaElTotalSinErroresDeRedondeo() {
        carrito.agregar(producto(1, precio = 0.10, disponible = 10))
        carrito.agregar(producto(1, precio = 0.10, disponible = 10))
        carrito.agregar(producto(2, precio = 0.20, disponible = 10))

        assertEquals(3, carrito.unidades())
        assertEquals(40L, carrito.totalCentimos()) // 0.10 + 0.10 + 0.20, sin 0.4000000001
        assertEquals(listOf(ItemPedidoRequest(1, 2), ItemPedidoRequest(2, 1)), carrito.items())
    }

    @Test
    fun noPasaDelStockDisponible() {
        val p = producto(1, disponible = 2)
        assertTrue(carrito.agregar(p))
        assertTrue(carrito.agregar(p))
        assertFalse(carrito.agregar(p))
        assertEquals(2, carrito.cantidadDe(1))
    }

    @Test
    fun noPasaDeVeinteUnidadesPorProductoComoPideElContrato() {
        val p = producto(1, disponible = 100)
        repeat(25) { carrito.agregar(p) }
        assertEquals(Carrito.MAX_POR_PRODUCTO, carrito.cantidadDe(1))
    }

    @Test
    fun noAgregaUnProductoAgotado() {
        assertFalse(carrito.agregar(producto(1, disponible = 0, estado = "AGOTADO")))
        assertTrue(carrito.lineas.value.isEmpty())
    }

    @Test
    fun cambiarACeroQuitaLaLinea() {
        carrito.agregar(producto(1))
        carrito.cambiar(1, 0)
        assertTrue(carrito.lineas.value.isEmpty())
    }

    @Test
    fun alRecargarElCatalogoAjustaAlStockNuevoYQuitaLosAgotados() {
        carrito.agregar(producto(1, disponible = 5))
        repeat(4) { carrito.agregar(producto(1, disponible = 5)) }
        carrito.agregar(producto(2, disponible = 5))

        carrito.actualizar(listOf(producto(1, disponible = 3), producto(2, disponible = 0, estado = "AGOTADO")))

        assertEquals(3, carrito.cantidadDe(1))
        assertEquals(0, carrito.cantidadDe(2))
    }

    @Test
    fun lasHorasDeRecojoEmpiezanEnElSiguienteCuartoDeHoraConMargen() {
        val horas = horasDeRecojo(LocalTime.of(10, 2))
        assertEquals(LocalTime.of(10, 30), horas.first()) // 10:02 + 15 min → siguiente cuarto: 10:30
        assertEquals(8, horas.size)
        assertEquals(LocalTime.of(12, 15), horas.last())
    }

    @Test
    fun lasHorasDeRecojoNoCruzanLaMedianoche() {
        val horas = horasDeRecojo(LocalTime.of(23, 10))
        assertEquals(listOf(LocalTime.of(23, 30), LocalTime.of(23, 45)), horas)
        assertTrue(horasDeRecojo(LocalTime.of(23, 50)).isEmpty())
    }

    @Test
    fun formatosParaElEstudiante() {
        assertEquals("S/ 10.50", soles(10.5))
        assertEquals("S/ 0.40", solesDeCentimos(40))
        assertEquals("Sin pagar", etiquetaEstado("PENDIENTE", null))
        assertEquals("Pago rechazado", etiquetaEstado("PENDIENTE", "FALLIDO"))
        assertEquals("Pagado · por confirmar", etiquetaEstado("PENDIENTE", "PAGADO"))
        assertEquals("Listo para recoger", etiquetaEstado("LISTO", "PAGADO"))
        assertEquals("07/11 19:51", fechaCorta("2026-11-07T19:51:20.772032"))
        assertEquals("otra cosa", fechaCorta("otra cosa"))
    }
}

fun producto(id: Int, precio: Double = 2.5, disponible: Int = 10, estado: String = "DISPONIBLE") =
    pe.edu.ucv.cafetin.app.datos.ProductoCatalogo(id, "Producto $id", null, precio, "Bebidas", disponible, estado)
