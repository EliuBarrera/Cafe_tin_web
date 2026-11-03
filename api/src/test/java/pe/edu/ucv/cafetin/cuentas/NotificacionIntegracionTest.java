package pe.edu.ucv.cafetin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.cuentas.modelo.Estudiante;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

/** RF-08: token FCM y bandeja de notificaciones. Usa el seed para tener pedidos a los que referirse. */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/datos-dev")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NotificacionIntegracionTest {

	private static final String CLAVE = "Cafetin2026";
	private static final AtomicInteger SECUENCIA = new AtomicInteger(4000);

	@Autowired
	MockMvc mvc;

	@Autowired
	UsuarioRepository usuarios;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void listaLasPropiasDeLaMasRecienteALaMasAntigua() throws Exception {
		Estudiante yo = crearEstudiante();
		Estudiante otro = crearEstudiante();
		insertar(yo, "PEDIDO_CONFIRMADO", "antigua", "2026-11-01 10:00:00", false);
		insertar(yo, "PEDIDO_LISTO", "reciente", "2026-11-02 10:00:00", false);
		insertar(otro, "PEDIDO_LISTO", "de otro", "2026-11-02 11:00:00", false);

		mvc.perform(get("/api/usuarios/me/notificaciones").header(HttpHeaders.AUTHORIZATION, bearer(yo)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].mensaje").value("reciente"))
				.andExpect(jsonPath("$[0].tipo").value("PEDIDO_LISTO"))
				.andExpect(jsonPath("$[0].idPedido").isNumber())
				.andExpect(jsonPath("$[0].leida").value(false))
				.andExpect(jsonPath("$[1].mensaje").value("antigua"));
	}

	@Test
	void puedeFiltrarSoloLasNoLeidas() throws Exception {
		Estudiante yo = crearEstudiante();
		insertar(yo, "PEDIDO_LISTO", "ya leída", "2026-11-01 10:00:00", true);
		insertar(yo, "PEDIDO_ENTREGADO", "sin leer", "2026-11-02 10:00:00", false);

		mvc.perform(get("/api/usuarios/me/notificaciones").param("soloNoLeidas", "true")
				.header(HttpHeaders.AUTHORIZATION, bearer(yo)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].mensaje").value("sin leer"));
	}

	@Test
	void lasAlertasDeStockLleganSinPedido() throws Exception {
		// El trigger trg_alerta_stock_minimo avisa a cada dueño al cruzar el mínimo.
		jdbc.update("UPDATE cafetin.inventario SET cantidad_disponible = 0 WHERE id_producto = 2");
		String dueno = bearerPorCorreo("rosa.quispe@cafetin.pe");

		mvc.perform(get("/api/usuarios/me/notificaciones").header(HttpHeaders.AUTHORIZATION, dueno))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.tipo == 'STOCK_AGOTADO')]").exists())
				.andExpect(jsonPath("$[?(@.tipo == 'STOCK_AGOTADO')].idPedido").value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.nullValue())));
	}

	@Test
	void marcaComoLeidaUnaPropia() throws Exception {
		Estudiante yo = crearEstudiante();
		int id = insertar(yo, "PEDIDO_LISTO", "mía", "2026-11-02 10:00:00", false);

		mvc.perform(patch("/api/usuarios/me/notificaciones/" + id + "/leida").header(HttpHeaders.AUTHORIZATION, bearer(yo)))
				.andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT leida FROM cafetin.notificacion WHERE id_notificacion = ?", Boolean.class, id)).isTrue();
	}

	@Test
	void unaAjenaResponde404YNoSeMarca() throws Exception {
		Estudiante dueno = crearEstudiante();
		int id = insertar(dueno, "PEDIDO_LISTO", "ajena", "2026-11-02 10:00:00", false);

		mvc.perform(patch("/api/usuarios/me/notificaciones/" + id + "/leida")
				.header(HttpHeaders.AUTHORIZATION, bearer(crearEstudiante())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

		assertThat(jdbc.queryForObject("SELECT leida FROM cafetin.notificacion WHERE id_notificacion = ?", Boolean.class, id)).isFalse();
	}

	@Test
	void guardaElTokenFcm() throws Exception {
		Estudiante yo = crearEstudiante();

		registrarToken(yo, "fcm-" + yo.getId());

		assertThat(tokenDe(yo)).isEqualTo("fcm-" + yo.getId());
	}

	@Test
	void siOtroUsuarioEntraEnElMismoTelefonoElAnteriorDejaDeTenerElToken() throws Exception {
		// Si no, el teléfono del segundo seguiría recibiendo los avisos de los pedidos del primero.
		Estudiante primero = crearEstudiante();
		Estudiante segundo = crearEstudiante();
		String telefono = "fcm-compartido-" + primero.getId();

		registrarToken(primero, telefono);
		registrarToken(segundo, telefono);

		assertThat(tokenDe(primero)).isNull();
		assertThat(tokenDe(segundo)).isEqualTo(telefono);
	}

	@Test
	void tokenFcmVacioResponde400() throws Exception {
		mvc.perform(put("/api/usuarios/me/fcm-token").header(HttpHeaders.AUTHORIZATION, bearer(crearEstudiante()))
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\": \" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	// ---------- Ayudas ----------

	private void registrarToken(Estudiante e, String token) throws Exception {
		mvc.perform(put("/api/usuarios/me/fcm-token").header(HttpHeaders.AUTHORIZATION, bearer(e))
				.contentType(MediaType.APPLICATION_JSON).content("{\"token\": \"%s\"}".formatted(token)))
				.andExpect(status().isNoContent());
	}

	private String tokenDe(Estudiante e) {
		return jdbc.queryForObject("SELECT fcm_token FROM cafetin.usuario WHERE id_usuario = ?", String.class, e.getId());
	}

	private int insertar(Estudiante e, String tipo, String mensaje, String fecha, boolean leida) {
		return jdbc.queryForObject("""
				INSERT INTO cafetin.notificacion (id_usuario, id_pedido, tipo, mensaje, fecha, leida)
				VALUES (?, (SELECT min(id_pedido) FROM cafetin.pedido), ?::cafetin.tipo_notif_enum, ?, ?::timestamp, ?)
				RETURNING id_notificacion""", Integer.class, e.getId(), tipo, mensaje, fecha, leida);
	}

	private Estudiante crearEstudiante() {
		int n = SECUENCIA.incrementAndGet();
		return usuarios.save(new Estudiante("Ana", "Prueba", "not" + n + "@ucvvirtual.edu.pe", encoder.encode(CLAVE),
				"N" + n, "Sistemas", (short) 6));
	}

	private String bearer(Estudiante e) throws Exception {
		return bearerPorCorreo(e.getCorreo());
	}

	private String bearerPorCorreo(String correo) throws Exception {
		String cuerpo = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"%s\", \"contrasena\": \"%s\"}".formatted(correo, CLAVE)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return "Bearer " + JsonPath.read(cuerpo, "$.token");
	}

}
