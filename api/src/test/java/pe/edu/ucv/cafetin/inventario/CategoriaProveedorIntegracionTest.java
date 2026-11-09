package pe.edu.ucv.cafetin.inventario;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
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

/** Categorías (lectura para todos, RF-04; escritura del dueño, RF-15) y proveedores (solo dueño, RF-15). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CategoriaProveedorIntegracionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	DatosPrueba datos;
	int dueno;

	@BeforeEach
	void preparar() {
		datos = new DatosPrueba(jdbc);
		dueno = datos.dueno();
	}

	// ---------- Categorías ----------

	@Test
	void creaYListaCategoriasParaCualquierRol() throws Exception {
		String nombre = "Postres " + DatosPrueba.siguiente();
		int id = JsonPath.read(cuerpo(enviar(post("/api/categorias"), "{\"nombre\": \"%s\", \"descripcion\": \"Dulces\"}"
				.formatted(nombre)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(notNullValue()))
				.andExpect(jsonPath("$.nombre").value(nombre))
				.andExpect(jsonPath("$.descripcion").value("Dulces"))
				.andExpect(jsonPath("$.activo").value(true))), "$.id");

		mvc.perform(get("/api/categorias").with(como(datos.estudiante(), "ESTUDIANTE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(id)));
	}

	@Test
	void categoriaRepetidaResponde409() throws Exception {
		String nombre = "Bebidas " + DatosPrueba.siguiente();
		enviar(post("/api/categorias"), "{\"nombre\": \"%s\"}".formatted(nombre)).andExpect(status().isCreated());

		enviar(post("/api/categorias"), "{\"nombre\": \"%s\"}".formatted(nombre))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
	}

	@Test
	void editaCategoria() throws Exception {
		int id = datos.categoria();
		String nombre = "Snacks " + DatosPrueba.siguiente();

		enviar(put("/api/categorias/" + id), "{\"nombre\": \"%s\", \"descripcion\": \"Salados\"}".formatted(nombre))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value(nombre))
				.andExpect(jsonPath("$.descripcion").value("Salados"));
		enviar(put("/api/categorias/999999"), "{\"nombre\": \"X\"}").andExpect(status().isNotFound());
	}

	@Test
	void categoriaDesactivadaNoSeListaYSePuedeReactivar() throws Exception {
		int id = datos.categoria();

		enviar(patch("/api/categorias/" + id + "/estado"), "{\"activo\": false}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
		mvc.perform(get("/api/categorias").with(como(dueno, "DUENO")))
				.andExpect(jsonPath("$[*].id", not(hasItem(id))));

		enviar(patch("/api/categorias/" + id + "/estado"), "{\"activo\": true}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));
		enviar(patch("/api/categorias/999999/estado"), "{\"activo\": true}").andExpect(status().isNotFound());
	}

	@Test
	void soloElDuenoEscribeCategorias() throws Exception {
		mvc.perform(post("/api/categorias").with(como(datos.estudiante(), "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": \"Hackeo\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void categoriaSinNombreResponde400() throws Exception {
		enviar(post("/api/categorias"), "{\"nombre\": \" \"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	// ---------- Proveedores ----------

	@Test
	void registraYListaProveedores() throws Exception {
		String nombre = "Distribuidora " + DatosPrueba.siguiente();
		int id = JsonPath.read(cuerpo(enviar(post("/api/proveedores"), """
				{"nombre": "%s", "telefono": "044123456", "correo": "ventas@norsur.pe", "direccion": "Av. España 1234"}"""
				.formatted(nombre)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre").value(nombre))
				.andExpect(jsonPath("$.telefono").value("044123456"))
				.andExpect(jsonPath("$.correo").value("ventas@norsur.pe"))
				.andExpect(jsonPath("$.direccion").value("Av. España 1234"))
				.andExpect(jsonPath("$.activo").value(true))), "$.id");

		mvc.perform(get("/api/proveedores").with(como(dueno, "DUENO")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(id)));
	}

	@Test
	void proveedorConTelefonoInvalidoResponde400() throws Exception {
		enviar(post("/api/proveedores"), "{\"nombre\": \"Malo %d\", \"telefono\": \"abc\"}".formatted(DatosPrueba.siguiente()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	@Test
	void proveedorRepetidoResponde409() throws Exception {
		String nombre = "Repetido " + DatosPrueba.siguiente();
		enviar(post("/api/proveedores"), "{\"nombre\": \"%s\", \"telefono\": \"044123456\"}".formatted(nombre))
				.andExpect(status().isCreated());

		enviar(post("/api/proveedores"), "{\"nombre\": \"%s\", \"telefono\": \"044999999\"}".formatted(nombre))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
	}

	@Test
	void editaYDesactivaProveedor() throws Exception {
		int id = datos.proveedor();

		enviar(put("/api/proveedores/" + id), "{\"nombre\": \"Editado %d\", \"telefono\": \"987654321\"}"
				.formatted(DatosPrueba.siguiente()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.telefono").value("987654321"));
		enviar(patch("/api/proveedores/" + id + "/estado"), "{\"activo\": false}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));

		mvc.perform(get("/api/proveedores").with(como(dueno, "DUENO")))
				.andExpect(jsonPath("$[*].id", hasItem(id)));
		enviar(put("/api/proveedores/999999"), "{\"nombre\": \"X\", \"telefono\": \"044123456\"}")
				.andExpect(status().isNotFound());
	}

	@Test
	void trabajadorNoAccedeAProveedores() throws Exception {
		mvc.perform(get("/api/proveedores").with(como(datos.trabajador("Luis", "Ramos"), "TRABAJADOR")))
				.andExpect(status().isForbidden());
	}

	// ---------- Ayudas ----------

	private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion,
			String json) throws Exception {
		return mvc.perform(peticion.with(como(dueno, "DUENO")).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String cuerpo(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
