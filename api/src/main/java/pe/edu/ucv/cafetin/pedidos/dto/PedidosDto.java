package pe.edu.ucv.cafetin.pedidos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPago;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido;
import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;

/** DTOs del paquete Pedidos (RF-05 a RF-09). Parte del contrato: no los cambies sin avisar. */
public final class PedidosDto {

	private PedidosDto() {
	}

	// -------------------------------------------------------------- entrada

	@Schema(description = "Pedido armado en el carrito (RF-05)")
	public record CrearPedidoRequest(
			@NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", pattern = "^\\d{2}:\\d{2}$",
					description = "Hora de recojo elegida, formato HH:mm", example = "12:50") LocalTime horaRecojo,
			@Size(max = 250) @Schema(example = "Sin ají, por favor") String observacion,
			@NotEmpty @Size(max = 20) @Schema(description = "Productos del pedido, sin repetir producto")
			List<@Valid ItemPedidoRequest> items) {
	}

	@Schema(description = "Línea del carrito")
	public record ItemPedidoRequest(
			@NotNull @Schema(example = "7") Integer idProducto,
			@NotNull @Min(1) @Max(20) @Schema(example = "2") Integer cantidad) {
	}

	/**
	 * Pago del pedido (RF-06). EFECTIVO no se acepta en la v1 (decisión del 02/11/2026): la app solo
	 * ofrece YAPE, PLIN y TARJETA, todos por la pasarela simulada.
	 */
	@Schema(description = "Método de pago (RF-06). En la v1 solo YAPE, PLIN o TARJETA")
	public record PagarRequest(
			@NotNull @Schema(allowableValues = { "YAPE", "PLIN", "TARJETA" }, example = "YAPE") MetodoPago metodoPago) {

		@JsonIgnore
		@Schema(hidden = true)
		@AssertTrue(message = "EFECTIVO no está disponible: usa YAPE, PLIN o TARJETA")
		public boolean isMetodoPermitido() {
			return metodoPago != MetodoPago.EFECTIVO;
		}
	}

	// --------------------------------------------------------------- salida

	@Schema(description = "Pedido con su detalle, pago y recibo")
	public record PedidoResponse(
			@Schema(example = "2") Integer id,
			@Schema(example = "PED-0426") String codigoPedido,
			EstadoPedido estado,
			LocalDateTime fechaHora,
			@JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "12:50") LocalTime horaRecojo,
			@Schema(description = "Calculado por la base", example = "10.50") BigDecimal total,
			String observacion,
			List<LineaPedidoResponse> items,
			@Schema(description = "Nulo si aún no se intentó pagar") PagoResponse pago,
			@Schema(description = "Nulo hasta que el pago queda PAGADO") ReciboResponse recibo) {
	}

	@Schema(description = "Línea del pedido con el precio al momento de la compra")
	public record LineaPedidoResponse(
			@Schema(example = "2") Integer idProducto,
			@Schema(example = "Tamal criollo") String nombre,
			@Schema(example = "2") Integer cantidad,
			@Schema(example = "4.00") BigDecimal precioUnitario,
			@Schema(example = "8.00") BigDecimal subtotal) {
	}

	@Schema(description = "Pago del pedido")
	public record PagoResponse(
			@Schema(example = "10.50") BigDecimal monto,
			MetodoPago metodoPago,
			EstadoPago estado,
			LocalDateTime fecha,
			@Schema(description = "Código de operación de la pasarela", example = "PL-220931") String referencia) {
	}

	@Schema(description = "Recibo digital (RF-07): el estudiante muestra el código al recoger")
	public record ReciboResponse(
			@Schema(example = "DR-0426-3390") String codigoVerificacion,
			LocalDateTime fechaEmision,
			boolean verificado) {
	}

	@Schema(description = "Pedido en el historial (RF-09)")
	public record PedidoResumenResponse(
			Integer id,
			@Schema(example = "PED-0425") String codigoPedido,
			EstadoPedido estado,
			@Schema(description = "Nulo si nunca se intentó pagar") EstadoPago estadoPago,
			LocalDateTime fechaHora,
			@JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "10:20") LocalTime horaRecojo,
			BigDecimal total,
			@Schema(description = "Unidades en total", example = "2") int cantidadProductos) {
	}

}
