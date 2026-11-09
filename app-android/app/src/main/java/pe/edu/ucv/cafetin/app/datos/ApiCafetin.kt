package pe.edu.ucv.cafetin.app.datos

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

/** Endpoints de Cuentas según docs/openapi.yaml. No agregues rutas que no estén en el contrato. */
interface ApiCafetin {

    @POST("api/auth/login")
    suspend fun login(@Body solicitud: LoginRequest): TokenResponse

    @POST("api/auth/registro")
    suspend fun registrar(@Body solicitud: RegistroEstudianteRequest): TokenResponse

    /** 202 exista o no la cuenta: la API no revela qué correos están registrados (RF-03). */
    @POST("api/auth/recuperar")
    suspend fun recuperar(@Body solicitud: RecuperarRequest): Response<Unit>

    @GET("api/usuarios/me")
    suspend fun perfil(): UsuarioResponse

    @PUT("api/usuarios/me/fcm-token")
    suspend fun registrarFcmToken(@Body solicitud: FcmTokenRequest): Response<Unit>
}
