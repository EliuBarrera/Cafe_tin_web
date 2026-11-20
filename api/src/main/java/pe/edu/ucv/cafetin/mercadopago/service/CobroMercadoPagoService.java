package pe.edu.ucv.cafetin.mercadopago.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.mercadopago.config.MercadoPagoPropiedades;
import pe.edu.ucv.cafetin.mercadopago.config.PagosPropiedades;
import pe.edu.ucv.cafetin.mercadopago.service.ClienteMercadoPago.PagoMp;
import pe.edu.ucv.cafetin.mercadopago.service.ClienteMercadoPago.PreferenciaCreada;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPago;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido;
import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;
import pe.edu.ucv.cafetin.pedidos.modelo.Pago;
import pe.edu.ucv.cafetin.pedidos.modelo.Pedido;
import pe.edu.ucv.cafetin.pedidos.repository.PagoRepository;
import pe.edu.ucv.cafetin.pedidos.repository.PedidoRepository;
import pe.edu.ucv.cafetin.pedidos.service.EmisorRecibos;
import pe.edu.ucv.cafetin.pedidos.service.TransicionesPedido;

/**
 * Cobro con Checkout Pro (RF-06, RF-07, RF-16).
 *
 * <ol>
 * <li>{@link #iniciar}: crea la preferencia con el token del cafetín y deja el pago PENDIENTE con su
 * {@code urlPago}. El stock no se toca hasta que Mercado Pago apruebe.</li>
 * <li>{@link #notificar} (webhook) y {@link #verificar} (la app al volver del checkout) llegan al mismo
 * {@code aplicar}, idempotente: consulta el pago a Mercado Pago, comprueba {@code external_reference} y
 * monto y, si está aprobado, pasa el pago a PAGADO (el trigger descuenta stock) y emite el recibo.</li>
 * <li>Si al aprobar ya no hay stock (otro estudiante pagó la última unidad) o el pedido se canceló, se
 * reembolsa el cobro: pago REEMBOLSADO y pedido CANCELADO. Es la única excepción a "sin reembolsos en la
 * v1" (decisión del 19/11/2026): con Checkout Pro el dinero se cobra antes de saber si hay stock.</li>
 * </ol>
 */
@Service
public class CobroMercadoPagoService {

	private static final Logger log = LoggerFactory.getLogger(CobroMercadoPagoService.class);
	private static final ZoneId LIMA = ZoneId.of("America/Lima");
	private static final DateTimeFormatter FECHA_MP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
	/** Minutos para pagar en Checkout Pro; después se vuelve a pedir el enlace. */
	private static final int MINUTOS_PARA_PAGAR = 15;
	private static final String CHECK_STOCK = "ck_inventario_cantidad";

	private enum Accion {
		NADA, REEMBOLSAR
	}

	private final ClienteMercadoPago cliente;
	private final ConexionMercadoPagoService conexion;
	private final PagoRepository pagos;
	private final PedidoRepository pedidos;
	private final EmisorRecibos recibos;
	private final TransicionesPedido transiciones;
	private final MercadoPagoPropiedades mp;
	private final PagosPropiedades config;
	private final EntityManager em;
	private final TransactionTemplate tx;

	public CobroMercadoPagoService(ClienteMercadoPago cliente, ConexionMercadoPagoService conexion,
			PagoRepository pagos, PedidoRepository pedidos, EmisorRecibos recibos, TransicionesPedido transiciones,
			MercadoPagoPropiedades mp, PagosPropiedades config, EntityManager em, PlatformTransactionManager transacciones) {
		this.cliente = cliente;
		this.conexion = conexion;
		this.pagos = pagos;
		this.pedidos = pedidos;
		this.recibos = recibos;
		this.transiciones = transiciones;
		this.mp = mp;
		this.config = config;
		this.em = em;
		this.tx = new TransactionTemplate(transacciones);
	}

	// ------------------------------------------------------------------ iniciar

	/** Dentro de la transacción de {@code PedidoService.pagar}: el pago ya tiene id (external_reference). */
	public void iniciar(Pedido pedido, Pago pago, String nombreCafeteria) {
		String token = conexion.tokenDe(pedido.getIdCafeteria());
		PreferenciaCreada creada = cliente.crearPreferencia(token, preferencia(pedido, pago, nombreCafeteria));
		pago.setMpPreferenciaId(creada.id());
		pago.setUrlPago(creada.initPoint());
	}

	Map<String, Object> preferencia(Pedido pedido, Pago pago, String nombreCafeteria) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("id", pedido.getCodigoPedido());
		item.put("title", "Pedido " + pedido.getCodigoPedido() + " - " + nombreCafeteria);
		item.put("quantity", 1);
		item.put("unit_price", pago.getMonto());
		item.put("currency_id", config.moneda());

		String vuelta = mp.urlPublicaApi() + "/api/mercadopago/retorno-pago?pedido=" + pedido.getId();
		OffsetDateTime ahora = OffsetDateTime.now(LIMA);

		Map<String, Object> pref = new LinkedHashMap<>();
		pref.put("items", List.of(item));
		pref.put("external_reference", String.valueOf(pago.getId()));
		pref.put("back_urls", Map.of("success", vuelta, "failure", vuelta, "pending", vuelta));
		// Mercado Pago solo acepta auto_return y notification_url con una URL pública (https). En local
		// sin túnel, la app confirma con /pago/verificar al volver.
		if (vuelta.startsWith("https://")) {
			pref.put("auto_return", "approved");
			pref.put("notification_url",
					mp.urlPublicaApi() + "/api/mercadopago/webhook?cafeteria=" + pedido.getIdCafeteria());
		}
		pref.put("expires", true);
		pref.put("expiration_date_from", ahora.format(FECHA_MP));
		pref.put("expiration_date_to", ahora.plusMinutes(MINUTOS_PARA_PAGAR).format(FECHA_MP));
		BigDecimal comision = pago.getMonto().multiply(config.comision()).setScale(2, RoundingMode.HALF_UP);
		if (comision.signum() > 0) {
			pref.put("marketplace_fee", comision);
		}
		return pref;
	}

	// ------------------------------------------------------------------ confirmar

	/** Webhook: Mercado Pago avisa que el pago {@code idPagoMp} del cafetín cambió. */
	public void notificar(int idCafeteria, long idPagoMp) {
		String token = conexion.tokenDe(idCafeteria);
		aplicar(idCafeteria, cliente.consultarPago(token, idPagoMp), token);
	}

	/**
	 * La app vuelve del checkout y pregunta. Busca en Mercado Pago los pagos de este pedido (por
	 * {@code external_reference}), así funciona aunque el webhook no llegue (p. ej. en local).
	 */
	public void verificar(Integer idEstudiante, Integer idPedido) {
		Pedido pedido = pedidos.findByIdAndEstudianteId(idPedido, idEstudiante)
				.orElseThrow(() -> new ApiException.NoEncontrado("No existe el pedido " + idPedido));
		if (!config.conMercadoPago()) {
			return; // la pasarela simulada cobra al instante: no hay nada que verificar
		}
		Pago pago = pagos.findByPedidoId(idPedido).orElse(null);
		if (pago == null || pago.getEstado() == EstadoPago.PAGADO || pago.getEstado() == EstadoPago.REEMBOLSADO) {
			return;
		}
		String token = conexion.tokenDe(pedido.getIdCafeteria());
		List<PagoMp> encontrados = cliente.buscarPagos(token, String.valueOf(pago.getId()));
		encontrados.stream().filter(PagoMp::aprobado).findFirst()
				.or(() -> encontrados.stream().findFirst())
				.ifPresent(p -> aplicar(pedido.getIdCafeteria(), p, token));
	}

	private void aplicar(int idCafeteria, PagoMp pagoMp, String token) {
		Integer idPago = idPago(pagoMp);
		if (idPago == null) {
			log.warn("Pago {} de Mercado Pago sin external_reference válido: se ignora", pagoMp.id());
			return;
		}
		Accion accion;
		try {
			accion = tx.execute(s -> aplicarEnTransaccion(idCafeteria, idPago, pagoMp));
		} catch (DataIntegrityViolationException e) {
			if (!String.valueOf(e.getMostSpecificCause().getMessage()).contains(CHECK_STOCK)) {
				throw e;
			}
			log.info("Pago {} aprobado sin stock para el pedido: se reembolsa", pagoMp.id());
			accion = Accion.REEMBOLSAR;
		}
		if (accion == Accion.REEMBOLSAR) {
			cliente.reembolsar(token, pagoMp.id());
			tx.executeWithoutResult(s -> marcarReembolsado(idPago, pagoMp));
		}
	}

	/**
	 * Bloquea el pedido y luego el pago (el mismo orden que {@code PedidoService.pagar} y que cancelar),
	 * para que no se crucen: el segundo en llegar ve el resultado del primero.
	 */
	private Accion aplicarEnTransaccion(int idCafeteria, Integer idPago, PagoMp pagoMp) {
		Integer idPedido = pagos.idPedidoDe(idPago).orElse(null);
		if (idPedido == null) {
			return Accion.NADA;
		}
		Pedido pedido = em.find(Pedido.class, idPedido, LockModeType.PESSIMISTIC_WRITE);
		Pago pago = pagos.paraActualizar(idPago).orElseThrow();
		if (!pedido.getIdCafeteria().equals(idCafeteria)) {
			log.warn("Pago {} de Mercado Pago apunta a un pedido de otra cafetería: se ignora", pagoMp.id());
			return Accion.NADA;
		}
		if (pago.getEstado() == EstadoPago.PAGADO || pago.getEstado() == EstadoPago.REEMBOLSADO) {
			return Accion.NADA; // ya se confirmó (webhook y verificación pueden llegar los dos)
		}
		if (pagoMp.aprobado()) {
			if (pagoMp.monto() == null || pago.getMonto().compareTo(pagoMp.monto()) != 0) {
				log.warn("Pago {} de Mercado Pago por {} no coincide con el total {} del pedido {}: no se confirma",
						pagoMp.id(), pagoMp.monto(), pago.getMonto(), pedido.getCodigoPedido());
				return Accion.NADA;
			}
			if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
				return Accion.REEMBOLSAR; // se canceló mientras el estudiante pagaba
			}
			pago.setMetodoPago(metodo(pagoMp));
			pago.setReferencia(String.valueOf(pagoMp.id()));
			pago.setMpPagoId(pagoMp.id());
			pago.setEstado(EstadoPago.PAGADO);
			pagos.saveAndFlush(pago); // trg_descontar_stock; sin stock → DataIntegrityViolationException
			recibos.emitir(pago, pedido);
			return Accion.NADA;
		}
		if (pagoMp.rechazado() && pago.getEstado() == EstadoPago.PENDIENTE) {
			pago.setEstado(EstadoPago.FALLIDO);
			pago.setMpPagoId(pagoMp.id());
			pagos.saveAndFlush(pago);
		}
		return Accion.NADA;
	}

	private void marcarReembolsado(Integer idPago, PagoMp pagoMp) {
		Integer idPedido = pagos.idPedidoDe(idPago).orElseThrow();
		Pedido pedido = em.find(Pedido.class, idPedido, LockModeType.PESSIMISTIC_WRITE);
		Pago pago = pagos.paraActualizar(idPago).orElseThrow();
		pago.setMetodoPago(metodo(pagoMp));
		pago.setReferencia(String.valueOf(pagoMp.id()));
		pago.setMpPagoId(pagoMp.id());
		pago.setEstado(EstadoPago.REEMBOLSADO);
		if (pedido.getEstado() == EstadoPedido.PENDIENTE) {
			transiciones.aplicar(pedido, EstadoPedido.CANCELADO, false);
		}
		pagos.saveAndFlush(pago);
	}

	private static Integer idPago(PagoMp pagoMp) {
		try {
			return pagoMp.referenciaExterna() == null ? null : Integer.valueOf(pagoMp.referenciaExterna());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Medio que informa Mercado Pago; lo que no es tarjeta ni Yape queda como OTRO. */
	public static MetodoPago metodo(PagoMp pagoMp) {
		if ("yape".equalsIgnoreCase(pagoMp.medio())) {
			return MetodoPago.YAPE;
		}
		String tipo = pagoMp.tipo() == null ? "" : pagoMp.tipo();
		return switch (tipo) {
			case "credit_card", "debit_card", "prepaid_card" -> MetodoPago.TARJETA;
			default -> MetodoPago.OTRO;
		};
	}

}
