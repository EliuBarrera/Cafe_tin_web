package pe.edu.ucv.cafetin.cuentas.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.RecuperarRequest;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.RestablecerRequest;
import pe.edu.ucv.cafetin.cuentas.modelo.Usuario;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

/** Recuperación de contraseña (RF-03). En la base solo queda el hash del token. */
@Service
public class RecuperacionService {

	private static final int BYTES_TOKEN = 32;

	private final SecureRandom azar = new SecureRandom();
	private final UsuarioRepository usuarios;
	private final PasswordEncoder encoder;
	private final EnvioRecuperacion envio;
	private final int minutos;

	public RecuperacionService(UsuarioRepository usuarios, PasswordEncoder encoder, EnvioRecuperacion envio,
			@Value("${cafetin.recuperacion.minutos}") int minutos) {
		this.usuarios = usuarios;
		this.encoder = encoder;
		this.envio = envio;
		this.minutos = minutos;
	}

	/** Sin efecto visible si el correo no existe o la cuenta está inactiva: no revela qué cuentas hay. */
	@Transactional
	public void solicitar(RecuperarRequest solicitud) {
		usuarios.findByCorreo(ValidacionCuenta.normalizarCorreo(solicitud.correo()))
				.filter(Usuario::isActivo)
				.ifPresent(usuario -> {
					String token = generarToken();
					usuarios.guardarTokenRecuperacion(usuario.getId(), hash(token), minutos);
					envio.enviar(usuario.getCorreo(), token);
				});
	}

	@Transactional
	public void restablecer(RestablecerRequest solicitud) {
		ValidacionCuenta.contrasena(solicitud.nuevaContrasena());

		int cambiadas = usuarios.restablecerContrasena(hash(solicitud.token()), encoder.encode(solicitud.nuevaContrasena()));
		if (cambiadas == 0) {
			throw new ApiException.ReglaNegocio("El enlace de recuperación no es válido o ya expiró");
		}
	}

	private String generarToken() {
		byte[] bytes = new byte[BYTES_TOKEN];
		azar.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** SHA-256 y no BCrypt: el token ya es aleatorio y hay que poder buscarlo por su hash. */
	private static String hash(String token) {
		try {
			byte[] resumen = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(resumen);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("La JVM no incluye SHA-256", e);
		}
	}

}
