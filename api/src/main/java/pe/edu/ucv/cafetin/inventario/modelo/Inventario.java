package pe.edu.ucv.cafetin.inventario.modelo;

import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Stock de un producto (composición 1:1, RF-16 a RF-18).
 *
 * <p><b>Ojo:</b> {@code trg_descontar_stock} cambia {@code cantidad_disponible} dentro de PostgreSQL.
 * Después de confirmar un pago no confíes en una instancia ya cargada: vuelve a consultarla
 * (o {@code entityManager.refresh}). {@code fecha_actualizacion} la escribe la base; para una
 * recarga (RF-18) actualízala con {@code CURRENT_TIMESTAMP} en la consulta, no con la hora de Java.
 */
@Entity
@Table(name = "inventario")
public class Inventario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_inventario")
	private Integer id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_producto", nullable = false, unique = true)
	private Producto producto;

	/** Nunca negativa (CHECK en la base). */
	@Column(name = "cantidad_disponible", nullable = false)
	private int cantidadDisponible;

	@Column(name = "stock_minimo", nullable = false)
	private int stockMinimo = 5;

	@Generated(event = { EventType.INSERT, EventType.UPDATE })
	@Column(name = "fecha_actualizacion", insertable = false, updatable = false)
	private LocalDateTime fechaActualizacion;

	protected Inventario() {
	}

	public Inventario(Producto producto, int cantidadInicial, int stockMinimo) {
		this.producto = producto;
		this.cantidadDisponible = cantidadInicial;
		this.stockMinimo = stockMinimo;
	}

	public Integer getId() { return id; }

	public Producto getProducto() { return producto; }

	public int getCantidadDisponible() { return cantidadDisponible; }
	public void setCantidadDisponible(int cantidadDisponible) { this.cantidadDisponible = cantidadDisponible; }

	public int getStockMinimo() { return stockMinimo; }
	public void setStockMinimo(int stockMinimo) { this.stockMinimo = stockMinimo; }

	public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }

}
