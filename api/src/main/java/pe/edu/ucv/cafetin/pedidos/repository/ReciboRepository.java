package pe.edu.ucv.cafetin.pedidos.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.pedidos.modelo.Recibo;

public interface ReciboRepository extends JpaRepository<Recibo, Integer> {

	Optional<Recibo> findByPagoId(Integer idPago);

	boolean existsByCodigoVerificacion(String codigoVerificacion);

}
