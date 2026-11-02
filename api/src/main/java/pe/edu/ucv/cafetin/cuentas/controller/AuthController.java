package pe.edu.ucv.cafetin.cuentas.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.ErrorRespuesta;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.LoginRequest;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.RecuperarRequest;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.RegistroEstudianteRequest;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.RestablecerRequest;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.TokenResponse;

/** Rutas públicas de autenticación (RF-01 a RF-03). Responsable: José Daniel Lizca. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Cuentas", description = "Registro, inicio de sesión, recuperación de contraseña y perfil")
@SecurityRequirements // públicas: sin JWT
public class AuthController {

	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registrar estudiante", description = "Público · RF-01. Devuelve el token para entrar directamente.")
	@ApiResponse(responseCode = "409", description = "Correo o código de estudiante ya registrado (CONFLICTO)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public TokenResponse registrar(@Valid @RequestBody RegistroEstudianteRequest solicitud) {
		throw new ApiException.NoImplementado("RF-01");
	}

	@PostMapping("/login")
	@Operation(summary = "Iniciar sesión", description = "Público · RF-01, RF-02. Sirve para los tres roles.")
	@ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos (NO_AUTENTICADO)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public TokenResponse login(@Valid @RequestBody LoginRequest solicitud) {
		throw new ApiException.NoImplementado("RF-01");
	}

	@PostMapping("/recuperar")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Pedir enlace de recuperación", description = """
			Público · RF-03. Responde 202 exista o no el correo (no revela qué cuentas existen).
			En el perfil dev el enlace se escribe en el log.""")
	public void recuperar(@Valid @RequestBody RecuperarRequest solicitud) {
		throw new ApiException.NoImplementado("RF-03");
	}

	@PostMapping("/restablecer")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Restablecer contraseña", description = "Público · RF-03. El token es de un solo uso y expira.")
	@ApiResponse(responseCode = "409", description = "Token inválido o expirado (REGLA_NEGOCIO)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public void restablecer(@Valid @RequestBody RestablecerRequest solicitud) {
		throw new ApiException.NoImplementado("RF-03");
	}

}
