package pe.edu.ucv.cafetin.inventario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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

}
