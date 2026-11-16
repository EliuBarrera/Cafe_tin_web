package pe.edu.ucv.cafetin.app.ui.cuentas

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.ucv.cafetin.app.datos.Sesion

@Composable
fun PantallaLogin(vm: LoginViewModel, alEntrar: (Sesion) -> Unit, alRegistrarse: () -> Unit, alRecuperar: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(e.sesion) { e.sesion?.let(alEntrar) }

    MarcoCuenta("Iniciar sesión") {
        CampoTexto("Correo", e.correo, vm::cambiarCorreo, e.errorCorreo, KeyboardType.Email)
        CampoTexto("Contraseña", e.contrasena, vm::cambiarContrasena, e.errorContrasena, KeyboardType.Password, secreto = true, ultimo = true)
        MensajeError(e.error)
        BotonPrincipal(if (e.cargando) "Entrando…" else "Entrar", e.cargando, vm::entrar)
        TextButton(onClick = alRecuperar) { Text("¿Olvidaste tu contraseña?") }
        Row {
            Text("¿Primera vez?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = alRegistrarse) { Text("Crea tu cuenta") }
        }
    }
}

@Composable
fun PantallaRegistro(vm: RegistroViewModel, alEntrar: (Sesion) -> Unit, alVolver: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(e.sesion) { e.sesion?.let(alEntrar) }
    val error = e.errores

    MarcoCuenta("Crear cuenta") {
        CampoTexto("Nombre", e.nombre, { vm.cambiar("nombre", it) }, error["nombre"])
        CampoTexto("Apellidos", e.apellidos, { vm.cambiar("apellidos", it) }, error["apellidos"])
        CampoTexto("Correo", e.correo, { vm.cambiar("correo", it) }, error["correo"], KeyboardType.Email)
        CampoTexto("Contraseña", e.contrasena, { vm.cambiar("contrasena", it) }, error["contrasena"], KeyboardType.Password, secreto = true)
        CampoTexto("Teléfono (opcional)", e.telefono, { vm.cambiar("telefono", it) }, error["telefono"], KeyboardType.Phone)
        CampoTexto("Código de estudiante", e.codigo, { vm.cambiar("codigo", it) }, error["codigo"])
        CampoTexto("Carrera", e.carrera, { vm.cambiar("carrera", it) }, error["carrera"])
        CampoTexto("Ciclo", e.ciclo, { vm.cambiar("ciclo", it) }, error["ciclo"], KeyboardType.Number, ultimo = true)
        MensajeError(e.error)
        BotonPrincipal(if (e.cargando) "Creando cuenta…" else "Crear cuenta", e.cargando, vm::registrar)
        TextButton(onClick = alVolver) { Text("Ya tengo cuenta") }
    }
}

@Composable
fun PantallaRecuperar(vm: RecuperarViewModel, alVolver: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()

    MarcoCuenta("Recuperar contraseña") {
        if (e.enviado) {
            // Mismo texto exista o no la cuenta: la API no revela qué correos están registrados.
            Text("Si ${e.correo.trim()} tiene una cuenta, te llegará un correo con un enlace para crear una contraseña nueva. Vence en 30 minutos.")
        } else {
            Text("Te enviaremos un enlace a tu correo para crear una contraseña nueva.")
            CampoTexto("Correo", e.correo, vm::cambiarCorreo, e.errorCorreo, KeyboardType.Email, ultimo = true)
            MensajeError(e.error)
            BotonPrincipal(if (e.cargando) "Enviando…" else "Enviar enlace", e.cargando, vm::enviar)
        }
        TextButton(onClick = alVolver) { Text("Volver a iniciar sesión") }
    }
}
