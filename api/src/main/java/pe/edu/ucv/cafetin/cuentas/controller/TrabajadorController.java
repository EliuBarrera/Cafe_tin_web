package pe.edu.ucv.cafetin.cuentas.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.ucv.cafetin.comun.dto.EstadoRequest;
import pe.edu.ucv.cafetin.comun.error.ErrorRespuesta;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.TrabajadorRequest;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.TrabajadorResponse;
import pe.edu.ucv.cafetin.cuentas.service.TrabajadorService;

/** Gestión de trabajadores por el dueño (RF-02). Responsable: José Daniel Lizca. */
@RestController
@RequestMapping("/api/trabajadores")
@Tag(name = "Cuentas")
public class TrabajadorController {

	private final TrabajadorService trabajadores;

	public TrabajadorController(TrabajadorService trabajadores) {
		this.trabajadores = trabajadores;
	}

	@GetMapping
	@Operation(summary = "Listar trabajadores", description = "DUENO · RF-02")
	public List<TrabajadorResponse> listar() {
		return trabajadores.listar();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Registrar trabajador", description = "DUENO · RF-02. El dueño inicial viene de los datos de prueba.")
	@ApiResponse(responseCode = "409", description = "Correo o código de empleado ya registrado (CONFLICTO)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public TrabajadorResponse registrar(@Valid @RequestBody TrabajadorRequest solicitud) {
		return trabajadores.registrar(solicitud);
	}

	@PatchMapping("/{id}/estado")
	@Operation(summary = "Activar o desactivar trabajador", description = "DUENO · RF-02. Un trabajador inactivo no puede iniciar sesión.")
	public TrabajadorResponse cambiarEstado(@PathVariable Integer id, @Valid @RequestBody EstadoRequest solicitud) {
		return trabajadores.cambiarEstado(id, solicitud.activo());
	}

}
