package pe.edu.ucv.cafetin.pedidos.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import pe.edu.ucv.cafetin.inventario.modelo.Inventario;

/**
 * Lectura del stock que necesita Pedidos. Vive aquí, y no en {@code inventario.repository}, para
 * no tocar el módulo de Inventario: solo lee, nunca escribe.
 */
public interface StockPedidoRepository extends Repository<Inventario, Integer> {

	/** Inventario con su producto, solo de productos activos (los dados de baja no se venden). */
	@Query("select i from Inventario i join fetch i.producto p where p.id in :ids and p.activo = true")
	List<Inventario> deProductosActivos(@Param("ids") Collection<Integer> idsProducto);

}
