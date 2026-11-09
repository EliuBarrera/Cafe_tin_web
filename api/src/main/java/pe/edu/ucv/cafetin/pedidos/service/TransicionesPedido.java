package pe.edu.ucv.cafetin.pedidos.service;

import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.CANCELADO;
import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.CONFIRMADO;
import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.EN_PREPARACION;
import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.ENTREGADO;
import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.LISTO;
import static pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido.PENDIENTE;

import java.util.Map;

import org.springframework.stereotype.Component;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido;
import pe.edu.ucv.cafetin.pedidos.modelo.Pedido;

/**
 * Única clase que decide los cambios de estado del pedido (CLAUDE.md, "Estados del pedido"):
 *
 * <pre>
 * PENDIENTE (pagado) → CONFIRMADO → EN_PREPARACION → LISTO → ENTREGADO
 * PENDIENTE (sin pagar) → CANCELADO
 * </pre>
 *
 * Cualquier otra transición responde 409 TRANSICION_INVALIDA. {@code PENDIENTE} cubre pedidos pagados
 * y sin pagar: lo que los distingue es {@code pago.estado}, por eso se recibe {@code pagado}.
 * Las reglas que no son de estado (p. ej. entregar exige recibo verificado) las comprueba quien llama.
 */
@Component
public class TransicionesPedido {

	/** Siguiente estado del flujo normal (todas exigen pedido pagado). */
	private static final Map<EstadoPedido, EstadoPedido> SIGUIENTE = Map.of(
			PENDIENTE, CONFIRMADO,
			CONFIRMADO, EN_PREPARACION,
			EN_PREPARACION, LISTO,
			LISTO, ENTREGADO);

	public void validar(EstadoPedido desde, EstadoPedido hacia, boolean pagado) {
		if (hacia == CANCELADO) {
			if (desde != PENDIENTE) {
				throw invalida(desde, hacia);
			}
			if (pagado) {
				throw new ApiException.TransicionInvalida(
						"El pedido ya está pagado y no se puede cancelar (no hay reembolsos)");
			}
			return;
		}
		if (SIGUIENTE.get(desde) != hacia) {
			throw invalida(desde, hacia);
		}
		if (!pagado) {
			throw new ApiException.TransicionInvalida("El pedido aún no está pagado");
		}
	}

	/** Valida y aplica el cambio sobre la entidad. */
	public void aplicar(Pedido pedido, EstadoPedido hacia, boolean pagado) {
		validar(pedido.getEstado(), hacia, pagado);
		pedido.setEstado(hacia);
	}

	private static ApiException invalida(EstadoPedido desde, EstadoPedido hacia) {
		return new ApiException.TransicionInvalida("No se puede pasar de " + desde + " a " + hacia);
	}

}
