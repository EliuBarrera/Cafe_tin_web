package pe.edu.ucv.cafetin.cuentas.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;

/**
 * Reglas que las anotaciones de los DTO no cubren. Sin ellas, estos datos llegarían a los CHECK de
 * la tabla {@code usuario} (409 confuso) o a BCrypt (500).
 */
final class ValidacionCuenta {

	/** BCrypt no admite más; {@code @Size} cuenta caracteres y una "ñ" ocupa 2 bytes. */
	private static final int BYTES_MAXIMOS_CONTRASENA = 72;
	private static final String FORMATO_CORREO = ".+@.+\\..+";
	private static final int LARGO_MINIMO_NOMBRE = 2;

	private ValidacionCuenta() {
	}

	static String normalizarCorreo(String correo) {
		return correo.strip().toLowerCase(Locale.ROOT);
	}

	static String correoNuevo(String correo) {
		String normalizado = normalizarCorreo(correo);
		exigir(normalizado.matches(FORMATO_CORREO), "El correo debe incluir un dominio válido");
		return normalizado;
	}

	static String nombre(String nombre) {
		String recortado = nombre.strip();
		exigir(recortado.length() >= LARGO_MINIMO_NOMBRE, "El nombre debe tener al menos 2 caracteres");
		return recortado;
	}

	static void contrasena(String contrasena) {
		exigir(contrasena.getBytes(StandardCharsets.UTF_8).length <= BYTES_MAXIMOS_CONTRASENA,
				"La contraseña es demasiado larga");
	}

	private static void exigir(boolean condicion, String mensaje) {
		if (!condicion) {
			throw new ApiException(CodigoError.VALIDACION, mensaje);
		}
	}

}
