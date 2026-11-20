package pe.edu.ucv.cafetin.mercadopago.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import pe.edu.ucv.cafetin.mercadopago.service.CifradorTokens;

/**
 * Con {@code cafetin.pagos.pasarela=mercadopago} la API no arranca si falta una credencial: es mejor
 * enterarse al desplegar que cuando el primer estudiante intenta pagar.
 */
@Configuration
@EnableConfigurationProperties({ PagosPropiedades.class, MercadoPagoPropiedades.class })
public class MercadoPagoConfig {

	public MercadoPagoConfig(PagosPropiedades pagos, MercadoPagoPropiedades mp, CifradorTokens cifrador) {
		if (!pagos.conMercadoPago()) {
			return;
		}
		if (!mp.configurada()) {
			throw new IllegalStateException(
					"Con CAFETIN_PAGOS_PASARELA=mercadopago faltan MP_CLIENT_ID o MP_CLIENT_SECRET (ver docs/mercadopago.md)");
		}
		if (mp.webhookSecret() == null || mp.webhookSecret().isBlank()) {
			throw new IllegalStateException(
					"Con CAFETIN_PAGOS_PASARELA=mercadopago falta MP_WEBHOOK_SECRET (ver docs/mercadopago.md)");
		}
		cifrador.exigirLlave();
	}

}
