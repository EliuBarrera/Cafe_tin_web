package pe.edu.ucv.cafetin.cuentas.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Entrega el enlace de recuperación (RF-03) solo si el token quedó guardado, y en segundo plano. */
@Component
public class AvisoRecuperacion {

	/** Correo y token en claro: solo viven en memoria hasta que se envía el enlace. */
	public record Solicitada(String correo, String token) {
	}

	private final EnvioRecuperacion envio;

	public AvisoRecuperacion(EnvioRecuperacion envio) {
		this.envio = envio;
	}

	@Async
	@TransactionalEventListener
	public void alSolicitar(Solicitada solicitud) {
		envio.enviar(solicitud.correo(), solicitud.token());
	}

}
