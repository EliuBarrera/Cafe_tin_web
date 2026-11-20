package pe.edu.ucv.cafetin.mercadopago.service;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import pe.edu.ucv.cafetin.mercadopago.config.MercadoPagoPropiedades;

/**
 * Llamadas HTTP a la API de Mercado Pago, sin el SDK (no suma dependencias; decisión del 19/11/2026).
 * Las operaciones de cobro usan el token del cafetín: así el dinero llega a su cuenta. Solo las de
 * OAuth usan las credenciales de la aplicación de la plataforma.
 */
@Component
public class ClienteMercadoPago {

	private static final Duration ESPERA = Duration.ofSeconds(10);

	private final RestClient http;
	private final MercadoPagoPropiedades mp;

	@Autowired
	public ClienteMercadoPago(MercadoPagoPropiedades mp) {
		this(RestClient.builder().requestFactory(fabrica()), mp);
	}

	/** Para los tests: un {@code RestClient.Builder} enlazado a {@code MockRestServiceServer}. */
	public ClienteMercadoPago(RestClient.Builder builder, MercadoPagoPropiedades mp) {
		this.http = builder.baseUrl(mp.urlApi()).build();
		this.mp = mp;
	}

	private static JdkClientHttpRequestFactory fabrica() {
		JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(ESPERA).build());
		fabrica.setReadTimeout(ESPERA);
		return fabrica;
	}

	// ------------------------------------------------------------------ OAuth

	/** Canjea el {@code code} que Mercado Pago entrega al volver de la autorización del cafetín. */
	public Tokens canjearCodigo(String codigo) {
		return pedirTokens(Map.of("grant_type", "authorization_code", "code", codigo,
				"redirect_uri", mp.urlRetornoOauth()));
	}

	public Tokens renovar(String refreshToken) {
		return pedirTokens(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken));
	}

	private Tokens pedirTokens(Map<String, String> datos) {
		Map<String, String> cuerpo = new LinkedHashMap<>(datos);
		cuerpo.put("client_id", mp.clientId());
		cuerpo.put("client_secret", mp.clientSecret());
		Tokens tokens = http.post().uri("/oauth/token")
				.contentType(MediaType.APPLICATION_JSON)
				.body(cuerpo)
				.retrieve()
				.body(Tokens.class);
		if (tokens == null || tokens.accessToken() == null || tokens.userId() == null) {
			throw new IllegalStateException("Mercado Pago no devolvió un token de acceso");
		}
		return tokens;
	}

	// ------------------------------------------------------------------ cobro

	public PreferenciaCreada crearPreferencia(String token, Map<String, Object> preferencia) {
		PreferenciaCreada creada = http.post().uri("/checkout/preferences")
				.headers(h -> h.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.body(preferencia)
				.retrieve()
				.body(PreferenciaCreada.class);
		if (creada == null || creada.id() == null || creada.initPoint() == null) {
			throw new IllegalStateException("Mercado Pago no devolvió la preferencia de pago");
		}
		return creada;
	}

	public PagoMp consultarPago(String token, long idPago) {
		return http.get().uri("/v1/payments/{id}", idPago)
				.headers(h -> h.setBearerAuth(token))
				.retrieve()
				.body(PagoMp.class);
	}

	/** Pagos de un {@code external_reference} (el id de nuestra fila de pago), el más reciente primero. */
	public List<PagoMp> buscarPagos(String token, String referenciaExterna) {
		Busqueda busqueda = http.get()
				.uri(u -> u.path("/v1/payments/search")
						.queryParam("external_reference", referenciaExterna)
						.queryParam("sort", "date_created")
						.queryParam("criteria", "desc")
						.build())
				.headers(h -> h.setBearerAuth(token))
				.retrieve()
				.body(Busqueda.class);
		return busqueda == null || busqueda.results() == null ? List.of() : busqueda.results();
	}

	/** Reembolso total. La clave de idempotencia evita un doble reembolso si se reintenta. */
	public void reembolsar(String token, long idPago) {
		http.post().uri("/v1/payments/{id}/refunds", idPago)
				.headers(h -> {
					h.setBearerAuth(token);
					h.set("X-Idempotency-Key", "reembolso-" + idPago);
				})
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of())
				.retrieve()
				.toBodilessEntity();
	}

	// ------------------------------------------------------------------ respuestas

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Tokens(
			@JsonProperty("access_token") String accessToken,
			@JsonProperty("refresh_token") String refreshToken,
			@JsonProperty("user_id") Long userId,
			@JsonProperty("expires_in") Long expiraEnSegundos) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PreferenciaCreada(
			String id,
			@JsonProperty("init_point") String initPoint) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PagoMp(
			Long id,
			String status,
			@JsonProperty("status_detail") String detalle,
			@JsonProperty("transaction_amount") BigDecimal monto,
			@JsonProperty("currency_id") String moneda,
			@JsonProperty("payment_type_id") String tipo,
			@JsonProperty("payment_method_id") String medio,
			@JsonProperty("external_reference") String referenciaExterna) {

		public boolean aprobado() {
			return "approved".equals(status);
		}

		public boolean rechazado() {
			return "rejected".equals(status) || "cancelled".equals(status);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Busqueda(List<PagoMp> results) {
	}

}
