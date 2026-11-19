package pe.edu.ucv.cafetin.mercadopago.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/** DTOs de la conexión con Mercado Pago. Parte del contrato: no los cambies sin avisar. */
public final class MercadoPagoDto {

	private MercadoPagoDto() {
	}

	@Schema(description = "Cuenta de Mercado Pago del cafetín (nunca incluye tokens)")
	public record EstadoConexionResponse(
			@Schema(description = "Pasarela con la que cobra la API", allowableValues = { "SIMULADA", "MERCADOPAGO" })
			String pasarela,
			@Schema(description = "Si el cafetín conectó su cuenta de Mercado Pago") boolean conectado,
			@Schema(description = "Id del usuario vendedor en Mercado Pago; nulo si no está conectado", example = "3862056228")
			Long idUsuarioMp,
			@Schema(description = "Cuándo se conectó; nulo si no está conectado") LocalDateTime conectadoEn) {
	}

	@Schema(description = "Página de Mercado Pago donde el dueño autoriza al cafetín a cobrar")
	public record UrlAutorizacionResponse(
			@Schema(example = "https://auth.mercadopago.com/authorization?client_id=…&state=…") String urlAutorizacion) {
	}

}
