package pe.edu.ucv.cafetin.reportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.ucv.cafetin.soporte.DatosPrueba.como;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.soporte.DatosPrueba;

/**
 * Ingresos del dueño: RF-20 (ventas), RF-21 (ingresos por día, semana y mes) y RF-22 (más vendidos).
 * Todos los tests comparten la base: cada uno usa ventas en un año propio (2021–2024) para que
 * los totales no se mezclen con las ventas de hoy que crean otros tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReporteIntegracionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	DatosPrueba datos;
	int dueno;
	int estudiante;
	int producto;

	@BeforeEach
	void preparar() {
		datos = new DatosPrueba(jdbc);
		dueno = datos.dueno();
		estudiante = datos.estudiante();
		producto = datos.producto(datos.categoria(), "1.00", 100);
	}

	// ---------- RF-21: ingresos por periodo ----------

	@Test
	void ingresosPorDiaSoloCuentanPagosPagadosDelRango() throws Exception {
		venta("10.00", "PAGADO", "2021-03-02 09:00");
		venta("20.50", "PAGADO", "2021-03-02 13:30");
		venta("99.00", "FALLIDO", "2021-03-02 14:00");
		venta("5.00", "PAGADO", "2021-03-05 23:59"); // hasta incluye todo el día
		venta("7.00", "PAGADO", "2021-03-06 00:00"); // fuera del rango

		ingresos("DIA", "2021-03-01", "2021-03-05").andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].inicio").value("2021-03-02"))
				.andExpect(jsonPath("$[0].pedidosPagados").value(2))
				.andExpect(jsonPath("$[0].ingresoTotal").value(30.50))
				.andExpect(jsonPath("$[0].ticketPromedio").value(15.25))
				.andExpect(jsonPath("$[1].inicio").value("2021-03-05"))
				.andExpect(jsonPath("$[1].ingresoTotal").value(5.00));
	}

	@Test
	void ingresosPorSemanaEmpiezanEnLunes() throws Exception {
		venta("4.00", "PAGADO", "2022-06-07 10:00"); // martes
		venta("6.00", "PAGADO", "2022-06-12 18:00"); // domingo, misma semana
		venta("3.00", "PAGADO", "2022-06-13 08:00"); // lunes siguiente

		ingresos("SEMANA", "2022-06-01", "2022-06-30").andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].inicio").value("2022-06-06"))
				.andExpect(jsonPath("$[0].pedidosPagados").value(2))
				.andExpect(jsonPath("$[0].ingresoTotal").value(10.00))
				.andExpect(jsonPath("$[1].inicio").value("2022-06-13"))
				.andExpect(jsonPath("$[1].ingresoTotal").value(3.00));
	}

	@Test
	void ingresosPorMesEmpiezanElDiaUno() throws Exception {
		venta("8.00", "PAGADO", "2023-01-15 12:00");
		venta("2.00", "PAGADO", "2023-01-31 23:00");
		venta("1.50", "PAGADO", "2023-02-01 07:00");

		ingresos("MES", "2023-01-01", "2023-02-28").andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].inicio").value("2023-01-01"))
				.andExpect(jsonPath("$[0].ingresoTotal").value(10.00))
				.andExpect(jsonPath("$[0].ticketPromedio").value(5.00))
				.andExpect(jsonPath("$[1].inicio").value("2023-02-01"))
				.andExpect(jsonPath("$[1].ingresoTotal").value(1.50));
	}

	@Test
	void sinFechasSonLosUltimos30DiasEnHoraDeLaBase() throws Exception {
		LocalDate hoy = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
		venta("2.00", "PAGADO", hoy + " 08:00");
		venta("2.00", "PAGADO", hoy.minusDays(29) + " 08:00");
		venta("2.00", "PAGADO", hoy.minusDays(30) + " 08:00");

		List<String> inicios = JsonPath.read(cuerpo(mvc.perform(get("/api/reportes/ingresos").with(como(dueno, "DUENO")))
				.andExpect(status().isOk())), "$[*].inicio");
		assertThat(inicios).contains(hoy.toString(), hoy.minusDays(29).toString())
				.doesNotContain(hoy.minusDays(30).toString());
	}

	@Test
	void desdeDespuesDeHastaOPeriodoDesconocidoResponden400() throws Exception {
		ingresos("DIA", "2021-03-10", "2021-03-01").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
		ingresos("ANIO", "2021-03-01", "2021-03-10").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
	}

	// ---------- RF-22: más vendidos ----------

	@Test
	void masVendidosOrdenaPorUnidadesYSoloCuentaPagados() throws Exception {
		int cat = datos.categoria();
		int primero = datos.producto(cat, "1.00", 0);
		int segundo = datos.producto(cat, "2.00", 0);
		// Cantidades muy por encima de las de otros tests, para ocupar los dos primeros puestos.
		ventaDe(primero, 20_000, "1.00", "PAGADO", "2021-01-10 10:00");
		ventaDe(primero, 10_000, "1.00", "PAGADO", "2021-01-11 10:00");
		ventaDe(segundo, 29_000, "2.00", "PAGADO", "2021-01-10 11:00");
		ventaDe(segundo, 5_000, "2.00", "FALLIDO", "2021-01-12 11:00");

		mvc.perform(get("/api/reportes/mas-vendidos").param("limite", "2").with(como(dueno, "DUENO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].idProducto").value(primero))
				.andExpect(jsonPath("$[0].unidadesVendidas").value(30_000))
				.andExpect(jsonPath("$[0].ingresoGenerado").value(30_000.00))
				.andExpect(jsonPath("$[0].nombre").isNotEmpty())
				.andExpect(jsonPath("$[0].categoria").isNotEmpty())
				.andExpect(jsonPath("$[1].idProducto").value(segundo))
				.andExpect(jsonPath("$[1].unidadesVendidas").value(29_000))
				.andExpect(jsonPath("$[1].ingresoGenerado").value(58_000.00));
	}

	@Test
	void limiteFueraDeRangoResponde400() throws Exception {
		mvc.perform(get("/api/reportes/mas-vendidos").param("limite", "0").with(como(dueno, "DUENO")))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/reportes/mas-vendidos").param("limite", "101").with(como(dueno, "DUENO")))
				.andExpect(status().isBadRequest());
	}

	// ---------- RF-20: ventas ----------

	@Test
	void ventasListaLosPagosPagadosDelRangoMasRecientesPrimero() throws Exception {
		int antigua = venta("3.00", "PAGADO", "2024-05-02 09:00");
		int media = venta("4.00", "PAGADO", "2024-05-03 09:00");
		int reciente = venta("5.00", "PAGADO", "2024-05-04 09:00");
		venta("6.00", "PENDIENTE", "2024-05-04 10:00");
		venta("7.00", "PAGADO", "2024-05-06 10:00"); // fuera del rango

		ventas("2024-05-01", "2024-05-05", 0, 50).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(3))
				.andExpect(jsonPath("$.contenido[0].idPago").value(reciente))
				.andExpect(jsonPath("$.contenido[0].codigoPedido").isNotEmpty())
				.andExpect(jsonPath("$.contenido[0].fecha").value("2024-05-04T09:00:00"))
				.andExpect(jsonPath("$.contenido[0].monto").value(5.00))
				.andExpect(jsonPath("$.contenido[0].metodoPago").value("YAPE"))
				.andExpect(jsonPath("$.contenido[0].estudiante").value("Ana Prueba"))
				.andExpect(jsonPath("$.contenido[1].idPago").value(media))
				.andExpect(jsonPath("$.contenido[2].idPago").value(antigua));
	}

	@Test
	void ventasSePaginan() throws Exception {
		venta("1.00", "PAGADO", "2024-08-01 09:00");
		venta("1.00", "PAGADO", "2024-08-02 09:00");
		int primera = venta("1.00", "PAGADO", "2024-07-31 09:00");

		ventas("2024-07-01", "2024-08-31", 1, 2).andExpect(status().isOk())
				.andExpect(jsonPath("$.pagina").value(1))
				.andExpect(jsonPath("$.tamano").value(2))
				.andExpect(jsonPath("$.totalElementos").value(3))
				.andExpect(jsonPath("$.totalPaginas").value(2))
				.andExpect(jsonPath("$.contenido", hasSize(1)))
				.andExpect(jsonPath("$.contenido[0].idPago").value(primera));
	}

	@Test
	void ventasSinFechasIncluyenLasDeHoyYNoLasViejas() throws Exception {
		LocalDate hoy = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
		int deHoy = venta("2.00", "PAGADO", hoy + " 07:00");
		int vieja = venta("2.00", "PAGADO", "2024-01-15 07:00");

		mvc.perform(get("/api/reportes/ventas").param("tamano", "200").with(como(dueno, "DUENO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido[*].idPago", hasItem(deHoy)))
				.andExpect(jsonPath("$.contenido[*].idPago", not(hasItem(vieja))));
	}

	@Test
	void ventasConDesdeDespuesDeHastaResponde400() throws Exception {
		ventas("2024-05-10", "2024-05-01", 0, 50).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
	}

	// ---------- RNF-05: solo el dueño ----------

	@Test
	void soloElDuenoVeReportes() throws Exception {
		mvc.perform(get("/api/reportes/ingresos").with(como(estudiante, "ESTUDIANTE")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		int trabajador = datos.trabajador("Luis", "Ramos");
		mvc.perform(get("/api/reportes/ventas").with(como(trabajador, "TRABAJADOR"))).andExpect(status().isForbidden());
		mvc.perform(get("/api/reportes/mas-vendidos").with(como(trabajador, "TRABAJADOR"))).andExpect(status().isForbidden());
	}

	// ---------- Ayudas ----------

	/** Venta de una unidad del producto del test, por el monto indicado. Devuelve el id del pago. */
	private int venta(String monto, String estadoPago, String fecha) {
		return ventaDe(producto, 1, monto, estadoPago, fecha);
	}

	/**
	 * Pedido con un detalle y su pago, por SQL y con la fecha fijada. El pago se inserta ya en su estado
	 * final: así no pasa por {@code trg_descontar_stock}, que aquí no interesa.
	 */
	private int ventaDe(int idProducto, int cantidad, String precio, String estadoPago, String fecha) {
		int n = DatosPrueba.siguiente();
		Integer pedido = jdbc.queryForObject("""
				INSERT INTO pedido (codigo_pedido, id_estudiante, hora_recojo, fecha_hora)
				VALUES (?, ?, '12:00', CAST(? AS timestamp)) RETURNING id_pedido""",
				Integer.class, "R-" + n, estudiante, fecha);
		jdbc.update("INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (?, ?, ?, ?)",
				pedido, idProducto, cantidad, new BigDecimal(precio));
		BigDecimal total = jdbc.queryForObject("SELECT total FROM pedido WHERE id_pedido = ?", BigDecimal.class, pedido);
		return jdbc.queryForObject("""
				INSERT INTO pago (id_pedido, monto, metodo_pago, estado, fecha)
				VALUES (?, ?, 'YAPE', CAST(? AS estado_pago_enum), CAST(? AS timestamp)) RETURNING id_pago""",
				Integer.class, pedido, total, estadoPago, fecha);
	}

	private ResultActions ingresos(String periodo, String desde, String hasta) throws Exception {
		return mvc.perform(get("/api/reportes/ingresos").with(como(dueno, "DUENO"))
				.param("periodo", periodo).param("desde", desde).param("hasta", hasta));
	}

	private ResultActions ventas(String desde, String hasta, int pagina, int tamano) throws Exception {
		MockHttpServletRequestBuilder peticion = get("/api/reportes/ventas").with(como(dueno, "DUENO"))
				.param("desde", desde).param("hasta", hasta)
				.param("pagina", String.valueOf(pagina)).param("tamano", String.valueOf(tamano));
		return mvc.perform(peticion);
	}

	private static String cuerpo(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
