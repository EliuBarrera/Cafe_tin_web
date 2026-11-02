package pe.edu.ucv.cafetin.inventario.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Producto (RF-15, RF-19). Su stock vive en {@link Inventario} (1:1).
 * {@code activo} es solo la baja manual del dueño: un producto agotado sigue activo y el
 * catálogo lo muestra como AGOTADO (decisión del 02/11/2026).
 */
@Entity
@Table(name = "producto")
public class Producto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_producto")
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_categoria", nullable = false)
	private Categoria categoria;

	/** Opcional: productos de preparación propia no tienen proveedor. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_proveedor")
	private Proveedor proveedor;

	@Column(nullable = false, length = 100, unique = true)
	private String nombre;

	@Column(length = 250)
	private String descripcion;

	@Column(nullable = false, precision = 8, scale = 2)
	private BigDecimal precio;

	@Column(name = "imagen_url", length = 300)
	private String imagenUrl;

	@Column(nullable = false)
	private boolean activo = true;

	@Generated
	@Column(name = "fecha_registro", insertable = false, updatable = false)
	private LocalDateTime fechaRegistro;

	protected Producto() {
	}

	public Producto(Categoria categoria, String nombre, BigDecimal precio) {
		this.categoria = categoria;
		this.nombre = nombre;
		this.precio = precio;
	}

	public Integer getId() { return id; }

	public Categoria getCategoria() { return categoria; }
	public void setCategoria(Categoria categoria) { this.categoria = categoria; }

	public Proveedor getProveedor() { return proveedor; }
	public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }

	public String getNombre() { return nombre; }
	public void setNombre(String nombre) { this.nombre = nombre; }

	public String getDescripcion() { return descripcion; }
	public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

	public BigDecimal getPrecio() { return precio; }
	public void setPrecio(BigDecimal precio) { this.precio = precio; }

	public String getImagenUrl() { return imagenUrl; }
	public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

	public boolean isActivo() { return activo; }
	public void setActivo(boolean activo) { this.activo = activo; }

	public LocalDateTime getFechaRegistro() { return fechaRegistro; }

}
