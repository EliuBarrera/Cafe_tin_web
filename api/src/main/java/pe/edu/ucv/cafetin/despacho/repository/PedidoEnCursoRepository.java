package pe.edu.ucv.cafetin.despacho.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.despacho.modelo.PedidoEnCurso;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPago;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido;

/** Vista {@code v_pedidos_en_curso} (solo lectura). Incluye pedidos sin pagar: filtra por {@code estadoPago}. */
public interface PedidoEnCursoRepository extends JpaRepository<PedidoEnCurso, Integer> {

	List<PedidoEnCurso> findByEstadoPagoOrderByHoraRecojoAscIdPedidoAsc(EstadoPago estadoPago);

	List<PedidoEnCurso> findByEstadoPagoAndEstadoOrderByHoraRecojoAscIdPedidoAsc(EstadoPago estadoPago,
			EstadoPedido estado);

}
