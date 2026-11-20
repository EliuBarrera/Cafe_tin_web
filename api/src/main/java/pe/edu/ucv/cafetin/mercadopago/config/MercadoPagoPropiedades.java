package pe.edu.ucv.cafetin.mercadopago.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code cafetin.mercadopago.*}: credenciales de la aplicación de Mercado Pago (la de la plataforma,
 * no la de cada cafetín) y las URL públicas que Mercado Pago usa para volver.
 *
 * @param clientId         {@code MP_CLIENT_ID} (App ID)
 * @param clientSecret     {@code MP_CLIENT_SECRET}
 * @param webhookSecret    {@code MP_WEBHOOK_SECRET}: firma de las notificaciones ({@code x-signature})
 * @param urlApi           API de Mercado Pago
 * @param urlAutorizacion  página de autorización OAuth
 * @param urlPublicaApi    URL pública de esta API (Render o un túnel): retorno OAuth, webhook y vuelta del checkout
 * @param urlPanel         panel web, al que vuelve el dueño tras conectar su cuenta
 * @param urlRetornoApp    enlace profundo de la app al terminar de pagar; se le agrega el id del pedido
 */
@ConfigurationProperties("cafetin.mercadopago")
public record MercadoPagoPropiedades(String clientId, String clientSecret, String webhookSecret, String urlApi,
		String urlAutorizacion, String urlPublicaApi, String urlPanel, String urlRetornoApp) {

	public MercadoPagoPropiedades {
		urlApi = sinBarraFinal(urlApi == null ? "https://api.mercadopago.com" : urlApi);
		urlAutorizacion = urlAutorizacion == null || urlAutorizacion.isBlank()
				? "https://auth.mercadopago.com/authorization" : urlAutorizacion;
		urlPublicaApi = sinBarraFinal(urlPublicaApi == null ? "http://localhost:8080" : urlPublicaApi);
		urlPanel = sinBarraFinal(urlPanel == null ? "http://localhost:5173" : urlPanel);
		urlRetornoApp = urlRetornoApp == null ? "cafetin://pago/" : urlRetornoApp;
	}

	/** Debe coincidir con la Redirect URL registrada en la aplicación de Mercado Pago. */
	public String urlRetornoOauth() {
		return urlPublicaApi + "/api/mercadopago/oauth/retorno";
	}

	public boolean configurada() {
		return presente(clientId) && presente(clientSecret);
	}

	private static boolean presente(String valor) {
		return valor != null && !valor.isBlank();
	}

	private static String sinBarraFinal(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}
