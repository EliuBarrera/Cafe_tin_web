package pe.edu.ucv.cafetin.app.datos

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Cliente HTTP y repositorio contra una API falsa (MockWebServer) con las respuestas del contrato. */
class CuentasRepositorioTest {

    private val servidor = MockWebServer()
    private val almacen = AlmacenEnMemoria()
    private lateinit var cuentas: CuentasRepositorio

    @Before
    fun preparar() {
        servidor.start()
        cuentas = CuentasRepositorio(ClienteApi.crear(servidor.url("/").toString(), almacen::tokenActual), almacen)
    }

    @After
    fun cerrar() = servidor.close()

    @Test
    fun loginEnviaLasCredencialesDelContratoYGuardaLaSesion() = runTest {
        servidor.enqueue(json(200, tokenDe("ESTUDIANTE")))

        val sesion = cuentas.login(" ana@ucv.pe ", "Cafetin2026")

        val peticion = servidor.takeRequest()
        assertEquals("/api/auth/login", peticion.url.encodedPath)
        assertEquals("""{"correo":"ana@ucv.pe","contrasena":"Cafetin2026"}""", peticion.body?.utf8())
        assertEquals(null, peticion.headers["Authorization"])
        assertEquals("Ana", sesion.nombre)
        assertEquals("jwt-de-ana", almacen.tokenActual())
    }

    @Test
    fun conSesionElInterceptorEnviaElJwt() = runTest {
        almacen.guardar(Sesion("jwt-de-ana", 4, "Ana", "ESTUDIANTE"))
        servidor.enqueue(json(200, """{"id":4,"nombre":"Ana","apellidos":"Torres","correo":"ana@ucv.pe","rol":"ESTUDIANTE"}"""))

        val sesion = cuentas.sesionVigente()

        assertEquals("Bearer jwt-de-ana", servidor.takeRequest().headers["Authorization"])
        assertEquals("Ana", sesion?.nombre)
    }

    @Test
    fun unTrabajadorNoEntraALaAppDelEstudiante() = runTest {
        servidor.enqueue(json(200, tokenDe("TRABAJADOR")))

        try {
            cuentas.login("carmen@cafetin.pe", "Cafetin2026")
            fail("Debía rechazar al trabajador")
        } catch (e: ErrorApi) {
            assertEquals("ACCESO_DENEGADO", e.codigo)
        }
        assertNull(almacen.tokenActual())
    }

    @Test
    fun elErrorDeLaApiConservaCodigoYMensaje() = runTest {
        servidor.enqueue(json(401, """{"codigo":"NO_AUTENTICADO","mensaje":"Correo o contraseña incorrectos","detalles":[]}"""))

        try {
            cuentas.login("ana@ucv.pe", "otra-clave")
            fail("Debía fallar")
        } catch (e: ErrorApi) {
            assertEquals(401, e.estado)
            assertEquals("NO_AUTENTICADO", e.codigo)
            assertEquals("Correo o contraseña incorrectos", e.message)
        }
    }

    @Test
    fun registroNoEnviaElTelefonoVacio() = runTest {
        servidor.enqueue(json(201, tokenDe("ESTUDIANTE")))

        cuentas.registrar(RegistroEstudianteRequest("Ana", "Torres", "ana@ucv.pe", "Cafetin2026", null, "2021045678", "Sistemas", 6))

        val cuerpo = servidor.takeRequest().body?.utf8().orEmpty()
        assertFalse(cuerpo.contains("telefono"))
        assertTrue(cuerpo.contains("\"ciclo\":6"))
    }

    @Test
    fun unTokenRechazadoBorraLaSesionGuardada() = runTest {
        almacen.guardar(Sesion("jwt-vencido", 4, "Ana", "ESTUDIANTE"))
        servidor.enqueue(json(401, """{"codigo":"NO_AUTENTICADO","mensaje":"Inicia sesión","detalles":[]}"""))

        assertNull(cuentas.sesionVigente())
        assertNull(almacen.tokenActual())
    }

    @Test
    fun sinConexionConservaLaSesionYDaUnMensajeClaro() = runTest {
        almacen.guardar(Sesion("jwt-de-ana", 4, "Ana", "ESTUDIANTE"))
        servidor.close() // el servidor deja de responder

        assertEquals("jwt-de-ana", cuentas.sesionVigente()?.token)
        try {
            cuentas.recuperar("ana@ucv.pe")
            fail("Debía fallar sin conexión")
        } catch (e: ErrorApi) {
            assertEquals("SIN_CONEXION", e.codigo)
        }
    }

    @Test
    fun recuperarAceptaLaRespuesta202SinCuerpo() = runTest {
        servidor.enqueue(MockResponse.Builder().code(202).build())

        cuentas.recuperar("ana@ucv.pe")

        assertEquals("""{"correo":"ana@ucv.pe"}""", servidor.takeRequest().body?.utf8())
    }

    private fun tokenDe(rol: String) =
        """{"token":"jwt-de-ana","expiraEn":28800,"usuario":{"id":4,"nombre":"Ana","apellidos":"Torres","correo":"ana@ucv.pe","rol":"$rol"}}"""

    private fun json(codigo: Int, cuerpo: String) =
        MockResponse.Builder().code(codigo).addHeader("Content-Type", "application/json").body(cuerpo).build()
}

/** Almacén sin DataStore para los tests. */
class AlmacenEnMemoria : AlmacenSesion {
    private var sesion: Sesion? = null
    override fun tokenActual() = sesion?.token
    override suspend fun cargar() = sesion
    override suspend fun guardar(sesion: Sesion) { this.sesion = sesion }
    override suspend fun borrar() { sesion = null }
}
