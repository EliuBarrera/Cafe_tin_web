package pe.edu.ucv.cafetin.inventario.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.inventario.modelo.Proveedor;

public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {

	List<Proveedor> findByIdCafeteriaOrderByNombreAsc(Integer idCafeteria);

	Optional<Proveedor> findByIdAndIdCafeteria(Integer id, Integer idCafeteria);

	boolean existsByIdCafeteriaAndNombre(Integer idCafeteria, String nombre);

	boolean existsByIdCafeteriaAndNombreAndIdNot(Integer idCafeteria, String nombre, Integer id);

}
