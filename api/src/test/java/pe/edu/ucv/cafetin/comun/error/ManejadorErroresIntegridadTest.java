package pe.edu.ucv.cafetin.comun.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** La violación del CHECK de stock se traduce a STOCK_INSUFICIENTE; el resto, a CONFLICTO. */
class ManejadorErroresIntegridadTest {

	private final ManejadorErrores manejador = new ManejadorErrores();

	@Test
	void checkDeStockEsStockInsuficiente() {
		var e = new DataIntegrityViolationException("could not execute statement", new SQLException(
				"ERROR: new row for relation \"inventario\" violates check constraint \"ck_inventario_cantidad\""));

		var respuesta = manejador.integridad(e);

		assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
		assertThat(respuesta.getBody().codigo()).isEqualTo("STOCK_INSUFICIENTE");
		assertThat(respuesta.getBody().mensaje()).isEqualTo("Un producto de tu pedido se agotó");
	}

	@Test
	void otraRestriccionEsConflictoSinFiltrarElSql() {
		var e = new DataIntegrityViolationException("could not execute statement", new SQLException(
				"ERROR: duplicate key value violates unique constraint \"uq_usuario_correo\""));

		var respuesta = manejador.integridad(e);

		assertThat(respuesta.getStatusCode().value()).isEqualTo(409);
		assertThat(respuesta.getBody().codigo()).isEqualTo("CONFLICTO");
		assertThat(respuesta.getBody().mensaje()).doesNotContain("uq_usuario_correo");
	}

}
