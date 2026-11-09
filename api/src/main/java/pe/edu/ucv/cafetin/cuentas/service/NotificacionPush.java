package pe.edu.ucv.cafetin.cuentas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import pe.edu.ucv.cafetin.comun.evento.PedidoEstadoCambiado;
import pe.edu.ucv.cafetin.cuentas.modelo.Usuario;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

/**
 * Envía por push los avisos de estado del pedido (RF-08). Escucha después del commit
 * ({@code AFTER_COMMIT}, el valor por defecto): un cambio que termina en 409 y se revierte no avisa.
 * La fila en {@code notificacion} ya quedó guardada, así que un fallo del envío solo se registra.
 */
@Component
public class NotificacionPush {

	private static final Logger log = LoggerFactory.getLogger(NotificacionPush.class);

	private final UsuarioRepository usuarios;
	private final EnvioPush envio;

	public NotificacionPush(UsuarioRepository usuarios, EnvioPush envio) {
		this.usuarios = usuarios;
		this.envio = envio;
	}

	@TransactionalEventListener
	public void alCambiarEstado(PedidoEstadoCambiado aviso) {
		String token = usuarios.findById(aviso.idUsuario()).map(Usuario::getFcmToken).orElse(null);
		if (token == null) {
			return; // sin teléfono registrado: la app lo verá en la bandeja al abrirse
		}
		try {
			envio.enviar(token, aviso);
		} catch (EnvioPush.TokenInvalido e) {
			usuarios.quitarFcmToken(aviso.idUsuario(), token);
			log.info("Token de push del usuario {} borrado: {}", aviso.idUsuario(), e.getMessage());
		} catch (RuntimeException e) {
			log.warn("No se pudo enviar el push del pedido {} al usuario {}: {}", aviso.idPedido(), aviso.idUsuario(),
					e.getMessage());
		}
	}

}
