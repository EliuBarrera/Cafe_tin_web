package pe.edu.ucv.cafetin.cuentas.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import pe.edu.ucv.cafetin.comun.evento.PedidoEstadoCambiado;

/**
 * Push por Firebase Cloud Messaging (API HTTP v1) sin el SDK de Firebase: con la cuenta de servicio se
 * firma un JWT (RS256, Nimbus ya viene con la seguridad), se cambia por un token de acceso de Google y
 * se llama a {@code messages:send}. Solo existe si se configura {@code FIREBASE_CREDENTIALS}: el JSON de
 * la cuenta de servicio o la ruta al archivo (en Render, un Secret File). Sin ella se usa {@link EnvioPushLog}.
 */
@Component
@Primary
@ConditionalOnProperty("FIREBASE_CREDENTIALS")
public class EnvioPushFcm implements EnvioPush {

	private static final String ALCANCE = "https://www.googleapis.com/auth/firebase.messaging";
	private static final String TOKEN_URI_GOOGLE = "https://oauth2.googleapis.com/token";
	private static final Duration ESPERA = Duration.ofSeconds(5);
	/** Se pide uno nuevo un minuto antes de que venza, para no usar uno que expire en camino. */
	private static final long MARGEN_SEGUNDOS = 60;

	private final String cuenta;
	private final String urlToken;
	private final String urlEnvio;
	private final RSAPrivateKey clave;
	private final RestClient http;

	private String tokenDeAcceso;
	private Instant tokenVence = Instant.EPOCH;

	public EnvioPushFcm(@Value("${FIREBASE_CREDENTIALS}") String credenciales,
			@Value("${cafetin.fcm.url:https://fcm.googleapis.com}") String urlFcm) {
		Map<String, Object> json = leerCuentaDeServicio(credenciales);
		this.cuenta = texto(json, "client_email");
		this.urlToken = json.get("token_uri") instanceof String uri ? uri : TOKEN_URI_GOOGLE;
		this.urlEnvio = urlFcm + "/v1/projects/" + texto(json, "project_id") + "/messages:send";
		this.clave = leerClave(texto(json, "private_key"));

		JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(ESPERA).build());
		fabrica.setReadTimeout(ESPERA);
		this.http = RestClient.builder().requestFactory(fabrica).build();
	}

	@Override
	public void enviar(String fcmToken, PedidoEstadoCambiado aviso) {
		Map<String, Object> mensaje = Map.of("message", Map.of(
				"token", fcmToken,
				"notification", Map.of("title", "Cafetín UCV", "body", aviso.mensaje()),
				// La app abre el pedido con estos datos; FCM solo acepta valores de texto.
				"data", Map.of("idPedido", String.valueOf(aviso.idPedido()), "tipo", aviso.tipo().name()),
				"android", Map.of("priority", "high")));
		try {
			http.post().uri(urlEnvio)
					.headers(h -> h.setBearerAuth(tokenDeAcceso()))
					.contentType(MediaType.APPLICATION_JSON)
					.body(mensaje)
					.retrieve()
					.toBodilessEntity();
		} catch (HttpClientErrorException e) {
			// FCM responde 404 / UNREGISTERED cuando la app se desinstaló o el token se renovó.
			if (e.getStatusCode().value() == 404 || e.getResponseBodyAsString().contains("UNREGISTERED")) {
				throw new TokenInvalido("FCM ya no reconoce el token (" + e.getStatusCode() + ")");
			}
			throw e;
		}
	}

	/** Token OAuth de Google, reutilizado mientras no venza (dura una hora). */
	private synchronized String tokenDeAcceso() {
		Instant ahora = Instant.now();
		if (ahora.isBefore(tokenVence)) {
			return tokenDeAcceso;
		}
		MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
		formulario.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
		formulario.add("assertion", afirmacionFirmada(ahora));

		Map<?, ?> respuesta = http.post().uri(urlToken)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(formulario)
				.retrieve()
				.body(Map.class);
		if (respuesta == null || !(respuesta.get("access_token") instanceof String token)) {
			throw new IllegalStateException("Google no devolvió un token de acceso para FCM");
		}
		long segundos = respuesta.get("expires_in") instanceof Number n ? n.longValue() : 3600;
		tokenDeAcceso = token;
		tokenVence = ahora.plusSeconds(segundos - MARGEN_SEGUNDOS);
		return tokenDeAcceso;
	}

	private String afirmacionFirmada(Instant ahora) {
		JWTClaimsSet reclamos = new JWTClaimsSet.Builder()
				.issuer(cuenta)
				.audience(urlToken)
				.claim("scope", ALCANCE)
				.issueTime(Date.from(ahora))
				.expirationTime(Date.from(ahora.plusSeconds(3600)))
				.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), reclamos);
		try {
			jwt.sign(new RSASSASigner(clave));
		} catch (JOSEException e) {
			throw new IllegalStateException("No se pudo firmar la solicitud de token para FCM", e);
		}
		return jwt.serialize();
	}

	// ------------------------------------------------------------------ cuenta de servicio

	/** Acepta el JSON directamente o la ruta al archivo descargado de Firebase. */
	private static Map<String, Object> leerCuentaDeServicio(String credenciales) {
		try {
			String json = credenciales.strip().startsWith("{") ? credenciales : Files.readString(Path.of(credenciales.strip()));
			return JSONObjectUtils.parse(json);
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer FIREBASE_CREDENTIALS", e);
		} catch (ParseException e) {
			throw new IllegalStateException("FIREBASE_CREDENTIALS no es un JSON válido de cuenta de servicio", e);
		}
	}

	private static String texto(Map<String, Object> json, String campo) {
		if (json.get(campo) instanceof String valor && !valor.isBlank()) {
			return valor;
		}
		throw new IllegalStateException("FIREBASE_CREDENTIALS no tiene el campo " + campo);
	}

	private static RSAPrivateKey leerClave(String pem) {
		String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
		try {
			return (RSAPrivateKey) KeyFactory.getInstance("RSA")
					.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("La private_key de FIREBASE_CREDENTIALS no es una clave RSA válida", e);
		}
	}

}
