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
import pe.edu.ucv.cafetin.comun.dto.Pagina;
import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.cuentas.repository.EstudianteRepository;
import pe.edu.ucv.cafetin.inventario.modelo.Inventario;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.CrearPedidoRequest;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.ItemPedidoRequest;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.LineaPedidoResponse;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.PagoResponse;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.PedidoResponse;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.PedidoResumenResponse;
import pe.edu.ucv.cafetin.pedidos.dto.PedidosDto.ReciboResponse;
import pe.edu.ucv.cafetin.pedidos.modelo.Pago;
import pe.edu.ucv.cafetin.pedidos.modelo.Pedido;
import pe.edu.ucv.cafetin.pedidos.repository.PagoRepository;
import pe.edu.ucv.cafetin.pedidos.repository.PedidoRepository;
import pe.edu.ucv.cafetin.pedidos.repository.ReciboRepository;
import pe.edu.ucv.cafetin.pedidos.repository.StockPedidoRepository;

/**
 * Pedidos del estudiante: crear (RF-05), ver (RF-07, RF-08) e historial (RF-09).
 * Crear no reserva stock: solo comprueba que alcance; se descuenta al pagar (trigger).
 */
@Service
public class PedidoService {

	private final PedidoRepository pedidos;
	private final PagoRepository pagos;
	private final ReciboRepository recibos;
	private final StockPedidoRepository stock;
	private final EstudianteRepository estudiantes;
	private final EntityManager em;

	public PedidoService(PedidoRepository pedidos, PagoRepository pagos, ReciboRepository recibos,
			StockPedidoRepository stock, EstudianteRepository estudiantes, EntityManager em) {
		this.pedidos = pedidos;
		this.pagos = pagos;
		this.recibos = recibos;
		this.stock = stock;
		this.estudiantes = estudiantes;
		this.em = em;
	}

	@Transactional
	public PedidoResponse crear(Integer idEstudiante, CrearPedidoRequest solicitud) {
		List<Integer> ids = solicitud.items().stream().map(ItemPedidoRequest::idProducto).toList();
		Map<Integer, Inventario> inventarios = stock.deProductosActivos(ids).stream()
				.collect(Collectors.toMap(i -> i.getProducto().getId(), Function.identity()));

		for (ItemPedidoRequest item : solicitud.items()) {
			Inventario inv = inventarios.get(item.idProducto());
			if (inv == null) {
				throw new ApiException(CodigoError.STOCK_INSUFICIENTE,
						"El producto " + item.idProducto() + " no está disponible");
			}
			if (inv.getCantidadDisponible() < item.cantidad()) {
				throw new ApiException(CodigoError.STOCK_INSUFICIENTE, "Solo quedan %d unidades de %s"
						.formatted(inv.getCantidadDisponible(), inv.getProducto().getNombre()));
			}
		}

		String codigo = "PED-" + pedidos.siguienteNumeroCodigo();
		Pedido pedido = new Pedido(codigo, estudiantes.getReferenceById(idEstudiante), solicitud.horaRecojo());
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

		return Pagina.de(page, p -> {
			Pago pago = pagosPorPedido.get(p.getId());
			return new PedidoResumenResponse(p.getId(), p.getCodigoPedido(), p.getEstado(),
					pago == null ? null : pago.getEstado(), p.getFechaHora(), p.getHoraRecojo(), p.getTotal(),
					unidades.getOrDefault(p.getId(), 0L).intValue());
		});
	}

	/** Un pedido ajeno responde igual que uno inexistente (404), para no revelar que existe. */
	Pedido pedidoDelEstudiante(Integer idEstudiante, Integer idPedido) {
		return pedidos.findByIdAndEstudianteId(idPedido, idEstudiante)
				.orElseThrow(() -> new ApiException.NoEncontrado("No existe el pedido " + idPedido));
	}

	PedidoResponse aRespuesta(Pedido p) {
		List<LineaPedidoResponse> items = p.getDetalles().stream()
				.map(d -> new LineaPedidoResponse(d.getProducto().getId(), d.getProducto().getNombre(),
						d.getCantidad().intValue(), d.getPrecioUnitario(), d.getSubtotal()))
				.toList();
		Pago pago = pagos.findByPedidoId(p.getId()).orElse(null);
		PagoResponse pagoResp = pago == null ? null
				: new PagoResponse(pago.getMonto(), pago.getMetodoPago(), pago.getEstado(), pago.getFecha(),
						pago.getReferencia());
		ReciboResponse reciboResp = pago == null ? null
				: recibos.findByPagoId(pago.getId())
						.map(r -> new ReciboResponse(r.getCodigoVerificacion(), r.getFechaEmision(), r.isVerificado()))
						.orElse(null);
		return new PedidoResponse(p.getId(), p.getCodigoPedido(), p.getEstado(), p.getFechaHora(), p.getHoraRecojo(),
				p.getTotal(), p.getObservacion(), items, pagoResp, reciboResp);
	}

}
