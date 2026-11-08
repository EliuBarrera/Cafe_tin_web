package pe.edu.ucv.cafetin.catalogo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import pe.edu.ucv.cafetin.catalogo.modelo.CatalogoDisponible;

/** Lectura de la vista v_catalogo_disponible (RF-04). */
public interface CatalogoRepository extends Repository<CatalogoDisponible, Integer> {

	@Query("select c from CatalogoDisponible c order by c.categoria, c.nombre")
	List<CatalogoDisponible> listar();

	/** La vista trae el nombre de la categoría, no su id: se filtra por los productos de la categoría. */
	@Query("""
			select c from CatalogoDisponible c
			where c.idProducto in (select p.id from Producto p where p.categoria.id = :idCategoria)
			order by c.nombre""")
	List<CatalogoDisponible> listarPorCategoria(@Param("idCategoria") Integer idCategoria);

}
