package pe.edu.ucv.cafetin.app.datos

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
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
     */
    fun interceptorJwt(token: () -> String?) = Interceptor { cadena ->
        val peticion = cadena.request()
        val actual = token()
        if (actual == null || peticion.url.encodedPath.startsWith("/api/auth/")) {
            cadena.proceed(peticion)
        } else {
            cadena.proceed(peticion.newBuilder().header("Authorization", "Bearer $actual").build())
        }
    }

    fun crear(urlBase: String, token: () -> String?): ApiCafetin {
        // Render gratis puede tardar en despertar: lectura generosa, conexión corta.
        val http = OkHttpClient.Builder()
            .addInterceptor(interceptorJwt(token))
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

    /** Ejecuta una llamada y convierte cualquier fallo en [ErrorApi]. */
    suspend fun <T> llamar(bloque: suspend () -> T): T = try {
        bloque()
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
    }
}
