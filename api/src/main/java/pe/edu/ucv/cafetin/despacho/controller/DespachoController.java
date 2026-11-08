package pe.edu.ucv.cafetin.despacho.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pe.edu.ucv.cafetin.comun.error.ErrorRespuesta;
import pe.edu.ucv.cafetin.cuentas.seguridad.UsuarioActual;
import pe.edu.ucv.cafetin.despacho.dto.DespachoDto.PedidoDespachoResponse;
import pe.edu.ucv.cafetin.despacho.dto.DespachoDto.VerificarReciboRequest;
import pe.edu.ucv.cafetin.despacho.service.DespachoService;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido;

/**
 * Tablero de despacho (RF-10 a RF-14). Las acciones son POST porque son transiciones de estado, no
 * ediciones; todas pasan por la máquina de estados (cualquier otra transición → 409
 * TRANSICION_INVALIDA). Responsable: Eliú Barrera (API) · Maria Elizabeth Ruiz (panel).
 */
@RestController
@RequestMapping("/api/despacho/pedidos")
@Tag(name = "Despacho", description = "Pedidos pagados y su ciclo hasta la entrega (TRABAJADOR y DUENO)")
public class DespachoController {

	private final DespachoService servicio;
	private final UsuarioActual usuario;

	public DespachoController(DespachoService servicio, UsuarioActual usuario) {
		this.servicio = servicio;
		this.usuario = usuario;
	}

	@GetMapping
	@Operation(summary = "Pedidos en curso", description = """
			TRABAJADOR, DUENO · RF-10. Solo pedidos PAGADOS en PENDIENTE, CONFIRMADO, EN_PREPARACION o LISTO,
			ordenados por hora de recojo. El panel consulta cada 5 s.""")
	public List<PedidoDespachoResponse> listar(
			@Parameter(description = "Filtrar por un estado; vacío = todos los en curso")
			@RequestParam(required = false) EstadoPedido estado) {
		return servicio.listar(estado);
	}

	@PostMapping("/{id}/confirmar")
	@Operation(summary = "Confirmar pedido", description = "TRABAJADOR, DUENO · RF-11. PENDIENTE → CONFIRMADO y asigna al usuario autenticado. Notifica al estudiante.")
	@ApiResponse(responseCode = "409", description = "Transición no permitida (TRANSICION_INVALIDA)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public PedidoDespachoResponse confirmar(@PathVariable Integer id) {
		return servicio.confirmar(usuario.id(), id);
	}

	@PostMapping("/{id}/preparar")
	@Operation(summary = "Empezar a preparar", description = "TRABAJADOR, DUENO · RF-11. CONFIRMADO → EN_PREPARACION.")
	@ApiResponse(responseCode = "409", description = "Transición no permitida (TRANSICION_INVALIDA)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public PedidoDespachoResponse preparar(@PathVariable Integer id) {
		return servicio.preparar(id);
	}

	@PostMapping("/{id}/listo")
	@Operation(summary = "Marcar listo para recoger", description = "TRABAJADOR, DUENO · RF-12. EN_PREPARACION → LISTO. Notifica al estudiante (M4).")
	@ApiResponse(responseCode = "409", description = "Transición no permitida (TRANSICION_INVALIDA)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public PedidoDespachoResponse listo(@PathVariable Integer id) {
		return servicio.listo(id);
	}

	@PostMapping("/{id}/verificar")
	@Operation(summary = "Verificar recibo", description = "TRABAJADOR, DUENO · RF-13. Compara el código que muestra el estudiante con el del recibo; el pedido debe estar LISTO.")
	@ApiResponse(responseCode = "409", description = "El código no coincide o el pedido no está LISTO (REGLA_NEGOCIO)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public PedidoDespachoResponse verificar(@PathVariable Integer id, @Valid @RequestBody VerificarReciboRequest solicitud) {
		return servicio.verificar(id, solicitud.codigoVerificacion());
	}

	@PostMapping("/{id}/entregar")
	@Operation(summary = "Entregar pedido", description = "TRABAJADOR, DUENO · RF-14. LISTO → ENTREGADO; exige el recibo verificado. Notifica al estudiante.")
	@ApiResponse(responseCode = "409", description = "Recibo sin verificar (REGLA_NEGOCIO) o transición no permitida (TRANSICION_INVALIDA)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public PedidoDespachoResponse entregar(@PathVariable Integer id) {
		return servicio.entregar(id);
	}

}
