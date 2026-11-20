package pe.edu.ucv.cafetin.cafeterias;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.ucv.cafetin.soporte.DatosPrueba.como;

import java.nio.charset.StandardCharsets;

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

/**
 * Varios cafetines en una instalación (V11): el dueño y el trabajador solo ven y cambian lo de su
 * cafetería (lo ajeno responde 404, como un pedido ajeno) y el pedido del estudiante es de un solo cafetín.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AislamientoCafeteriasIntegracionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	DatosPrueba datos;
	int cafeteriaA;
	int cafeteriaB;
	int duenoA;
	int duenoB;
	int trabajadorB;
	int estudiante;
	int categoriaA;
	int productoA;
	int productoB;

	@BeforeEach
	void preparar() {
		datos = new DatosPrueba(jdbc);
		cafeteriaA = datos.cafeteria();
		cafeteriaB = datos.cafeteria();
		duenoA = datos.dueno(cafeteriaA);
		duenoB = datos.dueno(cafeteriaB);
		trabajadorB = datos.trabajador(cafeteriaB, "Luis", "Ramos");
		estudiante = datos.estudiante();
		categoriaA = datos.categoria(cafeteriaA);
		productoA = datos.producto(categoriaA, "2.50", 30);
		productoB = datos.producto(datos.categoria(cafeteriaB), "3.00", 30);
	}

	// ---------- Estudiante ----------

	@Test
	void estudianteVeLosCafetinesActivos() throws Exception {
		jdbc.update("UPDATE cafeteria SET activo = FALSE WHERE id_cafeteria = ?", cafeteriaB);

		mvc.perform(get("/api/cafeterias").with(como(estudiante, "ESTUDIANTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(cafeteriaA)))
				.andExpect(jsonPath("$[*].id", not(hasItem(cafeteriaB))));
	}

	@Test
	void catalogoSoloTraeProductosDelCafetinElegido() throws Exception {
		mvc.perform(get("/api/catalogo").param("cafeteria", String.valueOf(cafeteriaA))
				.with(como(estudiante, "ESTUDIANTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].idProducto", hasItem(productoA)))
				.andExpect(jsonPath("$[*].idProducto", not(hasItem(productoB))));
	}

	@Test
	void catalogoSinCafetinResponde400() throws Exception {
		mvc.perform(get("/api/catalogo").with(como(estudiante, "ESTUDIANTE")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void pedidoConProductoDeOtroCafetinResponde409() throws Exception {
		crearPedido(cafeteriaA, productoB)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
	}

	@Test
	void pedidoEnCafetinInexistenteResponde404() throws Exception {
		crearPedido(999_999, productoA).andExpect(status().isNotFound());
	}

	@Test
	void pedidoDevuelveSuCafetinTambienEnElHistorial() throws Exception {
		crearPedido(cafeteriaA, productoA)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.cafeteria.id").value(cafeteriaA));

		mvc.perform(get("/api/pedidos/mios").with(como(estudiante, "ESTUDIANTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido[0].cafeteria.id").value(cafeteriaA));
	}

	// ---------- Dueño ----------

	@Test
	void duenoSoloListaYEditaProductosDeSuCafeteria() throws Exception {
		mvc.perform(get("/api/productos").with(como(duenoA, "DUENO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(productoA)))
				.andExpect(jsonPath("$[*].id", not(hasItem(productoB))));

		mvc.perform(get("/api/productos/" + productoB).with(como(duenoA, "DUENO")))
				.andExpect(status().isNotFound());
		mvc.perform(patch("/api/productos/" + productoB + "/estado").with(como(duenoA, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\": false}"))
				.andExpect(status().isNotFound());
		mvc.perform(patch("/api/inventario/" + productoB + "/recarga").with(como(duenoA, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\": 5}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void duenoNoUsaCategoriasNiProveedoresDeOtraCafeteria() throws Exception {
		mvc.perform(post("/api/productos").with(como(duenoB, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"idCategoria": %d, "nombre": "Ajeno %d", "precio": 2.00, "stockInicial": 5, "stockMinimo": 1}"""
						.formatted(categoriaA, DatosPrueba.siguiente())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));

		mvc.perform(put("/api/categorias/" + categoriaA).with(como(duenoB, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"Robada\"}"))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/categorias").with(como(duenoB, "DUENO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", not(hasItem(categoriaA))));

		int proveedorA = datos.proveedor(cafeteriaA);
		mvc.perform(get("/api/proveedores").with(como(duenoB, "DUENO")))
				.andExpect(jsonPath("$[*].id", not(hasItem(proveedorA))));
		mvc.perform(patch("/api/proveedores/" + proveedorA + "/estado").with(como(duenoB, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\": false}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void dosCafeteriasPuedenUsarElMismoNombreDeCategoria() throws Exception {
		String nombre = "Postres " + DatosPrueba.siguiente();
		for (int dueno : new int[] { duenoA, duenoB }) {
			mvc.perform(post("/api/categorias").with(como(dueno, "DUENO"))
					.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"%s\"}".formatted(nombre)))
					.andExpect(status().isCreated());
		}
	}

	@Test
	void duenoSoloVeYGestionaTrabajadoresDeSuCafeteria() throws Exception {
		mvc.perform(get("/api/trabajadores").with(como(duenoA, "DUENO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", not(hasItem(trabajadorB))));
		mvc.perform(patch("/api/trabajadores/" + trabajadorB + "/estado").with(como(duenoA, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\": false}"))
				.andExpect(status().isNotFound());

		int n = DatosPrueba.siguiente();
		int nuevo = JsonPath.read(cuerpo(mvc.perform(post("/api/trabajadores").with(como(duenoB, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"nombre": "Ana", "apellidos": "Nueva", "correo": "nueva%d@cafetin.pe", "contrasena": "Cafetin2026",
						 "codigoEmpleado": "N%d", "turno": "TARDE"}""".formatted(n, n)))
				.andExpect(status().isCreated())), "$.id");
		Integer suCafeteria = jdbc.queryForObject("SELECT id_cafeteria FROM trabajador WHERE id_usuario = ?",
				Integer.class, nuevo);
		org.assertj.core.api.Assertions.assertThat(suCafeteria).isEqualTo(cafeteriaB);
	}

	@Test
	void reportesSoloCuentanVentasDeSuCafeteria() throws Exception {
		pagar(crearPedidoId(cafeteriaB, productoB));

		mvc.perform(get("/api/reportes/ventas").with(como(duenoA, "DUENO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0));
		mvc.perform(get("/api/reportes/mas-vendidos").with(como(duenoA, "DUENO")))
				.andExpect(jsonPath("$[*].idProducto", not(hasItem(productoB))));
		mvc.perform(get("/api/reportes/ventas").with(como(duenoB, "DUENO")))
				.andExpect(jsonPath("$.totalElementos").value(1));
	}

	@Test
	void perfilDelDuenoIncluyeSuCafeteria() throws Exception {
		mvc.perform(get("/api/usuarios/me").with(como(duenoA, "DUENO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cafeteria.id").value(cafeteriaA));
		mvc.perform(get("/api/usuarios/me").with(como(estudiante, "ESTUDIANTE")))
				.andExpect(jsonPath("$.cafeteria").doesNotExist());
	}

	// ---------- Despacho ----------

	@Test
	void trabajadorSoloVeYMuevePedidosDeSuCafeteria() throws Exception {
		int pedidoA = crearPedidoId(cafeteriaA, productoA);
		int pedidoB = crearPedidoId(cafeteriaB, productoB);
		pagar(pedidoA);
		pagar(pedidoB);

		mvc.perform(get("/api/despacho/pedidos").with(como(trabajadorB, "TRABAJADOR")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].idPedido", hasItem(pedidoB)))
				.andExpect(jsonPath("$[*].idPedido", not(hasItem(pedidoA))));
		mvc.perform(post("/api/despacho/pedidos/" + pedidoA + "/confirmar").with(como(trabajadorB, "TRABAJADOR")))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/despacho/pedidos/" + pedidoA + "/confirmar").with(como(duenoA, "DUENO")))
				.andExpect(status().isOk());
	}

	@Test
	void alertasDeStockSoloLleganAlDuenoDeLaCafeteria() throws Exception {
		jdbc.update("UPDATE inventario SET cantidad_disponible = 0 WHERE id_producto = ?", productoB);

		mvc.perform(get("/api/alertas").with(como(duenoA, "DUENO")))
				.andExpect(jsonPath("$[*].idProducto", not(hasItem(productoB))));
		mvc.perform(get("/api/alertas").with(como(duenoB, "DUENO")))
				.andExpect(jsonPath("$[*].idProducto", hasItem(productoB)))
				.andExpect(jsonPath("$[*].idProducto", everyItem(is(productoB))));
	}

	// ---------- Ayudas ----------

	private ResultActions crearPedido(int cafeteria, int producto) throws Exception {
		return mvc.perform(post("/api/pedidos").with(como(estudiante, "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idCafeteria\": %d, \"horaRecojo\": \"12:30\", \"items\": [{\"idProducto\": %d, \"cantidad\": 1}]}"
						.formatted(cafeteria, producto)));
	}

	private int crearPedidoId(int cafeteria, int producto) throws Exception {
		return JsonPath.read(cuerpo(crearPedido(cafeteria, producto).andExpect(status().isCreated())), "$.id");
	}

	private void pagar(int pedido) throws Exception {
		mvc.perform(post("/api/pedidos/" + pedido + "/pago").with(como(estudiante, "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\": \"YAPE\"}"))
				.andExpect(status().isOk());
	}

	private static String cuerpo(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
