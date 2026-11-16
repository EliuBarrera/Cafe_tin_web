package pe.edu.ucv.cafetin.app.datos

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints del estudiante (Cuentas y Pedidos) según docs/openapi.yaml. No agregues rutas que no estén en el contrato. */
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

    // ---------------------------------------------------------------- Pedidos (RF-04 a RF-09)

    @GET("api/catalogo")
    suspend fun catalogo(): List<ProductoCatalogo>

    @POST("api/pedidos")
    suspend fun crearPedido(@Body solicitud: CrearPedidoRequest): PedidoResponse

    /** 402 PAGO_RECHAZADO se puede reintentar; 409 STOCK_INSUFICIENTE si otro compró las últimas unidades. */
    @POST("api/pedidos/{id}/pago")
    suspend fun pagar(@Path("id") id: Int, @Body solicitud: PagarRequest): PedidoResponse

    /** Solo un pedido PENDIENTE sin pagar; si no, 409 TRANSICION_INVALIDA. */
    @POST("api/pedidos/{id}/cancelar")
    suspend fun cancelar(@Path("id") id: Int): PedidoResponse

    @GET("api/pedidos/{id}")
    suspend fun pedido(@Path("id") id: Int): PedidoResponse

    @GET("api/pedidos/mios")
    suspend fun historial(@Query("pagina") pagina: Int, @Query("tamano") tamano: Int): PaginaPedidos

    @GET("api/usuarios/me/notificaciones")
    suspend fun notificaciones(): List<Notificacion>

    @PATCH("api/usuarios/me/notificaciones/{id}/leida")
    suspend fun marcarLeida(@Path("id") id: Int): Response<Unit>
}
