package pe.edu.ucv.cafetin.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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

/** Productos del dueño: RF-15 (alta con stock inicial) y RF-19 (ver, editar, deshabilitar). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProductoIntegracionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	DatosPrueba datos;
	int dueno;
	int cat;
	int prov;

	@BeforeEach
	void preparar() {
		datos = new DatosPrueba(jdbc);
		dueno = datos.dueno();
		cat = datos.categoria();
		prov = datos.proveedor();
	}

	// ---------- RF-15: alta con stock inicial ----------

	@Test
	void registraElProductoConSuInventarioYApareceEnElCatalogo() throws Exception {
		String nombre = "Chifles " + DatosPrueba.siguiente();

		String json = cuerpo(registrar(producto(nombre, "2.00", cat, prov, 30, 10))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(notNullValue()))
				.andExpect(jsonPath("$.nombre").value(nombre))
				.andExpect(jsonPath("$.precio").value(2.00))
				.andExpect(jsonPath("$.idCategoria").value(cat))
				.andExpect(jsonPath("$.categoria").value(notNullValue()))
				.andExpect(jsonPath("$.idProveedor").value(prov))
				.andExpect(jsonPath("$.proveedor").value(notNullValue()))
				.andExpect(jsonPath("$.activo").value(true))
				.andExpect(jsonPath("$.cantidadDisponible").value(30))
				.andExpect(jsonPath("$.stockMinimo").value(10))
				.andExpect(jsonPath("$.estadoStock").value("DISPONIBLE"))
				.andExpect(jsonPath("$.fechaActualizacionStock").value(notNullValue())));
		int id = JsonPath.read(json, "$.id");

		assertThat(jdbc.queryForObject("SELECT cantidad_disponible FROM inventario WHERE id_producto = ?", Integer.class, id))
				.isEqualTo(30);
		mvc.perform(get("/api/catalogo").with(como(datos.estudiante(), "ESTUDIANTE")))
				.andExpect(jsonPath("$[*].idProducto", hasItem(id)));
	}

	@Test
	void sinProveedorEsDePreparacionPropia() throws Exception {
		registrar(producto("Jugo " + DatosPrueba.siguiente(), "4.50", cat, null, 12, 3))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.idProveedor").value(nullValue()))
				.andExpect(jsonPath("$.proveedor").value(nullValue()));
	}

	@Test
	void elEstadoDelStockSaleDeLaCantidadYElMinimo() throws Exception {
		registrar(producto("Agotado " + DatosPrueba.siguiente(), "1.00", cat, null, 0, 5))
				.andExpect(jsonPath("$.estadoStock").value("AGOTADO"));
		registrar(producto("Pocas " + DatosPrueba.siguiente(), "1.00", cat, null, 5, 5))
				.andExpect(jsonPath("$.estadoStock").value("POCAS UNIDADES"));
	}

	@Test
	void nombreRepetidoResponde409YNoCreaNada() throws Exception {
		String nombre = "Tamal " + DatosPrueba.siguiente();
		registrar(producto(nombre, "4.00", cat, null, 10, 2)).andExpect(status().isCreated());

		registrar(producto(nombre, "5.00", cat, null, 10, 2))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM producto WHERE nombre = ?", Integer.class, nombre)).isEqualTo(1);
	}

	@Test
	void categoriaInexistenteOInactivaResponde409() throws Exception {
		registrar(producto("Sin cat " + DatosPrueba.siguiente(), "1.00", 999_999, null, 1, 1))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));

		int inactiva = datos.categoria();
		datos.desactivarCategoria(inactiva);
		registrar(producto("Cat inactiva " + DatosPrueba.siguiente(), "1.00", inactiva, null, 1, 1))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
	}

	@Test
	void proveedorInexistenteResponde409() throws Exception {
		registrar(producto("Sin prov " + DatosPrueba.siguiente(), "1.00", cat, 999_999, 1, 1))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
	}

	@Test
	void datosInvalidosResponden400() throws Exception {
		registrar(producto("Gratis " + DatosPrueba.siguiente(), "0.00", cat, null, 1, 1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
		registrar(producto("Negativo " + DatosPrueba.siguiente(), "1.00", cat, null, -1, 1))
				.andExpect(status().isBadRequest());
	}

	// ---------- RF-19: listar, ver, editar, deshabilitar ----------

	@Test
	void listaTodosOFiltraPorActivo() throws Exception {
		int activo = datos.producto(cat, "2.00", 10);
		int baja = datos.producto(cat, "2.00", 10);
		datos.desactivar(baja);

		listar(null).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(activo)))
				.andExpect(jsonPath("$[*].id", hasItem(baja)));
		listar("true").andExpect(jsonPath("$[*].id", hasItem(activo)))
				.andExpect(jsonPath("$[*].id", not(hasItem(baja))));
		listar("false").andExpect(jsonPath("$[*].id", hasItem(baja)))
				.andExpect(jsonPath("$[*].id", not(hasItem(activo))));
	}

	@Test
	void verProducto() throws Exception {
		int id = datos.producto(cat, "3.50", 7);

		mvc.perform(get("/api/productos/" + id).with(como(dueno, "DUENO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.precio").value(3.50))
				.andExpect(jsonPath("$.cantidadDisponible").value(7))
				.andExpect(jsonPath("$.stockMinimo").value(5));
		mvc.perform(get("/api/productos/999999").with(como(dueno, "DUENO")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
	}

	@Test
	void editarCambiaDatosYMinimoPeroNoElStock() throws Exception {
		int id = datos.producto(cat, "3.50", 20);
		int otraCat = datos.categoria();
		String nombre = "Renombrado " + DatosPrueba.siguiente();

		actualizar(id, edicion(nombre, "4.20", otraCat, prov, 8))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value(nombre))
				.andExpect(jsonPath("$.precio").value(4.20))
				.andExpect(jsonPath("$.idCategoria").value(otraCat))
				.andExpect(jsonPath("$.idProveedor").value(prov))
				.andExpect(jsonPath("$.stockMinimo").value(8))
				.andExpect(jsonPath("$.cantidadDisponible").value(20));
	}

	@Test
	void editarConElNombreDeOtroProductoResponde409() throws Exception {
		String ocupado = "Ocupado " + DatosPrueba.siguiente();
		registrar(producto(ocupado, "1.00", cat, null, 1, 1)).andExpect(status().isCreated());
		int id = datos.producto(cat, "2.00", 5);

		actualizar(id, edicion(ocupado, "2.00", cat, null, 5))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
	}

	@Test
	void editarConservandoSuPropioNombreSePermite() throws Exception {
		String nombre = "Mismo " + DatosPrueba.siguiente();
		int id = JsonPath.read(cuerpo(registrar(producto(nombre, "1.00", cat, null, 1, 1))), "$.id");

		actualizar(id, edicion(nombre, "1.50", cat, null, 1)).andExpect(status().isOk())
				.andExpect(jsonPath("$.precio").value(1.50));
	}

	@Test
	void editarProductoInexistenteResponde404() throws Exception {
		actualizar(999_999, edicion("X " + DatosPrueba.siguiente(), "1.00", cat, null, 1))
				.andExpect(status().isNotFound());
	}

	@Test
	void deshabilitarLoOcultaDelCatalogoYHabilitarLoDevuelve() throws Exception {
		int id = datos.producto(cat, "2.00", 10);
		int estudiante = datos.estudiante();

		cambiarEstado(id, false).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
		mvc.perform(get("/api/catalogo").with(como(estudiante, "ESTUDIANTE")))
				.andExpect(jsonPath("$[*].idProducto", not(hasItem(id))));

		cambiarEstado(id, true).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
		mvc.perform(get("/api/catalogo").with(como(estudiante, "ESTUDIANTE")))
				.andExpect(jsonPath("$[*].idProducto", hasItem(id)));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM producto WHERE id_producto = ?", Integer.class, id)).isEqualTo(1);
	}

	@Test
	void cambiarEstadoDeProductoInexistenteResponde404() throws Exception {
		cambiarEstado(999_999, false).andExpect(status().isNotFound());
	}

	// ---------- RNF-05: solo el dueño ----------

	@Test
	void estudianteYTrabajadorNoAccedenAProductos() throws Exception {
		mvc.perform(get("/api/productos").with(como(datos.estudiante(), "ESTUDIANTE")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		int trabajador = datos.trabajador("Luis", "Ramos");
		mvc.perform(post("/api/productos").with(como(trabajador, "TRABAJADOR"))
				.contentType(MediaType.APPLICATION_JSON).content(producto("X", "1.00", cat, null, 1, 1)))
				.andExpect(status().isForbidden());
	}

	// ---------- Ayudas ----------

	private static String producto(String nombre, String precio, int idCategoria, Integer idProveedor, int stock, int minimo) {
		return """
				{"nombre": "%s", "descripcion": "De prueba", "precio": %s, "idCategoria": %d, "idProveedor": %s,
				 "stockInicial": %d, "stockMinimo": %d}""".formatted(nombre, precio, idCategoria, idProveedor, stock, minimo);
	}

	private static String edicion(String nombre, String precio, int idCategoria, Integer idProveedor, int minimo) {
		return """
				{"nombre": "%s", "descripcion": "Editado", "precio": %s, "idCategoria": %d, "idProveedor": %s,
				 "stockMinimo": %d}""".formatted(nombre, precio, idCategoria, idProveedor, minimo);
	}

	private ResultActions registrar(String json) throws Exception {
		return mvc.perform(post("/api/productos").with(como(dueno, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions actualizar(int id, String json) throws Exception {
		return mvc.perform(put("/api/productos/" + id).with(como(dueno, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions cambiarEstado(int id, boolean activo) throws Exception {
		return mvc.perform(patch("/api/productos/" + id + "/estado").with(como(dueno, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\": " + activo + "}"));
	}

	private ResultActions listar(String activo) throws Exception {
		var peticion = get("/api/productos").with(como(dueno, "DUENO"));
		if (activo != null) {
			peticion.param("activo", activo);
		}
		return mvc.perform(peticion);
	}

	private static String cuerpo(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
