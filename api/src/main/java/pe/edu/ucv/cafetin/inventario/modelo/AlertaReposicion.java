package pe.edu.ucv.cafetin.inventario.modelo;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Vista {@code v_alertas_reposicion} (RF-17), solo lectura: productos en o bajo su stock mínimo,
 * con el proveedor y su teléfono para el tablero del dueño.
 */
@Entity
@Immutable
@Table(name = "v_alertas_reposicion")
public class AlertaReposicion {

	@Id
	@Column(name = "id_producto")
	private Integer idProducto;

	private String producto;

	@Column(name = "cantidad_disponible")
	private Integer cantidadDisponible;

	@Column(name = "stock_minimo")
	private Integer stockMinimo;

	/** Nulo si el producto no tiene proveedor. */
	private String proveedor;

	@Column(name = "telefono_proveedor")
	private String telefonoProveedor;

	@Column(name = "fecha_actualizacion")
	private LocalDateTime fechaActualizacion;

	protected AlertaReposicion() {
	}

	public Integer getIdProducto() { return idProducto; }
	public String getProducto() { return producto; }
	public Integer getCantidadDisponible() { return cantidadDisponible; }
	public Integer getStockMinimo() { return stockMinimo; }
	public String getProveedor() { return proveedor; }
	public String getTelefonoProveedor() { return telefonoProveedor; }
	public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }

}
