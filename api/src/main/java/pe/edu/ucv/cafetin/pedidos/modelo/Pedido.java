package pe.edu.ucv.cafetin.pedidos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.Generated;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import pe.edu.ucv.cafetin.cuentas.modelo.Estudiante;
import pe.edu.ucv.cafetin.cuentas.modelo.Trabajador;
import pe.edu.ucv.cafetin.inventario.modelo.Producto;

/**
 * Pedido de un estudiante (RF-05 a RF-14).
 *
 * <p><b>{@code total} es de solo lectura:</b> lo recalcula {@code trg_calcular_total_pedido} cada vez
 * que cambia {@code detalle_pedido}. Después de guardar los detalles, haz {@code flush} y
 * {@code entityManager.refresh(pedido)} para leer el total correcto.
 *
 * <p>{@code estado} solo lo cambia la clase de transiciones de estado (CLAUDE.md, "Estados del pedido").
 */
@Entity
@Table(name = "pedido")
public class Pedido {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_pedido")
	private Integer id;

	/** Código visible, p. ej. PED-0427. */
	@Column(name = "codigo_pedido", nullable = false, length = 12, unique = true)
	private String codigoPedido;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_estudiante", nullable = false)
	private Estudiante estudiante;

	/** Nulo hasta que un trabajador lo confirma (RF-11). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_trabajador")
	private Trabajador trabajador;

	@Generated
	@Column(name = "fecha_hora", insertable = false, updatable = false)
	private LocalDateTime fechaHora;

	@Column(name = "hora_recojo", nullable = false)
	private LocalTime horaRecojo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "estado_pedido_enum")
	private EstadoPedido estado = EstadoPedido.PENDIENTE;

	/** Lo calcula el trigger: solo lectura. */
	@Column(nullable = false, precision = 10, scale = 2, insertable = false, updatable = false)
	private BigDecimal total;

	@Column(length = 250)
	private String observacion;

	@OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<DetallePedido> detalles = new ArrayList<>();

	protected Pedido() {
	}

	public Pedido(String codigoPedido, Estudiante estudiante, LocalTime horaRecojo) {
		this.codigoPedido = codigoPedido;
		this.estudiante = estudiante;
		this.horaRecojo = horaRecojo;
	}

	/** Agrega una línea copiando el precio actual del producto (precio histórico, RN-06). */
	public DetallePedido agregarDetalle(Producto producto, int cantidad) {
		DetallePedido detalle = new DetallePedido(this, producto, cantidad, producto.getPrecio());
		detalles.add(detalle);
		return detalle;
	}

	public Integer getId() { return id; }

	public String getCodigoPedido() { return codigoPedido; }

	public Estudiante getEstudiante() { return estudiante; }

	public Trabajador getTrabajador() { return trabajador; }
	public void setTrabajador(Trabajador trabajador) { this.trabajador = trabajador; }

	public LocalDateTime getFechaHora() { return fechaHora; }

	public LocalTime getHoraRecojo() { return horaRecojo; }
	public void setHoraRecojo(LocalTime horaRecojo) { this.horaRecojo = horaRecojo; }

	public EstadoPedido getEstado() { return estado; }
	/** Solo para la clase de transiciones de estado. */
	public void setEstado(EstadoPedido estado) { this.estado = estado; }

	public BigDecimal getTotal() { return total; }

	public String getObservacion() { return observacion; }
	public void setObservacion(String observacion) { this.observacion = observacion; }

	public List<DetallePedido> getDetalles() { return Collections.unmodifiableList(detalles); }

}
