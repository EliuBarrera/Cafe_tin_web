package pe.edu.ucv.cafetin.pedidos.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import pe.edu.ucv.cafetin.pedidos.modelo.Pago;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

	Optional<Pago> findByPedidoId(Integer idPedido);

	List<Pago> findByPedidoIdIn(Collection<Integer> idsPedido);

	/**
	 * Reintento tras FALLIDO: la fila es la misma, pero la fecha pasa a la del intento nuevo. Si no, una
	 * venta reintentada otro día cuenta en el día del rechazo (RF-20, RF-21). La hora la pone la base (Lima).
	 */
	@Modifying(flushAutomatically = true)
	@Query(value = "UPDATE pago SET fecha = CURRENT_TIMESTAMP WHERE id_pago = :idPago", nativeQuery = true)
	void fecharAhora(@Param("idPago") Integer idPago);

}
