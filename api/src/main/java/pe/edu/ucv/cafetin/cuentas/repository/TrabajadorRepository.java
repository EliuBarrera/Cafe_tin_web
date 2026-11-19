package pe.edu.ucv.cafetin.cuentas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.cuentas.modelo.Trabajador;

public interface TrabajadorRepository extends JpaRepository<Trabajador, Integer> {

	boolean existsByCodigoEmpleado(String codigoEmpleado);

	List<Trabajador> findByIdCafeteria(Integer idCafeteria, Sort orden);

	Optional<Trabajador> findByIdAndIdCafeteria(Integer id, Integer idCafeteria);

}
