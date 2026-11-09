package pe.edu.ucv.cafetin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.ucv.cafetin.soporte.DatosPrueba.como;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.comun.evento.PedidoEstadoCambiado;
import pe.edu.ucv.cafetin.comun.modelo.TipoNotificacion;
import pe.edu.ucv.cafetin.cuentas.service.EnvioPush;
import pe.edu.ucv.cafetin.soporte.DatosPrueba;

/** RF-08: cada aviso de cambio de estado llega al teléfono del estudiante, solo si el cambio se confirmó. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PushEstadoPedidoIntegracionTest {

	private static final String TOKEN = "token-fcm-del-telefono";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	EnvioPush envio;

	int estudiante;
	int trabajador;
	int producto;

	@BeforeEach
	void preparar() {
		DatosPrueba datos = new DatosPrueba(jdbc);
		estudiante = datos.estudiante();
		trabajador = datos.trabajador("Carmen", "Díaz Morales");
		producto = datos.producto(datos.categoria(), "2.50", 50);
	}

	@Test
	void confirmarListoYEntregarEnvianPushConElCodigoDelPedido() throws Exception {
		guardarToken(TOKEN);
		int id = pedidoPagado();
		String codigo = jdbc.queryForObject("SELECT codigo_pedido FROM pedido WHERE id_pedido = ?", String.class, id);

		for (String paso : new String[] { "confirmar", "preparar", "listo" }) {
			accion(id, paso).andExpect(status().isOk());
		}
		mvc.perform(post("/api/despacho/pedidos/" + id + "/verificar").with(como(trabajador, "TRABAJADOR"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigoVerificacion\": \"" + codigoRecibo(id) + "\"}"))
				.andExpect(status().isOk());
		accion(id, "entregar").andExpect(status().isOk());

		// "preparar" no avisa: no existe un tipo de notificación para EN_PREPARACION.
		ArgumentCaptor<PedidoEstadoCambiado> avisos = ArgumentCaptor.forClass(PedidoEstadoCambiado.class);
		verify(envio, times(3)).enviar(eq(TOKEN), avisos.capture());
		assertThat(avisos.getAllValues()).extracting(PedidoEstadoCambiado::tipo).containsExactly(
				TipoNotificacion.PEDIDO_CONFIRMADO, TipoNotificacion.PEDIDO_LISTO, TipoNotificacion.PEDIDO_ENTREGADO);
		assertThat(avisos.getAllValues()).allSatisfy(aviso -> {
			assertThat(aviso.idUsuario()).isEqualTo(estudiante);
			assertThat(aviso.idPedido()).isEqualTo(id);
			assertThat(aviso.mensaje()).contains(codigo);
		});
	}

	@Test
	void unaTransicionRechazadaNoEnviaPush() throws Exception {
		guardarToken(TOKEN);
		int id = pedidoPagado();

		accion(id, "listo").andExpect(status().isConflict()); // PENDIENTE → LISTO no está permitido

		verify(envio, never()).enviar(anyString(), any());
	}

	@Test
	void sinTokenGuardaLaNotificacionPeroNoEnviaPush() throws Exception {
		int id = pedidoPagado();

		accion(id, "confirmar").andExpect(status().isOk());

		verify(envio, never()).enviar(anyString(), any());
		assertThat(tiposGuardados(id)).containsExactly("PEDIDO_CONFIRMADO");
	}

	@Test
	void siFallaElEnvioElDespachoSigueYLaNotificacionQueda() throws Exception {
		guardarToken(TOKEN);
		doThrow(new IllegalStateException("FCM no responde")).when(envio).enviar(anyString(), any());
		int id = pedidoPagado();

		accion(id, "confirmar").andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT estado::text FROM pedido WHERE id_pedido = ?", String.class, id))
				.isEqualTo("CONFIRMADO");
		assertThat(tiposGuardados(id)).containsExactly("PEDIDO_CONFIRMADO");
	}

	// ------------------------------------------------------------------ ayudas

	private void guardarToken(String token) {
		jdbc.update("UPDATE usuario SET fcm_token = ? WHERE id_usuario = ?", token, estudiante);
	}

	private int pedidoPagado() throws Exception {
		String json = mvc.perform(post("/api/pedidos").with(como(estudiante, "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"horaRecojo\": \"12:30\", \"items\": [{\"idProducto\": %d, \"cantidad\": 1}]}"
						.formatted(producto)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		int id = JsonPath.read(json, "$.id");
		mvc.perform(post("/api/pedidos/" + id + "/pago").with(como(estudiante, "ESTUDIANTE"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"metodoPago\": \"YAPE\"}"))
				.andExpect(status().isOk());
		return id;
	}

	private ResultActions accion(int idPedido, String accion) throws Exception {
		return mvc.perform(post("/api/despacho/pedidos/" + idPedido + "/" + accion).with(como(trabajador, "TRABAJADOR")));
	}

	private String codigoRecibo(int idPedido) {
		return jdbc.queryForObject(
				"SELECT r.codigo_verificacion FROM recibo r JOIN pago p USING (id_pago) WHERE p.id_pedido = ?",
				String.class, idPedido);
	}

	private List<String> tiposGuardados(int idPedido) {
		return jdbc.queryForList("SELECT tipo::text FROM notificacion WHERE id_pedido = ? ORDER BY id_notificacion",
				String.class, idPedido);
	}

}
