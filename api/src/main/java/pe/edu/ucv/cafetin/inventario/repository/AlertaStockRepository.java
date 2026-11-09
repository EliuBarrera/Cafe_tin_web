package pe.edu.ucv.cafetin.inventario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.edu.ucv.cafetin.inventario.modelo.AlertaStock;

/** Alertas de stock (RF-17) con su inventario, producto y proveedor: así se arma {@code AlertaResponse} en una consulta. */
public interface AlertaStockRepository extends JpaRepository<AlertaStock, Integer> {

	@Query("""
			select a from AlertaStock a join fetch a.inventario i join fetch i.producto p left join fetch p.proveedor
			where a.usuario.id = :idUsuario and a.atendida = :atendida
			order by a.fecha desc, a.id desc""")
	List<AlertaStock> delUsuario(@Param("idUsuario") Integer idUsuario, @Param("atendida") boolean atendida);

	@Query("""
			select a from AlertaStock a join fetch a.inventario i join fetch i.producto p left join fetch p.proveedor
			where a.id = :id and a.usuario.id = :idUsuario""")
	Optional<AlertaStock> deUsuario(@Param("id") Integer id, @Param("idUsuario") Integer idUsuario);

	/** RF-18. Al recargar, las alertas abiertas del producto se dan por atendidas (las de todos los dueños). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "UPDATE alerta_stock SET atendida = TRUE WHERE id_inventario = :idInventario AND NOT atendida",
			nativeQuery = true)
	int atenderDeInventario(@Param("idInventario") Integer idInventario);

}
