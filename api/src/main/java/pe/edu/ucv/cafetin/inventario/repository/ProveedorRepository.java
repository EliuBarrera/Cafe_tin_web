package pe.edu.ucv.cafetin.inventario.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.inventario.modelo.Proveedor;

public interface ProveedorRepository extends JpaRepository<Proveedor, Integer> {

	List<Proveedor> findAllByOrderByNombreAsc();

	boolean existsByNombre(String nombre);

	boolean existsByNombreAndIdNot(String nombre, Integer id);

}
