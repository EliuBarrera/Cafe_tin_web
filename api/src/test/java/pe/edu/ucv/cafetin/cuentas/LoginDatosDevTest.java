package pe.edu.ucv.cafetin.cuentas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;

/** Criterio de la Fase 2: los tres roles del seed (V2) inician sesión con su contraseña documentada. */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/datos-dev")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LoginDatosDevTest {

	@Autowired
	MockMvc mvc;

	@ParameterizedTest
	@CsvSource({
			"rosa.quispe@cafetin.pe, DUENO",
			"carmen.diaz@cafetin.pe, TRABAJADOR",
			"ebarreram@ucvvirtual.edu.pe, ESTUDIANTE" })
	void losTresRolesDelSeedInicianSesion(String correo, String rol) throws Exception {
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"%s\", \"contrasena\": \"Cafetin2026\"}".formatted(correo)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.rol").value(rol));
	}

}
