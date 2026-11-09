package pe.edu.ucv.cafetin.pedidos;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.CANCELADO;
import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.CONFIRMADO;
import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.EN_PREPARACION;
import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.ENTREGADO;
import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.LISTO;
import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.PENDIENTE;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido;
import pe.edu.ucv.cafetin.pedidos.service.TransicionesPedido;

/** Máquina de estados del pedido (CLAUDE.md, "Estados del pedido"). Sin base de datos. */
class TransicionesPedidoTest {

	private final TransicionesPedido transiciones = new TransicionesPedido();

	/** Las únicas transiciones válidas: "desde>hacia>pagado". */
	private static final Set<String> VALIDAS = Set.of(
			"PENDIENTE>CONFIRMADO>true",
			"CONFIRMADO>EN_PREPARACION>true",
			"EN_PREPARACION>LISTO>true",
			"LISTO>ENTREGADO>true",
			"PENDIENTE>CANCELADO>false");

	@ParameterizedTest(name = "{0} -> {1} (pagado={2}) es válida")
	@CsvSource({
			"PENDIENTE, CONFIRMADO, true",
			"CONFIRMADO, EN_PREPARACION, true",
			"EN_PREPARACION, LISTO, true",
			"LISTO, ENTREGADO, true",
			"PENDIENTE, CANCELADO, false" })
	void transicionesValidas(EstadoPedido desde, EstadoPedido hacia, boolean pagado) {
		assertThatCode(() -> transiciones.validar(desde, hacia, pagado)).doesNotThrowAnyException();
	}

	@Test
	void cualquierOtraCombinacionResponde409() {
		for (EstadoPedido desde : EstadoPedido.values()) {
			for (EstadoPedido hacia : EstadoPedido.values()) {
				for (boolean pagado : new boolean[] { true, false }) {
					if (VALIDAS.contains(desde + ">" + hacia + ">" + pagado)) {
						continue;
					}
					assertThatThrownBy(() -> transiciones.validar(desde, hacia, pagado))
							.as("%s -> %s (pagado=%s)", desde, hacia, pagado)
							.isInstanceOf(ApiException.class)
							.extracting(e -> ((ApiException) e).getCodigo())
							.isEqualTo(CodigoError.TRANSICION_INVALIDA);
				}
			}
		}
	}

	@Test
	void unPedidoPagadoNoSeCancela() {
		assertThatThrownBy(() -> transiciones.validar(PENDIENTE, CANCELADO, true))
				.isInstanceOf(ApiException.TransicionInvalida.class)
				.hasMessageContaining("pagado");
	}

	@Test
	void unPedidoSinPagarNoSeConfirma() {
		assertThatThrownBy(() -> transiciones.validar(PENDIENTE, CONFIRMADO, false))
				.isInstanceOf(ApiException.TransicionInvalida.class);
	}

	@Test
	void noSeRetrocedeNiSeSaltaEstados() {
		assertThatThrownBy(() -> transiciones.validar(LISTO, CONFIRMADO, true))
				.isInstanceOf(ApiException.TransicionInvalida.class);
		assertThatThrownBy(() -> transiciones.validar(CONFIRMADO, LISTO, true))
				.isInstanceOf(ApiException.TransicionInvalida.class);
		assertThatThrownBy(() -> transiciones.validar(EN_PREPARACION, ENTREGADO, true))
				.isInstanceOf(ApiException.TransicionInvalida.class);
	}

}
