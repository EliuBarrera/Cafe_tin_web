package pe.edu.ucv.cafetin.app.ui.pedidos

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.ucv.cafetin.app.datos.ProductoCatalogo
import pe.edu.ucv.cafetin.app.ui.cuentas.BotonPrincipal
import pe.edu.ucv.cafetin.app.ui.cuentas.MensajeError
import pe.edu.ucv.cafetin.app.ui.inicio.PedirPermisoNotificaciones

// ------------------------------------------------------------------ Catálogo (RF-04)

@Composable
fun PantallaCatalogo(vm: CatalogoViewModel, nombre: String, alCerrarSesion: () -> Unit, alVerCarrito: () -> Unit) {
    PedirPermisoNotificaciones()
    val estado by vm.estado.collectAsStateWithLifecycle()
    val lineas by vm.carrito.lineas.collectAsState()
    val avisos = remember { SnackbarHostState() }
    LaunchedEffect(estado.aviso) {
        estado.aviso?.let {
            avisos.showSnackbar(it)
            vm.avisoVisto()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Hola, $nombre", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = alCerrarSesion) { Text("Salir") }
        }
        when {
            estado.cargando && estado.productos.isEmpty() -> Cargando()
            estado.error != null && estado.productos.isEmpty() -> ErrorConReintento(estado.error!!, vm::cargar)
            else -> {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = estado.categoria == null, onClick = { vm.filtrar(null) }, label = { Text("Todo") })
                    estado.categorias.forEach { c ->
                        FilterChip(selected = estado.categoria == c, onClick = { vm.filtrar(c) }, label = { Text(c) })
                    }
                }
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(estado.visibles, key = { it.idProducto }) { p ->
                        TarjetaProducto(p, enCarrito = lineas.firstOrNull { it.producto.idProducto == p.idProducto }?.cantidad ?: 0) { vm.agregar(p) }
                    }
                }
                if (lineas.isNotEmpty()) {
                    val unidades = lineas.sumOf { it.cantidad }
                    Button(onClick = alVerCarrito, modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp)) {
                        Text("Ver carrito · $unidades ${if (unidades == 1) "producto" else "productos"} · ${solesDeCentimos(lineas.sumOf { it.subtotalCentimos })}")
                    }
                }
            }
        }
        SnackbarHost(avisos)
    }
}

@Composable
private fun TarjetaProducto(p: ProductoCatalogo, enCarrito: Int, alAgregar: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.nombre, style = MaterialTheme.typography.titleMedium)
                p.descripcion?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(soles(p.precio), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    when {
                        p.agotado -> Text("Agotado", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        p.estadoStock == "POCAS UNIDADES" -> Text("Quedan ${p.cantidadDisponible}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(
                onClick = alAgregar,
                enabled = !p.agotado,
                modifier = Modifier.semantics { contentDescription = "Agregar ${p.nombre} al carrito" },
            ) { Text(if (enCarrito > 0) "+1 ($enCarrito)" else "Agregar") }
        }
    }
}

// ------------------------------------------------------------------ Carrito (RF-05)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaCarrito(vm: CarritoViewModel, alCrear: (Int) -> Unit, alVolverAlCatalogo: () -> Unit) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val lineas by vm.carrito.lineas.collectAsState()
    LaunchedEffect(estado.pedidoCreado) { estado.pedidoCreado?.let(alCrear) }
    // Se limpia al salir y no antes: así, durante la transición al pago no asoma "carrito vacío".
    DisposableEffect(Unit) { onDispose { vm.navegado() } }
    if (lineas.isEmpty() && !estado.cargando && estado.pedidoCreado == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tu carrito está vacío", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = alVolverAlCatalogo) { Text("Ver el catálogo") }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Tu pedido", style = MaterialTheme.typography.headlineSmall) }
        items(lineas, key = { it.producto.idProducto }) { linea ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(linea.producto.nombre, style = MaterialTheme.typography.titleSmall)
                    Text(solesDeCentimos(linea.subtotalCentimos), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { vm.carrito.cambiar(linea.producto.idProducto, linea.cantidad - 1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(44.dp).semantics { contentDescription = "Quitar uno" }) { Text("−") }
                Text("${linea.cantidad}", Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                OutlinedButton(onClick = { vm.carrito.cambiar(linea.producto.idProducto, linea.cantidad + 1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(44.dp).semantics { contentDescription = "Agregar uno" }) { Text("+") }
            }
        }
        item {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(solesDeCentimos(lineas.sumOf { it.subtotalCentimos }), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text("¿A qué hora pasas a recoger?", style = MaterialTheme.typography.titleSmall)
            if (estado.horas.isEmpty()) {
                Text("Ya no hay horarios de recojo para hoy.", color = MaterialTheme.colorScheme.error)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                estado.horas.forEach { h ->
                    FilterChip(selected = estado.hora == h, onClick = { vm.elegirHora(h) }, label = { Text("%02d:%02d".format(h.hour, h.minute)) })
                }
            }
        }
        item {
            OutlinedTextField(
                value = estado.observacion,
                onValueChange = vm::cambiarObservacion,
                label = { Text("Indicaciones (opcional)") },
                placeholder = { Text("Sin ají, por favor") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            MensajeError(estado.error)
            Spacer(Modifier.height(8.dp))
            BotonPrincipal("Continuar al pago", estado.cargando, vm::confirmar)
        }
    }
}
