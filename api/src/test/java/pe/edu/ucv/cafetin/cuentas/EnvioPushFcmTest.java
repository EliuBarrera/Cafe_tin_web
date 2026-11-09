package pe.edu.ucv.cafetin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import pe.edu.ucv.cafetin.comun.evento.PedidoEstadoCambiado;
import pe.edu.ucv.cafetin.comun.modelo.TipoNotificacion;
import pe.edu.ucv.cafetin.cuentas.service.EnvioPush;
import pe.edu.ucv.cafetin.cuentas.service.EnvioPushFcm;

/**
 * RF-08 con Firebase: un servidor local hace de Google (token OAuth) y de FCM, con una cuenta de
 * servicio generada en el test. Así se prueba la firma, la caché del token y el formato del mensaje.
 */
class EnvioPushFcmTest {

	private static final PedidoEstadoCambiado AVISO = new PedidoEstadoCambiado(7, 42, TipoNotificacion.PEDIDO_LISTO,
			"Tu pedido PED-42 está listo. Acércate a recogerlo con tu recibo.");

	private HttpServer servidor;
	private KeyPair claves;
	private String base;
	private final List<String> peticionesDeToken = new ArrayList<>();
	private final List<Peticion> envios = new ArrayList<>();
	private int estadoFcm = 200;
	private String respuestaFcm = "{\"name\": \"projects/cafetin-test/messages/1\"}";

	record Peticion(String autorizacion, String cuerpo, String ruta) {
	}

	@BeforeEach
	void levantarServidor() throws Exception {
		KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
		generador.initialize(2048);
		claves = generador.generateKeyPair();

		servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/token", ex -> {
			peticionesDeToken.add(leer(ex));
			responder(ex, 200, "{\"access_token\": \"ya29.token-de-acceso\", \"expires_in\": 3599, \"token_type\": \"Bearer\"}");
		});
		servidor.createContext("/v1/", ex -> {
			envios.add(new Peticion(ex.getRequestHeaders().getFirst("Authorization"), leer(ex), ex.getRequestURI().getPath()));
			responder(ex, estadoFcm, respuestaFcm);
		});
		servidor.start();
		base = "http://127.0.0.1:" + servidor.getAddress().getPort();
	}

	@AfterEach
	void apagarServidor() {
		servidor.stop(0);
	}

	@Test
	void pideElTokenConUnJwtFirmadoPorLaCuentaDeServicioYEnviaElMensaje() throws Exception {
		envioFcm().enviar("token-del-telefono", AVISO);

		assertThat(peticionesDeToken).hasSize(1);
		Map<String, String> formulario = formulario(peticionesDeToken.getFirst());
		assertThat(formulario.get("grant_type")).isEqualTo("urn:ietf:params:oauth:grant-type:jwt-bearer");
		SignedJWT jwt = SignedJWT.parse(formulario.get("assertion"));
		assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) claves.getPublic()))).isTrue();
		assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("push@cafetin-test.iam.gserviceaccount.com");
		assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly(base + "/token");
		assertThat(jwt.getJWTClaimsSet().getStringClaim("scope"))
				.isEqualTo("https://www.googleapis.com/auth/firebase.messaging");

		assertThat(envios).hasSize(1);
		Peticion envio = envios.getFirst();
		assertThat(envio.ruta()).isEqualTo("/v1/projects/cafetin-test/messages:send");
		assertThat(envio.autorizacion()).isEqualTo("Bearer ya29.token-de-acceso");
		Map<String, Object> mensaje = JSONObjectUtils.getJSONObject(JSONObjectUtils.parse(envio.cuerpo()), "message");
		assertThat(mensaje.get("token")).isEqualTo("token-del-telefono");
		assertThat(JSONObjectUtils.getJSONObject(mensaje, "notification")).containsEntry("body", AVISO.mensaje());
		assertThat(JSONObjectUtils.getJSONObject(mensaje, "data"))
				.containsEntry("idPedido", "42").containsEntry("tipo", "PEDIDO_LISTO");
	}

	@Test
	void reutilizaElTokenDeAccesoMientrasNoVence() {
		EnvioPushFcm fcm = envioFcm();
		fcm.enviar("telefono-1", AVISO);
		fcm.enviar("telefono-2", AVISO);

		assertThat(peticionesDeToken).hasSize(1);
		assertThat(envios).hasSize(2);
	}

	@Test
	void unTokenDeTelefonoQueYaNoExisteSeReportaComoInvalido() {
		estadoFcm = 404;
		respuestaFcm = """
				{"error": {"code": 404, "status": "NOT_FOUND",
				  "details": [{"@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", "errorCode": "UNREGISTERED"}]}}""";

		assertThatThrownBy(() -> envioFcm().enviar("token-viejo", AVISO)).isInstanceOf(EnvioPush.TokenInvalido.class);
	}

	@Test
	void otrosErroresDeFcmNoBorranElToken() {
		estadoFcm = 503;
		respuestaFcm = "{\"error\": {\"code\": 503, \"status\": \"UNAVAILABLE\"}}";

		assertThatThrownBy(() -> envioFcm().enviar("token-del-telefono", AVISO))
				.isNotInstanceOf(EnvioPush.TokenInvalido.class);
	}

	// ------------------------------------------------------------------ ayudas

	private EnvioPushFcm envioFcm() {
		String pem = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(claves.getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
		String cuentaDeServicio = JSONObjectUtils.toJSONString(Map.of(
				"type", "service_account",
				"project_id", "cafetin-test",
				"client_email", "push@cafetin-test.iam.gserviceaccount.com",
				"private_key", pem,
				"token_uri", base + "/token"));
		return new EnvioPushFcm(cuentaDeServicio, base);
	}

	private static String leer(HttpExchange ex) throws IOException {
		return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
	}

	private static void responder(HttpExchange ex, int estado, String json) throws IOException {
		byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json");
		ex.sendResponseHeaders(estado, cuerpo.length);
		ex.getResponseBody().write(cuerpo);
		ex.close();
	}

	private static Map<String, String> formulario(String cuerpo) {
		Map<String, String> campos = new HashMap<>();
		for (String par : cuerpo.split("&")) {
			String[] kv = par.split("=", 2);
			campos.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
		}
		return campos;
	}

}
