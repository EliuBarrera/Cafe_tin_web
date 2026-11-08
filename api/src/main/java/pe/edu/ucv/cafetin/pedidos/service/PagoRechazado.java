package pe.edu.ucv.cafetin.pedidos.service;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;

/**
 * 402 PAGO_RECHAZADO. Es una clase propia para que {@code PedidoService.pagar} la excluya del
 * rollback: el pago debe quedar guardado como FALLIDO para que el reintento actualice esa fila.
 */
public class PagoRechazado extends ApiException {

	public PagoRechazado(String motivo) {
		super(CodigoError.PAGO_RECHAZADO, "La pasarela rechazó el cobro: " + motivo);
	}

}
