package pe.edu.ucv.cafetin.pedidos.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Pago de un pedido (1:1, RF-06, RF-20).
 *
 * <ul>
 * <li>Se crea {@link EstadoPago#PENDIENTE} y luego se actualiza a {@link EstadoPago#PAGADO}: solo ese
 * UPDATE dispara {@code trg_descontar_stock}. Crearlo ya PAGADO no descuenta stock.</li>
 * <li>{@code monto} = {@code pedido.total} refrescado de la base, nunca un valor del cliente.</li>
 * <li>Hay un solo pago por pedido (UNIQUE): un reintento tras FALLIDO actualiza esta misma fila.</li>
 * <li>No se guardan datos de tarjeta, solo la {@code referencia} de la pasarela (RNF-04).</li>
 * </ul>
 */
@Entity
@Table(name = "pago")
public class Pago {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_pago")
	private Integer id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_pedido", nullable = false, unique = true)
	private Pedido pedido;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Enumerated(EnumType.STRING)
	@Column(name = "metodo_pago", nullable = false, columnDefinition = "metodo_pago_enum")
	private MetodoPago metodoPago;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "estado_pago_enum")
	private EstadoPago estado = EstadoPago.PENDIENTE;

	@Generated
	@Column(insertable = false, updatable = false)
	private LocalDateTime fecha;

	/** Código de operación de la pasarela. */
	@Column(length = 60)
	private String referencia;

	protected Pago() {
	}

	public Pago(Pedido pedido, BigDecimal monto, MetodoPago metodoPago) {
		this.pedido = pedido;
		this.monto = monto;
		this.metodoPago = metodoPago;
	}

	public Integer getId() { return id; }

	public Pedido getPedido() { return pedido; }

	public BigDecimal getMonto() { return monto; }
	public void setMonto(BigDecimal monto) { this.monto = monto; }

	public MetodoPago getMetodoPago() { return metodoPago; }
	public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }

	public EstadoPago getEstado() { return estado; }
	public void setEstado(EstadoPago estado) { this.estado = estado; }

	public LocalDateTime getFecha() { return fecha; }

	public String getReferencia() { return referencia; }
	public void setReferencia(String referencia) { this.referencia = referencia; }

}
