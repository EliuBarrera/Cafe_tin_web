package pe.edu.ucv.cafetin.app.datos

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow

/** Operaciones de cuenta que usan las pantallas. Interfaz para probar los ViewModels sin red. */
interface Cuentas {
    suspend fun login(correo: String, contrasena: String): Sesion
    suspend fun registrar(datos: RegistroEstudianteRequest): Sesion
    suspend fun recuperar(correo: String)
    /** Sesión guardada y todavía válida, o null si no hay o la API la rechaza (401). */
    suspend fun sesionVigente(): Sesion?
    suspend fun cerrarSesion()
    /** Emite cuando la API rechaza el JWT a mitad de uso (401): la app vuelve al login. */
    val sesionRechazada: Flow<Unit> get() = emptyFlow()
}

/** RF-01 y RF-03 para el estudiante. Esta app es solo para estudiantes: otros roles usan el panel. */
class CuentasRepositorio(private val api: ApiCafetin, private val almacen: AlmacenSesion) : Cuentas {

    private val rechazos = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sesionRechazada: SharedFlow<Unit> = rechazos

    /** Lo llama el interceptor de [ClienteApi] (desde un hilo de OkHttp) ante un 401 con JWT. */
    fun jwtRechazado() {
        rechazos.tryEmit(Unit)
    }

    override suspend fun login(correo: String, contrasena: String): Sesion {
        val respuesta = ClienteApi.llamar { api.login(LoginRequest(correo.trim(), contrasena)) }
        return iniciar(respuesta)
    }

    override suspend fun registrar(datos: RegistroEstudianteRequest): Sesion =
        iniciar(ClienteApi.llamar { api.registrar(datos) })

    override suspend fun recuperar(correo: String) {
        ClienteApi.llamarSinCuerpo { api.recuperar(RecuperarRequest(correo.trim())) }
    }

    override suspend fun sesionVigente(): Sesion? {
        val guardada = almacen.cargar() ?: return null
        return try {
            val usuario = ClienteApi.llamar { api.perfil() }
            guardada.copy(nombre = usuario.nombre, rol = usuario.rol)
        } catch (e: ErrorApi) {
            when (e.estado) {
                401 -> null.also { almacen.borrar() } // token vencido o cuenta desactivada
                else -> guardada // sin conexión: se conserva y se reintenta luego
            }
        }
    }

    override suspend fun cerrarSesion() = almacen.borrar()

    /** RF-08: lo llamará la integración con Firebase cuando el proyecto exista. */
    suspend fun registrarTokenPush(token: String) {
        ClienteApi.llamarSinCuerpo { api.registrarFcmToken(FcmTokenRequest(token)) }
    }

    private suspend fun iniciar(respuesta: TokenResponse): Sesion {
        if (respuesta.usuario.rol != ROL_ESTUDIANTE) {
            throw ErrorApi(403, "ACCESO_DENEGADO", "Esta app es para estudiantes. Trabajadores y dueño usan el panel web.")
        }
        val sesion = Sesion(respuesta.token, respuesta.usuario.id, respuesta.usuario.nombre, respuesta.usuario.rol)
        almacen.guardar(sesion)
        return sesion
    }

    companion object {
        const val ROL_ESTUDIANTE = "ESTUDIANTE"
    }
}
