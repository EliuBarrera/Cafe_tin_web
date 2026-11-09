package pe.edu.ucv.cafetin.inventario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ucv.cafetin.inventario.modelo.Inventario;

/** Inventario con su producto, categoría y proveedor: así se arma {@code ProductoResponse} en una consulta. */
public interface InventarioRepository extends JpaRepository<Inventario, Integer> {

	@Query("""
			select i from Inventario i join fetch i.producto p join fetch p.categoria left join fetch p.proveedor
			where :activo is null or p.activo = :activo
			order by p.nombre""")
	List<Inventario> conProducto(@Param("activo") Boolean activo);

	@Query("""
			select i from Inventario i join fetch i.producto p join fetch p.categoria left join fetch p.proveedor
			where p.id = :idProducto""")
	Optional<Inventario> deProducto(@Param("idProducto") Integer idProducto);

	/**
	 * RF-18. Suma en la base, no en Java: una venta que pague a la vez no pierde ni duplica unidades.
	 * La fecha la pone la base (hora de Lima).
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE inventario SET cantidad_disponible = cantidad_disponible + :cantidad,
			                      fecha_actualizacion = CURRENT_TIMESTAMP
			WHERE id_producto = :idProducto""", nativeQuery = true)
	int recargar(@Param("idProducto") Integer idProducto, @Param("cantidad") int cantidad);

}
