package pe.edu.ucv.cafetin.cafeterias.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.cafeterias.modelo.Cafeteria;

public interface CafeteriaRepository extends JpaRepository<Cafeteria, Integer> {

	List<Cafeteria> findByActivoTrueOrderByNombreAsc();

	boolean existsByIdAndActivoTrue(Integer id);

}
