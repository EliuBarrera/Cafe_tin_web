package pe.edu.ucv.cafetin.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pe.edu.ucv.cafetin.soporte.DatosPrueba.como;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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

/**
 * RF-18 (recarga de stock) y RF-17 (alertas de stock). Las alertas las crea el trigger
 * {@code trg_alerta_stock_minimo} al bajar el stock; aquí se bajan por SQL, como lo haría una venta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RecargaAlertaIntegracionTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	DatosPrueba datos;
	int dueno;
	int cat;

	@BeforeEach
	void preparar() {
		datos = new DatosPrueba(jdbc);
		dueno = datos.dueno();
		cat = datos.categoria();
	}

	// ---------- RF-18: recarga ----------

	@Test
	void recargaSumaUnidadesYActualizaLaFecha() throws Exception {
		int id = datos.producto(cat, "2.00", 3);
		jdbc.update("UPDATE inventario SET fecha_actualizacion = '2026-01-01 08:00' WHERE id_producto = ?", id);

		String json = cuerpo(recargar(id, 24)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idProducto").value(id))
				.andExpect(jsonPath("$.producto").value(notNullValue()))
				.andExpect(jsonPath("$.cantidadDisponible").value(27))
				.andExpect(jsonPath("$.stockMinimo").value(5)));

		assertThat(datos.stock(id)).isEqualTo(27);
		LocalDateTime fecha = LocalDateTime.parse(JsonPath.read(json, "$.fechaActualizacion"));
		assertThat(fecha).isAfter(LocalDateTime.of(2026, 1, 1, 8, 0));
	}

	@Test
	void recargaMarcaAtendidasLasAlertasAbiertasDelProductoYNoLasDeOtros() throws Exception {
		int id = datos.producto(cat, "2.00", 10);
		int otro = datos.producto(cat, "2.00", 10);
		bajarStock(id, 2);   // STOCK_BAJO
		bajarStock(id, 0);   // STOCK_AGOTADO
		bajarStock(otro, 1); // STOCK_BAJO de otro producto
		assertThat(alertasAbiertas(id)).isEqualTo(2);

		recargar(id, 20).andExpect(status().isOk());

		assertThat(alertasAbiertas(id)).isZero();
		assertThat(alertasAbiertas(otro)).isEqualTo(1);
	}

	@Test
	void recargarNoCreaAlertas() throws Exception {
		int id = datos.producto(cat, "2.00", 0);
		int antes = alertasDe(id);

		recargar(id, 2).andExpect(status().isOk());

		assertThat(alertasDe(id)).isEqualTo(antes);
	}

	@Test
	void recargaDeProductoDeshabilitadoSePermite() throws Exception {
		int id = datos.producto(cat, "2.00", 1);
		datos.desactivar(id);

		recargar(id, 5).andExpect(status().isOk()).andExpect(jsonPath("$.cantidadDisponible").value(6));
	}

	@Test
	void cantidadCeroONegativaONulaResponde400() throws Exception {
		int id = datos.producto(cat, "2.00", 3);
		recargar(id, 0).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
		recargar(id, -4).andExpect(status().isBadRequest());
		mvc.perform(patch("/api/inventario/" + id + "/recarga").with(como(dueno, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		assertThat(datos.stock(id)).isEqualTo(3);
	}

	@Test
	void recargaDeProductoInexistenteResponde404() throws Exception {
		recargar(999_999, 5).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
	}

	@Test
	void soloElDuenoRecarga() throws Exception {
		int id = datos.producto(cat, "2.00", 3);
		mvc.perform(patch("/api/inventario/" + id + "/recarga").with(como(datos.trabajador("Luis", "Ramos"), "TRABAJADOR"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\": 5}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		assertThat(datos.stock(id)).isEqualTo(3);
	}

	// ---------- RF-17: alertas ----------

	@Test
	void listaLasPendientesConElStockActualYElProveedor() throws Exception {
		int prov = datos.proveedor();
		int id = datos.producto(cat, "2.00", 10);
		jdbc.update("UPDATE producto SET id_proveedor = ? WHERE id_producto = ?", prov, id);
		bajarStock(id, 3);
		int alerta = alertaDe(id, dueno);
		bajarStock(id, 2); // sigue bajo el mínimo: no hay alerta nueva, pero el stock actual cambia

		listar(dueno, null).andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == %d)].tipo", alerta).value("STOCK_BAJO"))
				.andExpect(jsonPath("$[?(@.id == %d)].mensaje", alerta).value(hasItem(notNullValue())))
				.andExpect(jsonPath("$[?(@.id == %d)].fecha", alerta).value(hasItem(notNullValue())))
				.andExpect(jsonPath("$[?(@.id == %d)].nivelDetectado", alerta).value(3))
				.andExpect(jsonPath("$[?(@.id == %d)].cantidadDisponible", alerta).value(2))
				.andExpect(jsonPath("$[?(@.id == %d)].stockMinimo", alerta).value(5))
				.andExpect(jsonPath("$[?(@.id == %d)].atendida", alerta).value(false))
				.andExpect(jsonPath("$[?(@.id == %d)].idProducto", alerta).value(id))
				.andExpect(jsonPath("$[?(@.id == %d)].producto", alerta).value(hasItem(notNullValue())))
				.andExpect(jsonPath("$[?(@.id == %d)].proveedor", alerta).value(hasItem(notNullValue())))
				.andExpect(jsonPath("$[?(@.id == %d)].telefonoProveedor", alerta).value("044123456"));
	}

	@Test
	void lasMasRecientesVanPrimero() throws Exception {
		int id = datos.producto(cat, "2.00", 10);
		bajarStock(id, 4);
		int primera = alertaDe(id, dueno);
		bajarStock(id, 0);
		int segunda = jdbc.queryForObject("""
				SELECT max(a.id_notificacion) FROM alerta_stock a JOIN notificacion n USING (id_notificacion)
				JOIN inventario i ON i.id_inventario = a.id_inventario
				WHERE i.id_producto = ? AND n.id_usuario = ?""", Integer.class, id, dueno);

		List<Integer> ids = JsonPath.read(cuerpo(listar(dueno, null)), "$[*].id");
		assertThat(ids).containsSubsequence(segunda, primera);
	}

	@Test
	void cadaDuenoVeSoloSusAlertas() throws Exception {
		int otroDueno = datos.dueno();
		int id = datos.producto(cat, "2.00", 10);
		bajarStock(id, 1);
		int mia = alertaDe(id, dueno);
		int suya = alertaDe(id, otroDueno);

		listar(dueno, null).andExpect(jsonPath("$[*].id", hasItem(mia)))
				.andExpect(jsonPath("$[*].id", not(hasItem(suya))));
		marcar(otroDueno, mia).andExpect(status().isNotFound());
	}

	@Test
	void marcarAtendidaLaPasaALaListaDeAtendidas() throws Exception {
		int id = datos.producto(cat, "2.00", 10);
		bajarStock(id, 0);
		int alerta = alertaDe(id, dueno);

		marcar(dueno, alerta).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(alerta))
				.andExpect(jsonPath("$.tipo").value("STOCK_AGOTADO"))
				.andExpect(jsonPath("$.atendida").value(true));

		listar(dueno, null).andExpect(jsonPath("$[*].id", not(hasItem(alerta))));
		listar(dueno, "true").andExpect(jsonPath("$[*].id", hasItem(alerta)));
		// Volver a marcarla no falla.
		marcar(dueno, alerta).andExpect(status().isOk()).andExpect(jsonPath("$.atendida").value(true));
	}

	@Test
	void marcarAlertaInexistenteONotificacionQueNoEsAlertaResponde404() throws Exception {
		marcar(dueno, 999_999).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

		int notificacion = jdbc.queryForObject("""
				INSERT INTO notificacion (id_usuario, tipo, mensaje) VALUES (?, 'PEDIDO_LISTO', 'No es alerta')
				RETURNING id_notificacion""", Integer.class, dueno);
		marcar(dueno, notificacion).andExpect(status().isNotFound());
	}

	@Test
	void soloElDuenoVeAlertas() throws Exception {
		mvc.perform(get("/api/alertas").with(como(datos.estudiante(), "ESTUDIANTE")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		mvc.perform(patch("/api/alertas/1/atendida").with(como(datos.trabajador("Eva", "Soto"), "TRABAJADOR")))
				.andExpect(status().isForbidden());
	}

	// ---------- Ayudas ----------

	/** Como una venta: el trigger crea la alerta si el stock cruza el mínimo o llega a 0. */
	private void bajarStock(int idProducto, int cantidad) {
		jdbc.update("UPDATE inventario SET cantidad_disponible = ? WHERE id_producto = ?", cantidad, idProducto);
	}

	private int alertaDe(int idProducto, int idDueno) {
		return jdbc.queryForObject("""
				SELECT min(a.id_notificacion) FROM alerta_stock a JOIN notificacion n USING (id_notificacion)
				JOIN inventario i ON i.id_inventario = a.id_inventario
				WHERE i.id_producto = ? AND n.id_usuario = ?""", Integer.class, idProducto, idDueno);
	}

	private int alertasDe(int idProducto) {
		return jdbc.queryForObject("""
				SELECT count(*) FROM alerta_stock a JOIN inventario i ON i.id_inventario = a.id_inventario
				WHERE i.id_producto = ?""", Integer.class, idProducto);
	}

	/** Alertas sin atender del producto, solo las del dueño de este test. */
	private int alertasAbiertas(int idProducto) {
		return jdbc.queryForObject("""
				SELECT count(*) FROM alerta_stock a JOIN notificacion n USING (id_notificacion)
				JOIN inventario i ON i.id_inventario = a.id_inventario
				WHERE i.id_producto = ? AND n.id_usuario = ? AND NOT a.atendida""", Integer.class, idProducto, dueno);
	}

	private ResultActions recargar(int idProducto, int cantidad) throws Exception {
		return mvc.perform(patch("/api/inventario/" + idProducto + "/recarga").with(como(dueno, "DUENO"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\": " + cantidad + "}"));
	}

	private ResultActions listar(int idDueno, String atendida) throws Exception {
		var peticion = get("/api/alertas").with(como(idDueno, "DUENO"));
		if (atendida != null) {
			peticion.param("atendida", atendida);
		}
		return mvc.perform(peticion);
	}

	private ResultActions marcar(int idDueno, int idAlerta) throws Exception {
		return mvc.perform(patch("/api/alertas/" + idAlerta + "/atendida").with(como(idDueno, "DUENO")));
	}

	private static String cuerpo(ResultActions r) throws Exception {
		return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

}
