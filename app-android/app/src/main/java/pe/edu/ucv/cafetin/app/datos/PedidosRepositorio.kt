package pe.edu.ucv.cafetin.app.datos

/** Operaciones de pedido que usan las pantallas. Interfaz para probar los ViewModels sin red. */
interface Pedidos {
    suspend fun cafeterias(): List<Cafeteria>
    suspend fun catalogo(idCafeteria: Int): List<ProductoCatalogo>
    suspend fun crear(solicitud: CrearPedidoRequest): PedidoResponse
    suspend fun pagar(idPedido: Int, metodoPago: String): PedidoResponse
    suspend fun cancelar(idPedido: Int): PedidoResponse
    suspend fun pedido(idPedido: Int): PedidoResponse
    suspend fun historial(pagina: Int, tamano: Int = TAMANO_PAGINA): PaginaPedidos
    suspend fun notificaciones(): List<Notificacion>
    suspend fun marcarLeida(idNotificacion: Int)

    companion object {
        const val TAMANO_PAGINA = 20
    }
}

/** RF-04 a RF-09. Todo error llega como [ErrorApi]: las pantallas deciden por su `codigo`. */
class PedidosRepositorio(private val api: ApiCafetin) : Pedidos {

    override suspend fun cafeterias() = ClienteApi.llamar { api.cafeterias() }

    override suspend fun catalogo(idCafeteria: Int) = ClienteApi.llamar { api.catalogo(idCafeteria) }

    override suspend fun crear(solicitud: CrearPedidoRequest) = ClienteApi.llamar { api.crearPedido(solicitud) }

    override suspend fun pagar(idPedido: Int, metodoPago: String) =
        ClienteApi.llamar { api.pagar(idPedido, PagarRequest(metodoPago)) }

    override suspend fun cancelar(idPedido: Int) = ClienteApi.llamar { api.cancelar(idPedido) }

    override suspend fun pedido(idPedido: Int) = ClienteApi.llamar { api.pedido(idPedido) }

    override suspend fun historial(pagina: Int, tamano: Int) = ClienteApi.llamar { api.historial(pagina, tamano) }

    override suspend fun notificaciones() = ClienteApi.llamar { api.notificaciones() }

    override suspend fun marcarLeida(idNotificacion: Int) = ClienteApi.llamarSinCuerpo { api.marcarLeida(idNotificacion) }
}
