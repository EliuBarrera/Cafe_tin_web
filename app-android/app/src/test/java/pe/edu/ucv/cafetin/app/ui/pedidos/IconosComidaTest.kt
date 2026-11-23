package pe.edu.ucv.cafetin.app.ui.pedidos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconosComidaTest {

    @Test
    fun productosDelSeedTienenIcono() {
        assertEquals(Comida.PLATO, Comida.de("Menú del día", "Menú"))
        assertEquals(Comida.PAN, Comida.de("Tamal criollo", "Desayunos"))
        assertEquals(Comida.SANDWICH, Comida.de("Pan con chicharrón", "Desayunos"))
        assertEquals(Comida.PAN, Comida.de("Empanada de ají", "Desayunos"))
        assertEquals(Comida.BEBIDA, Comida.de("Chicha morada 400 ml", "Bebidas"))
        assertEquals(Comida.BEBIDA, Comida.de("Jugo de piña 500 ml", "Bebidas"))
        assertEquals(Comida.CAFE, Comida.de("Café pasado", "Bebidas"))
        assertEquals(Comida.GALLETA, Comida.de("Galletas de avena", "Snacks"))
        assertEquals(Comida.SNACK, Comida.de("Papas fritas 40 g", "Snacks"))
    }

    @Test
    fun coincidePorPalabraCompleta() {
        assertEquals(Comida.SNACK, Comida.de("Tequeños", null)) // no es "te"
        assertEquals(Comida.TORTA, Comida.de("Arroz con leche", null)) // antes que "arroz"
        assertEquals(Comida.CAFE, Comida.de("CHOCOLATE CALIENTE", null))
    }

    @Test
    fun usaLaCategoriaSiElNombreNoDiceNada() {
        assertEquals(Comida.BEBIDA, Comida.de("Sorpresa de la casa", "Bebidas"))
        assertNull(Comida.de("Sorpresa de la casa", "Varios"))
        assertNull(Comida.de("Sorpresa de la casa", null))
    }
}
