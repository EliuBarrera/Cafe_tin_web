package pe.edu.ucv.cafetin.comun.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Un endpoint sin implementar responde 501 NO_IMPLEMENTADO. Se prueba sobre el manejador porque
 * ya no queda ningún endpoint del contrato en 501 para probarlo a través de un controller.
 */
class ManejadorErroresNoImplementadoTest {

	private final ManejadorErrores manejador = new ManejadorErrores();

	@Test
	void noImplementadoResponde501ConElRequisito() {
		var respuesta = manejador.negocio(new ApiException.NoImplementado("RF-22"));

		assertThat(respuesta.getStatusCode().value()).isEqualTo(501);
		assertThat(respuesta.getBody().codigo()).isEqualTo("NO_IMPLEMENTADO");
		assertThat(respuesta.getBody().mensaje()).isEqualTo("Pendiente de implementar (RF-22)");
		assertThat(respuesta.getBody().detalles()).isEmpty();
	}

}
