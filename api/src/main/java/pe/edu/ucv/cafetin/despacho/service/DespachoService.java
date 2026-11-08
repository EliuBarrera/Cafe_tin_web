package pe.edu.ucv.cafetin.despacho.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.modelo.Notificacion;
import pe.edu.ucv.cafetin.comun.modelo.TipoNotificacion;
import pe.edu.ucv.cafetin.cuentas.modelo.Usuario;
import pe.edu.ucv.cafetin.cuentas.repository.NotificacionRepository;
import pe.edu.ucv.cafetin.cuentas.repository.TrabajadorRepository;
import pe.edu.ucv.cafetin.despacho.dto.DespachoDto.LineaDespachoResponse;
import pe.edu.ucv.cafetin.despacho.dto.DespachoDto.PedidoDespachoResponse;
import pe.edu.ucv.cafetin.despacho.modelo.PedidoEnCurso;
import pe.edu.ucv.cafetin.despacho.repository.PedidoEnCursoRepository;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPago;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido;
import pe.edu.ucv.cafetin.pedidos.modelo.Pago;
import pe.edu.ucv.cafetin.pedidos.modelo.Pedido;
import pe.edu.ucv.cafetin.pedidos.modelo.Recibo;
import pe.edu.ucv.cafetin.pedidos.repository.PagoRepository;
import pe.edu.ucv.cafetin.pedidos.repository.PedidoRepository;
import pe.edu.ucv.cafetin.pedidos.repository.ReciboRepository;
import pe.edu.ucv.cafetin.pedidos.service.TransicionesPedido;

/**
 * Tablero de despacho (RF-10 a RF-14). Los cambios de estado los decide {@link TransicionesPedido};
 * aquí solo se agregan las reglas propias del despacho (asignar trabajador, verificar el recibo antes
 * de entregar) y la notificación al estudiante. Nunca devuelve el código de verificación (RF-13).
 */
@Service
public class DespachoService {

	private final PedidoEnCursoRepository enCurso;
	private final PedidoRepository pedidos;
	private final PagoRepository pagos;
	private final ReciboRepository recibos;
	private final TrabajadorRepository trabajadores;
	private final NotificacionRepository notificaciones;
	private final TransicionesPedido transiciones;

	public DespachoService(PedidoEnCursoRepository enCurso, PedidoRepository pedidos, PagoRepository pagos,
			ReciboRepository recibos, TrabajadorRepository trabajadores, NotificacionRepository notificaciones,
			TransicionesPedido transiciones) {
		this.enCurso = enCurso;
		this.pedidos = pedidos;
		this.pagos = pagos;
		this.recibos = recibos;
		this.trabajadores = trabajadores;
		this.notificaciones = notificaciones;
		this.transiciones = transiciones;
	}

	/** RF-10. Solo pagados, por hora de recojo. Un estado que no está en curso devuelve la lista vacía. */
	@Transactional(readOnly = true)
	public List<PedidoDespachoResponse> listar(EstadoPedido estado) {
		List<PedidoEnCurso> filas = estado == null
				? enCurso.findByEstadoPagoOrderByHoraRecojoAscIdPedidoAsc(EstadoPago.PAGADO)
				: enCurso.findByEstadoPagoAndEstadoOrderByHoraRecojoAscIdPedidoAsc(EstadoPago.PAGADO, estado);
		return aRespuestas(filas.stream().map(PedidoEnCurso::getIdPedido).toList());
	}

	/** RF-11. Asigna al trabajador autenticado; si confirma el dueño, queda sin trabajador (la FK es a trabajador). */
	@Transactional
	public PedidoDespachoResponse confirmar(Integer idUsuario, Integer idPedido) {
		Pedido pedido = bloquear(idPedido);
		transiciones.aplicar(pedido, EstadoPedido.CONFIRMADO, pagado(idPedido));
		trabajadores.findById(idUsuario).ifPresent(pedido::setTrabajador);
		notificar(pedido, TipoNotificacion.PEDIDO_CONFIRMADO, "Tu pedido %s fue confirmado y pronto lo prepararemos.");
		return aRespuesta(pedido);
	}

	/** RF-11. CONFIRMADO → EN_PREPARACION (sin aviso al estudiante: no hay tipo de notificación para ello). */
	@Transactional
	public PedidoDespachoResponse preparar(Integer idPedido) {
		Pedido pedido = bloquear(idPedido);
		transiciones.aplicar(pedido, EstadoPedido.EN_PREPARACION, pagado(idPedido));
		return aRespuesta(pedido);
	}

	/** RF-12. */
	@Transactional
	public PedidoDespachoResponse listo(Integer idPedido) {
		Pedido pedido = bloquear(idPedido);
		transiciones.aplicar(pedido, EstadoPedido.LISTO, pagado(idPedido));
		notificar(pedido, TipoNotificacion.PEDIDO_LISTO, "Tu pedido %s está listo. Acércate a recogerlo con tu recibo.");
		return aRespuesta(pedido);
	}

	/** RF-13. El pedido debe estar LISTO y el código coincidir con el del recibo; si ya estaba verificado, no cambia nada. */
	@Transactional
	public PedidoDespachoResponse verificar(Integer idPedido, String codigo) {
		Pedido pedido = bloquear(idPedido);
		if (pedido.getEstado() != EstadoPedido.LISTO) {
			throw new ApiException.ReglaNegocio("El recibo se verifica cuando el pedido está LISTO (ahora está "
					+ pedido.getEstado() + ")");
		}
		Recibo recibo = recibos.findByPagoPedidoId(idPedido)
				.orElseThrow(() -> new ApiException.ReglaNegocio("El pedido no tiene recibo"));
		if (!recibo.getCodigoVerificacion().equals(codigo.trim())) {
			throw new ApiException.ReglaNegocio("El código no coincide con el recibo del pedido");
		}
		if (!recibo.isVerificado()) {
			recibos.marcarVerificado(recibo.getId());
		}
		return aRespuesta(idPedido);
	}

	/** RF-14. LISTO → ENTREGADO; exige el recibo verificado. */
	@Transactional
	public PedidoDespachoResponse entregar(Integer idPedido) {
		Pedido pedido = bloquear(idPedido);
		transiciones.validar(pedido.getEstado(), EstadoPedido.ENTREGADO, pagado(idPedido));
		boolean verificado = recibos.findByPagoPedidoId(idPedido).map(Recibo::isVerificado).orElse(false);
		if (!verificado) {
			throw new ApiException.ReglaNegocio("Verifica el código del recibo antes de entregar el pedido");
		}
		transiciones.aplicar(pedido, EstadoPedido.ENTREGADO, true);
		notificar(pedido, TipoNotificacion.PEDIDO_ENTREGADO, "Entregamos tu pedido %s. ¡Buen provecho!");
		return aRespuesta(pedido);
	}

	// ------------------------------------------------------------------ ayudas

	private Pedido bloquear(Integer idPedido) {
		return pedidos.paraDespacho(idPedido)
				.orElseThrow(() -> new ApiException.NoEncontrado("No existe el pedido " + idPedido));
	}

	private boolean pagado(Integer idPedido) {
		return pagos.findByPedidoId(idPedido).map(p -> p.getEstado() == EstadoPago.PAGADO).orElse(false);
	}

	/** Fila en {@code notificacion}; el push por FCM llega en la Fase 5. */
	private void notificar(Pedido pedido, TipoNotificacion tipo, String plantilla) {
		notificaciones.save(new Notificacion(pedido.getEstudiante(), pedido, tipo,
				plantilla.formatted(pedido.getCodigoPedido())));
	}

	private PedidoDespachoResponse aRespuesta(Pedido pedido) {
		return aRespuesta(pedido.getId());
	}

	private PedidoDespachoResponse aRespuesta(Integer idPedido) {
		return aRespuestas(List.of(idPedido)).getFirst();
	}

	/** Arma las respuestas en el orden de {@code ids} con tres consultas, sin importar cuántos pedidos sean. */
	private List<PedidoDespachoResponse> aRespuestas(List<Integer> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<Integer, Pedido> porId = pedidos.conDetalle(ids).stream()
				.collect(Collectors.toMap(Pedido::getId, Function.identity()));
		Map<Integer, Pago> pagoPorPedido = pagos.findByPedidoIdIn(ids).stream()
				.collect(Collectors.toMap(p -> p.getPedido().getId(), Function.identity()));
		Set<Integer> verificados = recibos.pedidosVerificados(ids);

		return ids.stream().map(id -> {
			Pedido p = porId.get(id);
			Pago pago = pagoPorPedido.get(id);
			List<LineaDespachoResponse> items = p.getDetalles().stream()
					.map(d -> new LineaDespachoResponse(d.getProducto().getNombre(), d.getCantidad().intValue()))
					.toList();
			return new PedidoDespachoResponse(p.getId(), p.getCodigoPedido(), p.getEstado(), p.getHoraRecojo(),
					p.getTotal(), nombre(p.getEstudiante()), pago == null ? null : pago.getMetodoPago(), items,
					p.getTrabajador() == null ? null : nombre(p.getTrabajador()), verificados.contains(id));
		}).toList();
	}

	private static String nombre(Usuario usuario) {
		return usuario.getNombre() + " " + usuario.getApellidos();
	}

}
