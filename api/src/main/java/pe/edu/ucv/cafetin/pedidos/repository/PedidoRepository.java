package pe.edu.ucv.cafetin.pedidos.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import pe.edu.ucv.cafetin.pedidos.modelo.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Integer> {

	/** Número para el código visible PED-&lt;n&gt; (secuencia de V10). */
	@Query(value = "SELECT nextval('seq_codigo_pedido')", nativeQuery = true)
	long siguienteNumeroCodigo();

	/** Pedido solo si pertenece al estudiante: uno ajeno se trata como inexistente (404). */
	Optional<Pedido> findByIdAndEstudianteId(Integer id, Integer idEstudiante);

	/**
	 * Igual que {@link #findByIdAndEstudianteId}, pero bloquea la fila (SELECT … FOR UPDATE) para que
	 * dos operaciones sobre el mismo pedido (p. ej. pagar y cancelar a la vez) se ejecuten una tras otra.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Pedido p where p.id = :id and p.estudiante.id = :idEstudiante")
	Optional<Pedido> paraActualizar(@Param("id") Integer id, @Param("idEstudiante") Integer idEstudiante);

	@Query(value = "select p from Pedido p where p.estudiante.id = :idEstudiante order by p.fechaHora desc, p.id desc",
			countQuery = "select count(p) from Pedido p where p.estudiante.id = :idEstudiante")
	Page<Pedido> historial(@Param("idEstudiante") Integer idEstudiante, Pageable pagina);

	/** Unidades por pedido: filas [idPedido, suma de cantidades]. */
	@Query("select d.pedido.id, sum(d.cantidad) from DetallePedido d where d.pedido.id in :ids group by d.pedido.id")
	List<Object[]> unidadesPorPedido(@Param("ids") Collection<Integer> ids);

}
