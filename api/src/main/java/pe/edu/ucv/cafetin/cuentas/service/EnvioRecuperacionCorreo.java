package pe.edu.ucv.cafetin.cuentas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envía el enlace de recuperación por correo (RF-03). Solo existe si hay un servidor SMTP configurado
 * ({@code SPRING_MAIL_HOST}); sin él se usa {@link EnvioRecuperacionLog}. Un fallo del servidor se
 * registra y no se propaga: la API responde 202 igual, sin revelar si el correo tiene cuenta.
 */
@Component
@Primary
@ConditionalOnProperty("spring.mail.host")
public class EnvioRecuperacionCorreo implements EnvioRecuperacion {

	private static final Logger log = LoggerFactory.getLogger(EnvioRecuperacionCorreo.class);

	private final JavaMailSender correo;
	private final String remitente;
	private final String enlaceBase;
	private final int minutos;

	/** {@code remitente} vacío = la cuenta SMTP (Gmail exige que coincidan). */
	public EnvioRecuperacionCorreo(JavaMailSender correo, @Value("${spring.mail.username:}") String cuentaSmtp,
			@Value("${cafetin.correo.remitente:}") String remitente,
			@Value("${cafetin.recuperacion.enlace-base}") String enlaceBase,
			@Value("${cafetin.recuperacion.minutos}") int minutos) {
		this.correo = correo;
		this.remitente = remitente.isBlank() ? cuentaSmtp : remitente;
		this.enlaceBase = enlaceBase;
		this.minutos = minutos;
	}

	@Override
	public void enviar(String destinatario, String token) {
		SimpleMailMessage mensaje = new SimpleMailMessage();
		mensaje.setFrom(remitente);
		mensaje.setTo(destinatario);
		mensaje.setSubject("Recupera tu contraseña de Cafetín UCV");
		mensaje.setText("""
				Hola:

				Pediste cambiar tu contraseña de Cafetín UCV. Entra a este enlace para elegir una nueva:

				%s%s

				El enlace vence en %d minutos y sirve una sola vez. Si no lo pediste, ignora este correo:
				tu contraseña no cambia.
				""".formatted(enlaceBase, token, minutos));
		try {
			correo.send(mensaje);
		} catch (MailException e) {
			log.warn("No se pudo enviar el correo de recuperación a {}: {}", destinatario, e.getMessage());
		}
	}

}
