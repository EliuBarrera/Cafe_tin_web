package pe.edu.ucv.cafetin.cuentas.service;

import pe.edu.ucv.cafetin.comun.evento.PedidoEstadoCambiado;

/** Entrega un aviso de estado al teléfono del estudiante (RF-08). */
public interface EnvioPush {

	/** @throws TokenInvalido si el servicio de push ya no reconoce el token (app desinstalada, token renovado). */
	void enviar(String fcmToken, PedidoEstadoCambiado aviso);

	/** El token ya no sirve y conviene borrarlo para no seguir enviando a un teléfono que no existe. */
	class TokenInvalido extends RuntimeException {

		public TokenInvalido(String motivo) {
			super(motivo);
		}

	}

}
