package pe.edu.ucv.cafetin.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import pe.edu.ucv.cafetin.mercadopago.config.MercadoPagoPropiedades;
import pe.edu.ucv.cafetin.mercadopago.service.CifradorTokens;
import pe.edu.ucv.cafetin.mercadopago.service.FirmaWebhook;

/** Tokens cifrados en la base y firma de las notificaciones de Mercado Pago. */
class CifradoYFirmaTest {

	static final String LLAVE = Base64.getEncoder().encodeToString(new byte[32]);
	static final String SECRETO = "secreto-webhook";

	// ---------- Cifrado de tokens ----------

	@Test
	void cifraYDescifraSinGuardarElTextoPlano() {
		CifradorTokens cifrador = new CifradorTokens(LLAVE);

		String guardado = cifrador.cifrar("token-de-prueba-123");

		assertThat(guardado).startsWith("v1:").doesNotContain("token-de-prueba");
		assertThat(cifrador.cifrar("token-de-prueba-123")).isNotEqualTo(guardado); // IV aleatorio
		assertThat(cifrador.descifrar(guardado)).isEqualTo("token-de-prueba-123");
	}

	@Test
	void unTokenAlteradoOConOtraLlaveNoSeDescifra() {
		String guardado = new CifradorTokens(LLAVE).cifrar("token-de-prueba-123");
		byte[] otra = new byte[32];
		otra[0] = 1;

		assertThatThrownBy(() -> new CifradorTokens(Base64.getEncoder().encodeToString(otra)).descifrar(guardado))
				.isInstanceOf(IllegalStateException.class);
		String alterado = guardado.substring(0, guardado.length() - 4) + "AAAA";
		assertThatThrownBy(() -> new CifradorTokens(LLAVE).descifrar(alterado)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void sinLlaveOConLlaveCortaNoCifra() {
		assertThatThrownBy(() -> new CifradorTokens("").exigirLlave()).hasMessageContaining("CAFETIN_CIFRADO_LLAVE");
		assertThatThrownBy(() -> new CifradorTokens(Base64.getEncoder().encodeToString(new byte[16])).cifrar("x"))
				.hasMessageContaining("32 bytes");
	}

	// ---------- Firma del webhook ----------

	@Test
	void aceptaLaFirmaCorrecta() throws Exception {
		String firma = "ts=1704908010,v1=" + hmac("id:123456;request-id:req-1;ts:1704908010;");

		assertThat(firmador().valida(firma, "req-1", "123456")).isTrue();
	}

	@Test
	void rechazaFirmaAlteradaOtroIdOFaltante() throws Exception {
		String firma = "ts=1704908010,v1=" + hmac("id:123456;request-id:req-1;ts:1704908010;");

		assertThat(firmador().valida(firma, "req-1", "999999")).isFalse();
		assertThat(firmador().valida(firma.replace("ts=1704908010", "ts=1704908011"), "req-1", "123456")).isFalse();
		assertThat(firmador().valida(null, "req-1", "123456")).isFalse();
		assertThat(firmador().valida("ts=1,v1=no-es-hex", "req-1", "123456")).isFalse();
	}

	@Test
	void sinSecretoConfiguradoNingunaFirmaEsValida() throws Exception {
		String firma = "ts=1,v1=" + hmac("id:1;ts:1;");
		FirmaWebhook sinSecreto = new FirmaWebhook(propiedades(null));

		assertThat(sinSecreto.valida(firma, null, "1")).isFalse();
	}

	static FirmaWebhook firmador() {
		return new FirmaWebhook(propiedades(SECRETO));
	}

	static MercadoPagoPropiedades propiedades(String secretoWebhook) {
		return new MercadoPagoPropiedades("123", "secreto-app", secretoWebhook, "http://localhost:1", null,
				"https://api.cafetin.test", "https://panel.cafetin.test", null);
	}

	static String hmac(String manifiesto) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(manifiesto.getBytes(StandardCharsets.UTF_8)));
	}

}
