package pe.edu.ucv.cafetin.app.ui.cuentas

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
import pe.edu.ucv.cafetin.app.datos.CampoError
import pe.edu.ucv.cafetin.app.datos.Cuentas
import pe.edu.ucv.cafetin.app.datos.ErrorApi
import pe.edu.ucv.cafetin.app.datos.RegistroEstudianteRequest
import pe.edu.ucv.cafetin.app.datos.Sesion

@OptIn(ExperimentalCoroutinesApi::class)
class CuentasViewModelsTest {

    private val cuentas = CuentasFalsas()

    @Before
    fun preparar() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun limpiar() = Dispatchers.resetMain()

    @Test
    fun loginValidaAntesDeLlamarALaApi() {
        val vm = LoginViewModel(cuentas)

        vm.cambiarCorreo("ana")
        vm.entrar()

        assertEquals("Escribe un correo válido", vm.estado.value.errorCorreo)
        assertEquals("Escribe tu contraseña", vm.estado.value.errorContrasena)
        assertEquals(0, cuentas.llamadas)
    }

    @Test
    fun loginCorrectoDejaLaSesion() {
        val vm = LoginViewModel(cuentas)

        vm.cambiarCorreo("ana@ucv.pe")
        vm.cambiarContrasena("Cafetin2026")
        vm.entrar()

        assertEquals("Ana", vm.estado.value.sesion?.nombre)
        assertFalse(vm.estado.value.cargando)
    }

    @Test
    fun contrasenaIncorrectaMuestraUnMensajeClaro() {
        cuentas.falla = ErrorApi(401, "NO_AUTENTICADO", "Credenciales inválidas")
        val vm = LoginViewModel(cuentas)

        vm.cambiarCorreo("ana@ucv.pe")
        vm.cambiarContrasena("otra-clave")
        vm.entrar()

        assertEquals("Correo o contraseña incorrectos", vm.estado.value.error)
        assertNull(vm.estado.value.sesion)
    }

    @Test
    fun registroMarcaTodosLosCamposInvalidosALaVez() {
        val vm = RegistroViewModel(cuentas)

        vm.cambiar("telefono", "944-12")
        vm.registrar()

        val errores = vm.estado.value.errores
        assertEquals(setOf("nombre", "apellidos", "correo", "contrasena", "telefono", "codigo", "carrera", "ciclo"), errores.keys)
        assertEquals(0, cuentas.llamadas)
    }

    @Test
    fun registroEnviaLosDatosLimpiosYSinTelefonoVacio() {
        val vm = RegistroViewModel(cuentas)
        llenarRegistro(vm)

        vm.registrar()

        val enviado = cuentas.registrado!!
        assertEquals("Ana", enviado.nombre)
        assertNull(enviado.telefono)
        assertEquals(6, enviado.ciclo)
        assertTrue(vm.estado.value.sesion != null)
    }

    @Test
    fun unCorreoRepetidoMuestraElMensajeDeLaApi() {
        cuentas.falla = ErrorApi(409, "CONFLICTO", "Ya existe una cuenta con ese correo")
        val vm = RegistroViewModel(cuentas)
        llenarRegistro(vm)

        vm.registrar()

        assertEquals("Ya existe una cuenta con ese correo", vm.estado.value.error)
    }

    @Test
    fun losErroresPorCampoDeLaApiSeMuestranJuntoAlCampo() {
        cuentas.falla = ErrorApi(400, "VALIDACION", "Datos inválidos", listOf(CampoError("codigo", "ya registrado")))
        val vm = RegistroViewModel(cuentas)
        llenarRegistro(vm)

        vm.registrar()

        assertEquals("ya registrado", vm.estado.value.errores["codigo"])
    }

    @Test
    fun recuperarConfirmaElEnvio() {
        val vm = RecuperarViewModel(cuentas)

        vm.cambiarCorreo("ana@ucv.pe")
        vm.enviar()

        assertTrue(vm.estado.value.enviado)
        assertEquals("ana@ucv.pe", cuentas.recuperado)
    }

    private fun llenarRegistro(vm: RegistroViewModel) {
        mapOf(
            "nombre" to " Ana ", "apellidos" to "Torres Vega", "correo" to "ana@ucv.pe", "contrasena" to "Cafetin2026",
            "codigo" to "2021045678", "carrera" to "Ingeniería de Sistemas", "ciclo" to "6",
        ).forEach { (campo, valor) -> vm.cambiar(campo, valor) }
    }
}

private class CuentasFalsas : Cuentas {
    var falla: ErrorApi? = null
    var llamadas = 0
    var registrado: RegistroEstudianteRequest? = null
    var recuperado: String? = null
    private val ana = Sesion("jwt", 4, "Ana", "ESTUDIANTE")

    override suspend fun login(correo: String, contrasena: String): Sesion {
        llamadas++
        falla?.let { throw it }
        return ana
    }

    override suspend fun registrar(datos: RegistroEstudianteRequest): Sesion {
        llamadas++
        falla?.let { throw it }
        registrado = datos
        return ana
    }

    override suspend fun recuperar(correo: String) {
        llamadas++
        falla?.let { throw it }
        recuperado = correo
    }

    override suspend fun sesionVigente() = null
    override suspend fun cerrarSesion() = Unit
}
