package pe.edu.ucv.cafetin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import pe.edu.ucv.cafetin.cuentas.service.EnvioRecuperacionCorreo;

/** RF-03 en producción: el enlace llega por correo y un fallo del servidor SMTP no rompe la petición. */
class EnvioRecuperacionCorreoTest {

	private static final String BASE = "https://cafe-tin-web.vercel.app/restablecer?token=";

	private final JavaMailSender correo = mock(JavaMailSender.class);

	@Test
	void enviaElEnlaceConElTokenYLaExpiracion() {
		new EnvioRecuperacionCorreo(correo, "cafetin@gmail.com", "", BASE, 30).enviar("ana@ucv.edu.pe", "TOKEN-123");

		ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(correo).send(enviado.capture());
		SimpleMailMessage mensaje = enviado.getValue();
		assertThat(mensaje.getTo()).containsExactly("ana@ucv.edu.pe");
		assertThat(mensaje.getFrom()).isEqualTo("cafetin@gmail.com");
		assertThat(mensaje.getSubject()).contains("contraseña");
		assertThat(mensaje.getText()).contains(BASE + "TOKEN-123").contains("30 minutos");
	}

	@Test
	void usaElRemitenteConfiguradoSiExiste() {
		new EnvioRecuperacionCorreo(correo, "cuenta-smtp@gmail.com", "Cafetín UCV <no-responder@cafetin.pe>", BASE, 30)
				.enviar("ana@ucv.edu.pe", "TOKEN-123");

		ArgumentCaptor<SimpleMailMessage> enviado = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(correo).send(enviado.capture());
		assertThat(enviado.getValue().getFrom()).isEqualTo("Cafetín UCV <no-responder@cafetin.pe>");
	}

	@Test
	void unFalloDelServidorDeCorreoNoSePropaga() {
		doThrow(new MailSendException("SMTP caído")).when(correo).send(any(SimpleMailMessage.class));

		assertThatCode(() -> new EnvioRecuperacionCorreo(correo, "cafetin@gmail.com", "", BASE, 30)
				.enviar("ana@ucv.edu.pe", "TOKEN-123")).doesNotThrowAnyException();
	}

}
