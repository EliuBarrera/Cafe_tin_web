package pe.edu.ucv.cafetin.cuentas.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.cuentas.modelo.Estudiante;

public interface EstudianteRepository extends JpaRepository<Estudiante, Integer> {

	boolean existsByCodigo(String codigo);

}
