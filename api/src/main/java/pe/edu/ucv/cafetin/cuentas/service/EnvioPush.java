package pe.edu.ucv.cafetin.cuentas.service;

import pe.edu.ucv.cafetin.comun.evento.PedidoEstadoCambiado;

/** Entrega un aviso de estado al teléfono del estudiante (RF-08). */
public interface EnvioPush {

	void enviar(String fcmToken, PedidoEstadoCambiado aviso);

}
