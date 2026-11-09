package pe.edu.ucv.cafetin.despacho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.soporte.DatosPrueba;

/** Despacho: RF-10 (en curso), RF-11 (confirmar/preparar), RF-12 (listo), RF-13 (verificar), RF-14 (entregar). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DespachoIntegracionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	DatosPrueba datos;
	int estudiante;
	int trabajador;
	int producto;

	@BeforeEach
	void preparar() {
		datos = new DatosPrueba(jdbc);
		estudiante = datos.estudiante();
		trabajador = datos.trabajador("Carmen", "Díaz Morales");
		producto = datos.producto(datos.categoria(), "2.50", 50);
	}

	// ---------- RF-10: pedidos en curso ----------

	@Test
	void listaSoloPedidosPagadosOrdenadosPorHoraDeRecojoYSinCodigoDeRecibo() throws Exception {
		int tarde = pedidoPagado("13:30");
		int temprano = pedidoPagado("11:10");
		int sinPagar = pedido("10:00");

		String json = cuerpo(listar(trabajador, null).andExpect(status().isOk())
				.andExpect(jsonPath("$[*].idPedido", hasItem(tarde)))
				.andExpect(jsonPath("$[*].idPedido", hasItem(temprano)))
				.andExpect(jsonPath("$[*].idPedido", not(hasItem(sinPagar))))
				.andExpect(jsonPath("$[0].codigoVerificacion").doesNotExist()));

		List<Integer> ids = JsonPath.read(json, "$[*].idPedido");
		assertThat(ids.indexOf(temprano)).isLessThan(ids.indexOf(tarde));

		String uno = "$[?(@.idPedido == " + temprano + ")]";
		assertThat(JsonPath.<List<String>>read(json, uno + ".estado")).containsExactly("PENDIENTE");
		assertThat(JsonPath.<List<String>>read(json, uno + ".estudiante")).containsExactly("Ana Prueba");
		assertThat(JsonPath.<List<String>>read(json, uno + ".metodoPago")).containsExactly("YAPE");
		assertThat(JsonPath.<List<Integer>>read(json, uno + ".items[0].cantidad")).containsExactly(2);
		assertThat(JsonPath.<List<Object>>read(json, uno + ".trabajador")).containsExactly((Object) null);
		assertThat(JsonPath.<List<Boolean>>read(json, uno + ".reciboVerificado")).containsExactly(false);
	}

	@Test
	void filtraPorEstado() throws Exception {
		int confirmado = pedidoPagado("12:00");
		int pendiente = pedidoPagado("12:05");
		accion(trabajador, confirmado, "confirmar").andExpect(status().isOk());

		listar(trabajador, "CONFIRMADO").andExpect(status().isOk())
				.andExpect(jsonPath("$[*].idPedido", hasItem(confirmado)))
				.andExpect(jsonPath("$[*].idPedido", not(hasItem(pendiente))))
				.andExpect(jsonPath("$[*].estado", not(hasItem("PENDIENTE"))));
	}

	// ---------- Flujo completo (RF-11 a RF-14) ----------

	@Test
	void flujoCompletoHastaEntregarYNotificaAlEstudiante() throws Exception {
		int id = pedidoPagado("12:50");

		accion(trabajador, id, "confirmar").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CONFIRMADO"))
				.andExpect(jsonPath("$.trabajador").value("Carmen Díaz Morales"));
		assertThat(jdbc.queryForObject("SELECT id_trabajador FROM pedido WHERE id_pedido = ?", Integer.class, id))
				.isEqualTo(trabajador);

		accion(trabajador, id, "preparar").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("EN_PREPARACION"));
		accion(trabajador, id, "listo").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("LISTO"));

		verificar(trabajador, id, codigoRecibo(id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.reciboVerificado").value(true))
				.andExpect(jsonPath("$.estado").value("LISTO"));
		assertThat(jdbc.queryForObject("""
				SELECT r.fecha_verificacion IS NOT NULL FROM recibo r JOIN pago p USING (id_pago) WHERE p.id_pedido = ?""",
				Boolean.class, id)).isTrue();

		accion(trabajador, id, "entregar").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ENTREGADO"));

		listar(trabajador, null).andExpect(jsonPath("$[*].idPedido", not(hasItem(id))));
		assertThat(jdbc.queryForList(
				"SELECT tipo::text FROM notificacion WHERE id_pedido = ? AND id_usuario = ? ORDER BY id_notificacion",
				String.class, id, estudiante))
				.containsExactly("PEDIDO_CONFIRMADO", "PEDIDO_LISTO", "PEDIDO_ENTREGADO");
	}

	@Test
	void elDuenoTambienDespachaPeroNoQuedaComoTrabajador() throws Exception {
		int id = pedidoPagado("12:50");

		accion(datos.dueno(), id, "confirmar").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CONFIRMADO"))
				.andExpect(jsonPath("$.trabajador").value(nullValue()));
	}

	// ---------- Transiciones inválidas ----------

	@Test
	void confirmarUnPedidoSinPagarResponde409() throws Exception {
		int id = pedido("12:50");

		accion(trabajador, id, "confirmar").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		assertThat(estado(id)).isEqualTo("PENDIENTE");
	}

	@Test
	void saltarseUnPasoOConfirmarDosVecesResponde409() throws Exception {
		int id = pedidoPagado("12:50");
		accion(trabajador, id, "listo").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

		accion(trabajador, id, "confirmar").andExpect(status().isOk());
		accion(trabajador, id, "confirmar").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		assertThat(estado(id)).isEqualTo("CONFIRMADO");
	}

	@Test
	void entregarSinReciboVerificadoResponde409() throws Exception {
		int id = pedidoListo();

		accion(trabajador, id, "entregar").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
		assertThat(estado(id)).isEqualTo("LISTO");
	}

	// ---------- RF-13: verificar recibo ----------

	@Test
	void codigoIncorrectoResponde409YNoVerifica() throws Exception {
		int id = pedidoListo();

		verificar(trabajador, id, "DR-1-0000").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
		assertThat(jdbc.queryForObject(
				"SELECT r.verificado FROM recibo r JOIN pago p USING (id_pago) WHERE p.id_pedido = ?", Boolean.class, id))
				.isFalse();
	}

	@Test
	void verificarAntesDeEstarListoResponde409() throws Exception {
		int id = pedidoPagado("12:50");
		accion(trabajador, id, "confirmar").andExpect(status().isOk());

		verificar(trabajador, id, codigoRecibo(id)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
	}

	@Test
	void codigoConFormatoInvalidoResponde400() throws Exception {
		int id = pedidoListo();

		verificar(trabajador, id, "12345").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	// ---------- Acceso y existencia ----------

	@Test
	void unEstudianteNoAccedeAlDespacho() throws Exception {
		int id = pedidoPagado("12:50");

		listar(estudiante, "ESTUDIANTE", null).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		mvc.perform(post("/api/despacho/pedidos/" + id + "/confirmar").with(como(estudiante, "ESTUDIANTE")))
				.andExpect(status().isForbidden());
		assertThat(estado(id)).isEqualTo("PENDIENTE");
	}

	@Test
	void pedidoInexistenteResponde404() throws Exception {
		accion(trabajador, 999_999, "confirmar").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
	}

	// ---------- Ayudas ----------

	/** Pedido de 2 unidades sin pagar, creado por la API como lo haría la app. */
	private int pedido(String horaRecojo) throws Exception {
		String json = cuerpo(mvc.perform(post("/api/pedidos").with(como(estudiante, "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"horaRecojo\": \"%s\", \"items\": [{\"idProducto\": %d, \"cantidad\": 2}]}"
						.formatted(horaRecojo, producto)))
				.andExpect(status().isCreated()));
		return JsonPath.read(json, "$.id");
	}

	/** Pagado con la pasarela simulada (aprueba todo). */
	private int pedidoPagado(String horaRecojo) throws Exception {
		int id = pedido(horaRecojo);
		mvc.perform(post("/api/pedidos/" + id + "/pago").with(como(estudiante, "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\": \"YAPE\"}"))
				.andExpect(status().isOk());
		return id;
	}

	private int pedidoListo() throws Exception {
		int id = pedidoPagado("12:50");
		for (String paso : List.of("confirmar", "preparar", "listo")) {
			accion(trabajador, id, paso).andExpect(status().isOk());
		}
		return id;
	}

	private ResultActions listar(int idUsuario, String estado) throws Exception {
		return listar(idUsuario, "TRABAJADOR", estado);
	}

	private ResultActions listar(int idUsuario, String rol, String estado) throws Exception {
		var peticion = get("/api/despacho/pedidos").with(como(idUsuario, rol));
		if (estado != null) {
			peticion.param("estado", estado);
		}
		return mvc.perform(peticion);
	}

	private ResultActions accion(int idUsuario, int idPedido, String accion) throws Exception {
		String rol = idUsuario == trabajador ? "TRABAJADOR" : "DUENO";
		return mvc.perform(post("/api/despacho/pedidos/" + idPedido + "/" + accion).with(como(idUsuario, rol)));
	}

	private ResultActions verificar(int idUsuario, int idPedido, String codigo) throws Exception {
		return mvc.perform(post("/api/despacho/pedidos/" + idPedido + "/verificar").with(como(idUsuario, "TRABAJADOR"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"codigoVerificacion\": \"" + codigo + "\"}"));
	}

	private String codigoRecibo(int idPedido) {
		return jdbc.queryForObject(
				"SELECT r.codigo_verificacion FROM recibo r JOIN pago p USING (id_pago) WHERE p.id_pedido = ?",
				String.class, idPedido);
	}

	private String estado(int idPedido) {
		return jdbc.queryForObject("SELECT estado::text FROM pedido WHERE id_pedido = ?", String.class, idPedido);
	}

	private static String cuerpo(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
