package pe.edu.ucv.cafetin.cuentas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sin servicio de correo, el enlace se escribe en el log solo en desarrollo. Fuera de dev no se
 * registra el token: quien lo leyera en el log podría cambiar la contraseña de la cuenta.
 */
@Component
public class EnvioRecuperacionLog implements EnvioRecuperacion {

	private static final Logger log = LoggerFactory.getLogger(EnvioRecuperacionLog.class);

	private final boolean registrarEnlace;
	private final String enlaceBase;

	public EnvioRecuperacionLog(@Value("${cafetin.recuperacion.registrar-enlace}") boolean registrarEnlace,
			@Value("${cafetin.recuperacion.enlace-base}") String enlaceBase) {
		this.registrarEnlace = registrarEnlace;
		this.enlaceBase = enlaceBase;
	}

	@Override
	public void enviar(String correo, String token) {
		if (registrarEnlace) {
			log.info("Enlace de recuperación para {}: {}{}", correo, enlaceBase, token);
		} else {
			log.warn("Recuperación solicitada, pero no hay un servicio de correo configurado para entregar el enlace");
		}
	}

}
