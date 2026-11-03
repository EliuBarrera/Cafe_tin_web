package pe.edu.ucv.cafetin.cuentas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.comun.modelo.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

	@EntityGraph(attributePaths = "pedido")
	List<Notificacion> findByUsuarioIdOrderByFechaDescIdDesc(Integer idUsuario);

	@EntityGraph(attributePaths = "pedido")
	List<Notificacion> findByUsuarioIdAndLeidaFalseOrderByFechaDescIdDesc(Integer idUsuario);

	Optional<Notificacion> findByIdAndUsuarioId(Integer id, Integer idUsuario);

}
