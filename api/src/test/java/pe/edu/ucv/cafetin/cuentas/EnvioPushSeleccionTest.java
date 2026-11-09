package pe.edu.ucv.cafetin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nimbusds.jose.util.JSONObjectUtils;

import pe.edu.ucv.cafetin.TestcontainersConfiguration;
import pe.edu.ucv.cafetin.cuentas.service.EnvioPush;
import pe.edu.ucv.cafetin.cuentas.service.EnvioPushFcm;
import pe.edu.ucv.cafetin.cuentas.service.EnvioPushLog;

/** Con FIREBASE_CREDENTIALS el push sale por FCM; sin ella (dev y tests) solo se registra en el log. */
class EnvioPushSeleccionTest {

	@Nested
	@SpringBootTest
	@Import(TestcontainersConfiguration.class)
	@ActiveProfiles("test")
	class SinFirebase {

		@Autowired
		EnvioPush envio;

		@Test
		void usaElLog() {
			assertThat(envio).isInstanceOf(EnvioPushLog.class);
		}

	}

	@Nested
	@SpringBootTest
	@Import(TestcontainersConfiguration.class)
	@ActiveProfiles("test")
	class ConFirebase {

		@DynamicPropertySource
		static void cuentaDeServicio(DynamicPropertyRegistry registro) throws NoSuchAlgorithmException {
			KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
			generador.initialize(2048);
			String pem = "-----BEGIN PRIVATE KEY-----\n"
					+ Base64.getEncoder().encodeToString(generador.generateKeyPair().getPrivate().getEncoded())
					+ "\n-----END PRIVATE KEY-----\n";
			String json = JSONObjectUtils.toJSONString(Map.of("project_id", "cafetin-test",
					"client_email", "push@cafetin-test.iam.gserviceaccount.com", "private_key", pem));
			registro.add("FIREBASE_CREDENTIALS", () -> json);
		}

		@Autowired
		EnvioPush envio;

		@Test
		void usaFcm() {
			assertThat(envio).isInstanceOf(EnvioPushFcm.class);
		}

	}

}
