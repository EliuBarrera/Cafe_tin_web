package pe.edu.ucv.cafetin.inventario.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.inventario.modelo.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Integer> {

	boolean existsByIdCafeteriaAndNombre(Integer idCafeteria, String nombre);

	boolean existsByIdCafeteriaAndNombreAndIdNot(Integer idCafeteria, String nombre, Integer id);

}
