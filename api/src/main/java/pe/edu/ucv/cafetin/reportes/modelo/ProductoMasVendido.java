package pe.edu.ucv.cafetin.reportes.modelo;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista {@code v_productos_mas_vendidos} (RF-22), solo lectura: unidades e ingreso por producto
 * de todos los pagos PAGADO. La vista no tiene fecha: para filtrar por periodo hace falta otra consulta.
 */
@Entity
@Immutable
@Table(name = "v_productos_mas_vendidos")
public class ProductoMasVendido {

	@Id
	@Column(name = "id_producto")
	private Integer idProducto;

	private String nombre;

	private String categoria;

	@Column(name = "unidades_vendidas")
	private Long unidadesVendidas;

	@Column(name = "ingreso_generado")
	private BigDecimal ingresoGenerado;

	protected ProductoMasVendido() {
	}

	public Integer getIdProducto() { return idProducto; }
	public String getNombre() { return nombre; }
	public String getCategoria() { return categoria; }
	public Long getUnidadesVendidas() { return unidadesVendidas; }
	public BigDecimal getIngresoGenerado() { return ingresoGenerado; }

}
