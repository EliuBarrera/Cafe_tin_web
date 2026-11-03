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
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.FcmTokenRequest;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.NotificacionResponse;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.UsuarioResponse;
import pe.edu.ucv.cafetin.cuentas.seguridad.UsuarioActual;
import pe.edu.ucv.cafetin.cuentas.service.NotificacionService;
import pe.edu.ucv.cafetin.cuentas.service.UsuarioService;

/** Datos y notificaciones del usuario autenticado (cualquier rol). Responsable: José Daniel Lizca. */
@RestController
@RequestMapping("/api/usuarios/me")
@Tag(name = "Cuentas")
public class UsuarioController {

	private final UsuarioService usuarios;
	private final NotificacionService notificaciones;
	private final UsuarioActual usuarioActual;

	public UsuarioController(UsuarioService usuarios, NotificacionService notificaciones, UsuarioActual usuarioActual) {
		this.usuarios = usuarios;
		this.notificaciones = notificaciones;
		this.usuarioActual = usuarioActual;
	}

	@GetMapping
	@Operation(summary = "Mi perfil", description = "Autenticado (cualquier rol). La app y el panel lo usan al abrir para saber el rol.")
	public UsuarioResponse perfil() {
		return usuarios.perfil(usuarioActual.id());
	}

	@PutMapping("/fcm-token")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Registrar token FCM", description = "Autenticado · RF-08. La app lo envía después del login.")
	public void registrarFcmToken(@Valid @RequestBody FcmTokenRequest solicitud) {
		notificaciones.registrarFcmToken(usuarioActual.id(), solicitud.token());
	}

	@GetMapping("/notificaciones")
	@Operation(summary = "Mis notificaciones", description = "Autenticado · RF-08. Más recientes primero; respaldo si el push FCM se retrasa.")
	public List<NotificacionResponse> notificaciones(
			@Parameter(description = "true = solo las no leídas") @RequestParam(defaultValue = "false") boolean soloNoLeidas) {
		return notificaciones.listar(usuarioActual.id(), soloNoLeidas);
	}

	@PatchMapping("/notificaciones/{id}/leida")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Marcar notificación como leída", description = "Autenticado · RF-08. Solo notificaciones propias.")
	public void marcarLeida(@PathVariable Integer id) {
		notificaciones.marcarLeida(id, usuarioActual.id());
	}

}
