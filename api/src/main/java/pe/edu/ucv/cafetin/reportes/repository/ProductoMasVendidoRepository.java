package pe.edu.ucv.cafetin.reportes.repository;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import pe.edu.ucv.cafetin.reportes.modelo.ProductoMasVendido;

/** Vista {@code v_productos_mas_vendidos} (RF-22). Empates: más ingreso primero y luego por id. */
public interface ProductoMasVendidoRepository extends JpaRepository<ProductoMasVendido, Integer> {

	List<ProductoMasVendido> findAllByOrderByUnidadesVendidasDescIngresoGeneradoDescIdProductoAsc(Limit limite);

}
