package pe.edu.ucv.cafetin.app.ui.cuentas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mismas reglas que la API (CuentasDto): si cambian allá, este test recuerda cambiarlas aquí. */
class ValidacionesTest {

    @Test
    fun correo() {
        assertNull(Validaciones.correo("ana@ucvvirtual.edu.pe"))
        assertNull(Validaciones.correo("  ana@ucv.pe  "))
        assertEquals("Escribe tu correo", Validaciones.correo(" "))
        assertEquals("Escribe un correo válido", Validaciones.correo("ana@ucv")) // la base exige un punto en el dominio
        assertEquals("Escribe un correo válido", Validaciones.correo("ana ucv.pe"))
    }

    @Test
    fun contrasenaCuentaBytesNoCaracteres() {
        assertNull(Validaciones.contrasena("Cafetin2026"))
        assertEquals("Debe tener al menos 8 caracteres", Validaciones.contrasena("corta"))
        assertNull(Validaciones.contrasena("a".repeat(72)))
        assertEquals("Es demasiado larga", Validaciones.contrasena("ñ".repeat(40))) // 40 caracteres, 80 bytes
    }

    @Test
    fun telefonoOpcionalDeNueveDigitos() {
        assertNull(Validaciones.telefono(""))
        assertNull(Validaciones.telefono("944123456"))
        assertEquals("Debe tener 9 dígitos", Validaciones.telefono("94412"))
    }

    @Test
    fun nombreCicloYObligatorios() {
        assertEquals("Escribe tu nombre", Validaciones.nombre(" A "))
        assertNull(Validaciones.nombre("Ana"))
        assertNull(Validaciones.ciclo("6"))
        assertEquals("Entre 1 y 12", Validaciones.ciclo("13"))
        assertEquals("Entre 1 y 12", Validaciones.ciclo(""))
        assertEquals("Máximo 15 caracteres", Validaciones.obligatorio("1".repeat(16), 15, "falta"))
        assertEquals("falta", Validaciones.obligatorio("", 15, "falta"))
    }
}
