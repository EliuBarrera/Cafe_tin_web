package pe.edu.ucv.cafetin.mercadopago.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code cafetin.pagos.*}: qué pasarela cobra, la comisión de la plataforma y la moneda.
 *
 * @param pasarela {@code simulada} (por defecto: tests y desarrollo sin credenciales) o {@code mercadopago}
 * @param comision fracción del total que se queda la plataforma ({@code 0.05} = 5 %); 0 por defecto
 * @param moneda   código ISO que se envía a Mercado Pago: {@code PEN} en Perú, {@code COP} con la
 *                 aplicación de prueba colombiana (decisión del 19/11/2026)
 */
@ConfigurationProperties("cafetin.pagos")
public record PagosPropiedades(Pasarela pasarela, BigDecimal comision, String moneda) {

	public enum Pasarela {
		SIMULADA, MERCADOPAGO
	}

	public PagosPropiedades {
		pasarela = pasarela == null ? Pasarela.SIMULADA : pasarela;
		comision = comision == null ? BigDecimal.ZERO : comision;
		moneda = moneda == null || moneda.isBlank() ? "PEN" : moneda.strip().toUpperCase();
		if (comision.signum() < 0 || comision.compareTo(BigDecimal.ONE) >= 0) {
			throw new IllegalStateException("CAFETIN_PAGOS_COMISION debe estar entre 0 y 1 (0.05 = 5 %)");
		}
	}

	public boolean conMercadoPago() {
		return pasarela == Pasarela.MERCADOPAGO;
	}

}
