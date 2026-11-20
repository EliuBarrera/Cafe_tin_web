package pe.edu.ucv.cafetin.mercadopago.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import pe.edu.ucv.cafetin.mercadopago.config.MercadoPagoPropiedades;

/**
 * Verifica que una notificación venga de Mercado Pago. El encabezado {@code x-signature} trae
 * {@code ts=<segundos>,v1=<hmac>}; el HMAC-SHA256 con {@code MP_WEBHOOK_SECRET} se calcula sobre
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} (se omite la parte que no venga).
 * Aunque la firma sea válida, el pago se vuelve a consultar a Mercado Pago antes de confirmarlo.
 */
@Component
public class FirmaWebhook {

	private final MercadoPagoPropiedades mp;

	public FirmaWebhook(MercadoPagoPropiedades mp) {
		this.mp = mp;
	}

	public boolean valida(String firma, String idSolicitud, String idDato) {
		String secreto = mp.webhookSecret();
		if (secreto == null || secreto.isBlank() || firma == null) {
			return false;
		}
		String ts = null;
		String v1 = null;
		for (String parte : firma.split(",")) {
			String[] clave = parte.strip().split("=", 2);
			if (clave.length == 2 && clave[0].equals("ts")) {
				ts = clave[1];
			} else if (clave.length == 2 && clave[0].equals("v1")) {
				v1 = clave[1];
			}
		}
		if (ts == null || v1 == null) {
			return false;
		}
		StringBuilder manifiesto = new StringBuilder();
		if (idDato != null && !idDato.isBlank()) {
			// Mercado Pago firma los id alfanuméricos en minúsculas.
			manifiesto.append("id:").append(idDato.toLowerCase(Locale.ROOT)).append(';');
		}
		if (idSolicitud != null && !idSolicitud.isBlank()) {
			manifiesto.append("request-id:").append(idSolicitud).append(';');
		}
		manifiesto.append("ts:").append(ts).append(';');
		byte[] esperado = hmac(secreto, manifiesto.toString());
		byte[] recibido;
		try {
			recibido = HexFormat.of().parseHex(v1);
		} catch (IllegalArgumentException e) {
			return false;
		}
		return MessageDigest.isEqual(esperado, recibido);
	}

	static byte[] hmac(String secreto, String texto) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return mac.doFinal(texto.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA256 no disponible", e);
		}
	}

}
