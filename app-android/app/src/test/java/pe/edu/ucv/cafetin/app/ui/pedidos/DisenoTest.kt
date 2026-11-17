package pe.edu.ucv.cafetin.app.ui.pedidos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.edu.ucv.cafetin.app.datos.LineaPedido
import java.time.LocalTime

/** Reglas de las mejoras de diseño: repetir pedido, sugerencia, saludo y seguimiento. */
class DisenoTest {

    @Test
    fun repetirUsaElPrecioDeHoyYSaltaLoQueYaNoEsta() {
        val carrito = Carrito()
        val hoy = listOf(producto(1, precio = 3.0, disponible = 10), producto(2, disponible = 0, estado = "AGOTADO"))

        val agregadas = carrito.repetir(
            listOf(LineaPedido(1, "Café", 2, 2.5, 5.0), LineaPedido(2, "Jugo", 1, 4.5, 4.5), LineaPedido(9, "De baja", 1, 1.0, 1.0)),
            hoy,
        )

        assertEquals(2, agregadas)
        assertEquals(600L, carrito.totalCentimos()) // 2 × S/ 3.00 de hoy, no S/ 2.50 del pedido viejo
    }

    @Test
    fun repetirRespetaElStockDisponible() {
        val carrito = Carrito()
        assertEquals(1, carrito.repetir(listOf(LineaPedido(1, "Café", 3, 2.5, 7.5)), listOf(producto(1, disponible = 1))))
    }

    @Test
    fun sugiereLaBebidaMasEconomicaQueFalta() {
        val tamal = producto(1).copy(categoria = "Desayunos")
        val catalogo = listOf(
            tamal,
            producto(2, precio = 4.5).copy(categoria = "Bebidas"),
            producto(3, precio = 2.0).copy(categoria = "Bebidas"),
            producto(4, precio = 1.0).copy(categoria = "Snacks"),
            producto(5, precio = 0.5, disponible = 0, estado = "AGOTADO").copy(categoria = "Bebidas"),
        )
        assertEquals(3, sugerencia(listOf(LineaCarrito(tamal, 1)), catalogo)?.idProducto)
    }

    @Test
    fun sinBebidasDisponiblesSugiereOtraCategoriaYConCarritoVacioNada() {
        val cafe = producto(1).copy(categoria = "Bebidas")
        val catalogo = listOf(cafe, producto(4, precio = 1.0).copy(categoria = "Snacks"))
        assertEquals(4, sugerencia(listOf(LineaCarrito(cafe, 1)), catalogo)?.idProducto)
        assertNull(sugerencia(emptyList(), catalogo))
    }

    @Test
    fun saludoSegunLaHora() {
        assertEquals("Buenos días", saludo(LocalTime.of(7, 30)))
        assertEquals("Buenas tardes", saludo(LocalTime.of(13, 0)))
        assertEquals("Buenas noches", saludo(LocalTime.of(21, 0)))
    }

    @Test
    fun pasoDelSeguimiento() {
        assertEquals(-1, pasoActual("PENDIENTE", null))
        assertEquals(0, pasoActual("PENDIENTE", "PAGADO"))
        assertEquals(3, pasoActual("LISTO", "PAGADO"))
        assertEquals(-1, pasoActual("CANCELADO", null))
    }
}
