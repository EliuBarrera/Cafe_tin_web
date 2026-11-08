package pe.edu.ucv.cafetin.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.soporte.DatosPrueba;

/** RF-04: catálogo con disponibilidad, desde v_catalogo_disponible. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CatalogoIntegracionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	DatosPrueba datos;

	@BeforeEach
	void preparar() {
		datos = new DatosPrueba(jdbc);
	}

	@Test
	void muestraActivosConSuEstadoDeStockYOcultaLosDadosDeBaja() throws Exception {
		int cat = datos.categoria();
		int disponible = datos.producto(cat, "2.50", 40);
		int pocas = datos.producto(cat, "4.00", 3);
		int agotado = datos.producto(cat, "5.50", 0);
		int deBaja = datos.producto(cat, "3.00", 10);
		datos.desactivar(deBaja);

		String cuerpo = mvc.perform(get("/api/catalogo").param("categoria", String.valueOf(cat))
						.with(como(datos.estudiante(), "ESTUDIANTE")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(JsonPath.<List<Integer>>read(cuerpo, "$[*].idProducto"))
				.containsExactlyInAnyOrder(disponible, pocas, agotado);
		assertThat(estado(cuerpo, disponible)).isEqualTo("DISPONIBLE");
		assertThat(estado(cuerpo, pocas)).isEqualTo("POCAS UNIDADES");
		assertThat(estado(cuerpo, agotado)).isEqualTo("AGOTADO");
		assertThat(JsonPath.<List<Double>>read(cuerpo, "$[?(@.idProducto == " + disponible + ")].precio"))
				.containsExactly(2.50);
	}

	@Test
	void filtraPorCategoria() throws Exception {
		int cat1 = datos.categoria();
		int cat2 = datos.categoria();
		int p1 = datos.producto(cat1, "2.00", 10);
		int p2 = datos.producto(cat2, "2.00", 10);

		String cuerpo = mvc.perform(get("/api/catalogo").param("categoria", String.valueOf(cat2))
						.with(como(1, "TRABAJADOR")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(JsonPath.<List<Integer>>read(cuerpo, "$[*].idProducto")).containsExactly(p2).doesNotContain(p1);
	}

	@Test
	void sinFiltroIncluyeTodasLasCategorias() throws Exception {
		int p1 = datos.producto(datos.categoria(), "2.00", 10);
		int p2 = datos.producto(datos.categoria(), "2.00", 10);

		String cuerpo = mvc.perform(get("/api/catalogo").with(como(1, "DUENO")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(JsonPath.<List<Integer>>read(cuerpo, "$[*].idProducto")).contains(p1, p2);
	}

	@Test
	void sinTokenResponde401() throws Exception {
		mvc.perform(get("/api/catalogo"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	private static String estado(String cuerpo, int idProducto) {
		return JsonPath.<List<String>>read(cuerpo, "$[?(@.idProducto == " + idProducto + ")].estadoStock").getFirst();
	}

}
