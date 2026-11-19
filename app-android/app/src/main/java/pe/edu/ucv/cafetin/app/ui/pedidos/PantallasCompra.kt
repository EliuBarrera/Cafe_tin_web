package pe.edu.ucv.cafetin.app.ui.pedidos

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.ucv.cafetin.app.datos.Cafeteria
import pe.edu.ucv.cafetin.app.datos.ProductoCatalogo
import pe.edu.ucv.cafetin.app.ui.Acentos
import pe.edu.ucv.cafetin.app.ui.cuentas.BotonPrincipal
import pe.edu.ucv.cafetin.app.ui.cuentas.MensajeError
import pe.edu.ucv.cafetin.app.ui.estiloPrecio
import pe.edu.ucv.cafetin.app.ui.inicio.PedirPermisoNotificaciones
import java.time.LocalTime

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
    LaunchedEffect(estado.irAlCarrito) {
        if (estado.irAlCarrito) {
            vm.navegadoAlCarrito()
            alVerCarrito()
        }
    }

    estado.porConfirmar?.let { nueva ->
        AlertDialog(
            onDismissRequest = vm::cancelarCambio,
            title = { Text("¿Cambiar a ${nueva.nombre}?") },
            text = { Text("Tu carrito tiene productos de ${estado.cafeteria?.nombre}. Un pedido es de un solo cafetín: si cambias, se vacía.") },
            confirmButton = { TextButton(onClick = vm::confirmarCambio) { Text("Cambiar y vaciar") } },
            dismissButton = { TextButton(onClick = vm::cancelarCambio) { Text("Seguir en ${estado.cafeteria?.nombre}") } },
        )
    }

    if (estado.eligiendo || estado.cafeteria == null) {
        ElegirCafeteria(
            estado = estado,
            nombre = nombre,
            alElegir = vm::elegir,
            alReintentar = vm::cambiarCafeteria,
            alCancelar = if (estado.cafeteria != null) vm::cancelarEleccion else null,
            alCerrarSesion = alCerrarSesion,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                item { Cabecera(nombre, estado.cafeteria, alCerrarSesion, alCambiar = vm::cambiarCafeteria) }
                estado.ultimo?.let { ultimo ->
                    item {
                        TarjetaRepetir(
                            texto = "${ultimo.codigoPedido} · ${ultimo.cantidadProductos} prod. · ${soles(ultimo.total)}",
                            cargando = estado.repitiendo,
                            alRepetir = vm::repetirUltimo,
                        )
                    }
                }
                when {
                    estado.cargando && estado.productos.isEmpty() -> item { Esqueleto() }
                    estado.error != null && estado.productos.isEmpty() -> item {
                        Box(Modifier.heightIn(min = 320.dp)) { ErrorConReintento(estado.error!!, vm::cargar) }
                    }
                    else -> {
                        item {
                            // Categorías arriba (Hick): pocas opciones a la vista por vez.
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ChipCategoria("Todo", estado.categoria == null) { vm.filtrar(null) }
                                estado.categorias.forEach { c -> ChipCategoria(c, estado.categoria == c) { vm.filtrar(c) } }
                            }
                        }
                        items(estado.visibles, key = { it.idProducto }) { p ->
                            val enCarrito = lineas.firstOrNull { it.producto.idProducto == p.idProducto }?.cantidad ?: 0
                            TarjetaProducto(
                                p = p,
                                enCarrito = enCarrito,
                                alAgregar = { vm.agregar(p) },
                                alRestar = { vm.carrito.cambiar(p.idProducto, enCarrito - 1) },
                            )
                        }
                    }
                }
            }
            if (lineas.isNotEmpty()) {
                val unidades = lineas.sumOf { it.cantidad }
                BarraAccion {
                    Button(onClick = alVerCarrito, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Ver carrito · $unidades", Modifier.weight(1f))
                        Text(solesDeCentimos(lineas.sumOf { it.subtotalCentimos }), style = estiloPrecio)
                    }
                }
            }
        }
        SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
}

/**
 * Primera impresión (Lindgaard et al., 2006): saludo según la hora y la promesa central del producto,
 * recoger sin fila a una hora concreta (el tiempo de espera es lo que más pesa al elegir dónde comprar).
 */
@Composable
private fun Cabecera(nombre: String, cafeteria: Cafeteria?, alCerrarSesion: () -> Unit, alCambiar: (() -> Unit)? = null) {
    val ahora = remember { LocalTime.now() }
    val primeraHora = remember { horasDeRecojo(ahora).firstOrNull() }
    val degradado = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    Column(
        Modifier.fillMaxWidth()
            .background(degradado, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CAFETÍN UCV", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
            TextButton(onClick = alCerrarSesion) { Text("Salir", color = Color.White) }
        }
        Text(
            "${saludo(ahora)}, $nombre",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (primeraHora != null) "Pide ahora y recoge desde las %02d:%02d, sin hacer fila.".format(primeraHora.hour, primeraHora.minute)
            else "Por hoy ya no hay horarios de recojo. Puedes ver el catálogo.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(end = 12.dp),
        )
        if (cafeteria != null && alCambiar != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Recoges en ${cafeteria.nombre}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = alCambiar, modifier = Modifier.semantics { contentDescription = "Cambiar de cafetín" }) {
                    Text("Cambiar", color = Color.White)
                }
            }
        }
    }
}

/** Lista de cafetines: el estudiante elige dónde pedir; todo su pedido será de ese cafetín. */
@Composable
private fun ElegirCafeteria(
    estado: CatalogoEstado,
    nombre: String,
    alElegir: (Cafeteria) -> Unit,
    alReintentar: () -> Unit,
    alCancelar: (() -> Unit)?,
    alCerrarSesion: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Cabecera(nombre, null, alCerrarSesion) }
        item {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("¿Dónde vas a recoger?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                alCancelar?.let { TextButton(onClick = it) { Text("Volver") } }
            }
        }
        when {
            estado.cargando && estado.cafeterias.isEmpty() -> item { Esqueleto() }
            estado.error != null && estado.cafeterias.isEmpty() -> item {
                Box(Modifier.heightIn(min = 320.dp)) { ErrorConReintento(estado.error, alReintentar) }
            }
            else -> items(estado.cafeterias, key = { it.id }) { c ->
                val actual = c.id == estado.cafeteria?.id
                Card(
                    onClick = { alElegir(c) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).heightIn(min = 72.dp),
                    colors = CardDefaults.cardColors(containerColor = if (actual) fondoCrema() else MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarProducto(c.nombre, c.nombre)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nombre, style = MaterialTheme.typography.titleMedium)
                            c.ubicacion?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (actual) Text("Actual", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Reconocer, no recordar (Nielsen) y hábito (Wood y Neal, 2009): repetir lo de siempre con un toque. */
@Composable
private fun TarjetaRepetir(texto: String, cargando: Boolean, alRepetir: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = fondoCrema()),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("¿Lo de siempre?", style = MaterialTheme.typography.titleMedium)
                Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = alRepetir, enabled = !cargando, modifier = Modifier.heightIn(min = 48.dp)) {
                if (cargando) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp) else Text("Repetir")
            }
        }
    }
}

@Composable
private fun ChipCategoria(texto: String, elegida: Boolean, alPulsar: () -> Unit) {
    FilterChip(
        selected = elegida,
        onClick = alPulsar,
        label = { Text(texto) },
        modifier = Modifier.heightIn(min = 40.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun TarjetaProducto(p: ProductoCatalogo, enCarrito: Int, alAgregar: () -> Unit, alRestar: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarProducto(p.nombre, p.categoria)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                p.descripcion?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(soles(p.precio), style = estiloPrecio, color = MaterialTheme.colorScheme.primary)
                    // Disponibilidad real (nunca escasez inventada): solo cuando de verdad quedan pocas.
                    when {
                        p.agotado -> Text("Agotado", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        p.estadoStock == "POCAS UNIDADES" -> Text("Quedan ${p.cantidadDisponible}", color = if (isSystemInDarkTheme()) Acentos.caramelo else Acentos.carameloTexto, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (enCarrito == 0) {
                FilledTonalButton(
                    onClick = alAgregar,
                    enabled = !p.agotado,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Agregar ${p.nombre} al carrito" },
                ) { Text("Agregar") }
            } else {
                SelectorCantidad(p.nombre, enCarrito, puedeSumar = enCarrito < minOf(p.cantidadDisponible, Carrito.MAX_POR_PRODUCTO), alRestar = alRestar, alSumar = alAgregar)
            }
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
        Vacio("Tu carrito está vacío", "Elige algo del catálogo y te lo tenemos listo a la hora que digas.") {
            OutlinedButton(onClick = alVolverAlCatalogo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ver el catálogo") }
        }
        return
    }
    var sugerenciaDescartada by rememberSaveable { mutableStateOf(false) }
    val sugerida = if (sugerenciaDescartada) null else sugerencia(lineas, vm.carrito.catalogo)

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Tu pedido", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) }
            items(lineas, key = { it.producto.idProducto }) { linea ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarProducto(linea.producto.nombre, linea.producto.categoria, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(linea.producto.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(solesDeCentimos(linea.subtotalCentimos), style = estiloPrecio, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SelectorCantidad(
                        linea.producto.nombre,
                        linea.cantidad,
                        puedeSumar = linea.cantidad < minOf(linea.producto.cantidadDisponible, Carrito.MAX_POR_PRODUCTO),
                        alRestar = { vm.carrito.cambiar(linea.producto.idProducto, linea.cantidad - 1) },
                        alSumar = { vm.carrito.cambiar(linea.producto.idProducto, linea.cantidad + 1) },
                    )
                }
            }
            sugerida?.let { p ->
                item(key = "sugerencia") {
                    TarjetaSugerencia(p, alAgregar = { vm.carrito.agregar(p) }, alDescartar = { sugerenciaDescartada = true })
                }
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("¿A qué hora pasas a recoger?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (estado.horas.isEmpty()) {
                    Text("Ya no hay horarios de recojo para hoy.", color = MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    estado.horas.forEach { h ->
                        ChipCategoria("%02d:%02d".format(h.hour, h.minute), estado.hora == h) { vm.elegirHora(h) }
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
            item { MensajeError(estado.error) }
        }
        // Total siempre visible y acción principal fija abajo (precio transparente, Fitts).
        BarraAccion {
            Column {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(solesDeCentimos(lineas.sumOf { it.subtotalCentimos }), style = MaterialTheme.typography.titleLarge.merge(estiloPrecio))
                }
                BotonPrincipal("Continuar al pago", estado.cargando, vm::confirmar)
            }
        }
    }
}

/**
 * Una sola sugerencia antes de pagar (impulso, Rook 1987), siempre el producto más económico disponible y
 * con "No, gracias" al mismo nivel que "Agregar" (filtro ético: nada se agrega solo).
 * Métrica sugerida: % de pedidos con el producto sugerido.
 */
@Composable
private fun TarjetaSugerencia(p: ProductoCatalogo, alAgregar: () -> Unit, alDescartar: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = fondoCrema())) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarProducto(p.nombre, p.categoria, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("¿Algo para acompañar?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${p.nombre} · ${soles(p.precio)}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = alDescartar, modifier = Modifier.heightIn(min = 48.dp)) { Text("No, gracias") }
            FilledTonalButton(onClick = alAgregar, modifier = Modifier.heightIn(min = 48.dp)) { Text("Agregar") }
        }
    }
}
