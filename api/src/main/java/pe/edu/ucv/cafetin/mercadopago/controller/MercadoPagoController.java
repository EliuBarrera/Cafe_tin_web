package pe.edu.ucv.cafetin.mercadopago.controller;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.mercadopago.config.MercadoPagoPropiedades;
import pe.edu.ucv.cafetin.mercadopago.config.PagosPropiedades;
import pe.edu.ucv.cafetin.mercadopago.service.CobroMercadoPagoService;
import pe.edu.ucv.cafetin.mercadopago.service.ConexionMercadoPagoService;
import pe.edu.ucv.cafetin.mercadopago.service.FirmaWebhook;

/**
 * Rutas públicas que llama Mercado Pago (o el navegador al volver de Mercado Pago). Ninguna confía en
 * lo que recibe: el OAuth valida el {@code state}, y el webhook verifica la firma y vuelve a consultar
 * el pago con el token del cafetín.
 */
@RestController
@RequestMapping("/api/mercadopago")
@Tag(name = "Cobros")
public class MercadoPagoController {

	private static final Logger log = LoggerFactory.getLogger(MercadoPagoController.class);

	private final ConexionMercadoPagoService conexion;
	private final CobroMercadoPagoService cobro;
	private final FirmaWebhook firma;
	private final PagosPropiedades pagos;
	private final MercadoPagoPropiedades mp;

	public MercadoPagoController(ConexionMercadoPagoService conexion, CobroMercadoPagoService cobro, FirmaWebhook firma,
			PagosPropiedades pagos, MercadoPagoPropiedades mp) {
		this.conexion = conexion;
		this.cobro = cobro;
		this.firma = firma;
		this.pagos = pagos;
		this.mp = mp;
	}

	@GetMapping("/oauth/retorno")
	@Operation(summary = "Retorno del OAuth de Mercado Pago", description = """
			Público. Mercado Pago redirige aquí al dueño tras autorizar. Valida el state, guarda los tokens
			cifrados y redirige (302) al panel con ?mercadopago=conectado o ?mercadopago=error.""")
	public ResponseEntity<Void> retornoOauth(@RequestParam(required = false) String code,
			@RequestParam(required = false) String state, @RequestParam(required = false) String error) {
		return redirigir(conexion.completar(code, state, error));
	}

	@GetMapping("/retorno-pago")
	@Operation(summary = "Vuelta del checkout", description = """
			Público. Checkout Pro vuelve aquí al terminar (aprobado, rechazado o pendiente) y se redirige (302)
			a la app (cafetin://pago/{pedido}), que confirma con POST /api/pedidos/{id}/pago/verificar.""")
	public ResponseEntity<Void> retornoPago(@RequestParam Integer pedido) {
		return redirigir(mp.urlRetornoApp() + pedido);
	}

	@PostMapping("/webhook")
	@Operation(summary = "Notificación de Mercado Pago", description = """
			Público, con firma x-signature (MP_WEBHOOK_SECRET). Solo atiende pagos: consulta el pago a Mercado
			Pago y lo confirma (idempotente). Firma inválida → 401.""")
	public ResponseEntity<Void> webhook(
			@RequestParam(required = false) Integer cafeteria,
			@RequestParam(name = "data.id", required = false) String idDato,
			@RequestParam(required = false) String type,
			@RequestHeader(name = "x-signature", required = false) String xSignature,
			@RequestHeader(name = "x-request-id", required = false) String xRequestId,
			@RequestBody(required = false) Map<String, Object> cuerpo) {
		if (!pagos.conMercadoPago()) {
			return ResponseEntity.ok().build();
		}
		String tipo = type != null ? type : cuerpo != null && cuerpo.get("type") instanceof String t ? t : null;
		String id = idDato != null ? idDato
				: cuerpo != null && cuerpo.get("data") instanceof Map<?, ?> data && data.get("id") != null
						? String.valueOf(data.get("id")) : null;
		if (!firma.valida(xSignature, xRequestId, id)) {
			throw new ApiException(CodigoError.NO_AUTENTICADO, "Firma de Mercado Pago inválida");
		}
		if (!"payment".equals(tipo) || cafeteria == null || id == null) {
			return ResponseEntity.ok().build(); // otros avisos (merchant_order, etc.): nada que hacer
		}
		long idPagoMp;
		try {
			idPagoMp = Long.parseLong(id);
		} catch (NumberFormatException e) {
			return ResponseEntity.ok().build();
		}
		try {
			cobro.notificar(cafeteria, idPagoMp);
		} catch (ApiException.ReglaNegocio e) {
			// El cafetín desconectó su cuenta: no hay con qué consultar; reintentar no ayudaría.
			log.warn("Aviso del pago {} para la cafetería {} sin cuenta conectada", idPagoMp, cafeteria);
		}
		return ResponseEntity.ok().build();
	}

	private static ResponseEntity<Void> redirigir(String url) {
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
	}

}
