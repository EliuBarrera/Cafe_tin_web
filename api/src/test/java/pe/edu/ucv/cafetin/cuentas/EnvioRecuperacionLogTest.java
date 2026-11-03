package pe.edu.ucv.cafetin.cuentas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import pe.edu.ucv.cafetin.cuentas.service.EnvioRecuperacionLog;

/** El token solo puede llegar al log si se activa a propósito (perfil dev), nunca en producción. */
@ExtendWith(OutputCaptureExtension.class)
class EnvioRecuperacionLogTest {

	private static final String BASE = "http://localhost:5173/restablecer?token=";

	@Test
	void enDesarrolloEscribeElEnlace(CapturedOutput salida) {
		new EnvioRecuperacionLog(true, BASE).enviar("ana@ucv.edu.pe", "TOKEN-SECRETO");

		assertThat(salida).contains(BASE + "TOKEN-SECRETO");
	}

	@Test
	void sinActivarloNuncaEscribeElToken(CapturedOutput salida) {
		new EnvioRecuperacionLog(false, BASE).enviar("ana@ucv.edu.pe", "TOKEN-SECRETO");

		assertThat(salida).doesNotContain("TOKEN-SECRETO");
	}

}
