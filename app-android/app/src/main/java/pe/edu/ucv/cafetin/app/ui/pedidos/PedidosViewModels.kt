package pe.edu.ucv.cafetin.app.ui.pedidos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.ucv.cafetin.app.datos.CrearPedidoRequest
import pe.edu.ucv.cafetin.app.datos.ErrorApi
import pe.edu.ucv.cafetin.app.datos.Notificacion
import pe.edu.ucv.cafetin.app.datos.PedidoResponse
import pe.edu.ucv.cafetin.app.datos.PedidoResumen
import pe.edu.ucv.cafetin.app.datos.Pedidos
import pe.edu.ucv.cafetin.app.datos.ProductoCatalogo
import java.time.LocalTime
import java.util.Locale

// ------------------------------------------------------------------ Catálogo (RF-04)

data class CatalogoEstado(
    val cargando: Boolean = true,
    val error: String? = null,
    val productos: List<ProductoCatalogo> = emptyList(),
    val categorias: List<String> = emptyList(),
    /** Categoría elegida; null = todas. */
    val categoria: String? = null,
    val aviso: String? = null,
) {
    val visibles: List<ProductoCatalogo>
        get() = if (categoria == null) productos else productos.filter { it.categoria == categoria }
}

class CatalogoViewModel(private val pedidos: Pedidos, val carrito: Carrito) : ViewModel() {

    private val _estado = MutableStateFlow(CatalogoEstado())
    val estado: StateFlow<CatalogoEstado> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val productos = pedidos.catalogo()
                carrito.actualizar(productos)
                val categorias = productos.mapNotNull { it.categoria }.distinct().sorted()
                _estado.update {
                    it.copy(
                        cargando = false,
                        productos = productos,
                        categorias = categorias,
                        categoria = it.categoria?.takeIf { c -> c in categorias },
                    )
                }
            } catch (e: ErrorApi) {
                _estado.update { it.copy(cargando = false, error = e.message) }
            }
        }
    }

    fun filtrar(categoria: String?) = _estado.update { it.copy(categoria = categoria) }

    fun agregar(producto: ProductoCatalogo) {
        val aviso = if (carrito.agregar(producto)) null else "No hay más unidades de ${producto.nombre}"
        _estado.update { it.copy(aviso = aviso) }
    }

    fun avisoVisto() = _estado.update { it.copy(aviso = null) }
}

// ------------------------------------------------------------------ Carrito (RF-05)

data class CarritoEstado(
    val horas: List<LocalTime> = emptyList(),
    val hora: LocalTime? = null,
    val observacion: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    /** Pedido recién creado: la pantalla navega a su pago. */
    val pedidoCreado: Int? = null,
)

class CarritoViewModel(
    private val pedidos: Pedidos,
    val carrito: Carrito,
    reloj: () -> LocalTime = { LocalTime.now() },
) : ViewModel() {

    private val _estado = MutableStateFlow(CarritoEstado(horas = horasDeRecojo(reloj())).let { it.copy(hora = it.horas.firstOrNull()) })
    val estado: StateFlow<CarritoEstado> = _estado.asStateFlow()

    fun elegirHora(hora: LocalTime) = _estado.update { it.copy(hora = hora, error = null) }

    fun cambiarObservacion(texto: String) = _estado.update { it.copy(observacion = texto.take(MAX_OBSERVACION)) }

    fun confirmar() {
        val e = _estado.value
        val error = when {
            carrito.lineas.value.isEmpty() -> "Tu carrito está vacío"
            e.hora == null -> "Elige a qué hora pasas a recoger"
            else -> null
        }
        if (error != null) {
            _estado.update { it.copy(error = error) }
            return
        }
        val solicitud = CrearPedidoRequest(
            horaRecojo = String.format(Locale.ROOT, "%02d:%02d", e.hora!!.hour, e.hora.minute),
            items = carrito.items(),
            observacion = e.observacion.trim().ifEmpty { null },
        )
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val pedido = pedidos.crear(solicitud)
                carrito.vaciar()
                _estado.update { it.copy(cargando = false, pedidoCreado = pedido.id, observacion = "") }
            } catch (ex: ErrorApi) {
                // STOCK_INSUFICIENTE trae el producto y las unidades que quedan en el mensaje.
                _estado.update { it.copy(cargando = false, error = ex.message) }
            }
        }
    }

    fun navegado() = _estado.update { it.copy(pedidoCreado = null) }

    companion object {
        const val MAX_OBSERVACION = 250
    }
}

// ------------------------------------------------------------------ Pago (RF-06, RF-07)

data class PagoEstado(
    val cargando: Boolean = true,
    val pedido: PedidoResponse? = null,
    val metodo: String = "YAPE",
    val pagando: Boolean = false,
    val error: String? = null,
    /** El pago quedó PAGADO: la pantalla muestra el recibo. */
    val pagado: Boolean = false,
)

class PagoViewModel(private val pedidos: Pedidos, private val idPedido: Int) : ViewModel() {

    private val _estado = MutableStateFlow(PagoEstado())
    val estado: StateFlow<PagoEstado> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val pedido = pedidos.pedido(idPedido)
                _estado.update { it.copy(cargando = false, pedido = pedido, pagado = pedido.pagado) }
            } catch (e: ErrorApi) {
                _estado.update { it.copy(cargando = false, error = e.message) }
            }
        }
    }

    fun elegirMetodo(metodo: String) = _estado.update { it.copy(metodo = metodo, error = null) }

    fun pagar() {
        if (_estado.value.pagando) return
        _estado.update { it.copy(pagando = true, error = null) }
        viewModelScope.launch {
            try {
                val pedido = pedidos.pagar(idPedido, _estado.value.metodo)
                _estado.update { it.copy(pagando = false, pedido = pedido, pagado = pedido.pagado) }
            } catch (e: ErrorApi) {
                val mensaje = when (e.codigo) {
                    "PAGO_RECHAZADO" -> "El pago fue rechazado. Tu pedido sigue guardado: intenta de nuevo o usa otro método."
                    "STOCK_INSUFICIENTE" -> "Otro estudiante compró las últimas unidades. Cancela este pedido y arma uno nuevo."
                    else -> e.message
                }
                _estado.update { it.copy(pagando = false, error = mensaje) }
                // Ya pagado (p. ej. en otro intento que sí llegó): se relee y se muestra el recibo.
                if (e.codigo == "REGLA_NEGOCIO") recargar()
            }
        }
    }

    private suspend fun recargar() {
        runCatching { pedidos.pedido(idPedido) }.onSuccess { p -> _estado.update { it.copy(pedido = p, pagado = p.pagado) } }
    }
}

// ------------------------------------------------------------------ Detalle y estado (RF-07, RF-08)

data class PedidoEstado(
    val cargando: Boolean = true,
    val pedido: PedidoResponse? = null,
    val error: String? = null,
    val cancelando: Boolean = false,
)

class PedidoViewModel(private val pedidos: Pedidos, private val idPedido: Int) : ViewModel() {

    private val _estado = MutableStateFlow(PedidoEstado())
    val estado: StateFlow<PedidoEstado> = _estado.asStateFlow()

    init {
        refrescar()
    }

    /** La pantalla lo llama cada pocos segundos mientras el pedido no esté en un estado final (RF-08). */
    fun refrescar() {
        viewModelScope.launch {
            try {
                val pedido = pedidos.pedido(idPedido)
                _estado.update { it.copy(cargando = false, pedido = pedido, error = null) }
            } catch (e: ErrorApi) {
                // Si ya se ve el pedido, un fallo de red al refrescar no lo tapa.
                _estado.update { it.copy(cargando = false, error = if (it.pedido == null || e.codigo != "SIN_CONEXION") e.message else it.error) }
            }
        }
    }

    fun cancelar() {
        _estado.update { it.copy(cancelando = true, error = null) }
        viewModelScope.launch {
            try {
                val pedido = pedidos.cancelar(idPedido)
                _estado.update { it.copy(cancelando = false, pedido = pedido) }
            } catch (e: ErrorApi) {
                val mensaje = if (e.codigo == "TRANSICION_INVALIDA") "Este pedido ya no se puede cancelar." else e.message
                _estado.update { it.copy(cancelando = false, error = mensaje) }
                // Se relee el estado real (p. ej. ya lo confirmaron) sin borrar el mensaje.
                runCatching { pedidos.pedido(idPedido) }.onSuccess { p -> _estado.update { it.copy(pedido = p) } }
            }
        }
    }
}

// ------------------------------------------------------------------ Historial (RF-09)

data class HistorialEstado(
    val cargando: Boolean = true,
    val pedidos: List<PedidoResumen> = emptyList(),
    val pagina: Int = 0,
    val hayMas: Boolean = false,
    val error: String? = null,
)

class HistorialViewModel(private val pedidos: Pedidos) : ViewModel() {

    private val _estado = MutableStateFlow(HistorialEstado())
    val estado: StateFlow<HistorialEstado> = _estado.asStateFlow()

    init {
        recargar()
    }

    fun recargar() = cargar(0)

    fun cargarMas() {
        val e = _estado.value
        if (!e.cargando && e.hayMas) cargar(e.pagina + 1)
    }

    private fun cargar(pagina: Int) {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val resultado = pedidos.historial(pagina)
                _estado.update {
                    it.copy(
                        cargando = false,
                        pedidos = if (pagina == 0) resultado.contenido else it.pedidos + resultado.contenido,
                        pagina = pagina,
                        hayMas = pagina + 1 < resultado.totalPaginas,
                    )
                }
            } catch (e: ErrorApi) {
                _estado.update { it.copy(cargando = false, error = e.message) }
            }
        }
    }
}

// ------------------------------------------------------------------ Avisos (RF-08)

data class AvisosEstado(
    val cargando: Boolean = true,
    val avisos: List<Notificacion> = emptyList(),
    val error: String? = null,
) {
    val noLeidos: Int get() = avisos.count { !it.leida }
}

class AvisosViewModel(private val pedidos: Pedidos) : ViewModel() {

    private val _estado = MutableStateFlow(AvisosEstado())
    val estado: StateFlow<AvisosEstado> = _estado.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            try {
                val avisos = pedidos.notificaciones()
                _estado.update { it.copy(cargando = false, avisos = avisos, error = null) }
            } catch (e: ErrorApi) {
                _estado.update { it.copy(cargando = false, error = if (it.avisos.isEmpty()) e.message else null) }
            }
        }
    }

    /** Se marca leído al abrirlo; si falla, no importa: se reintenta la próxima vez. */
    fun abrir(aviso: Notificacion) {
        if (aviso.leida) return
        _estado.update { e -> e.copy(avisos = e.avisos.map { if (it.id == aviso.id) it.copy(leida = true) else it }) }
        viewModelScope.launch { runCatching { pedidos.marcarLeida(aviso.id) } }
    }
}
