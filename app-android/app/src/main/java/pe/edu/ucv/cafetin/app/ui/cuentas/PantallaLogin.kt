package pe.edu.ucv.cafetin.app.ui.cuentas

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.ucv.cafetin.app.R
import pe.edu.ucv.cafetin.app.datos.Sesion
import pe.edu.ucv.cafetin.app.ui.ArchivoExpandida
import pe.edu.ucv.cafetin.app.ui.LogoCafetin
import pe.edu.ucv.cafetin.app.ui.Paleta
import pe.edu.ucv.cafetin.app.ui.Script
import pe.edu.ucv.cafetin.app.ui.TituloPantalla
import pe.edu.ucv.cafetin.app.ui.lineaRuta
import pe.edu.ucv.cafetin.app.ui.topografia

/**
 * Login (RF-01): la portada muestra la marca y un botón de vidrio "Entrar". Al pulsarlo sube una
 * tarjeta desde abajo con las tres opciones (entrar con el correo, crear cuenta, recuperar contraseña);
 * "Entrar con mi correo" cambia la tarjeta por el formulario. "Atrás" deshace cada paso.
 */
@Composable
fun PantallaLogin(vm: LoginViewModel, alEntrar: (Sesion) -> Unit, alRegistrarse: () -> Unit, alRecuperar: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(e.sesion) { e.sesion?.let(alEntrar) }

    var mostrar by rememberSaveable { mutableStateOf(false) }
    var conCorreo by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = mostrar) { if (conCorreo) conCorreo = false else mostrar = false }

    val progreso by animateFloatAsState(
        targetValue = if (mostrar) 1f else 0f,
        animationSpec = tween(if (mostrar) 300 else 200),
        label = "tarjeta",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .topografia(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.075f))
            .lineaRuta(Paleta.naranja.copy(alpha = 0.85f)),
    ) {
        // Portada: se desvanece un poco cuando sube la tarjeta, para que la mirada vaya a ella.
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp).graphicsLayer { alpha = 1f - 0.5f * progreso },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoCafetin(168.dp)
            Spacer(Modifier.height(8.dp))
            MarcaCafeTin()
            Text(
                "sin hacer fila",
                fontFamily = Script,
                fontSize = 34.sp,
                color = Paleta.naranja,
                modifier = Modifier.rotate(-4f),
            )
            Spacer(Modifier.height(40.dp))
            BotonVidrio("Entrar") { mostrar = !mostrar }
        }

        if (mostrar || progreso > 0f) {
            TarjetaAcceso(
                Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = size.height * 1.1f * (1f - progreso)
                        alpha = progreso
                    },
            ) {
                AnimatedContent(conCorreo, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "paso") { correo ->
                    if (!correo) {
                        Bienvenida(
                            alCorreo = { conCorreo = true },
                            alRegistrarse = alRegistrarse,
                            alRecuperar = alRecuperar,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { conCorreo = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("← Otras opciones", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TituloPantalla("Iniciar sesión", color = MaterialTheme.colorScheme.onSurface)
                            CampoTexto("Correo", e.correo, vm::cambiarCorreo, e.errorCorreo, KeyboardType.Email)
                            CampoTexto("Contraseña", e.contrasena, vm::cambiarContrasena, e.errorContrasena, KeyboardType.Password, secreto = true, ultimo = true)
                            MensajeError(e.error)
                            BotonPrincipal(if (e.cargando) "Entrando…" else "Entrar", e.cargando, vm::entrar)
                        }
                    }
                }
            }
        }
    }
}

/** "Cafe & TIN": el nombre manuscrito y la sílaba en titular expandido, como el logo. */
@Composable
private fun MarcaCafeTin() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Café y Tin" },
    ) {
        Text("Cafe & ", fontFamily = Script, fontSize = 52.sp, color = MaterialTheme.colorScheme.onBackground)
        Text("TIN", fontFamily = ArchivoExpandida, fontWeight = FontWeight.Black, fontSize = 44.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * Botón de vidrio: borde con brillo en diagonal y relleno blanco que se desvanece hacia abajo. Se
 * achica un poco al pulsarlo.
 */
@Composable
private fun BotonVidrio(texto: String, alPulsar: () -> Unit) {
    val interaccion = remember { MutableInteractionSource() }
    val pulsado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(if (pulsado) 0.97f else 1f, tween(200), label = "escala")
    val forma = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .graphicsLayer { scaleX = escala; scaleY = escala }
            .shadow(8.dp, forma, ambientColor = Paleta.tierra, spotColor = Paleta.tierra)
            .clip(forma)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.05f), Color.White.copy(alpha = 0.9f))))
            .padding(1.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.45f), Paleta.superficie.copy(alpha = 0.6f))))
            .clickable(interactionSource = interaccion, indication = null, role = Role.Button, onClick = alPulsar)
            .padding(horizontal = 40.dp, vertical = 14.dp),
    ) {
        Text(texto.uppercase(), style = MaterialTheme.typography.labelLarge, fontSize = 17.sp, color = Paleta.tierra)
    }
}

/** Tarjeta que sube desde abajo: papel con curvas de nivel, borde claro y esquinas muy redondeadas. */
@Composable
private fun TarjetaAcceso(modifier: Modifier, contenido: @Composable () -> Unit) {
    val forma = RoundedCornerShape(40.dp)
    val superficie = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(12.dp)
            .shadow(16.dp, forma, ambientColor = Paleta.tierra, spotColor = Paleta.tierra)
            .clip(forma)
            .background(Brush.verticalGradient(listOf(superficie, MaterialTheme.colorScheme.surfaceContainerHigh)))
            .topografia(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), lado = 260.dp)
            .border(1.dp, Color.White.copy(alpha = 0.8f), forma),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) { contenido() }
    }
}

@Composable
private fun Bienvenida(alCorreo: () -> Unit, alRegistrarse: () -> Unit, alRecuperar: () -> Unit) {
    Column {
        LogoCafetin(60.dp)
        Spacer(Modifier.height(16.dp))
        TituloPantalla("Empieza aquí", color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pide en tu cafetín, paga desde el celular\ny recoge sin hacer fila.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonAcceso(R.drawable.ic_correo, "Entrar con mi correo", oscuro = true, alPulsar = alCorreo)
            BotonAcceso(R.drawable.ic_usuario_mas, "Crear mi cuenta", alPulsar = alRegistrarse)
            BotonAcceso(R.drawable.ic_llave, "Olvidé mi contraseña", alPulsar = alRecuperar)
        }
    }
}

/** Opción de acceso: píldora oscura (la principal) o clara, con ícono y texto centrados. */
@Composable
private fun BotonAcceso(@DrawableRes icono: Int, texto: String, oscuro: Boolean = false, alPulsar: () -> Unit) {
    val esquema = MaterialTheme.colorScheme
    Surface(
        onClick = alPulsar,
        shape = CircleShape,
        color = if (oscuro) esquema.onSurface else esquema.surfaceContainerLowest,
        contentColor = if (oscuro) esquema.surface else esquema.onSurface,
        shadowElevation = if (oscuro) 0.dp else 2.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icono), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(texto, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}
