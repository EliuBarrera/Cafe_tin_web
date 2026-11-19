package pe.edu.ucv.cafetin.app.ui.pedidos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.ucv.cafetin.app.datos.Cafeteria
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
    /** Último pedido pagado, para repetirlo con un toque (reconocer, no recordar). */
    val ultimo: PedidoResumen? = null,
    val repitiendo: Boolean = false,
    /** Se agregó un pedido anterior al carrito: la pantalla va al carrito. */
    val irAlCarrito: Boolean = false,
    /** Cafetín cuyo catálogo se muestra; null mientras el estudiante no elige. */
    val cafeteria: Cafeteria? = null,
    /** La pantalla muestra la lista de cafetines en vez del catálogo. */
    val eligiendo: Boolean = false,
    val cafeterias: List<Cafeteria> = emptyList(),
    /** Cafetín elegido con el carrito lleno de otro: espera que el estudiante confirme vaciarlo. */
    val porConfirmar: Cafeteria? = null,
) {
    val visibles: List<ProductoCatalogo>
        get() = if (categoria == null) productos else productos.filter { it.categoria == categoria }
}

/**
 * Catálogo de un cafetín. Al abrir: el del carrito si ya hay uno; si no, el último elegido (DataStore); si no,
 * el único que haya; y si hay varios, el estudiante elige de la lista.
 */
class CatalogoViewModel(
    private val pedidos: Pedidos,
    val carrito: Carrito,
    private val preferida: suspend () -> Int? = { null },
    private val recordar: suspend (Int) -> Unit = {},
) : ViewModel() {

    private val _estado = MutableStateFlow(CatalogoEstado(cafeteria = carrito.cafeteria.value))
    val estado: StateFlow<CatalogoEstado> = _estado.asStateFlow()

    init {
        if (carrito.cafeteria.value != null) {
            cargar()
            cargarUltimo()
        } else {
            cargarCafeterias(automatica = true)
        }
        // "Volver a pedir" desde el historial puede cambiar el cafetín del carrito: el catálogo lo sigue.
        viewModelScope.launch {
            carrito.cafeteria.collect { c -> if (c != null && c.id != _estado.value.cafeteria?.id) aplicar(c) }
        }
    }

    /** Con [automatica], elige sola si hay una preferida o un único cafetín; si no, muestra la lista. */
    private fun cargarCafeterias(automatica: Boolean) {
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val lista = pedidos.cafeterias()
                val sola = if (!automatica) null
                else runCatching { preferida() }.getOrNull()?.let { id -> lista.firstOrNull { it.id == id } }
                    ?: lista.singleOrNull()
                _estado.update { it.copy(cafeterias = lista, cargando = false, eligiendo = sola == null) }
                if (sola != null) aplicar(sola)
                else if (lista.isEmpty()) _estado.update { it.copy(error = "Todavía no hay cafetines atendiendo") }
            } catch (e: ErrorApi) {
                _estado.update { it.copy(cargando = false, error = e.message, eligiendo = true) }
            }
        }
    }

    /** "Cambiar" en la cabecera. */
    fun cambiarCafeteria() {
        _estado.update { it.copy(eligiendo = true) }
        cargarCafeterias(automatica = false)
    }

    /** Volver al catálogo sin cambiar (solo si ya había uno). */
    fun cancelarEleccion() {
        if (_estado.value.cafeteria != null) _estado.update { it.copy(eligiendo = false, error = null) }
    }

    fun elegir(cafeteria: Cafeteria) {
        val actual = carrito.cafeteria.value
        if (actual != null && actual.id != cafeteria.id && carrito.lineas.value.isNotEmpty()) {
            _estado.update { it.copy(porConfirmar = cafeteria) }
        } else {
            aplicar(cafeteria)
        }
    }

    fun confirmarCambio() = _estado.value.porConfirmar?.let { aplicar(it) }

    fun cancelarCambio() = _estado.update { it.copy(porConfirmar = null) }

    private fun aplicar(cafeteria: Cafeteria) {
        val cambio = _estado.value.cafeteria?.id != cafeteria.id
        // Primero el estado y luego el carrito: así el colector de init no vuelve a aplicarlo.
        _estado.update {
            it.copy(
                cafeteria = cafeteria, eligiendo = false, porConfirmar = null,
                productos = if (cambio) emptyList() else it.productos,
                categorias = if (cambio) emptyList() else it.categorias,
                categoria = if (cambio) null else it.categoria,
                ultimo = if (cambio) null else it.ultimo,
            )
        }
        carrito.elegir(cafeteria)
        viewModelScope.launch { runCatching { recordar(cafeteria.id) } }
        cargar()
        cargarUltimo()
    }

    /** Si falla no pasa nada: la tarjeta de repetir simplemente no aparece. Solo pedidos de este cafetín. */
    private fun cargarUltimo() {
        val idCafeteria = carrito.cafeteria.value?.id ?: return
        viewModelScope.launch {
            runCatching { pedidos.historial(0, 5) }.onSuccess { pagina ->
                val ultimo = pagina.contenido.firstOrNull {
                    it.estadoPago == "PAGADO" && it.estado != "CANCELADO" && (it.cafeteria == null || it.cafeteria.id == idCafeteria)
                }
                _estado.update { it.copy(ultimo = ultimo) }
            }
        }
    }

    /** Métrica sugerida: % de pedidos que empiezan desde "repetir". */
    fun repetirUltimo() {
        val ultimo = _estado.value.ultimo ?: return
        _estado.update { it.copy(repitiendo = true) }
        viewModelScope.launch {
            try {
                val agregadas = carrito.repetir(pedidos.pedido(ultimo.id).items)
                _estado.update {
                    if (agregadas > 0) it.copy(repitiendo = false, irAlCarrito = true)
                    else it.copy(repitiendo = false, aviso = "Los productos de tu último pedido no están disponibles ahora")
                }
            } catch (e: ErrorApi) {
                _estado.update { it.copy(repitiendo = false, aviso = e.message) }
            }
        }
    }

    fun navegadoAlCarrito() = _estado.update { it.copy(irAlCarrito = false) }

    fun cargar() {
        val idCafeteria = carrito.cafeteria.value?.id
        if (idCafeteria == null) {
            cargarCafeterias(automatica = false)
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val productos = pedidos.catalogo(idCafeteria)
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
            carrito.cafeteria.value == null -> "Elige un cafetín en el catálogo"
            e.hora == null -> "Elige a qué hora pasas a recoger"
            else -> null
        }
        if (error != null) {
            _estado.update { it.copy(error = error) }
            return
        }
        val solicitud = CrearPedidoRequest(
            idCafeteria = carrito.cafeteria.value!!.id,
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
    /** Pedido que se está agregando al carrito con "Volver a pedir". */
    val repitiendo: Int? = null,
    val aviso: String? = null,
    val irAlCarrito: Boolean = false,
)

class HistorialViewModel(private val pedidos: Pedidos, private val carrito: Carrito = Carrito()) : ViewModel() {

    /**
     * "Volver a pedir": lee el detalle, actualiza precios y stock con el catálogo de hoy de su cafetín y llena
     * el carrito. Si el carrito ya tiene productos de otro cafetín, no los mezcla: avisa.
     */
    fun volverAPedir(idPedido: Int) {
        _estado.update { it.copy(repitiendo = idPedido, aviso = null) }
        viewModelScope.launch {
            try {
                val pedido = pedidos.pedido(idPedido)
                val cafeteria = pedido.cafeteria ?: carrito.cafeteria.value
                if (cafeteria == null) {
                    _estado.update { it.copy(repitiendo = null, aviso = "Elige un cafetín en el catálogo") }
                    return@launch
                }
                val actual = carrito.cafeteria.value
                if (actual != null && actual.id != cafeteria.id && carrito.lineas.value.isNotEmpty()) {
                    _estado.update {
                        it.copy(repitiendo = null, aviso = "Tu carrito es de ${actual.nombre}. Termina o vacía ese pedido primero")
                    }
                    return@launch
                }
                carrito.elegir(cafeteria)
                val catalogo = pedidos.catalogo(cafeteria.id).also(carrito::actualizar)
                val agregadas = carrito.repetir(pedido.items, catalogo)
                _estado.update {
                    if (agregadas > 0) it.copy(repitiendo = null, irAlCarrito = true)
                    else it.copy(repitiendo = null, aviso = "Esos productos no están disponibles ahora")
                }
            } catch (e: ErrorApi) {
                _estado.update { it.copy(repitiendo = null, aviso = e.message) }
            }
        }
    }

    fun navegadoAlCarrito() = _estado.update { it.copy(irAlCarrito = false) }
    fun avisoVisto() = _estado.update { it.copy(aviso = null) }


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
