package pe.edu.ucv.cafetin.mercadopago.service;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.mercadopago.config.MercadoPagoPropiedades;
import pe.edu.ucv.cafetin.mercadopago.config.PagosPropiedades;
import pe.edu.ucv.cafetin.mercadopago.dto.MercadoPagoDto.EstadoConexionResponse;
import pe.edu.ucv.cafetin.mercadopago.dto.MercadoPagoDto.UrlAutorizacionResponse;
import pe.edu.ucv.cafetin.mercadopago.repository.CuentaMercadoPagoRepository;
import pe.edu.ucv.cafetin.mercadopago.repository.CuentaMercadoPagoRepository.Cuenta;
import pe.edu.ucv.cafetin.mercadopago.service.ClienteMercadoPago.Tokens;

/**
 * El dueño conecta la cuenta de Mercado Pago de su cafetín (OAuth): la API guarda los tokens cifrados
 * y con ellos cobra en nombre del cafetín. El {@code state} es de un solo uso y vence a los 10 minutos.
 */
@Service
public class ConexionMercadoPagoService {

	private static final Logger log = LoggerFactory.getLogger(ConexionMercadoPagoService.class);

	private final CuentaMercadoPagoRepository cuentas;
	private final ClienteMercadoPago cliente;
	private final CifradorTokens cifrador;
	private final MercadoPagoPropiedades mp;
	private final PagosPropiedades pagos;
	private final SecureRandom azar = new SecureRandom();

	public ConexionMercadoPagoService(CuentaMercadoPagoRepository cuentas, ClienteMercadoPago cliente,
			CifradorTokens cifrador, MercadoPagoPropiedades mp, PagosPropiedades pagos) {
		this.cuentas = cuentas;
		this.cliente = cliente;
		this.cifrador = cifrador;
		this.mp = mp;
		this.pagos = pagos;
	}

	public EstadoConexionResponse estado(int idCafeteria) {
		String pasarela = pagos.pasarela().name();
		return cuentas.de(idCafeteria)
				.map(c -> new EstadoConexionResponse(pasarela, true, c.idUsuarioMp(), c.conectadoEn()))
				.orElseGet(() -> new EstadoConexionResponse(pasarela, false, null, null));
	}

	public UrlAutorizacionResponse iniciar(int idCafeteria) {
		exigirMercadoPago();
		byte[] bytes = new byte[32];
		azar.nextBytes(bytes);
		String estado = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		cuentas.guardarEstado(estado, idCafeteria);
		String url = UriComponentsBuilder.fromUriString(mp.urlAutorizacion())
				.queryParam("client_id", mp.clientId())
				.queryParam("response_type", "code")
				.queryParam("platform_id", "mp")
				.queryParam("state", estado)
				.queryParam("redirect_uri", mp.urlRetornoOauth())
				.encode()
				.build()
				.toUriString();
		return new UrlAutorizacionResponse(url);
	}

	/**
	 * Vuelta de Mercado Pago. Devuelve la URL del panel a la que se redirige al dueño, con
	 * {@code mercadopago=conectado} o {@code mercadopago=error}: es una navegación del navegador, no
	 * una llamada del panel, así que un error JSON no le serviría al dueño.
	 */
	public String completar(String codigo, String estado, String error) {
		String destino = mp.urlPanel() + "/cobros?mercadopago=";
		if (estado == null || estado.isBlank()) {
			return destino + "error";
		}
		// Se consume aunque el dueño haya rechazado la autorización: el state no se reutiliza.
		Integer idCafeteria = cuentas.consumirEstado(estado).orElse(null);
		if (idCafeteria == null || error != null || codigo == null || codigo.isBlank() || !mp.configurada()) {
			return destino + "error";
		}
		try {
			Tokens tokens = cliente.canjearCodigo(codigo);
			guardar(idCafeteria, tokens, true);
			log.info("Cafetería {} conectó la cuenta de Mercado Pago {}", idCafeteria, tokens.userId());
			return destino + "conectado";
		} catch (RestClientException | IllegalStateException e) {
			log.warn("No se pudo completar el OAuth de Mercado Pago de la cafetería {}: {}", idCafeteria, e.getMessage());
			return destino + "error";
		}
	}

	public void desconectar(int idCafeteria) {
		cuentas.borrar(idCafeteria);
	}

	/**
	 * Token de acceso del cafetín para cobrar. Si vence en menos de 7 días se renueva antes (dura
	 * 180 días). Sin cuenta conectada, el cafetín no puede cobrar: 409.
	 */
	public String tokenDe(int idCafeteria) {
		Cuenta cuenta = cuentas.de(idCafeteria).orElseThrow(() -> new ApiException.ReglaNegocio(
				"El cafetín aún no acepta pagos: su dueño debe conectar Mercado Pago"));
		if (cuenta.porVencer() && cuenta.refreshToken() != null) {
			try {
				Tokens nuevos = cliente.renovar(cifrador.descifrar(cuenta.refreshToken()));
				guardar(idCafeteria, nuevos, false);
				return nuevos.accessToken();
			} catch (RestClientException e) {
				// Se sigue con el token actual mientras no venza; se volverá a intentar en el próximo cobro.
				log.warn("No se pudo renovar el token de Mercado Pago de la cafetería {}: {}", idCafeteria, e.getMessage());
			}
		}
		return cifrador.descifrar(cuenta.accessToken());
	}

	private void guardar(int idCafeteria, Tokens tokens, boolean conexionNueva) {
		cuentas.guardar(idCafeteria, tokens.userId(), cifrador.cifrar(tokens.accessToken()),
				tokens.refreshToken() == null ? null : cifrador.cifrar(tokens.refreshToken()),
				tokens.expiraEnSegundos() == null ? 15_552_000L : tokens.expiraEnSegundos(), conexionNueva);
	}

	private void exigirMercadoPago() {
		if (!pagos.conMercadoPago()) {
			throw new ApiException.ReglaNegocio(
					"La API cobra con la pasarela simulada: Mercado Pago no está activado (CAFETIN_PAGOS_PASARELA)");
		}
	}

}
