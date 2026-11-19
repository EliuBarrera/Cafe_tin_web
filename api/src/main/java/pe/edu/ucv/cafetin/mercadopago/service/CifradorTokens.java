package pe.edu.ucv.cafetin.mercadopago.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra los tokens de Mercado Pago de cada cafetín antes de guardarlos (AES-256-GCM). La llave es
 * {@code CAFETIN_CIFRADO_LLAVE}: 32 bytes en Base64 ({@code openssl rand -base64 32}). Así, una copia
 * de la base no basta para cobrar en nombre de un cafetín. Formato guardado: {@code v1:<base64(iv‖cifrado)>}.
 */
@Component
public class CifradorTokens {

	private static final String PREFIJO = "v1:";
	private static final int BYTES_IV = 12;
	private static final int BITS_ETIQUETA = 128;

	private final SecretKey llave;
	private final String problema;
	private final SecureRandom azar = new SecureRandom();

	public CifradorTokens(@Value("${cafetin.cifrado.llave:}") String llaveBase64) {
		SecretKey leida = null;
		String error = null;
		if (llaveBase64 == null || llaveBase64.isBlank()) {
			error = "Falta CAFETIN_CIFRADO_LLAVE. Genera una con: openssl rand -base64 32";
		} else {
			try {
				byte[] bytes = Base64.getDecoder().decode(llaveBase64.strip());
				if (bytes.length != 32) {
					error = "CAFETIN_CIFRADO_LLAVE debe tener 32 bytes (openssl rand -base64 32)";
				} else {
					leida = new SecretKeySpec(bytes, "AES");
				}
			} catch (IllegalArgumentException e) {
				error = "CAFETIN_CIFRADO_LLAVE no es Base64 válido (openssl rand -base64 32)";
			}
		}
		this.llave = leida;
		this.problema = error;
	}

	/** Falla al arrancar si se usará Mercado Pago sin una llave válida. */
	public void exigirLlave() {
		if (llave == null) {
			throw new IllegalStateException(problema);
		}
	}

	public String cifrar(String texto) {
		exigirLlave();
		try {
			byte[] iv = new byte[BYTES_IV];
			azar.nextBytes(iv);
			Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
			cifra.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(BITS_ETIQUETA, iv));
			byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
			byte[] todo = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
			return PREFIJO + Base64.getEncoder().encodeToString(todo);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo cifrar el token", e);
		}
	}

	public String descifrar(String guardado) {
		exigirLlave();
		if (guardado == null || !guardado.startsWith(PREFIJO)) {
			throw new IllegalStateException("Token guardado con un formato desconocido");
		}
		try {
			byte[] todo = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
			Cipher cifra = Cipher.getInstance("AES/GCM/NoPadding");
			cifra.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(BITS_ETIQUETA, todo, 0, BYTES_IV));
			return new String(cifra.doFinal(todo, BYTES_IV, todo.length - BYTES_IV), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			// Llave cambiada o dato alterado: el dueño tiene que volver a conectar su cuenta.
			throw new IllegalStateException("No se pudo descifrar el token (¿cambió CAFETIN_CIFRADO_LLAVE?)", e);
		}
	}

}
