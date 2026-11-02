package pe.edu.ucv.cafetin.catalogo.modelo;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista {@code v_catalogo_disponible} (RF-04), solo lectura: productos activos con su stock.
 * {@code estadoStock} vale DISPONIBLE, POCAS UNIDADES o AGOTADO.
 */
@Entity
@Immutable
@Table(name = "v_catalogo_disponible")
public class CatalogoDisponible {

	@Id
	@Column(name = "id_producto")
	private Integer idProducto;

	private String nombre;

	private String descripcion;

	private BigDecimal precio;

	private String categoria;

	@Column(name = "cantidad_disponible")
	private Integer cantidadDisponible;

	@Column(name = "estado_stock")
	private String estadoStock;

	protected CatalogoDisponible() {
	}

	public Integer getIdProducto() { return idProducto; }
	public String getNombre() { return nombre; }
	public String getDescripcion() { return descripcion; }
	public BigDecimal getPrecio() { return precio; }
	public String getCategoria() { return categoria; }
	public Integer getCantidadDisponible() { return cantidadDisponible; }
	public String getEstadoStock() { return estadoStock; }

}
