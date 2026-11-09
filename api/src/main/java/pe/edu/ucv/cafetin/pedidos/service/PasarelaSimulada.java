package pe.edu.ucv.cafetin.pedidos.service;

import java.math.BigDecimal;
import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;

/**
 * Pasarela simulada: aprueba todo cobro y devuelve una referencia con prefijo por método
 * (YP-, PL-, TJ-). Los tests que necesitan un rechazo reemplazan este bean con un mock.
 */
@Component
public class PasarelaSimulada implements PasarelaPago {

	private final SecureRandom azar = new SecureRandom();

	@Override
	public ResultadoCobro cobrar(String codigoPedido, BigDecimal monto, MetodoPago metodo) {
		String prefijo = switch (metodo) {
			case YAPE -> "YP";
			case PLIN -> "PL";
			case TARJETA -> "TJ";
			case EFECTIVO -> throw new IllegalArgumentException("EFECTIVO no pasa por la pasarela");
		};
		return ResultadoCobro.aprobado("%s-%06d".formatted(prefijo, azar.nextInt(1_000_000)));
	}

}
