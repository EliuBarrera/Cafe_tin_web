package pe.edu.ucv.cafetin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.cuentas.modelo.Estudiante;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;
import pe.edu.ucv.cafetin.cuentas.service.EnvioRecuperacion;

/** RF-03: token de un solo uso, guardado como hash y con expiración. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RecuperacionIntegracionTest {

	private static final String CLAVE = "Cafetin2026";
	private static final String NUEVA = "NuevaClave2026";
	private static final AtomicInteger SECUENCIA = new AtomicInteger(2000);
	/** El enlace sale en otro hilo después del commit: se espera hasta este límite a que llegue (o no). */
	private static final long ESPERA_ENVIO = 3000;
	private static final long ESPERA_SIN_ENVIO = 300;

	@Autowired
	MockMvc mvc;

	@Autowired
	UsuarioRepository usuarios;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	EnvioRecuperacion envio;

	@BeforeEach
	void limpiar() {
		clearInvocations(envio);
	}

	@Test
	void flujoCompletoCambiaLaContrasena() throws Exception {
		Estudiante e = crearEstudiante();
		String token = pedirToken(e);

		restablecer(token, NUEVA).andExpect(status().isNoContent());

		login(e.getCorreo(), NUEVA).andExpect(status().isOk());
		login(e.getCorreo(), CLAVE).andExpect(status().isUnauthorized());
	}

	@Test
	void seGuardaElHashNoElToken() throws Exception {
		Estudiante e = crearEstudiante();
		String token = pedirToken(e);

		String guardado = jdbc.queryForObject("SELECT token_recupera FROM cafetin.usuario WHERE id_usuario = ?",
				String.class, e.getId());
		assertThat(guardado).isNotBlank().isNotEqualTo(token).doesNotContain(token);
	}

	@Test
	void expiraEnTreintaMinutosSegunElRelojDeLaBase() throws Exception {
		Estudiante e = crearEstudiante();
		pedirToken(e);

		Integer minutos = jdbc.queryForObject("""
				SELECT round(EXTRACT(EPOCH FROM token_expira - LOCALTIMESTAMP) / 60)::int
				FROM cafetin.usuario WHERE id_usuario = ?""", Integer.class, e.getId());
		assertThat(minutos).isEqualTo(30);
	}

	@Test
	void elTokenEsDeUnSoloUso() throws Exception {
		String token = pedirToken(crearEstudiante());

		restablecer(token, NUEVA).andExpect(status().isNoContent());

		restablecer(token, "OtraClave2026").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
	}

	@Test
	void tokenExpiradoResponde409YNoCambiaLaContrasena() throws Exception {
		Estudiante e = crearEstudiante();
		String token = pedirToken(e);
		jdbc.update("UPDATE cafetin.usuario SET token_expira = LOCALTIMESTAMP - INTERVAL '1 minute' WHERE id_usuario = ?", e.getId());

		restablecer(token, NUEVA).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));

		login(e.getCorreo(), CLAVE).andExpect(status().isOk());
	}

	@Test
	void tokenInventadoResponde409() throws Exception {
		restablecer("token-inventado", NUEVA).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
	}

	@Test
	void pedirUnoNuevoInvalidaElAnterior() throws Exception {
		Estudiante e = crearEstudiante();
		String primero = pedirToken(e);
		clearInvocations(envio);
		String segundo = pedirToken(e);

		restablecer(primero, NUEVA).andExpect(status().isConflict());
		restablecer(segundo, NUEVA).andExpect(status().isNoContent());
	}

	@Test
	void correoInexistenteResponde202SinEnviarNada() throws Exception {
		recuperar("nadie@ucvvirtual.edu.pe").andExpect(status().isAccepted());

		verify(envio, after(ESPERA_SIN_ENVIO).never()).enviar(any(), any());
	}

	@Test
	void cuentaInactivaResponde202SinEnviarNada() throws Exception {
		Estudiante e = crearEstudiante();
		e.setActivo(false);
		usuarios.save(e);

		recuperar(e.getCorreo()).andExpect(status().isAccepted());

		verify(envio, after(ESPERA_SIN_ENVIO).never()).enviar(any(), any());
	}

	@Test
	void laRespuestaNoEsperaAlCorreoYNoRevelaSiLaCuentaExiste() throws Exception {
		Estudiante e = crearEstudiante();
		doAnswer(invocacion -> {
			Thread.sleep(2000); // servidor SMTP lento
			return null;
		}).when(envio).enviar(any(), any());

		long inicio = System.nanoTime();
		recuperar(e.getCorreo()).andExpect(status().isAccepted());
		Duration conCuenta = Duration.ofNanos(System.nanoTime() - inicio);

		// Si la respuesta esperara al correo, tardaría 2 s más que para un correo sin cuenta.
		assertThat(conCuenta).isLessThan(Duration.ofSeconds(1));
		verify(envio, timeout(ESPERA_ENVIO)).enviar(eq(e.getCorreo()), any());
	}

	@Test
	void contrasenaInvalidaNoConsumeElToken() throws Exception {
		String token = pedirToken(crearEstudiante());

		restablecer(token, "corta").andExpect(status().isBadRequest());
		restablecer(token, "ñ".repeat(40)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));

		restablecer(token, NUEVA).andExpect(status().isNoContent());
	}

	// ---------- Ayudas ----------

	private String pedirToken(Estudiante e) throws Exception {
		recuperar(e.getCorreo()).andExpect(status().isAccepted());
		ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
		verify(envio, timeout(ESPERA_ENVIO)).enviar(eq(e.getCorreo()), token.capture());
		return token.getValue();
	}

	private ResultActions recuperar(String correo) throws Exception {
		return mvc.perform(post("/api/auth/recuperar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"%s\"}".formatted(correo)));
	}

	private ResultActions restablecer(String token, String nueva) throws Exception {
		return mvc.perform(post("/api/auth/restablecer").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"%s\", \"nuevaContrasena\": \"%s\"}".formatted(token, nueva)));
	}

	private ResultActions login(String correo, String clave) throws Exception {
		return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"%s\", \"contrasena\": \"%s\"}".formatted(correo, clave)));
	}

	private Estudiante crearEstudiante() {
		int n = SECUENCIA.incrementAndGet();
		return usuarios.save(new Estudiante("Ana", "Prueba", "rec" + n + "@ucvvirtual.edu.pe", encoder.encode(CLAVE),
				"R" + n, "Sistemas", (short) 6));
	}

}
