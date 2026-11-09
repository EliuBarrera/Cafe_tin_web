package pe.edu.ucv.cafetin.pedidos.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ucv.cafetin.pedidos.modelo.Recibo;

public interface ReciboRepository extends JpaRepository<Recibo, Integer> {

	Optional<Recibo> findByPagoId(Integer idPago);

	boolean existsByCodigoVerificacion(String codigoVerificacion);

	Optional<Recibo> findByPagoPedidoId(Integer idPedido);

	/** Ids de los pedidos (entre los dados) cuyo recibo ya se verificó (RF-13). */
	@Query("select r.pago.pedido.id from Recibo r where r.verificado = true and r.pago.pedido.id in :ids")
	Set<Integer> pedidosVerificados(@Param("ids") Collection<Integer> ids);

	/** RF-13. La hora la pone la base (America/Lima), no Java. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "UPDATE recibo SET verificado = TRUE, fecha_verificacion = LOCALTIMESTAMP WHERE id_recibo = :id",
			nativeQuery = true)
	void marcarVerificado(@Param("id") Integer idRecibo);

}
