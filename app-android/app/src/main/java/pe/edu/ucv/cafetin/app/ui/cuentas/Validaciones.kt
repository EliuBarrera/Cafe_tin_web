package pe.edu.ucv.cafetin.app.ui.cuentas

/**
 * Mismas reglas que la API (CuentasDto y ValidacionCuenta) para avisar antes de enviar. La API valida
 * de nuevo: esto solo evita un viaje y da el mensaje junto al campo.
 */
object Validaciones {

    private val CORREO = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val TELEFONO = Regex("^[0-9]{9}$")
    const val CONTRASENA_MINIMA = 8

    /** BCrypt admite 72 bytes, no 72 caracteres: una "ñ" ocupa 2. */
    const val CONTRASENA_MAXIMA_BYTES = 72

    fun correo(valor: String): String? = when {
        valor.isBlank() -> "Escribe tu correo"
        valor.trim().length > 120 || !CORREO.matches(valor.trim()) -> "Escribe un correo válido"
        else -> null
    }

    fun contrasena(valor: String): String? = when {
        valor.length < CONTRASENA_MINIMA -> "Debe tener al menos $CONTRASENA_MINIMA caracteres"
        valor.toByteArray(Charsets.UTF_8).size > CONTRASENA_MAXIMA_BYTES -> "Es demasiado larga"
        else -> null
    }

    fun nombre(valor: String): String? = when {
        valor.trim().length < 2 -> "Escribe tu nombre"
        valor.trim().length > 60 -> "Máximo 60 caracteres"
        else -> null
    }

    fun obligatorio(valor: String, maximo: Int, faltante: String): String? = when {
        valor.isBlank() -> faltante
        valor.trim().length > maximo -> "Máximo $maximo caracteres"
        else -> null
    }

    /** Opcional: vacío es válido. */
    fun telefono(valor: String): String? =
        if (valor.isBlank() || TELEFONO.matches(valor.trim())) null else "Debe tener 9 dígitos"

    fun ciclo(valor: String): String? =
        if (valor.trim().toIntOrNull() in 1..12) null else "Entre 1 y 12"
}
