package pe.edu.ucv.cafetin.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.ucv.cafetin.soporte.DatosPrueba.como;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import com.jayway.jsonpath.JsonPath;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;
import pe.edu.ucv.cafetin.pedidos.service.PasarelaPago;
import pe.edu.ucv.cafetin.pedidos.service.PasarelaPago.ResultadoCobro;
import pe.edu.ucv.cafetin.soporte.DatosPrueba;

/** RF-06 (pagar), RF-07 (recibo), RF-16 (descuento de stock) y cancelación del pedido. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PagoPedidoIntegracionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	/** La pasarela se simula aquí para poder forzar un rechazo. */
	@MockitoBean
	PasarelaPago pasarela;

	DatosPrueba datos;
	int estudiante;
	int cat;

	@BeforeEach
	void preparar() {
		datos = new DatosPrueba(jdbc);
		estudiante = datos.estudiante();
		cat = datos.categoria();
		when(pasarela.cobrar(any(), any(), any())).thenReturn(ResultadoCobro.aprobado("OP-123456"));
	}

	// ---------- Pagar (RF-06, RF-07, RF-16) ----------

	@Test
	void pagoAprobadoDescuentaStockYEmiteRecibo() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		int tamal = datos.producto(cat, "4.00", 6);
		String pedido = cuerpo(crear(estudiante, """
				{"horaRecojo": "12:50", "items": [{"idProducto": %d, "cantidad": 2}, {"idProducto": %d, "cantidad": 1}]}"""
				.formatted(cafe, tamal)));
		int id = JsonPath.read(pedido, "$.id");
		String numero = JsonPath.<String>read(pedido, "$.codigoPedido").substring("PED-".length());

		pagar(estudiante, id, "YAPE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.pago.estado").value("PAGADO"))
				.andExpect(jsonPath("$.pago.metodoPago").value("YAPE"))
				.andExpect(jsonPath("$.pago.monto").value(9.00))
				.andExpect(jsonPath("$.pago.referencia").value("OP-123456"))
				.andExpect(jsonPath("$.pago.fecha").value(notNullValue()))
				.andExpect(jsonPath("$.recibo.codigoVerificacion").value(matchesPattern("DR-" + numero + "-\\d{4}")))
				.andExpect(jsonPath("$.recibo.fechaEmision").value(notNullValue()))
				.andExpect(jsonPath("$.recibo.verificado").value(false));

		assertThat(datos.stock(cafe)).isEqualTo(8);
		assertThat(datos.stock(tamal)).isEqualTo(5);
		assertThat(filasPago(id)).isEqualTo(1);
	}

	@Test
	void montoEsElTotalDelPedido() throws Exception {
		int cafe = datos.producto(cat, "3.70", 10);
		int id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 3))), "$.id");

		pagar(estudiante, id, "TARJETA").andExpect(status().isOk());

		assertThat(jdbc.queryForObject("""
				SELECT pg.monto = pe.total FROM pago pg JOIN pedido pe USING (id_pedido) WHERE pe.id_pedido = ?""",
				Boolean.class, id)).isTrue();
	}

	@Test
	void pagarDosVecesResponde409YNoDescuentaDeNuevo() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		int id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 2))), "$.id");
		pagar(estudiante, id, "YAPE").andExpect(status().isOk());

		pagar(estudiante, id, "PLIN")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));

		assertThat(datos.stock(cafe)).isEqualTo(8);
		assertThat(filasPago(id)).isEqualTo(1);
	}

	@Test
	void efectivoResponde400() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		int id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 1))), "$.id");

		pagar(estudiante, id, "EFECTIVO")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
		assertThat(filasPago(id)).isZero();
	}

	@Test
	void pagoRechazadoConservaElPedidoYElReintentoUsaLaMismaFila() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		int id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 2))), "$.id");
		when(pasarela.cobrar(any(), any(), eq(MetodoPago.TARJETA))).thenReturn(ResultadoCobro.rechazado("Fondos insuficientes"));

		pagar(estudiante, id, "TARJETA")
				.andExpect(status().isPaymentRequired())
				.andExpect(jsonPath("$.codigo").value("PAGO_RECHAZADO"));

		ver(estudiante, id).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.pago.estado").value("FALLIDO"))
				.andExpect(jsonPath("$.recibo").value(nullValue()));
		assertThat(datos.stock(cafe)).isEqualTo(10);
		Integer idPago = jdbc.queryForObject("SELECT id_pago FROM pago WHERE id_pedido = ?", Integer.class, id);

		pagar(estudiante, id, "PLIN")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pago.estado").value("PAGADO"))
				.andExpect(jsonPath("$.pago.metodoPago").value("PLIN"));

		assertThat(filasPago(id)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT id_pago FROM pago WHERE id_pedido = ?", Integer.class, id)).isEqualTo(idPago);
		assertThat(datos.stock(cafe)).isEqualTo(8);
	}

	@Test
	void siOtroCompraLasUltimasUnidadesAntesResponde409() throws Exception {
		int tamal = datos.producto(cat, "4.00", 2);
		int otro = datos.estudiante();
		int mio = JsonPath.read(cuerpo(crear(estudiante, unItem(tamal, 2))), "$.id");
		int suyo = JsonPath.read(cuerpo(crear(otro, unItem(tamal, 2))), "$.id");
		pagar(otro, suyo, "YAPE").andExpect(status().isOk());

		pagar(estudiante, mio, "YAPE")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));

		assertThat(datos.stock(tamal)).isZero();
		ver(estudiante, mio).andExpect(jsonPath("$.recibo").value(nullValue()));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pago WHERE id_pedido = ? AND estado = 'PAGADO'",
				Integer.class, mio)).isZero();
	}

	@Test
	void pagoConcurrenteDeLaUltimaUnidadSoloPasaUno() throws Exception {
		int tamal = datos.producto(cat, "4.00", 1);
		int otro = datos.estudiante();
		int p1 = JsonPath.read(cuerpo(crear(estudiante, unItem(tamal, 1))), "$.id");
		int p2 = JsonPath.read(cuerpo(crear(otro, unItem(tamal, 1))), "$.id");

		CountDownLatch salida = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> resultados = new ArrayList<>();
			for (int[] par : new int[][] { { estudiante, p1 }, { otro, p2 } }) {
				resultados.add(hilos.submit(() -> {
					salida.await();
					return pagar(par[0], par[1], "YAPE").andReturn().getResponse().getStatus();
				}));
			}
			salida.countDown();
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> r : resultados) {
				estados.add(r.get());
			}
			assertThat(estados).containsExactlyInAnyOrder(200, 409);
		} finally {
			hilos.shutdownNow();
		}
		assertThat(datos.stock(tamal)).isZero();
	}

	@Test
	void pagarPedidoAjenoResponde404() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		int id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 1))), "$.id");

		pagar(datos.estudiante(), id, "YAPE")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
		assertThat(filasPago(id)).isZero();
	}

	// ---------- Cancelar ----------

	@Test
	void cancelarPedidoSinPagar() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		int id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 1))), "$.id");

		cancelar(estudiante, id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO"));
		assertThat(datos.stock(cafe)).isEqualTo(10);
	}

	@Test
	void cancelarTrasUnPagoRechazadoSePermite() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		int id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 1))), "$.id");
		when(pasarela.cobrar(any(), any(), any())).thenReturn(ResultadoCobro.rechazado("Rechazado"));
		pagar(estudiante, id, "YAPE").andExpect(status().isPaymentRequired());

		cancelar(estudiante, id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO"));
	}

	@Test
	void unPedidoPagadoNoSeCancela() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		int id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 1))), "$.id");
		pagar(estudiante, id, "YAPE").andExpect(status().isOk());

		cancelar(estudiante, id)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		ver(estudiante, id).andExpect(jsonPath("$.estado").value("PENDIENTE"));
	}

	@Test
	void unPedidoCanceladoNoSePagaNiSeCancelaOtraVez() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		int id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 1))), "$.id");
		cancelar(estudiante, id).andExpect(status().isOk());

		pagar(estudiante, id, "YAPE")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
		cancelar(estudiante, id)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		assertThat(filasPago(id)).isZero();
	}

	@Test
	void cancelarPedidoAjenoResponde404() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		int id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 1))), "$.id");

		cancelar(datos.estudiante(), id).andExpect(status().isNotFound());
		ver(estudiante, id).andExpect(jsonPath("$.estado").value("PENDIENTE"));
	}

	// ---------- Ayudas ----------

	private ResultActions crear(int idEstudiante, String json) throws Exception {
		return mvc.perform(post("/api/pedidos").with(como(idEstudiante, "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions pagar(int idEstudiante, int idPedido, String metodo) throws Exception {
		return mvc.perform(post("/api/pedidos/" + idPedido + "/pago").with(como(idEstudiante, "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\": \"" + metodo + "\"}"));
	}

	private ResultActions cancelar(int idEstudiante, int idPedido) throws Exception {
		return mvc.perform(post("/api/pedidos/" + idPedido + "/cancelar").with(como(idEstudiante, "ESTUDIANTE")));
	}

	private ResultActions ver(int idEstudiante, int idPedido) throws Exception {
		return mvc.perform(get("/api/pedidos/" + idPedido).with(como(idEstudiante, "ESTUDIANTE")));
	}

	private int filasPago(int idPedido) {
		return jdbc.queryForObject("SELECT count(*) FROM pago WHERE id_pedido = ?", Integer.class, idPedido);
	}

	private static String unItem(int idProducto, int cantidad) {
		return "{\"horaRecojo\": \"12:50\", \"items\": [{\"idProducto\": %d, \"cantidad\": %d}]}"
				.formatted(idProducto, cantidad);
	}

	private static String cuerpo(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
