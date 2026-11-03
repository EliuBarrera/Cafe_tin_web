package pe.edu.ucv.cafetin.cuentas.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.cuentas.modelo.Trabajador;

public interface TrabajadorRepository extends JpaRepository<Trabajador, Integer> {

	boolean existsByCodigoEmpleado(String codigoEmpleado);

}
