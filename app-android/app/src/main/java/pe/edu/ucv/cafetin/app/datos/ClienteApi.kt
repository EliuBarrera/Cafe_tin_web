package pe.edu.ucv.cafetin.app.datos

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error de la API. Decide por [codigo] (CodigoError del contrato), nunca por el texto. */
class ErrorApi(val estado: Int, val codigo: String, mensaje: String, val detalles: List<CampoError> = emptyList()) :
    Exception(mensaje)

object ClienteApi {

    val json = Json {
        ignoreUnknownKeys = true // la API puede sumar campos sin romper la app
        explicitNulls = false // un opcional nulo no viaja: la API lo guarda nulo
    }

    /**
     * Añade el JWT guardado a cada petición, salvo a las rutas de /api/auth: ahí la API lo ignora y un token
     * vencido no debe estorbar al volver a entrar.
     *
     * Si la API responde 401 a una petición que llevó el JWT vigente (token vencido o cuenta desactivada),
     * llama a [alRechazarJwt] para que la app cierre la sesión, sea cual sea la pantalla que hizo la llamada.
     */
    fun interceptorJwt(token: () -> String?, alRechazarJwt: () -> Unit = {}) = Interceptor { cadena ->
        val peticion = cadena.request()
        val actual = token()
        if (actual == null || peticion.url.encodedPath.startsWith("/api/auth/")) {
            cadena.proceed(peticion)
        } else {
            val respuesta = cadena.proceed(peticion.newBuilder().header("Authorization", "Bearer $actual").build())
            // Se compara con el token de ahora: si mientras tanto se inició otra sesión, no se cierra.
            if (respuesta.code == 401 && token() == actual) alRechazarJwt()
            respuesta
        }
    }

    fun crear(urlBase: String, token: () -> String?, alRechazarJwt: () -> Unit = {}): ApiCafetin {
        // Render gratis puede tardar en despertar: lectura generosa, conexión corta.
        val http = OkHttpClient.Builder()
            .addInterceptor(interceptorJwt(token, alRechazarJwt))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(if (urlBase.endsWith("/")) urlBase else "$urlBase/")
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiCafetin::class.java)
    }

    /**
     * Como [llamar], para endpoints sin cuerpo (`Response<Unit>`): Retrofit no lanza excepción ante un 4xx/5xx
     * cuando la función devuelve `Response`, así que aquí se convierte en [ErrorApi] igual que el resto.
     */
    suspend fun llamarSinCuerpo(bloque: suspend () -> Response<Unit>) {
        llamar {
            val respuesta = bloque()
            if (!respuesta.isSuccessful) throw HttpException(respuesta)
        }
    }

    /** Ejecuta una llamada y convierte cualquier fallo en [ErrorApi]. La cancelación de corrutinas se deja pasar. */
    suspend fun <T> llamar(bloque: suspend () -> T): T = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ErrorApi) {
        throw e
    } catch (e: HttpException) {
        val cuerpo = e.response()?.errorBody()?.string()
        val error = cuerpo?.let { runCatching { json.decodeFromString<ErrorRespuesta>(it) }.getOrNull() }
        throw ErrorApi(
            e.code(),
            error?.codigo ?: "ERROR_INTERNO",
            error?.mensaje ?: "Ocurrió un error inesperado. Intenta de nuevo.",
            error?.detalles.orEmpty(),
        )
    } catch (e: IOException) {
        throw ErrorApi(0, "SIN_CONEXION", "No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.")
    } catch (e: Exception) {
        // Respuesta que no se puede leer (p. ej. HTML de Render mientras despierta): error, no un cierre de la app.
        throw ErrorApi(0, "ERROR_INTERNO", "El servidor respondió algo inesperado. Intenta de nuevo.")
    }
}
