package pe.edu.ucv.cafetin.comun;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;

/**
 * Formato único de error {codigo, mensaje, detalles} a través de controllers reales.
 * Usuario simulado con rol ESTUDIANTE, el que exige {@code /api/pedidos/**}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@WithMockUser(roles = "ESTUDIANTE")
class ManejadorErroresTest {

	@Autowired
	MockMvc mvc;

	@Test
	void endpointSinImplementarResponde501() throws Exception {
		mvc.perform(post("/api/pedidos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"horaRecojo": "12:50", "items": [{"idProducto": 7, "cantidad": 2}]}"""))
				.andExpect(status().isNotImplemented())
				.andExpect(jsonPath("$.codigo").value("NO_IMPLEMENTADO"))
				.andExpect(jsonPath("$.mensaje").value("Pendiente de implementar (RF-05)"))
				.andExpect(jsonPath("$.detalles").isEmpty());
	}

	@Test
	void cuerpoConCamposInvalidosResponde400ConDetalles() throws Exception {
		mvc.perform(post("/api/pedidos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
				{"items": [{"idProducto": 7, "cantidad": 0}]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.detalles[?(@.campo == 'horaRecojo')]").exists())
				.andExpect(jsonPath("$.detalles[?(@.campo == 'items[0].cantidad')]").exists());
	}

	@Test
	void pagoEnEfectivoSeRechaza() throws Exception {
		mvc.perform(post("/api/pedidos/1/pago").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"metodoPago\": \"EFECTIVO\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.detalles[0].mensaje").value("EFECTIVO no está disponible: usa YAPE, PLIN o TARJETA"));
	}

	@Test
	void jsonMalFormadoResponde400() throws Exception {
		mvc.perform(post("/api/pedidos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{ no es json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CUERPO_INVALIDO"));
	}

	@Test
	void idNoNumericoResponde400() throws Exception {
		mvc.perform(get("/api/pedidos/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
	}

	@Test
	@WithMockUser(roles = "DUENO")
	void parametroFueraDeRangoResponde400() throws Exception {
		mvc.perform(get("/api/reportes/mas-vendidos").param("limite", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.detalles[0].campo").value("limite"));
	}

	@Test
	void rutaInexistenteResponde404() throws Exception {
		// Bajo /api/usuarios/me/**: otra ruta la denegaría la seguridad antes de llegar aquí.
		mvc.perform(get("/api/usuarios/me/no-existe"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
	}

}
