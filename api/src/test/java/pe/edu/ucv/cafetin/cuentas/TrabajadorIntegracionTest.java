package pe.edu.ucv.cafetin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.cuentas.modelo.Dueno;
import pe.edu.ucv.cafetin.cuentas.modelo.Estudiante;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

/** RF-02: el dueño registra, lista, activa y desactiva trabajadores. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TrabajadorIntegracionTest {

	private static final String CLAVE = "Cafetin2026";
	private static final AtomicInteger SECUENCIA = new AtomicInteger(3000);

	@Autowired
	MockMvc mvc;

	@Autowired
	UsuarioRepository usuarios;

	@Autowired
	PasswordEncoder encoder;

	@Test
	void duenoRegistraTrabajadorQuePuedeIniciarSesion() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		String correo = "trb" + n + "@cafetin.pe";

		alta(bearerDueno(), correo, "T" + n, "TARDE")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.correo").value(correo))
				.andExpect(jsonPath("$.codigoEmpleado").value("T" + n))
				.andExpect(jsonPath("$.turno").value("TARDE"))
				.andExpect(jsonPath("$.fechaIngreso").isNotEmpty())
				.andExpect(jsonPath("$.activo").value(true));

		login(correo).andExpect(status().isOk()).andExpect(jsonPath("$.usuario.rol").value("TRABAJADOR"));
	}

	@Test
	void listaIncluyeALosRegistrados() throws Exception {
		String dueno = bearerDueno();
		int n = SECUENCIA.incrementAndGet();
		alta(dueno, "lista" + n + "@cafetin.pe", "T" + n, "MANANA").andExpect(status().isCreated());

		String cuerpo = mvc.perform(get("/api/trabajadores").header(HttpHeaders.AUTHORIZATION, dueno))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(JsonPath.<java.util.List<String>>read(cuerpo, "$[*].correo")).contains("lista" + n + "@cafetin.pe");
		assertThat(cuerpo).doesNotContain("contrasena");
	}

	@Test
	void correoOCodigoDuplicadoResponde409() throws Exception {
		String dueno = bearerDueno();
		int n = SECUENCIA.incrementAndGet();
		alta(dueno, "dup" + n + "@cafetin.pe", "T" + n, "NOCHE").andExpect(status().isCreated());

		alta(dueno, "DUP" + n + "@cafetin.pe", "U" + n, "NOCHE").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
		alta(dueno, "otro" + n + "@cafetin.pe", "T" + n, "NOCHE").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
	}

	@Test
	void turnoInexistenteResponde400() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		alta(bearerDueno(), "turno" + n + "@cafetin.pe", "T" + n, "MADRUGADA").andExpect(status().isBadRequest());
	}

	@Test
	void desactivarBloqueaElLoginYElTokenYaEmitido() throws Exception {
		String dueno = bearerDueno();
		int n = SECUENCIA.incrementAndGet();
		String correo = "baja" + n + "@cafetin.pe";
		Integer id = JsonPath.read(alta(dueno, correo, "T" + n, "MANANA").andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8), "$.id");
		String tokenTrabajador = "Bearer " + JsonPath.read(login(correo).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8), "$.token");

		cambiarEstado(dueno, id, false).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));

		login(correo).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/despacho/pedidos").header(HttpHeaders.AUTHORIZATION, tokenTrabajador))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

		cambiarEstado(dueno, id, true).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(true));

		login(correo).andExpect(status().isOk());
	}

	@Test
	void trabajadorInexistenteResponde404() throws Exception {
		cambiarEstado(bearerDueno(), 999999, false).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
	}

	@Test
	void unEstudianteNoSeGestionaComoTrabajador() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		Estudiante e = usuarios.save(new Estudiante("Ana", "Prueba", "est" + n + "@ucvvirtual.edu.pe",
				encoder.encode(CLAVE), "E" + n, "Sistemas", (short) 6));

		cambiarEstado(bearerDueno(), e.getId(), false).andExpect(status().isNotFound());
	}

	@Test
	void soloElDuenoGestionaTrabajadores() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		String correo = "t" + n + "@cafetin.pe";
		alta(bearerDueno(), correo, "T" + n, "MANANA").andExpect(status().isCreated());
		String trabajador = "Bearer " + JsonPath.read(login(correo).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8), "$.token");

		alta(trabajador, "z" + n + "@cafetin.pe", "Z" + n, "MANANA").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
	}

	// ---------- Ayudas ----------

	private ResultActions alta(String bearer, String correo, String codigo, String turno) throws Exception {
		return mvc.perform(post("/api/trabajadores").header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"nombre": "Carmen", "apellidos": "Prueba", "correo": "%s", "contrasena": "%s",
						 "telefono": "912345678", "codigoEmpleado": "%s", "turno": "%s"}""".formatted(correo, CLAVE, codigo, turno)));
	}

	private ResultActions cambiarEstado(String bearer, int id, boolean activo) throws Exception {
		return mvc.perform(patch("/api/trabajadores/" + id + "/estado").header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON).content("{\"activo\": " + activo + "}"));
	}

	private ResultActions login(String correo) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"%s\", \"contrasena\": \"%s\"}".formatted(correo, CLAVE)));
	}

	private String bearerDueno() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		String correo = "dueno" + n + "@cafetin.pe";
		usuarios.save(new Dueno("Rosa", "Prueba", correo, encoder.encode(CLAVE)));
		return "Bearer " + JsonPath.read(login(correo).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8), "$.token");
	}

}
