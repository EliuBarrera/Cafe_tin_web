package pe.edu.ucv.cafetin.app.ui.cuentas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.ucv.cafetin.app.datos.Cuentas
import pe.edu.ucv.cafetin.app.datos.ErrorApi
import pe.edu.ucv.cafetin.app.datos.RegistroEstudianteRequest
import pe.edu.ucv.cafetin.app.datos.Sesion

// ------------------------------------------------------------------ Login (RF-01)

data class LoginEstado(
    val correo: String = "",
    val contrasena: String = "",
    val errorCorreo: String? = null,
    val errorContrasena: String? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val sesion: Sesion? = null,
)

class LoginViewModel(private val cuentas: Cuentas) : ViewModel() {

    private val _estado = MutableStateFlow(LoginEstado())
    val estado: StateFlow<LoginEstado> = _estado.asStateFlow()

    fun cambiarCorreo(valor: String) = _estado.update { it.copy(correo = valor, errorCorreo = null, error = null) }
    fun cambiarContrasena(valor: String) = _estado.update { it.copy(contrasena = valor, errorContrasena = null, error = null) }

    fun entrar() {
        val e = _estado.value
        val errorCorreo = Validaciones.correo(e.correo)
        val errorContrasena = if (e.contrasena.isEmpty()) "Escribe tu contraseña" else null
        if (errorCorreo != null || errorContrasena != null) {
            _estado.update { it.copy(errorCorreo = errorCorreo, errorContrasena = errorContrasena) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val sesion = cuentas.login(e.correo, e.contrasena)
                _estado.update { it.copy(cargando = false, sesion = sesion) }
            } catch (ex: ErrorApi) {
                val mensaje = if (ex.codigo == "NO_AUTENTICADO") "Correo o contraseña incorrectos" else ex.message
                _estado.update { it.copy(cargando = false, error = mensaje) }
            }
        }
    }
}

// ------------------------------------------------------------------ Registro (RF-01)

data class RegistroEstado(
    val nombre: String = "",
    val apellidos: String = "",
    val correo: String = "",
    val contrasena: String = "",
    val telefono: String = "",
    val codigo: String = "",
    val carrera: String = "",
    val ciclo: String = "",
    /** Mensaje por campo, con la clave del campo del contrato (nombre, correo, ciclo…). */
    val errores: Map<String, String> = emptyMap(),
    val cargando: Boolean = false,
    val error: String? = null,
    val sesion: Sesion? = null,
)

class RegistroViewModel(private val cuentas: Cuentas) : ViewModel() {

    private val _estado = MutableStateFlow(RegistroEstado())
    val estado: StateFlow<RegistroEstado> = _estado.asStateFlow()

    /** Cambia un campo por su nombre en el contrato y limpia su error. */
    fun cambiar(campo: String, valor: String) = _estado.update {
        val sinError = it.copy(errores = it.errores - campo, error = null)
        when (campo) {
            "nombre" -> sinError.copy(nombre = valor)
            "apellidos" -> sinError.copy(apellidos = valor)
            "correo" -> sinError.copy(correo = valor)
            "contrasena" -> sinError.copy(contrasena = valor)
            "telefono" -> sinError.copy(telefono = valor.filter(Char::isDigit).take(9))
            "codigo" -> sinError.copy(codigo = valor)
            "carrera" -> sinError.copy(carrera = valor)
            "ciclo" -> sinError.copy(ciclo = valor.filter(Char::isDigit).take(2))
            else -> sinError
        }
    }

    fun registrar() {
        val e = _estado.value
        val errores = buildMap {
            Validaciones.nombre(e.nombre)?.let { put("nombre", it) }
            Validaciones.obligatorio(e.apellidos, 80, "Escribe tus apellidos")?.let { put("apellidos", it) }
            Validaciones.correo(e.correo)?.let { put("correo", it) }
            Validaciones.contrasena(e.contrasena)?.let { put("contrasena", it) }
            Validaciones.telefono(e.telefono)?.let { put("telefono", it) }
            Validaciones.obligatorio(e.codigo, 15, "Escribe tu código de estudiante")?.let { put("codigo", it) }
            Validaciones.obligatorio(e.carrera, 80, "Escribe tu carrera")?.let { put("carrera", it) }
            Validaciones.ciclo(e.ciclo)?.let { put("ciclo", it) }
        }
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                val sesion = cuentas.registrar(
                    RegistroEstudianteRequest(
                        nombre = e.nombre.trim(),
                        apellidos = e.apellidos.trim(),
                        correo = e.correo.trim(),
                        contrasena = e.contrasena,
                        telefono = e.telefono.trim().ifEmpty { null },
                        codigo = e.codigo.trim(),
                        carrera = e.carrera.trim(),
                        ciclo = e.ciclo.trim().toInt(),
                    ),
                )
                _estado.update { it.copy(cargando = false, sesion = sesion) }
            } catch (ex: ErrorApi) {
                // 400 VALIDACION trae el campo en detalles: se muestra junto a él.
                val porCampo = ex.detalles.mapNotNull { d -> d.campo?.let { c -> c to (d.mensaje ?: "Revisa este dato") } }.toMap()
                _estado.update { it.copy(cargando = false, errores = porCampo, error = ex.message) }
            }
        }
    }
}

// ------------------------------------------------------------------ Recuperar contraseña (RF-03)

data class RecuperarEstado(
    val correo: String = "",
    val errorCorreo: String? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val enviado: Boolean = false,
)

class RecuperarViewModel(private val cuentas: Cuentas) : ViewModel() {

    private val _estado = MutableStateFlow(RecuperarEstado())
    val estado: StateFlow<RecuperarEstado> = _estado.asStateFlow()

    fun cambiarCorreo(valor: String) = _estado.update { it.copy(correo = valor, errorCorreo = null, error = null) }

    fun enviar() {
        val error = Validaciones.correo(_estado.value.correo)
        if (error != null) {
            _estado.update { it.copy(errorCorreo = error) }
            return
        }
        _estado.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            try {
                cuentas.recuperar(_estado.value.correo)
                _estado.update { it.copy(cargando = false, enviado = true) }
            } catch (ex: ErrorApi) {
                _estado.update { it.copy(cargando = false, error = ex.message) }
            }
        }
    }
}
