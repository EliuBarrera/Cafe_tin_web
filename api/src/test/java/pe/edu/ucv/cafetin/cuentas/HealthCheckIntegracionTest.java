package pe.edu.ucv.cafetin.cuentas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;

/** RNF-06: Render consulta el health check sin token; el resto de Actuator no queda expuesto. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class HealthCheckIntegracionTest {

	@Autowired
	MockMvc mvc;

	@Test
	void livenessRespondeSinTokenParaRender() throws Exception {
		mvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void healthNoMuestraDetallesDeLaBase() throws Exception {
		mvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void otrosEndpointsDeActuatorNoEstanDisponibles() throws Exception {
		mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
		mvc.perform(get("/actuator")).andExpect(status().isUnauthorized());
	}

}
