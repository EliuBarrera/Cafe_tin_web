package pe.edu.ucv.cafetin.pedidos.service;

import java.math.BigDecimal;

import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;

/**
 * Pasarela de cobro (RF-06). En la v1 es simulada ({@link PasarelaSimulada}, decisión del 02/11/2026);
 * una real solo tendría que implementar esta interfaz. Nunca recibe ni guarda datos de tarjeta.
 */
public interface PasarelaPago {

	ResultadoCobro cobrar(String codigoPedido, BigDecimal monto, MetodoPago metodo);

	/** Resultado del cobro: si se aprobó, la referencia de la operación; si no, el motivo. */
	record ResultadoCobro(boolean aprobado, String referencia, String motivo) {

		public static ResultadoCobro aprobado(String referencia) {
			return new ResultadoCobro(true, referencia, null);
		}

		public static ResultadoCobro rechazado(String motivo) {
			return new ResultadoCobro(false, null, motivo);
		}
	}

}
