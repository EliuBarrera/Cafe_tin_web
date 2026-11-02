package pe.edu.ucv.cafetin.cuentas.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.FcmTokenRequest;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.NotificacionResponse;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.UsuarioResponse;

/** Datos y notificaciones del usuario autenticado (cualquier rol). Responsable: José Daniel Lizca. */
@RestController
@RequestMapping("/api/usuarios/me")
@Tag(name = "Cuentas")
public class UsuarioController {

	@GetMapping
	@Operation(summary = "Mi perfil", description = "Autenticado (cualquier rol). La app y el panel lo usan al abrir para saber el rol.")
	public UsuarioResponse perfil() {
		throw new ApiException.NoImplementado("RF-01");
	}

	@PutMapping("/fcm-token")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Registrar token FCM", description = "Autenticado · RF-08. La app lo envía después del login.")
	public void registrarFcmToken(@Valid @RequestBody FcmTokenRequest solicitud) {
		throw new ApiException.NoImplementado("RF-08");
	}

	@GetMapping("/notificaciones")
	@Operation(summary = "Mis notificaciones", description = "Autenticado · RF-08. Más recientes primero; respaldo si el push FCM se retrasa.")
	public List<NotificacionResponse> notificaciones(
			@Parameter(description = "true = solo las no leídas") @RequestParam(defaultValue = "false") boolean soloNoLeidas) {
		throw new ApiException.NoImplementado("RF-08");
	}

	@PatchMapping("/notificaciones/{id}/leida")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Marcar notificación como leída", description = "Autenticado · RF-08. Solo notificaciones propias.")
	public void marcarLeida(@PathVariable Integer id) {
		throw new ApiException.NoImplementado("RF-08");
	}

}
