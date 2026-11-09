package pe.edu.ucv.cafetin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.cuentas.service.EnvioRecuperacion;
import pe.edu.ucv.cafetin.cuentas.service.EnvioRecuperacionCorreo;
import pe.edu.ucv.cafetin.cuentas.service.EnvioRecuperacionLog;

/** Con SPRING_MAIL_HOST configurado se envía por correo; sin él (dev y tests) se usa el log. */
class EnvioRecuperacionSeleccionTest {

	@Nested
	@SpringBootTest
	@Import(TestcontainersConfiguration.class)
	@ActiveProfiles("test")
	class SinServidorDeCorreo {

		@Autowired
		EnvioRecuperacion envio;

		@Test
		void usaElLog() {
			assertThat(envio).isInstanceOf(EnvioRecuperacionLog.class);
		}

	}

	@Nested
	@SpringBootTest(properties = { "spring.mail.host=localhost", "spring.mail.username=cafetin@gmail.com" })
	@Import(TestcontainersConfiguration.class)
	@ActiveProfiles("test")
	class ConServidorDeCorreo {

		@Autowired
		EnvioRecuperacion envio;

		@Test
		void usaElCorreo() {
			assertThat(envio).isInstanceOf(EnvioRecuperacionCorreo.class);
		}

	}

}
