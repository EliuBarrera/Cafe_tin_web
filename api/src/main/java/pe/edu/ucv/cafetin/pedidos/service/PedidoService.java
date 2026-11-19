package pe.edu.ucv.cafetin.pedidos.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import pe.edu.ucv.cafetin.cafeterias.dto.CafeteriaResponse;
import pe.edu.ucv.cafetin.cafeterias.modelo.Cafeteria;
import pe.edu.ucv.cafetin.cafeterias.repository.CafeteriaRepository;
import pe.edu.ucv.cafetin.cafeterias.service.CafeteriaService;
import pe.edu.ucv.cafetin.comun.dto.Pagina;
import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.cuentas.repository.EstudianteRepository;
import pe.edu.ucv.cafetin.inventario.modelo.Inventario;
import pe.edu.ucv.cafetin.mercadopago.config.PagosPropiedades;
import pe.edu.ucv.cafetin.mercadopago.service.CobroMercadoPagoService;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.CrearPedidoRequest;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.ItemPedidoRequest;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.LineaPedidoResponse;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.PagoResponse;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.PedidoResponse;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.PedidoResumenResponse;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.ReciboResponse;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPago;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido;
import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;
import pe.edu.ucv.cafetin.pedidos.modelo.Pago;
import pe.edu.ucv.cafetin.pedidos.modelo.Pedido;
import pe.edu.ucv.cafetin.pedidos.repository.PagoRepository;
import pe.edu.ucv.cafetin.pedidos.repository.PedidoRepository;
import pe.edu.ucv.cafetin.pedidos.repository.ReciboRepository;
import pe.edu.ucv.cafetin.pedidos.repository.StockPedidoRepository;
import pe.edu.ucv.cafetin.pedidos.service.PasarelaPago.ResultadoCobro;

/**
 * Pedidos del estudiante: crear (RF-05), pagar (RF-06, RF-07), cancelar, ver (RF-07, RF-08) e
 * historial (RF-09).
 * Crear no reserva stock: solo comprueba que alcance; se descuenta al pagar (trigger).
 */
@Service
public class PedidoService {

	private final PedidoRepository pedidos;
	private final PagoRepository pagos;
	private final ReciboRepository recibos;
	private final StockPedidoRepository stock;
	private final EstudianteRepository estudiantes;
	private final TransicionesPedido transiciones;
	private final PasarelaPago pasarela;
	private final CafeteriaRepository cafeterias;
	private final EmisorRecibos emisor;
	private final CobroMercadoPagoService mercadoPago;
	private final PagosPropiedades config;
	private final EntityManager em;

	public PedidoService(PedidoRepository pedidos, PagoRepository pagos, ReciboRepository recibos,
			StockPedidoRepository stock, EstudianteRepository estudiantes, TransicionesPedido transiciones,
			PasarelaPago pasarela, CafeteriaRepository cafeterias, EmisorRecibos emisor,
			CobroMercadoPagoService mercadoPago, PagosPropiedades config, EntityManager em) {
		this.pedidos = pedidos;
		this.transiciones = transiciones;
		this.pasarela = pasarela;
		this.pagos = pagos;
		this.recibos = recibos;
		this.stock = stock;
		this.estudiantes = estudiantes;
		this.cafeterias = cafeterias;
		this.emisor = emisor;
		this.mercadoPago = mercadoPago;
		this.config = config;
		this.em = em;
	}

	@Transactional
	public PedidoResponse crear(Integer idEstudiante, CrearPedidoRequest solicitud) {
		Integer idCafeteria = solicitud.idCafeteria();
		if (!cafeterias.existsByIdAndActivoTrue(idCafeteria)) {
			throw new ApiException.NoEncontrado("No existe la cafetería " + idCafeteria);
		}
		List<Integer> ids = solicitud.items().stream().map(ItemPedidoRequest::idProducto).toList();
		Map<Integer, Inventario> inventarios = stock.deProductosActivos(idCafeteria, ids).stream()
				.collect(Collectors.toMap(i -> i.getProducto().getId(), Function.identity()));

		for (ItemPedidoRequest item : solicitud.items()) {
			Inventario inv = inventarios.get(item.idProducto());
			if (inv == null) {
				throw new ApiException(CodigoError.STOCK_INSUFICIENTE,
						"El producto " + item.idProducto() + " no está disponible en este cafetín");
			}
			if (inv.getCantidadDisponible() < item.cantidad()) {
				throw new ApiException(CodigoError.STOCK_INSUFICIENTE, "Solo quedan %d unidades de %s"
						.formatted(inv.getCantidadDisponible(), inv.getProducto().getNombre()));
			}
		}

		String codigo = "PED-" + pedidos.siguienteNumeroCodigo();
		Pedido pedido = new Pedido(idCafeteria, codigo, estudiantes.getReferenceById(idEstudiante), solicitud.horaRecojo());
		pedido.setObservacion(solicitud.observacion());
		for (ItemPedidoRequest item : solicitud.items()) {
			pedido.agregarDetalle(inventarios.get(item.idProducto()).getProducto(), item.cantidad());
		}

		pedidos.save(pedido);
		// El total lo calcula trg_calcular_total_pedido: se lee de la base.
		em.flush();
		em.refresh(pedido);
		return aRespuesta(pedido);
	}

	/**
	 * RF-06, RF-07, RF-16. El pago se guarda PENDIENTE y luego pasa a PAGADO: ese UPDATE dispara
	 * {@code trg_descontar_stock}. Si el stock no alcanza, el CHECK de la base lanza
	 * {@code DataIntegrityViolationException} (409 STOCK_INSUFICIENTE) y se revierte todo.
	 * Un rechazo de la pasarela no revierte: el pago queda FALLIDO y el reintento actualiza esa fila,
	 * con la fecha del intento nuevo.
	 * <p>
	 * Con Mercado Pago ({@code cafetin.pagos.pasarela=mercadopago}) no se cobra aquí: el pago queda
	 * PENDIENTE con la {@code urlPago} de Checkout Pro y se confirma después ({@link CobroMercadoPagoService}).
	 * El método que manda la app es provisional: al confirmar se guarda el que informa Mercado Pago.
	 */
	@Transactional(noRollbackFor = PagoRechazado.class)
	public PedidoResponse pagar(Integer idEstudiante, Integer idPedido, MetodoPago metodo) {
		Pedido pedido = pedidos.paraActualizar(idPedido, idEstudiante)
				.orElseThrow(() -> new ApiException.NoEncontrado("No existe el pedido " + idPedido));
		if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
			throw new ApiException.ReglaNegocio("El pedido está " + pedido.getEstado() + " y no se puede pagar");
		}
		Pago pago = pagos.findByPedidoId(idPedido).orElse(null);
		if (pago != null && pago.getEstado() == EstadoPago.PAGADO) {
			throw new ApiException.ReglaNegocio("El pedido ya está pagado");
		}

		// Monto = total que calculó la base, nunca un valor del cliente.
		if (pago == null) {
			pago = new Pago(pedido, pedido.getTotal(), metodo);
		} else {
			pago.setMonto(pedido.getTotal());
			pago.setMetodoPago(metodo);
			pago.setEstado(EstadoPago.PENDIENTE);
			pago.setReferencia(null);
			pago.setUrlPago(null);
			pago.setMpPreferenciaId(null);
		}
		boolean reintento = pago.getId() != null;
		pago = pagos.saveAndFlush(pago);
		if (reintento) {
			pagos.fecharAhora(pago.getId());
			em.refresh(pago);
		}

		if (config.conMercadoPago()) {
			String nombre = cafeterias.findById(pedido.getIdCafeteria()).map(Cafeteria::getNombre).orElse("Cafetín");
			mercadoPago.iniciar(pedido, pago, nombre); // sin cuenta conectada: 409 y se revierte todo
			pagos.saveAndFlush(pago);
			return aRespuesta(pedido);
		}

		ResultadoCobro cobro = pasarela.cobrar(pedido.getCodigoPedido(), pago.getMonto(), metodo);
		if (!cobro.aprobado()) {
			pago.setEstado(EstadoPago.FALLIDO);
			pagos.saveAndFlush(pago);
			throw new PagoRechazado(cobro.motivo());
		}

		pago.setReferencia(cobro.referencia());
		pago.setEstado(EstadoPago.PAGADO);
		pagos.saveAndFlush(pago);
		emisor.emitir(pago, pedido);
		return aRespuesta(pedido);
	}

	/** Solo pedidos PENDIENTE sin pagar (decisión del 02/11/2026). */
	@Transactional
	public PedidoResponse cancelar(Integer idEstudiante, Integer idPedido) {
		Pedido pedido = pedidos.paraActualizar(idPedido, idEstudiante)
				.orElseThrow(() -> new ApiException.NoEncontrado("No existe el pedido " + idPedido));
		boolean pagado = pagos.findByPedidoId(idPedido).map(p -> p.getEstado() == EstadoPago.PAGADO).orElse(false);
		transiciones.aplicar(pedido, EstadoPedido.CANCELADO, pagado);
		return aRespuesta(pedido);
	}

	@Transactional(readOnly = true)
	public PedidoResponse obtener(Integer idEstudiante, Integer idPedido) {
		return aRespuesta(pedidoDelEstudiante(idEstudiante, idPedido));
	}

	@Transactional(readOnly = true)
	public Pagina<PedidoResumenResponse> historial(Integer idEstudiante, int pagina, int tamano) {
		Page<Pedido> page = pedidos.historial(idEstudiante, PageRequest.of(pagina, tamano));
		List<Integer> ids = page.getContent().stream().map(Pedido::getId).toList();
		if (ids.isEmpty()) {
			return Pagina.de(page, p -> null);
		}
		Map<Integer, Pago> pagosPorPedido = pagos.findByPedidoIdIn(ids).stream()
				.collect(Collectors.toMap(p -> p.getPedido().getId(), Function.identity()));
		Map<Integer, Long> unidades = pedidos.unidadesPorPedido(ids).stream()
				.collect(Collectors.toMap(f -> (Integer) f[0], f -> ((Number) f[1]).longValue()));
		Map<Integer, CafeteriaResponse> cafeteriaPorId = cafeterias
				.findAllById(page.getContent().stream().map(Pedido::getIdCafeteria).distinct().toList()).stream()
				.collect(Collectors.toMap(Cafeteria::getId, CafeteriaService::aRespuesta));

		return Pagina.de(page, p -> {
			Pago pago = pagosPorPedido.get(p.getId());
			return new PedidoResumenResponse(p.getId(), p.getCodigoPedido(), cafeteriaPorId.get(p.getIdCafeteria()),
					p.getEstado(),
					pago == null ? null : pago.getEstado(), p.getFechaHora(), p.getHoraRecojo(), p.getTotal(),
					unidades.getOrDefault(p.getId(), 0L).intValue());
		});
	}

	/** Un pedido ajeno responde igual que uno inexistente (404), para no revelar que existe. */
	Pedido pedidoDelEstudiante(Integer idEstudiante, Integer idPedido) {
		return pedidos.findByIdAndEstudianteId(idPedido, idEstudiante)
				.orElseThrow(() -> new ApiException.NoEncontrado("No existe el pedido " + idPedido));
	}

	/** La URL de Checkout Pro solo sirve mientras el pago no se confirmó. */
	private static boolean porPagar(Pago pago) {
		return pago.getEstado() == EstadoPago.PENDIENTE || pago.getEstado() == EstadoPago.FALLIDO;
	}

	PedidoResponse aRespuesta(Pedido p) {
		List<LineaPedidoResponse> items = p.getDetalles().stream()
				.map(d -> new LineaPedidoResponse(d.getProducto().getId(), d.getProducto().getNombre(),
						d.getCantidad().intValue(), d.getPrecioUnitario(), d.getSubtotal()))
				.toList();
		Pago pago = pagos.findByPedidoId(p.getId()).orElse(null);
		PagoResponse pagoResp = pago == null ? null
				: new PagoResponse(pago.getMonto(), pago.getMetodoPago(), pago.getEstado(), pago.getFecha(),
						pago.getReferencia(), porPagar(pago) ? pago.getUrlPago() : null);
		ReciboResponse reciboResp = pago == null ? null
				: recibos.findByPagoId(pago.getId())
						.map(r -> new ReciboResponse(r.getCodigoVerificacion(), r.getFechaEmision(), r.isVerificado()))
						.orElse(null);
		CafeteriaResponse cafeteria = cafeterias.findById(p.getIdCafeteria()).map(CafeteriaService::aRespuesta)
				.orElse(null);
		return new PedidoResponse(p.getId(), p.getCodigoPedido(), cafeteria, p.getEstado(), p.getFechaHora(), p.getHoraRecojo(),
				p.getTotal(), p.getObservacion(), items, pagoResp, reciboResp);
	}

}
