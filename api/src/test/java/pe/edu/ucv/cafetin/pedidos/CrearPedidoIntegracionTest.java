package pe.edu.ucv.cafetin.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.ucv.cafetin.soporte.DatosPrueba.como;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.soporte.DatosPrueba;

/** RF-05 (crear pedido), RF-07/RF-08 (ver mi pedido) y RF-09 (historial). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CrearPedidoIntegracionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	DatosPrueba datos;
	int estudiante;
	int cat;

	@BeforeEach
	void preparar() {
		datos = new DatosPrueba(jdbc);
		estudiante = datos.estudiante();
		cat = datos.categoria();
	}

	// ---------- Crear (RF-05) ----------

	@Test
	void pedidoDeDosProductosCalculaElTotalYQuedaPendienteSinPagar() throws Exception {
		int cafe = datos.producto(cat, "2.50", 40);
		int tamal = datos.producto(cat, "4.00", 10);

		crear(estudiante, """
				{"horaRecojo": "12:50", "observacion": "Sin ají",
				 "items": [{"idProducto": %d, "cantidad": 2}, {"idProducto": %d, "cantidad": 1}]}"""
				.formatted(cafe, tamal))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.codigoPedido").value(matchesPattern("PED-\\d+")))
				.andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.horaRecojo").value("12:50"))
				.andExpect(jsonPath("$.observacion").value("Sin ají"))
				.andExpect(jsonPath("$.fechaHora").isNotEmpty())
				.andExpect(jsonPath("$.total").value(9.00))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[?(@.idProducto == %d)].precioUnitario".formatted(cafe)).value(2.50))
				.andExpect(jsonPath("$.items[?(@.idProducto == %d)].subtotal".formatted(cafe)).value(5.00))
				.andExpect(jsonPath("$.items[?(@.idProducto == %d)].nombre".formatted(tamal)).isNotEmpty())
				.andExpect(jsonPath("$.pago").value(nullValue()))
				.andExpect(jsonPath("$.recibo").value(nullValue()));
	}

	@Test
	void crearNoDescuentaStock() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);

		crear(estudiante, unItem(cafe, 3)).andExpect(status().isCreated());

		assertThat(datos.stock(cafe)).isEqualTo(10);
	}

	@Test
	void codigosDePedidoSonDistintos() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);

		String c1 = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 1))), "$.codigoPedido");
		String c2 = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 1))), "$.codigoPedido");

		assertThat(c1).isNotEqualTo(c2);
	}

	@Test
	void guardaElPrecioDelMomentoAunqueDespuesCambie() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		Integer id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 2))), "$.id");

		datos.cambiarPrecio(cafe, "3.00");

		ver(estudiante, id).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].precioUnitario").value(2.50))
				.andExpect(jsonPath("$.total").value(5.00));
	}

	@Test
	void cantidadMayorQueElStockResponde409() throws Exception {
		int tamal = datos.producto(cat, "4.00", 2);

		crear(estudiante, unItem(tamal, 3))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
	}

	@Test
	void productoAgotadoOInactivoOInexistenteResponde409() throws Exception {
		int agotado = datos.producto(cat, "4.00", 0);
		int deBaja = datos.producto(cat, "4.00", 10);
		datos.desactivar(deBaja);

		for (int id : new int[] { agotado, deBaja, 999999 }) {
			crear(estudiante, unItem(id, 1))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
		}
	}

	@Test
	void productoRepetidoResponde400() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);

		crear(estudiante, """
				{"horaRecojo": "12:50", "items": [{"idProducto": %d, "cantidad": 1}, {"idProducto": %d, "cantidad": 2}]}"""
				.formatted(cafe, cafe))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	@Test
	void sinItemsResponde400() throws Exception {
		crear(estudiante, "{\"horaRecojo\": \"12:50\", \"items\": []}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	@Test
	void unTrabajadorNoCreaPedidos() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);

		mvc.perform(post("/api/pedidos").with(como(1, "TRABAJADOR"))
						.contentType(MediaType.APPLICATION_JSON).content(unItem(cafe, 1)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
	}

	// ---------- Ver mi pedido (RF-07, RF-08) ----------

	@Test
	void elEstudianteVeSuPedido() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		Integer id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 2))), "$.id");

		ver(estudiante, id).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.items[0].cantidad").value(2));
	}

	@Test
	void pedidoAjenoOInexistenteResponde404() throws Exception {
		int cafe = datos.producto(cat, "2.50", 10);
		Integer id = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 1))), "$.id");
		int otro = datos.estudiante();

		ver(otro, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
		ver(estudiante, 999999).andExpect(status().isNotFound());
	}

	// ---------- Historial (RF-09) ----------

	@Test
	void historialMuestraSoloLosPropiosYLosMasRecientesPrimero() throws Exception {
		int cafe = datos.producto(cat, "2.50", 20);
		Integer primero = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 1))), "$.id");
		Integer segundo = JsonPath.read(cuerpo(crear(estudiante, unItem(cafe, 3))), "$.id");
		int otro = datos.estudiante();
		crear(otro, unItem(cafe, 1)).andExpect(status().isCreated());

		String cuerpo = cuerpo(mvc.perform(get("/api/pedidos/mios").with(como(estudiante, "ESTUDIANTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.contenido[0].cantidadProductos").value(3))
				.andExpect(jsonPath("$.contenido[0].estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.contenido[0].estadoPago").value(nullValue()))
				.andExpect(jsonPath("$.contenido[0].total").value(7.50)));

		assertThat(JsonPath.<List<Integer>>read(cuerpo, "$.contenido[*].id")).containsExactly(segundo, primero);
	}

	@Test
	void historialPagina() throws Exception {
		int cafe = datos.producto(cat, "2.50", 20);
		for (int i = 0; i < 3; i++) {
			crear(estudiante, unItem(cafe, 1)).andExpect(status().isCreated());
		}

		mvc.perform(get("/api/pedidos/mios").param("pagina", "1").param("tamano", "2")
						.with(como(estudiante, "ESTUDIANTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.totalElementos").value(3))
				.andExpect(jsonPath("$.totalPaginas").value(2));
	}

	// ---------- Ayudas ----------

	private ResultActions crear(int idEstudiante, String json) throws Exception {
		return mvc.perform(post("/api/pedidos").with(como(idEstudiante, "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions ver(int idEstudiante, int idPedido) throws Exception {
		return mvc.perform(get("/api/pedidos/" + idPedido).with(como(idEstudiante, "ESTUDIANTE")));
	}

	private static String unItem(int idProducto, int cantidad) {
		return "{\"horaRecojo\": \"12:50\", \"items\": [{\"idProducto\": %d, \"cantidad\": %d}]}"
				.formatted(idProducto, cantidad);
	}

	private static String cuerpo(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
