package pe.edu.ucv.cafetin.app.datos

import kotlinx.serialization.Serializable

// Copias de los DTOs de docs/openapi.yaml (paquete Cuentas). Los nombres de campo son los del contrato.

@Serializable
data class LoginRequest(val correo: String, val contrasena: String)

@Serializable
data class RegistroEstudianteRequest(
    val nombre: String,
    val apellidos: String,
    val correo: String,
    val contrasena: String,
    val telefono: String? = null,
    val codigo: String,
    val carrera: String,
    val ciclo: Int,
)

@Serializable
data class RecuperarRequest(val correo: String)

@Serializable
data class FcmTokenRequest(val token: String)

@Serializable
data class UsuarioResponse(
    val id: Int,
    val nombre: String,
    val apellidos: String,
    val correo: String,
    val rol: String,
)

@Serializable
data class TokenResponse(val token: String, val expiraEn: Long, val usuario: UsuarioResponse)

@Serializable
data class CampoError(val campo: String? = null, val mensaje: String? = null)

/** Formato único de error de la API: {codigo, mensaje, detalles}. */
@Serializable
data class ErrorRespuesta(
    val codigo: String,
    val mensaje: String,
    val detalles: List<CampoError> = emptyList(),
)
