package pe.edu.ucv.cafetin.cuentas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pe.edu.ucv.cafetin.comun.evento.PedidoEstadoCambiado;

/**
 * Mientras no haya un proyecto de Firebase configurado, el aviso solo se escribe en el log. El
 * estudiante igual lo ve en su bandeja ({@code GET /api/usuarios/me/notificaciones}).
 */
@Component
public class EnvioPushLog implements EnvioPush {

	private static final Logger log = LoggerFactory.getLogger(EnvioPushLog.class);

	@Override
	public void enviar(String fcmToken, PedidoEstadoCambiado aviso) {
		log.info("Push sin FCM configurado para el usuario {} ({}): {}", aviso.idUsuario(), aviso.tipo(),
				aviso.mensaje());
	}

}
