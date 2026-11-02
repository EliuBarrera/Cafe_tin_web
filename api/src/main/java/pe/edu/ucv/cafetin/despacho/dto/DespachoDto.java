package pe.edu.ucv.cafetin.despacho.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido;
import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;

/** DTOs del paquete Despacho (RF-10 a RF-14). Parte del contrato: no los cambies sin avisar. */
public final class DespachoDto {

	private DespachoDto() {
	}

	/**
	 * Pedido en el tablero de despacho. <b>No incluye el código de verificación del recibo</b>:
	 * el trabajador debe pedírselo al estudiante y escribirlo (RF-13).
	 */
	@Schema(description = "Pedido pagado en el tablero de despacho")
	public record PedidoDespachoResponse(
			@Schema(example = "2") Integer idPedido,
			@Schema(example = "PED-0426") String codigoPedido,
			EstadoPedido estado,
			@JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "12:50") LocalTime horaRecojo,
			@Schema(example = "10.50") BigDecimal total,
			@Schema(example = "María Quiroz Vásquez") String estudiante,
			MetodoPago metodoPago,
			List<LineaDespachoResponse> items,
			@Schema(description = "Trabajador que lo confirmó; nulo si aún está PENDIENTE", example = "Carmen Díaz Morales")
			String trabajador,
			@Schema(description = "true cuando el código del recibo ya se verificó (habilita entregar)") boolean reciboVerificado) {
	}

	@Schema(description = "Producto a preparar")
	public record LineaDespachoResponse(
			@Schema(example = "Tamal criollo") String producto,
			@Schema(example = "2") Integer cantidad) {
	}

	@Schema(description = "Código que muestra el estudiante (RF-13)")
	public record VerificarReciboRequest(
			@NotBlank @Pattern(regexp = "^DR-\\d+-\\d{4}$", message = "formato DR-0000-0000")
			@Schema(example = "DR-0426-3390") String codigoVerificacion) {
	}

}
