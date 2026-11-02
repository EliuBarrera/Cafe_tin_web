package pe.edu.ucv.cafetin.pedidos.modelo;

import java.math.BigDecimal;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.edu.ucv.cafetin.inventario.modelo.Producto;

/**
 * Línea de un pedido (composición con {@link Pedido}, RF-05).
 * {@code subtotal} es una columna GENERATED de PostgreSQL: Hibernate la lee después de insertar
 * o actualizar y nunca la escribe. Se crea con {@link Pedido#agregarDetalle}.
 */
@Entity
@Table(name = "detalle_pedido")
public class DetallePedido {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_detalle")
	private Integer id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_pedido", nullable = false)
	private Pedido pedido;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_producto", nullable = false)
	private Producto producto;

	@Column(nullable = false)
	private Short cantidad;

	/** Precio al momento de la compra, no el actual del producto (RN-06). */
	@Column(name = "precio_unitario", nullable = false, precision = 8, scale = 2)
	private BigDecimal precioUnitario;

	@Generated(event = { EventType.INSERT, EventType.UPDATE })
	@Column(precision = 10, scale = 2, insertable = false, updatable = false)
	private BigDecimal subtotal;

	protected DetallePedido() {
	}

	DetallePedido(Pedido pedido, Producto producto, int cantidad, BigDecimal precioUnitario) {
		this.pedido = pedido;
		this.producto = producto;
		this.cantidad = (short) cantidad;
		this.precioUnitario = precioUnitario;
	}

	public Integer getId() { return id; }

	public Pedido getPedido() { return pedido; }

	public Producto getProducto() { return producto; }

	public Short getCantidad() { return cantidad; }
	public void setCantidad(Short cantidad) { this.cantidad = cantidad; }

	public BigDecimal getPrecioUnitario() { return precioUnitario; }

	public BigDecimal getSubtotal() { return subtotal; }

}
