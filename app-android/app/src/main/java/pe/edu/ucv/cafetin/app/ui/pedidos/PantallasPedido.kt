package pe.edu.ucv.cafetin.app.ui.pedidos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pe.edu.ucv.cafetin.app.datos.Notificacion
import pe.edu.ucv.cafetin.app.datos.PedidoResponse
import pe.edu.ucv.cafetin.app.datos.PedidoResumen
import pe.edu.ucv.cafetin.app.ui.Acentos
import pe.edu.ucv.cafetin.app.ui.cuentas.BotonPrincipal
import pe.edu.ucv.cafetin.app.ui.cuentas.MensajeError
import pe.edu.ucv.cafetin.app.ui.estiloPrecio

/** Cada cuánto se consulta el estado de un pedido abierto (RF-08 mientras no llegue el push). */
private const val INTERVALO_REFRESCO_MS = 5_000L

// ------------------------------------------------------------------ Pago (RF-06)

@Composable
fun PantallaPago(vm: PagoViewModel, alPagar: (Int) -> Unit) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(estado.pagado) {
        if (estado.pagado) estado.pedido?.let { alPagar(it.id) }
    }
    val pedido = estado.pedido
    when {
        estado.cargando -> Esqueleto(3, 120.dp)
        pedido == null -> ErrorConReintento(estado.error ?: "No se encontró el pedido", vm::cargar)
        else -> Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text("Pagar pedido", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text("${pedido.codigoPedido} · recojo a las ${pedido.horaRecojo ?: "—"}${pedido.cafeteria?.let { " en ${it.nombre}" }.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { DetalleLineas(pedido) }
                item {
                    Text("¿Cómo pagas?", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    // Pago dentro de la app con medios locales: reduce el "dolor de pagar" (Prelec y Loewenstein, 1998).
                    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        METODOS_PAGO.forEach { (codigo, nombre) ->
                            OpcionPago(nombre, estado.metodo == codigo, Modifier.weight(1f)) { vm.elegirMetodo(codigo) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pago de prueba: no se te cobra de verdad ni se guardan datos de tarjeta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { MensajeError(estado.error) }
            }
            BarraAccion { BotonPrincipal("Pagar ${soles(pedido.total)}", estado.pagando, vm::pagar) }
        }
    }
}

@Composable
private fun OpcionPago(nombre: String, elegida: Boolean, modifier: Modifier, alElegir: () -> Unit) {
    val color = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier.heightIn(min = 88.dp).selectable(selected = elegida, onClick = alElegir, role = Role.RadioButton),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(if (elegida) 2.dp else 1.dp, color),
        color = if (elegida) fondoCrema() else MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    nombre.take(1),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (elegida) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(nombre, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ------------------------------------------------------------------ Detalle, recibo y estado (RF-07, RF-08)

@Composable
fun PantallaPedido(vm: PedidoViewModel, alPagar: (Int) -> Unit) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val pedido = estado.pedido
    // Mientras la pantalla está abierta y el pedido puede cambiar, se consulta cada pocos segundos.
    LaunchedEffect(pedido?.estado) {
        if (pedido != null && !esFinal(pedido.estado)) {
            while (true) {
                delay(INTERVALO_REFRESCO_MS)
                vm.refrescar()
            }
        }
    }
    var confirmarCancelar by remember { mutableStateOf(false) }

    when {
        estado.cargando -> Esqueleto(3, 120.dp)
        pedido == null -> ErrorConReintento(estado.error ?: "No se encontró el pedido", vm::refrescar)
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pedido.codigoPedido, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
                    EtiquetaEstado(pedido.estado, pedido.pago?.estado)
                }
                Text("Recojo a las ${pedido.horaRecojo ?: "—"}${pedido.cafeteria?.let { " en ${it.nombre}" }.orEmpty()} · pedido el ${fechaCorta(pedido.fechaHora)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (pedido.estado == "LISTO") item { BannerListo() }
            val paso = pasoActual(pedido.estado, pedido.pago?.estado)
            if (paso >= 0) item { SeguimientoPedido(paso) }
            pedido.recibo?.let { recibo ->
                item {
                    TicketRecibo(
                        recibo.codigoVerificacion,
                        if (recibo.verificado) "Verificado en el mostrador" else mensajeRecojo(pedido.estado),
                    )
                }
            }
            item { DetalleLineas(pedido) }
            pedido.observacion?.let { item { Text("Indicaciones: $it", style = MaterialTheme.typography.bodyMedium) } }
            pedido.pago?.let { pago ->
                item {
                    Text(
                        "Pago ${pago.metodoPago.lowercase().replaceFirstChar { it.uppercase() }} · ${etiquetaPago(pago.estado)}" +
                            (pago.referencia?.let { " · op. $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                MensajeError(estado.error)
                if (pedido.estado == "PENDIENTE" && !pedido.pagado) {
                    BotonPrincipal("Pagar ${soles(pedido.total)}", false) { alPagar(pedido.id) }
                    Spacer(Modifier.height(8.dp))
                    // Cancelar cuesta lo mismo que pagar: un botón visible, no escondido (filtro ético).
                    OutlinedButton(onClick = { confirmarCancelar = true }, enabled = !estado.cancelando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Cancelar pedido")
                    }
                }
            }
        }
    }

    if (confirmarCancelar) {
        AlertDialog(
            onDismissRequest = { confirmarCancelar = false },
            title = { Text("¿Cancelar el pedido?") },
            text = { Text("Aún no lo pagaste, así que no se te cobra nada.") },
            confirmButton = { TextButton(onClick = { confirmarCancelar = false; vm.cancelar() }) { Text("Sí, cancelar") } },
            dismissButton = { TextButton(onClick = { confirmarCancelar = false }) { Text("No") } },
        )
    }
}

@Composable
private fun BannerListo() {
    Surface(color = Acentos.exito, contentColor = Color.White, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
            Text("☕", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("¡Tu pedido está listo!", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Acércate al mostrador y muestra tu código.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }
    }
}

private fun mensajeRecojo(estado: String) = when (estado) {
    "LISTO" -> "Muestra este código en el mostrador."
    "CANCELADO" -> "Pedido cancelado"
    else -> "Muestra este código cuando recojas tu pedido."
}

private fun etiquetaPago(estado: String) = when (estado) {
    "PAGADO" -> "pagado"
    "FALLIDO" -> "rechazado"
    "PENDIENTE" -> "en proceso"
    else -> estado.lowercase()
}

@Composable
private fun DetalleLineas(pedido: PedidoResponse) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pedido.items.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${l.cantidad}×", style = estiloPrecio, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp))
                    Text(l.nombre, Modifier.weight(1f))
                    Text(soles(l.subtotal), style = estiloPrecio)
                }
            }
            HorizontalDivider()
            Row {
                Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(soles(pedido.total), style = MaterialTheme.typography.titleMedium.merge(estiloPrecio))
            }
        }
    }
}

// ------------------------------------------------------------------ Historial (RF-09)

@Composable
fun PantallaHistorial(vm: HistorialViewModel, alAbrir: (Int) -> Unit, alIrAlCarrito: () -> Unit, alVerCatalogo: () -> Unit) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    // Al volver a esta pestaña se relee: los estados cambian mientras tanto.
    LaunchedEffect(Unit) { if (estado.pedidos.isNotEmpty()) vm.recargar() }
    LaunchedEffect(estado.irAlCarrito) {
        if (estado.irAlCarrito) {
            vm.navegadoAlCarrito()
            alIrAlCarrito()
        }
    }
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            avisos.showSnackbar(it)
            vm.avisoVisto()
        }
    }
    Box(Modifier.fillMaxSize()) {
        when {
            estado.cargando && estado.pedidos.isEmpty() -> Esqueleto()
            estado.error != null && estado.pedidos.isEmpty() -> ErrorConReintento(estado.error!!, vm::recargar)
            estado.pedidos.isEmpty() -> Vacio("Aún no tienes pedidos", "Tu primer pedido aparecerá aquí con su recibo.") {
                OutlinedButton(onClick = alVerCatalogo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ver el catálogo") }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Mis pedidos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
                items(estado.pedidos, key = { it.id }) { p ->
                    FilaPedido(p, repitiendo = estado.repitiendo == p.id, alPulsar = { alAbrir(p.id) }, alRepetir = { vm.volverAPedir(p.id) })
                }
                if (estado.hayMas) {
                    item {
                        LaunchedEffect(estado.pagina) { vm.cargarMas() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FilaPedido(p: PedidoResumen, repitiendo: Boolean, alPulsar: () -> Unit, alRepetir: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = alPulsar),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.codigoPedido, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                EtiquetaEstado(p.estado, p.estadoPago)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(fechaCorta(p.fechaHora), p.cafeteria?.nombre, "${p.cantidadProductos} prod.", soles(p.total)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (p.estadoPago == "PAGADO") {
                    TextButton(onClick = alRepetir, enabled = !repitiendo, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (repitiendo) "Agregando…" else "Volver a pedir")
                    }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Avisos (RF-08)

@Composable
fun PantallaAvisos(vm: AvisosViewModel, alAbrirPedido: (Int) -> Unit) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        while (true) {
            vm.cargar()
            delay(INTERVALO_REFRESCO_MS * 2)
        }
    }
    when {
        estado.cargando -> Esqueleto(4, 72.dp)
        estado.error != null -> ErrorConReintento(estado.error!!, vm::cargar)
        estado.avisos.isEmpty() -> Vacio("Sin avisos por ahora", "Te avisaremos aquí cuando tu pedido cambie de estado.")
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Avisos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
            items(estado.avisos, key = { it.id }) { a ->
                FilaAviso(a) {
                    vm.abrir(a)
                    a.idPedido?.let(alAbrirPedido)
                }
            }
        }
    }
}

private fun iconoAviso(tipo: String) = when (tipo) {
    "PEDIDO_LISTO" -> "☕"
    "PEDIDO_ENTREGADO" -> "✓"
    "PEDIDO_CANCELADO" -> "×"
    else -> "•"
}

@Composable
private fun FilaAviso(a: Notificacion, alPulsar: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = alPulsar),
        colors = CardDefaults.cardColors(containerColor = if (a.leida) MaterialTheme.colorScheme.surface else fondoCrema()),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (a.tipo == "PEDIDO_LISTO") Acentos.exito else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    iconoAviso(a.tipo),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (a.tipo == "PEDIDO_LISTO") Color.White else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(a.mensaje, fontWeight = if (a.leida) FontWeight.Normal else FontWeight.SemiBold)
                Text(fechaCorta(a.fecha), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!a.leida) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Acentos.caramelo))
            }
        }
    }
}
