package pe.edu.ucv.cafetin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.cuentas.modelo.Dueno;
import pe.edu.ucv.cafetin.cuentas.modelo.Estudiante;
import pe.edu.ucv.cafetin.cuentas.modelo.Trabajador;
import pe.edu.ucv.cafetin.cuentas.modelo.Turno;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

/** RF-01, RF-02 y RNF-05: registro, login, JWT, reglas por rol y formato de los errores 401/403. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthIntegracionTest {

	private static final String CLAVE = "Cafetin2026";
	private static final AtomicInteger SECUENCIA = new AtomicInteger(1000);

	@Autowired
	MockMvc mvc;

	@Autowired
	UsuarioRepository usuarios;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JdbcTemplate jdbc;

	@Value("${cafetin.jwt.secreto}")
	String secreto;

	// ---------- Registro (RF-01) ----------

	@Test
	void registroCreaEstudianteYDevuelveTokenUtil() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		String correo = "nuevo" + n + "@ucvvirtual.edu.pe";

		String cuerpo = mvc.perform(registro(correo, "C" + n, CLAVE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.expiraEn").value(28800))
				.andExpect(jsonPath("$.usuario.correo").value(correo))
				.andExpect(jsonPath("$.usuario.rol").value("ESTUDIANTE"))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + JsonPath.read(cuerpo, "$.token")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correo").value(correo))
				.andExpect(jsonPath("$.rol").value("ESTUDIANTE"));
	}

	@Test
	void laContrasenaSeGuardaConBcrypt() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		String correo = "hash" + n + "@ucvvirtual.edu.pe";

		mvc.perform(registro(correo, "C" + n, CLAVE)).andExpect(status().isCreated());

		String guardada = jdbc.queryForObject("SELECT contrasena FROM cafetin.usuario WHERE correo = ?", String.class, correo);
		assertThat(guardada).startsWith("$2").isNotEqualTo(CLAVE);
		assertThat(encoder.matches(CLAVE, guardada)).isTrue();
	}

	@Test
	void correoDuplicadoResponde409AunqueCambienLasMayusculas() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		mvc.perform(registro("dup" + n + "@ucvvirtual.edu.pe", "C" + n, CLAVE)).andExpect(status().isCreated());

		mvc.perform(registro("DUP" + n + "@ucvvirtual.edu.pe", "D" + n, CLAVE))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
	}

	@Test
	void codigoDeEstudianteDuplicadoResponde409() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		mvc.perform(registro("a" + n + "@ucvvirtual.edu.pe", "C" + n, CLAVE)).andExpect(status().isCreated());

		mvc.perform(registro("b" + n + "@ucvvirtual.edu.pe", "C" + n, CLAVE))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
	}

	@Test
	void correoSinDominioConPuntoResponde400EnVezDeRomperElCheck() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		mvc.perform(registro("sinpunto" + n + "@ucv", "C" + n, CLAVE))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	@Test
	void nombreQueQuedaCortoAlRecortarEspaciosResponde400() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
				{"nombre": " a ", "apellidos": "Prueba", "correo": "corto%d@ucvvirtual.edu.pe", "contrasena": "%s",
				 "codigo": "C%d", "carrera": "Sistemas", "ciclo": 6}""".formatted(n, CLAVE, n)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	@Test
	void contrasenaDeMasDe72BytesResponde400() throws Exception {
		int n = SECUENCIA.incrementAndGet();
		// 40 caracteres de 2 bytes: pasa @Size(max = 72) pero BCrypt no admite más de 72 bytes.
		mvc.perform(registro("larga" + n + "@ucvvirtual.edu.pe", "C" + n, "ñ".repeat(40)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	// ---------- Login (RF-01, RF-02) ----------

	@Test
	void loginCorrectoDevuelveTokenYRol() throws Exception {
		Estudiante e = crearEstudiante();

		mvc.perform(login(e.getCorreo(), CLAVE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.usuario.id").value(e.getId()))
				.andExpect(jsonPath("$.usuario.rol").value("ESTUDIANTE"));
	}

	@Test
	void loginNoDistingueMayusculasEnElCorreo() throws Exception {
		Estudiante e = crearEstudiante();

		mvc.perform(login(e.getCorreo().toUpperCase(), CLAVE)).andExpect(status().isOk());
	}

	@Test
	void contrasenaErroneaResponde401ConFormatoDeError() throws Exception {
		Estudiante e = crearEstudiante();

		mvc.perform(login(e.getCorreo(), "OtraClave2026"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
				.andExpect(jsonPath("$.detalles").isEmpty());
	}

	@Test
	void correoInexistenteRespondeIgualQueContrasenaErronea() throws Exception {
		Estudiante e = crearEstudiante();

		String inexistente = mvc.perform(login("nadie@ucvvirtual.edu.pe", CLAVE))
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		String erronea = mvc.perform(login(e.getCorreo(), "OtraClave2026"))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(inexistente).isEqualTo(erronea);
	}

	@Test
	void usuarioInactivoNoPuedeIniciarSesion() throws Exception {
		Trabajador t = crearTrabajador();
		t.setActivo(false);
		usuarios.save(t);

		mvc.perform(login(t.getCorreo(), CLAVE))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	@Test
	void tokenVencidoEnElEncabezadoNoImpideIniciarSesion() throws Exception {
		// La app añade el JWT guardado a toda petición: con uno vencido debe poder volver a entrar.
		Estudiante e = crearEstudiante();

		mvc.perform(login(e.getCorreo(), CLAVE).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVencido(e.getId())))
				.andExpect(status().isOk());
	}

	// ---------- 401 ----------

	@Test
	void sinTokenResponde401ConFormatoDeError() throws Exception {
		mvc.perform(get("/api/usuarios/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
				.andExpect(jsonPath("$.detalles").isEmpty());
	}

	@Test
	void tokenFalsoResponde401ConFormatoDeError() throws Exception {
		mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer no.es.un-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	@Test
	void tokenVencidoResponde401() throws Exception {
		Estudiante e = crearEstudiante();

		mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVencido(e.getId())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	@Test
	void tokenFirmadoConOtroSecretoResponde401() throws Exception {
		String ajeno = firmar("otro-secreto-de-al-menos-32-bytes-de-largo", 1, Instant.now().plusSeconds(600), "DUENO");

		mvc.perform(get("/api/reportes/ingresos").param("periodo", "DIA").header(HttpHeaders.AUTHORIZATION, "Bearer " + ajeno))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenDeUnUsuarioQueYaNoExisteResponde401() throws Exception {
		String huerfano = firmar(secreto, 999999, Instant.now().plusSeconds(600), "DUENO");

		mvc.perform(get("/api/reportes/ventas").header(HttpHeaders.AUTHORIZATION, "Bearer " + huerfano))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	// ---------- Reglas por rol (RNF-05). 501 = la seguridad dejó pasar y el endpoint aún no existe ----------

	@Test
	void estudianteEnReportesResponde403ConFormatoDeError() throws Exception {
		mvc.perform(get("/api/reportes/ventas").header(HttpHeaders.AUTHORIZATION, bearer(crearEstudiante())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
				.andExpect(jsonPath("$.detalles").isEmpty());
	}

	@Test
	void trabajadorNoEntraAInventarioNiAPedidosDeEstudiante() throws Exception {
		String token = bearer(crearTrabajador());

		mvc.perform(get("/api/productos").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
		mvc.perform(get("/api/pedidos/mios").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
		mvc.perform(get("/api/trabajadores").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
	}

	@Test
	void trabajadorEntraADespacho() throws Exception {
		mvc.perform(get("/api/despacho/pedidos").header(HttpHeaders.AUTHORIZATION, bearer(crearTrabajador())))
				.andExpect(status().isNotImplemented());
	}

	@Test
	void estudianteEntraASusPedidosYNoADespacho() throws Exception {
		String token = bearer(crearEstudiante());

		mvc.perform(get("/api/pedidos/mios").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
		mvc.perform(get("/api/despacho/pedidos").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
	}

	@Test
	void categoriasSeLeenConCualquierRolPeroSoloElDuenoEscribe() throws Exception {
		String estudiante = bearer(crearEstudiante());
		String dueno = bearer(crearDueno());
		String nueva = "{\"nombre\": \"Postres\"}";

		mvc.perform(get("/api/categorias").header(HttpHeaders.AUTHORIZATION, estudiante)).andExpect(status().isNotImplemented());
		mvc.perform(post("/api/categorias").header(HttpHeaders.AUTHORIZATION, estudiante)
				.contentType(MediaType.APPLICATION_JSON).content(nueva)).andExpect(status().isForbidden());
		mvc.perform(post("/api/categorias").header(HttpHeaders.AUTHORIZATION, dueno)
				.contentType(MediaType.APPLICATION_JSON).content(nueva)).andExpect(status().isNotImplemented());
	}

	@Test
	void duenoEntraAReportesYDespacho() throws Exception {
		String dueno = bearer(crearDueno());

		mvc.perform(get("/api/reportes/ventas").header(HttpHeaders.AUTHORIZATION, dueno)).andExpect(status().isNotImplemented());
		mvc.perform(get("/api/despacho/pedidos").header(HttpHeaders.AUTHORIZATION, dueno)).andExpect(status().isNotImplemented());
	}

	@Test
	void rutaFueraDelContratoQuedaDenegada() throws Exception {
		mvc.perform(get("/api/otra-cosa")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/otra-cosa").header(HttpHeaders.AUTHORIZATION, bearer(crearEstudiante())))
				.andExpect(status().isForbidden());
	}

	// ---------- CORS ----------

	@Test
	void corsPermiteElOrigenDelPanelYRechazaOtros() throws Exception {
		mvc.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

		mvc.perform(options("/api/auth/login").header(HttpHeaders.ORIGIN, "https://sitio-ajeno.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden());
	}

	// ---------- Ayudas ----------

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String correo, String clave) {
		return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"%s\", \"contrasena\": \"%s\"}".formatted(correo, clave));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registro(String correo, String codigo, String clave) {
		return post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
				{"nombre": "Prueba", "apellidos": "De Registro", "correo": "%s", "contrasena": "%s",
				 "telefono": "914567890", "codigo": "%s", "carrera": "Sistemas", "ciclo": 6}""".formatted(correo, clave, codigo));
	}

	private Estudiante crearEstudiante() {
		int n = SECUENCIA.incrementAndGet();
		return usuarios.save(new Estudiante("Ana", "Prueba", "est" + n + "@ucvvirtual.edu.pe", encoder.encode(CLAVE),
				"E" + n, "Sistemas", (short) 6));
	}

	private Dueno crearDueno() {
		int n = SECUENCIA.incrementAndGet();
		return usuarios.save(new Dueno("Rosa", "Prueba", "dueno" + n + "@cafetin.pe", encoder.encode(CLAVE)));
	}

	private Trabajador crearTrabajador() {
		int n = SECUENCIA.incrementAndGet();
		return usuarios.save(new Trabajador("Luis", "Prueba", "trb" + n + "@cafetin.pe", encoder.encode(CLAVE),
				"T" + n, Turno.MANANA));
	}

	/** Pasa por el login real, para que las reglas de rol se prueben con el token que emite la API. */
	private String bearer(pe.edu.ucv.cafetin.cuentas.modelo.Usuario usuario) throws Exception {
		String cuerpo = mvc.perform(login(usuario.getCorreo(), CLAVE)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return "Bearer " + JsonPath.read(cuerpo, "$.token");
	}

	private String tokenVencido(int id) {
		return firmar(secreto, id, Instant.now().minusSeconds(3600), "ESTUDIANTE");
	}

	private static String firmar(String secreto, int id, Instant expira, String rol) {
		var clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		var claims = JwtClaimsSet.builder().subject(String.valueOf(id)).issuedAt(expira.minusSeconds(60))
				.expiresAt(expira).claim("rol", rol).build();
		return new NimbusJwtEncoder(new ImmutableSecret<>(clave))
				.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

}
