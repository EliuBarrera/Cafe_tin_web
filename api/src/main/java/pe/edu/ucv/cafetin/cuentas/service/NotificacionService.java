package pe.edu.ucv.cafetin.cuentas.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ucv.cafetin.comun.error.ApiException;
import pe.edu.ucv.cafetin.comun.modelo.Notificacion;
import pe.edu.ucv.cafetin.cuentas.dto.CuentasDto.NotificacionResponse;
import pe.edu.ucv.cafetin.cuentas.repository.NotificacionRepository;
import pe.edu.ucv.cafetin.cuentas.repository.UsuarioRepository;

/** Bandeja de notificaciones y token del dispositivo (RF-08). El envío por FCM llega en la Fase 5. */
@Service
public class NotificacionService {

	private final NotificacionRepository notificaciones;
	private final UsuarioRepository usuarios;

	public NotificacionService(NotificacionRepository notificaciones, UsuarioRepository usuarios) {
		this.notificaciones = notificaciones;
		this.usuarios = usuarios;
	}

	/**
	 * Un teléfono pertenece a quien inició sesión último: se quita el token a cualquier otra cuenta
	 * para que ese teléfono no siga recibiendo los avisos de la anterior.
	 */
	@Transactional
	public void registrarFcmToken(Integer idUsuario, String token) {
		usuarios.quitarFcmTokenDeOtros(token, idUsuario);
		usuarios.guardarFcmToken(idUsuario, token);
	}

	@Transactional(readOnly = true)
	public List<NotificacionResponse> listar(Integer idUsuario, boolean soloNoLeidas) {
		List<Notificacion> encontradas = soloNoLeidas
				? notificaciones.findByUsuarioIdAndLeidaFalseOrderByFechaDescIdDesc(idUsuario)
				: notificaciones.findByUsuarioIdOrderByFechaDescIdDesc(idUsuario);
		return encontradas.stream().map(NotificacionService::aRespuesta).toList();
	}

	@Transactional
	public void marcarLeida(Integer id, Integer idUsuario) {
		notificaciones.findByIdAndUsuarioId(id, idUsuario)
				.orElseThrow(() -> new ApiException.NoEncontrado("La notificación no existe"))
				.setLeida(true);
	}

	private static NotificacionResponse aRespuesta(Notificacion n) {
		Integer idPedido = n.getPedido() == null ? null : n.getPedido().getId();
		return new NotificacionResponse(n.getId(), n.getTipo().name(), n.getMensaje(), idPedido, n.getFecha(), n.isLeida());
	}

}
