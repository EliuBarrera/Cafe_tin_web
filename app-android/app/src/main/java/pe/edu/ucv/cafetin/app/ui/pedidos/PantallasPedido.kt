package pe.edu.ucv.cafetin.app.ui.pedidos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pe.edu.ucv.cafetin.app.datos.Notificacion
import pe.edu.ucv.cafetin.app.datos.PedidoResponse
import pe.edu.ucv.cafetin.app.datos.PedidoResumen
import pe.edu.ucv.cafetin.app.ui.cuentas.BotonPrincipal
import pe.edu.ucv.cafetin.app.ui.cuentas.MensajeError

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
        estado.cargando -> Cargando()
        pedido == null -> ErrorConReintento(estado.error ?: "No se encontró el pedido", vm::cargar)
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Pagar pedido", style = MaterialTheme.typography.headlineSmall)
                Text("${pedido.codigoPedido} · recojo ${pedido.horaRecojo ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { DetalleLineas(pedido) }
            item {
                Text("¿Cómo pagas?", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    METODOS_PAGO.forEach { (codigo, nombre) ->
                        FilterChip(selected = estado.metodo == codigo, onClick = { vm.elegirMetodo(codigo) }, label = { Text(nombre) })
                    }
                }
                Text(
                    "Pago de prueba: no se te cobra de verdad ni se guardan datos de tarjeta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                MensajeError(estado.error)
                Spacer(Modifier.height(8.dp))
                BotonPrincipal("Pagar ${soles(pedido.total)}", estado.pagando, vm::pagar)
            }
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
        estado.cargando -> Cargando()
        pedido == null -> ErrorConReintento(estado.error ?: "No se encontró el pedido", vm::refrescar)
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pedido.codigoPedido, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    EtiquetaEstado(pedido.estado, pedido.pago?.estado)
                }
                Text("Recojo a las ${pedido.horaRecojo ?: "—"} · pedido el ${fechaCorta(pedido.fechaHora)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            pedido.recibo?.let { recibo ->
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Recibo digital", style = MaterialTheme.typography.labelLarge)
                            Text(recibo.codigoVerificacion, fontSize = 30.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(
                                if (recibo.verificado) "Verificado en el mostrador" else mensajeRecojo(pedido.estado),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
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
                    OutlinedButton(onClick = { confirmarCancelar = true }, enabled = !estado.cancelando, modifier = Modifier.fillMaxWidth()) {
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

private fun mensajeRecojo(estado: String) = when (estado) {
    "LISTO" -> "¡Tu pedido está listo! Muestra este código en el mostrador."
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            pedido.items.forEach { l ->
                Row {
                    Text("${l.cantidad} × ${l.nombre}", Modifier.weight(1f))
                    Text(soles(l.subtotal))
                }
            }
            HorizontalDivider()
            Row {
                Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(soles(pedido.total), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ------------------------------------------------------------------ Historial (RF-09)

@Composable
fun PantallaHistorial(vm: HistorialViewModel, alAbrir: (Int) -> Unit) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    // Al volver a esta pestaña se relee: los estados cambian mientras tanto.
    LaunchedEffect(Unit) { if (estado.pedidos.isNotEmpty()) vm.recargar() }
    when {
        estado.cargando && estado.pedidos.isEmpty() -> Cargando()
        estado.error != null && estado.pedidos.isEmpty() -> ErrorConReintento(estado.error!!, vm::recargar)
        estado.pedidos.isEmpty() -> Vacio("Aún no tienes pedidos.")
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Mis pedidos", style = MaterialTheme.typography.headlineSmall) }
            items(estado.pedidos, key = { it.id }) { p -> FilaPedido(p) { alAbrir(p.id) } }
            if (estado.hayMas) {
                item {
                    LaunchedEffect(estado.pagina) { vm.cargarMas() }
                    Cargando()
                }
            }
        }
    }
}

@Composable
private fun FilaPedido(p: PedidoResumen, alPulsar: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = alPulsar)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.codigoPedido, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                EtiquetaEstado(p.estado, p.estadoPago)
            }
            Text(
                "${fechaCorta(p.fechaHora)} · recojo ${p.horaRecojo ?: "—"} · ${p.cantidadProductos} prod. · ${soles(p.total)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        estado.cargando -> Cargando()
        estado.error != null -> ErrorConReintento(estado.error!!, vm::cargar)
        estado.avisos.isEmpty() -> Vacio("No tienes avisos. Te avisaremos aquí cuando tu pedido cambie de estado.")
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Avisos", style = MaterialTheme.typography.headlineSmall) }
            items(estado.avisos, key = { it.id }) { a ->
                FilaAviso(a) {
                    vm.abrir(a)
                    a.idPedido?.let(alAbrirPedido)
                }
            }
        }
    }
}

@Composable
private fun FilaAviso(a: Notificacion, alPulsar: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = alPulsar),
        colors = if (a.leida) CardDefaults.cardColors() else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(a.mensaje, fontWeight = if (a.leida) FontWeight.Normal else FontWeight.SemiBold)
            Text(fechaCorta(a.fecha), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
