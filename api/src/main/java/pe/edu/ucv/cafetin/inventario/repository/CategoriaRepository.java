package pe.edu.ucv.cafetin.inventario.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.inventario.modelo.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

	List<Categoria> findByActivoTrueOrderByNombreAsc();

	boolean existsByNombre(String nombre);

	boolean existsByNombreAndIdNot(String nombre, Integer id);

}
