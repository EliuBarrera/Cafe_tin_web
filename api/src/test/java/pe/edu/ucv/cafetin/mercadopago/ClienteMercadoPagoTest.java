package pe.edu.ucv.cafetin.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import pe.edu.ucv.cafetin.mercadopago.config.MercadoPagoPropiedades;
import pe.edu.ucv.cafetin.mercadopago.service.ClienteMercadoPago;
import pe.edu.ucv.cafetin.mercadopago.service.ClienteMercadoPago.PagoMp;
import pe.edu.ucv.cafetin.mercadopago.service.CobroMercadoPagoService;
import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;

/** Peticiones a la API de Mercado Pago: token correcto en cada llamada y lectura de las respuestas. */
class ClienteMercadoPagoTest {

	static final String API = "https://api.mp.test";
	/** Valores inventados: no tienen el formato de una credencial real. */
	static final String TOKEN_CAFETIN = "token-falso-del-cafetin";

	MockRestServiceServer servidor;
	ClienteMercadoPago cliente;

	@BeforeEach
	void preparar() {
		RestClient.Builder builder = RestClient.builder();
		servidor = MockRestServiceServer.bindTo(builder).build();
		cliente = new ClienteMercadoPago(builder, new MercadoPagoPropiedades("app-123", "clave-falsa-app", "w", API,
				null, "https://api.cafetin.test", null, null));
	}

	@Test
	void canjearCodigoUsaLasCredencialesDeLaAplicacionYLaRedirectUrl() {
		servidor.expect(requestTo(API + "/oauth/token"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.grant_type").value("authorization_code"))
				.andExpect(jsonPath("$.code").value("codigo-falso"))
				.andExpect(jsonPath("$.client_id").value("app-123"))
				.andExpect(jsonPath("$.client_secret").value("clave-falsa-app"))
				.andExpect(jsonPath("$.redirect_uri").value("https://api.cafetin.test/api/mercadopago/oauth/retorno"))
				.andRespond(withSuccess("""
						{"access_token": "%s", "refresh_token": "renovacion-falsa", "user_id": 3862056228,
						 "expires_in": 15552000, "token_type": "bearer"}""".formatted(TOKEN_CAFETIN),
						MediaType.APPLICATION_JSON));

		ClienteMercadoPago.Tokens tokens = cliente.canjearCodigo("codigo-falso");

		assertThat(tokens.accessToken()).isEqualTo(TOKEN_CAFETIN);
		assertThat(tokens.refreshToken()).isEqualTo("renovacion-falsa");
		assertThat(tokens.userId()).isEqualTo(3862056228L);
		assertThat(tokens.expiraEnSegundos()).isEqualTo(15552000L);
		servidor.verify();
	}

	@Test
	void crearPreferenciaUsaElTokenDelCafetin() {
		servidor.expect(requestTo(API + "/checkout/preferences"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer " + TOKEN_CAFETIN))
				.andExpect(jsonPath("$.external_reference").value("77"))
				.andExpect(jsonPath("$.marketplace_fee").value(0.45))
				.andRespond(withSuccess("""
						{"id": "123-pref", "init_point": "https://www.mercadopago.com.co/checkout/v1/redirect?pref_id=123-pref"}""",
						MediaType.APPLICATION_JSON));

		var creada = cliente.crearPreferencia(TOKEN_CAFETIN,
				Map.of("external_reference", "77", "marketplace_fee", new BigDecimal("0.45")));

		assertThat(creada.id()).isEqualTo("123-pref");
		assertThat(creada.initPoint()).contains("pref_id=123-pref");
		servidor.verify();
	}

	@Test
	void consultarYBuscarPagosLeenEstadoMontoYMedio() {
		String pago = """
				{"id": 555, "status": "approved", "status_detail": "accredited", "transaction_amount": 9.00,
				 "currency_id": "COP", "payment_type_id": "credit_card", "payment_method_id": "visa",
				 "external_reference": "77", "otro_campo": {"x": 1}}""";
		servidor.expect(requestTo(API + "/v1/payments/555"))
				.andExpect(header("Authorization", "Bearer " + TOKEN_CAFETIN))
				.andRespond(withSuccess(pago, MediaType.APPLICATION_JSON));
		servidor.expect(requestTo(startsWith(API + "/v1/payments/search")))
				.andExpect(queryParam("external_reference", "77"))
				.andRespond(withSuccess("{\"results\": [" + pago + "], \"paging\": {\"total\": 1}}",
						MediaType.APPLICATION_JSON));

		PagoMp consultado = cliente.consultarPago(TOKEN_CAFETIN, 555);
		var encontrados = cliente.buscarPagos(TOKEN_CAFETIN, "77");

		assertThat(consultado.aprobado()).isTrue();
		assertThat(consultado.monto()).isEqualByComparingTo("9.00");
		assertThat(consultado.referenciaExterna()).isEqualTo("77");
		assertThat(encontrados).singleElement().extracting(PagoMp::id).isEqualTo(555L);
		servidor.verify();
	}

	@Test
	void reembolsarEnviaClaveDeIdempotencia() {
		servidor.expect(requestTo(API + "/v1/payments/555/refunds"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("X-Idempotency-Key", "reembolso-555"))
				.andExpect(content().json("{}"))
				.andRespond(withSuccess("{\"id\": 1, \"status\": \"approved\"}", MediaType.APPLICATION_JSON));

		cliente.reembolsar(TOKEN_CAFETIN, 555);

		servidor.verify();
	}

	@Test
	void elMedioDeMercadoPagoSeTraduceAlEnumDeLaBase() {
		assertThat(CobroMercadoPagoService.metodo(pagoMp("credit_card", "visa"))).isEqualTo(MetodoPago.TARJETA);
		assertThat(CobroMercadoPagoService.metodo(pagoMp("debit_card", "debvisa"))).isEqualTo(MetodoPago.TARJETA);
		assertThat(CobroMercadoPagoService.metodo(pagoMp("bank_transfer", "yape"))).isEqualTo(MetodoPago.YAPE);
		assertThat(CobroMercadoPagoService.metodo(pagoMp("ticket", "efecty"))).isEqualTo(MetodoPago.OTRO);
	}

	private static PagoMp pagoMp(String tipo, String medio) {
		return new PagoMp(1L, "approved", null, BigDecimal.ONE, "PEN", tipo, medio, "1");
	}

}
