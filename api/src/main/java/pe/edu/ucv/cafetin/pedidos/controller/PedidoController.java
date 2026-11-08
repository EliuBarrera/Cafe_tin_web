package pe.edu.ucv.cafetin.pedidos.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import pe.edu.ucv.cafetin.comun.dto.Pagina;
import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.ErrorRespuesta;
import pe.edu.ucv.cafetin.cuentas.seguridad.UsuarioActual;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.CrearPedidoRequest;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.PagarRequest;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.PedidoResponse;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.PedidoResumenResponse;
import pe.edu.ucv.cafetin.pedidos.service.PedidoService;

/**
 * Pedidos del estudiante autenticado (RF-05 a RF-09). Un estudiante solo ve y opera sus propios
 * pedidos: uno ajeno responde 404. Responsable: Eliú Barrera.
 */
@RestController
@RequestMapping("/api/pedidos")
@Tag(name = "Pedidos")
public class PedidoController {

	private final PedidoService servicio;
	private final UsuarioActual usuario;

	public PedidoController(PedidoService servicio, UsuarioActual usuario) {
		this.servicio = servicio;
		this.usuario = usuario;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Crear pedido", description = """
			ESTUDIANTE · RF-05. Copia el precio actual de cada producto, valida que haya stock y deja el
			pedido PENDIENTE sin pagar. El total lo calcula la base. No reserva stock: se descuenta al pagar.""")
	@ApiResponse(responseCode = "409", description = "Producto inactivo o sin stock suficiente (STOCK_INSUFICIENTE)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public PedidoResponse crear(@Valid @RequestBody CrearPedidoRequest solicitud) {
		return servicio.crear(usuario.id(), solicitud);
	}

	@PostMapping("/{id}/pago")
	@Operation(summary = "Pagar pedido", description = """
			ESTUDIANTE · RF-06, RF-07, RF-16, RF-20. Cobra con la pasarela simulada, pasa el pago a PAGADO
			(la base descuenta el stock) y genera el recibo. Si la pasarela rechaza, el pedido se conserva y
			se puede reintentar con el mismo u otro método. Debe responder en menos de 5 s (RNF-02).""")
	@ApiResponse(responseCode = "402", description = "La pasarela rechazó el cobro (PAGO_RECHAZADO); se puede reintentar",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	@ApiResponse(responseCode = "409", description = """
			Otro estudiante compró las últimas unidades (STOCK_INSUFICIENTE) o el pedido ya está pagado o
			cancelado (REGLA_NEGOCIO)""",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public PedidoResponse pagar(@PathVariable Integer id, @Valid @RequestBody PagarRequest solicitud) {
		throw new ApiException.NoImplementado("RF-06");
	}

	@PostMapping("/{id}/cancelar")
	@Operation(summary = "Cancelar pedido", description = """
			ESTUDIANTE · RF-05. Solo pedidos PENDIENTE sin pagar (decisión del 02/11/2026); un pedido pagado
			no se cancela desde la app.""")
	@ApiResponse(responseCode = "409", description = "El pedido ya está pagado o no está PENDIENTE (TRANSICION_INVALIDA)",
			content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
	public PedidoResponse cancelar(@PathVariable Integer id) {
		throw new ApiException.NoImplementado("RF-05");
	}

	@GetMapping("/{id}")
	@Operation(summary = "Ver mi pedido", description = """
			ESTUDIANTE (propio) · RF-07, RF-08. Estado, detalle, pago y recibo. La app lo consulta para
			refrescar el estado si el push no llegó.""")
	public PedidoResponse obtener(@PathVariable Integer id) {
		return servicio.obtener(usuario.id(), id);
	}

	@GetMapping("/mios")
	@Operation(summary = "Mi historial de pedidos", description = "ESTUDIANTE · RF-09. Más recientes primero.")
	public Pagina<PedidoResumenResponse> historial(
			@RequestParam(defaultValue = "0") @Min(0) int pagina,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
		return servicio.historial(usuario.id(), pagina, tamano);
	}

}
