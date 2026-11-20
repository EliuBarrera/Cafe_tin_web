package pe.edu.ucv.cafetin.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.ucv.cafetin.soporte.DatosPrueba.como;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.mercadopago.service.CifradorTokens;
import pe.edu.ucv.cafetin.mercadopago.service.ClienteMercadoPago;
import pe.edu.ucv.cafetin.mercadopago.service.ClienteMercadoPago.PagoMp;
import pe.edu.ucv.cafetin.mercadopago.service.ClienteMercadoPago.PreferenciaCreada;
import pe.edu.ucv.cafetin.mercadopago.service.ClienteMercadoPago.Tokens;
import pe.edu.ucv.cafetin.soporte.DatosPrueba;

/**
 * RF-06 y RF-07 con Mercado Pago: conexión OAuth del cafetín, Checkout Pro, confirmación por webhook y
 * por verificación (idempotentes), reembolso cuando el stock se agotó y aislamiento entre cafeterías.
 * La API de Mercado Pago se simula ({@link ClienteMercadoPago}); sus peticiones se prueban en
 * {@link ClienteMercadoPagoTest}.
 */
@SpringBootTest(properties = {
		"cafetin.pagos.pasarela=mercadopago",
		"cafetin.pagos.comision=0.05",
		"cafetin.pagos.moneda=COP",
		"cafetin.mercadopago.client-id=app-prueba",
		"cafetin.mercadopago.client-secret=clave-falsa-app",
		"cafetin.mercadopago.webhook-secret=" + CifradoYFirmaTest.SECRETO,
		"cafetin.mercadopago.url-publica-api=https://api.cafetin.test",
		"cafetin.mercadopago.url-panel=https://panel.cafetin.test",
		"cafetin.cifrado.llave=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MercadoPagoIntegracionTest {

	/** Inventado: no tiene el formato de una credencial real. */
	static final String TOKEN = "token-falso-del-cafetin";
	static final String URL_PAGO = "https://mp.test/checkout?pref_id=pref-1";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	CifradorTokens cifrador;

	@MockitoBean
	ClienteMercadoPago mercadoPago;

	DatosPrueba datos;
	int cafeteria;
	int dueno;
	int estudiante;
	int cat;

	@BeforeEach
	void preparar() {
		datos = new DatosPrueba(jdbc);
		cafeteria = datos.cafeteria();
		dueno = datos.dueno(cafeteria);
		estudiante = datos.estudiante();
		cat = datos.categoria(cafeteria);
		when(mercadoPago.crearPreferencia(any(), any())).thenReturn(new PreferenciaCreada("pref-1", URL_PAGO));
	}

	// ---------- Conectar la cuenta del cafetín (OAuth) ----------

	@Test
	void elDuenoConectaSuCuentaYLosTokensSeGuardanCifrados() throws Exception {
		when(mercadoPago.canjearCodigo("codigo-ok")).thenReturn(new Tokens(TOKEN, "renovacion-falsa", 3862056228L, 15552000L));
		String url = JsonPath.read(cuerpo(mvc.perform(post("/api/cafeterias/mia/mercadopago/conectar")
				.with(como(dueno, "DUENO"))).andExpect(status().isOk())), "$.urlAutorizacion");
		var parametros = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
		assertThat(url).startsWith("https://auth.mercadopago.com/authorization");
		assertThat(parametros.getFirst("client_id")).isEqualTo("app-prueba");
		String estado = parametros.getFirst("state");

		retornoOauth("codigo-ok", estado)
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "https://panel.cafetin.test/cobros?mercadopago=conectado"));

		String guardado = jdbc.queryForObject("SELECT mp_access_token FROM cafeteria WHERE id_cafeteria = ?",
				String.class, cafeteria);
		assertThat(guardado).startsWith("v1:").doesNotContain(TOKEN);
		assertThat(cifrador.descifrar(guardado)).isEqualTo(TOKEN);
		mvc.perform(get("/api/cafeterias/mia/mercadopago").with(como(dueno, "DUENO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pasarela").value("MERCADOPAGO"))
				.andExpect(jsonPath("$.conectado").value(true))
				.andExpect(jsonPath("$.idUsuarioMp").value(3862056228L))
				.andExpect(jsonPath("$.conectadoEn").value(notNullValue()))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString(TOKEN))));

		// El state es de un solo uso.
		retornoOauth("codigo-ok", estado)
				.andExpect(header().string("Location", "https://panel.cafetin.test/cobros?mercadopago=error"));
		verify(mercadoPago, times(1)).canjearCodigo(anyString());
	}

	@Test
	void unStateInventadoOVencidoNoConectaNada() throws Exception {
		retornoOauth("codigo-ok", "inventado")
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "https://panel.cafetin.test/cobros?mercadopago=error"));
		jdbc.update("INSERT INTO oauth_estado (estado, id_cafeteria, expira) VALUES ('vencido', ?, LOCALTIMESTAMP - INTERVAL '1 minute')",
				cafeteria);
		retornoOauth("codigo-ok", "vencido")
				.andExpect(header().string("Location", "https://panel.cafetin.test/cobros?mercadopago=error"));

		verify(mercadoPago, never()).canjearCodigo(anyString());
		assertThat(conectado(cafeteria)).isFalse();
	}

	@Test
	void desconectarBorraLosTokens() throws Exception {
		conectar(cafeteria);

		mvc.perform(delete("/api/cafeterias/mia/mercadopago").with(como(dueno, "DUENO"))).andExpect(status().isNoContent());

		assertThat(conectado(cafeteria)).isFalse();
		mvc.perform(get("/api/cafeterias/mia/mercadopago").with(como(dueno, "DUENO")))
				.andExpect(jsonPath("$.conectado").value(false))
				.andExpect(jsonPath("$.idUsuarioMp").value(nullValue()));
	}

	@Test
	void soloElDuenoVeOConectaLaCuenta() throws Exception {
		mvc.perform(get("/api/cafeterias/mia/mercadopago").with(como(estudiante, "ESTUDIANTE")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		mvc.perform(post("/api/cafeterias/mia/mercadopago/conectar").with(como(datos.trabajador(cafeteria, "Luis", "Prueba"), "TRABAJADOR")))
				.andExpect(status().isForbidden());
	}

	// ---------- Pagar con Checkout Pro ----------

	@Test
	void sinCuentaConectadaElCafetinNoCobra() throws Exception {
		int cafe = datos.producto(cat, "3.00", 10);
		int id = crearPedido(cafe, 1);

		pagar(id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"))
				.andExpect(jsonPath("$.mensaje").value(containsString("Mercado Pago")));

		assertThat(filasPago(id)).isZero();
	}

	@SuppressWarnings("unchecked")
	@Test
	void pagarDevuelveLaUrlDeCheckoutYNoTocaElStock() throws Exception {
		conectar(cafeteria);
		int cafe = datos.producto(cat, "3.00", 10);
		int id = crearPedido(cafe, 3);

		pagar(id).andExpect(status().isOk())
				.andExpect(jsonPath("$.pago.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.pago.urlPago").value(URL_PAGO))
				.andExpect(jsonPath("$.recibo").value(nullValue()));

		assertThat(datos.stock(cafe)).isEqualTo(10);
		ArgumentCaptor<Map<String, Object>> preferencia = ArgumentCaptor.forClass(Map.class);
		verify(mercadoPago).crearPreferencia(eq(TOKEN), preferencia.capture());
		Map<String, Object> pref = preferencia.getValue();
		assertThat(pref.get("external_reference")).isEqualTo(String.valueOf(idPago(id)));
		assertThat((BigDecimal) pref.get("marketplace_fee")).isEqualByComparingTo("0.45"); // 5 % de 9.00
		assertThat(pref.get("notification_url"))
				.isEqualTo("https://api.cafetin.test/api/mercadopago/webhook?cafeteria=" + cafeteria);
		List<Map<String, Object>> items = (List<Map<String, Object>>) pref.get("items");
		assertThat(items).singleElement().satisfies(item -> {
			assertThat((BigDecimal) item.get("unit_price")).isEqualByComparingTo("9.00");
			assertThat(item.get("currency_id")).isEqualTo("COP");
		});
	}

	@Test
	void verificarYWebhookConfirmanUnaSolaVez() throws Exception {
		conectar(cafeteria);
		int cafe = datos.producto(cat, "2.50", 10);
		int id = crearPedido(cafe, 2);
		pagar(id).andExpect(status().isOk());
		PagoMp aprobado = pagoMp(555L, "approved", "5.00", idPago(id));
		when(mercadoPago.buscarPagos(TOKEN, String.valueOf(idPago(id)))).thenReturn(List.of(aprobado));
		when(mercadoPago.consultarPago(TOKEN, 555L)).thenReturn(aprobado);

		verificar(estudiante, id).andExpect(status().isOk())
				.andExpect(jsonPath("$.pago.estado").value("PAGADO"))
				.andExpect(jsonPath("$.pago.metodoPago").value("TARJETA"))
				.andExpect(jsonPath("$.pago.referencia").value("555"))
				.andExpect(jsonPath("$.pago.urlPago").value(nullValue()))
				.andExpect(jsonPath("$.recibo.codigoVerificacion").value(notNullValue()));
		webhook(cafeteria, "555", true).andExpect(status().isOk());
		verificar(estudiante, id).andExpect(status().isOk());

		assertThat(datos.stock(cafe)).isEqualTo(8);
		assertThat(filasPago(id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM recibo r JOIN pago p USING (id_pago) WHERE p.id_pedido = ?",
				Integer.class, id)).isEqualTo(1);
	}

	@Test
	void elWebhookConfirmaElPago() throws Exception {
		conectar(cafeteria);
		int cafe = datos.producto(cat, "2.50", 10);
		int id = crearPedido(cafe, 1);
		pagar(id).andExpect(status().isOk());
		when(mercadoPago.consultarPago(TOKEN, 777L)).thenReturn(pagoMp(777L, "approved", "2.50", idPago(id)));

		webhook(cafeteria, "777", true).andExpect(status().isOk());

		assertThat(estadoPago(id)).isEqualTo("PAGADO");
		assertThat(datos.stock(cafe)).isEqualTo(9);
	}

	@Test
	void unWebhookSinFirmaValidaResponde401YNoConsulta() throws Exception {
		conectar(cafeteria);

		webhook(cafeteria, "555", false)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

		verify(mercadoPago, never()).consultarPago(anyString(), anyLong());
	}

	@Test
	void unPagoPorOtroMontoNoSeConfirma() throws Exception {
		conectar(cafeteria);
		int cafe = datos.producto(cat, "2.50", 10);
		int id = crearPedido(cafe, 2);
		pagar(id).andExpect(status().isOk());
		when(mercadoPago.buscarPagos(TOKEN, String.valueOf(idPago(id))))
				.thenReturn(List.of(pagoMp(556L, "approved", "0.10", idPago(id))));

		verificar(estudiante, id).andExpect(status().isOk())
				.andExpect(jsonPath("$.pago.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.recibo").value(nullValue()));
		assertThat(datos.stock(cafe)).isEqualTo(10);
	}

	@Test
	void unPagoDeOtraCafeteriaNoSeConfirma() throws Exception {
		conectar(cafeteria);
		int otra = datos.cafeteria();
		conectar(otra);
		int cafe = datos.producto(cat, "2.50", 10);
		int id = crearPedido(cafe, 1);
		pagar(id).andExpect(status().isOk());
		when(mercadoPago.consultarPago(TOKEN, 888L)).thenReturn(pagoMp(888L, "approved", "2.50", idPago(id)));

		webhook(otra, "888", true).andExpect(status().isOk());

		assertThat(estadoPago(id)).isEqualTo("PENDIENTE");
	}

	@Test
	void pagoRechazadoQuedaFallidoYElReintentoUsaLaMismaFila() throws Exception {
		conectar(cafeteria);
		int cafe = datos.producto(cat, "2.50", 10);
		int id = crearPedido(cafe, 1);
		pagar(id).andExpect(status().isOk());
		int idPago = idPago(id);
		when(mercadoPago.buscarPagos(TOKEN, String.valueOf(idPago)))
				.thenReturn(List.of(pagoMp(601L, "rejected", "2.50", idPago)));

		verificar(estudiante, id).andExpect(jsonPath("$.pago.estado").value("FALLIDO"))
				.andExpect(jsonPath("$.estado").value("PENDIENTE"));
		when(mercadoPago.crearPreferencia(any(), any())).thenReturn(new PreferenciaCreada("pref-2", URL_PAGO + "2"));
		pagar(id).andExpect(status().isOk())
				.andExpect(jsonPath("$.pago.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.pago.urlPago").value(URL_PAGO + "2"));

		assertThat(filasPago(id)).isEqualTo(1);
		assertThat(idPago(id)).isEqualTo(idPago);
		assertThat(datos.stock(cafe)).isEqualTo(10);
	}

	@Test
	void siSeAgotoElStockAntesDeConfirmarSeReembolsaYSeCancela() throws Exception {
		conectar(cafeteria);
		int tamal = datos.producto(cat, "4.00", 1);
		int otro = datos.estudiante();
		int mio = crearPedido(estudiante, tamal, 1);
		int suyo = crearPedido(otro, tamal, 1);
		pagar(estudiante, mio).andExpect(status().isOk());
		pagar(otro, suyo).andExpect(status().isOk());
		when(mercadoPago.consultarPago(TOKEN, 701L)).thenReturn(pagoMp(701L, "approved", "4.00", idPago(suyo)));
		when(mercadoPago.consultarPago(TOKEN, 702L)).thenReturn(pagoMp(702L, "approved", "4.00", idPago(mio)));

		webhook(cafeteria, "701", true).andExpect(status().isOk());
		webhook(cafeteria, "702", true).andExpect(status().isOk());

		assertThat(estadoPago(suyo)).isEqualTo("PAGADO");
		verify(mercadoPago).reembolsar(TOKEN, 702L);
		verify(mercadoPago, never()).reembolsar(TOKEN, 701L);
		ver(estudiante, mio)
				.andExpect(jsonPath("$.estado").value("CANCELADO"))
				.andExpect(jsonPath("$.pago.estado").value("REEMBOLSADO"))
				.andExpect(jsonPath("$.recibo").value(nullValue()));
		assertThat(datos.stock(tamal)).isZero();
	}

	@Test
	void unPagoAprobadoDeUnPedidoCanceladoSeReembolsa() throws Exception {
		conectar(cafeteria);
		int cafe = datos.producto(cat, "2.50", 10);
		int id = crearPedido(cafe, 1);
		pagar(id).andExpect(status().isOk());
		mvc.perform(post("/api/pedidos/" + id + "/cancelar").with(como(estudiante, "ESTUDIANTE"))).andExpect(status().isOk());
		when(mercadoPago.consultarPago(TOKEN, 901L)).thenReturn(pagoMp(901L, "approved", "2.50", idPago(id)));

		webhook(cafeteria, "901", true).andExpect(status().isOk());

		verify(mercadoPago).reembolsar(TOKEN, 901L);
		assertThat(estadoPago(id)).isEqualTo("REEMBOLSADO");
		assertThat(datos.stock(cafe)).isEqualTo(10);
	}

	@Test
	void verificarUnPedidoAjenoResponde404() throws Exception {
		conectar(cafeteria);
		int cafe = datos.producto(cat, "2.50", 10);
		int id = crearPedido(cafe, 1);

		verificar(datos.estudiante(), id).andExpect(status().isNotFound());
	}

	@Test
	void laVueltaDelCheckoutRedirigeALaApp() throws Exception {
		mvc.perform(get("/api/mercadopago/retorno-pago").param("pedido", "42").param("status", "approved"))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "cafetin://pago/42"));
	}

	// ---------- Ayudas ----------

	private void conectar(int idCafeteria) {
		jdbc.update("""
				UPDATE cafeteria SET mp_user_id = 1, mp_access_token = ?, mp_refresh_token = ?,
				       mp_token_expira = LOCALTIMESTAMP + INTERVAL '180 days', mp_conectado_en = LOCALTIMESTAMP
				WHERE id_cafeteria = ?""", cifrador.cifrar(TOKEN), cifrador.cifrar("renovacion-falsa"), idCafeteria);
	}

	private boolean conectado(int idCafeteria) {
		return jdbc.queryForObject("SELECT mp_access_token IS NOT NULL FROM cafeteria WHERE id_cafeteria = ?",
				Boolean.class, idCafeteria);
	}

	private static PagoMp pagoMp(long id, String estado, String monto, int idPago) {
		return new PagoMp(id, estado, null, new BigDecimal(monto), "COP", "credit_card", "visa", String.valueOf(idPago));
	}

	private int crearPedido(int idProducto, int cantidad) throws Exception {
		return crearPedido(estudiante, idProducto, cantidad);
	}

	private int crearPedido(int idEstudiante, int idProducto, int cantidad) throws Exception {
		String json = "{\"idCafeteria\": %d, \"horaRecojo\": \"12:50\", \"items\": [{\"idProducto\": %d, \"cantidad\": %d}]}"
				.formatted(cafeteria, idProducto, cantidad);
		return JsonPath.read(cuerpo(mvc.perform(post("/api/pedidos").with(como(idEstudiante, "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated())), "$.id");
	}

	private ResultActions pagar(int idPedido) throws Exception {
		return pagar(estudiante, idPedido);
	}

	private ResultActions pagar(int idEstudiante, int idPedido) throws Exception {
		return mvc.perform(post("/api/pedidos/" + idPedido + "/pago").with(como(idEstudiante, "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\": \"TARJETA\"}"));
	}

	private ResultActions verificar(int idEstudiante, int idPedido) throws Exception {
		return mvc.perform(post("/api/pedidos/" + idPedido + "/pago/verificar").with(como(idEstudiante, "ESTUDIANTE")));
	}

	private ResultActions ver(int idEstudiante, int idPedido) throws Exception {
		return mvc.perform(get("/api/pedidos/" + idPedido).with(como(idEstudiante, "ESTUDIANTE")));
	}

	private ResultActions retornoOauth(String codigo, String estado) throws Exception {
		return mvc.perform(get("/api/mercadopago/oauth/retorno").param("code", codigo).param("state", estado));
	}

	private ResultActions webhook(int idCafeteria, String idPagoMp, boolean firmaValida) throws Exception {
		String ts = "1704908010";
		String firma = firmaValida
				? CifradoYFirmaTest.hmac("id:" + idPagoMp + ";request-id:req-" + idPagoMp + ";ts:" + ts + ";")
				: "00".repeat(32);
		return mvc.perform(post("/api/mercadopago/webhook")
				.param("cafeteria", String.valueOf(idCafeteria))
				.param("data.id", idPagoMp)
				.param("type", "payment")
				.header("x-signature", "ts=" + ts + ",v1=" + firma)
				.header("x-request-id", "req-" + idPagoMp)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\": \"payment\", \"data\": {\"id\": \"" + idPagoMp + "\"}}"));
	}

	private int idPago(int idPedido) {
		return jdbc.queryForObject("SELECT id_pago FROM pago WHERE id_pedido = ?", Integer.class, idPedido);
	}

	private String estadoPago(int idPedido) {
		return jdbc.queryForObject("SELECT estado::text FROM pago WHERE id_pedido = ?", String.class, idPedido);
	}

	private int filasPago(int idPedido) {
		return jdbc.queryForObject("SELECT count(*) FROM pago WHERE id_pedido = ?", Integer.class, idPedido);
	}

	private static String cuerpo(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
