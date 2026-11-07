package pe.edu.ucv.cafetin.pedidos.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.pedidos.modelo.Pago;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

	Optional<Pago> findByPedidoId(Integer idPedido);

	List<Pago> findByPedidoIdIn(Collection<Integer> idsPedido);

}
