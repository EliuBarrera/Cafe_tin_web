package pe.edu.ucv.cafetin.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;

/**
 * Protege el contrato congelado: el OpenAPI que genera el código debe ser idéntico a
 * {@code docs/openapi.yaml}. Si cambias un endpoint o un DTO a propósito (y avisaste al equipo),
 * regenera el archivo con:
 *
 * <pre>./mvnw test -Dtest=ContratoOpenApiTest -Dcontrato.actualizar=true</pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@WithMockUser
class ContratoOpenApiTest {

	static final Path CONTRATO = Path.of("..", "docs", "openapi.yaml");

	@Autowired
	MockMvc mvc;

	@Test
	void openApiGeneradoCoincideConDocsOpenapiYaml() throws Exception {
		String generado = mvc.perform(get("/v3/api-docs.yaml")).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);

		assertThat(generado).contains("/api/pedidos/{id}/pago", "/api/despacho/pedidos/{id}/entregar", "ErrorRespuesta");

		if (Boolean.getBoolean("contrato.actualizar")) {
			Files.writeString(CONTRATO, generado, StandardCharsets.UTF_8);
		}
		assertThat(CONTRATO).as("Falta docs/openapi.yaml: genera con -Dcontrato.actualizar=true").exists();
		assertThat(Files.readString(CONTRATO, StandardCharsets.UTF_8))
				.as("El contrato cambió. Si fue a propósito y avisaste al equipo, regenera docs/openapi.yaml "
						+ "con: ./mvnw test -Dtest=ContratoOpenApiTest -Dcontrato.actualizar=true")
				.isEqualTo(generado);
	}

}
