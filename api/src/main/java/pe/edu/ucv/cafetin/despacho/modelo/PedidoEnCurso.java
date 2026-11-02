package pe.edu.ucv.cafetin.despacho.modelo;

import java.math.BigDecimal;
import java.time.LocalTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPago;
import pe.edu.ucv.cafetin.pedidos.modelo.EstadoPedido;
import pe.edu.ucv.cafetin.pedidos.modelo.MetodoPago;

/**
 * Vista {@code v_pedidos_en_curso} (RF-10), solo lectura.
 *
 * <p><b>Incluye pedidos sin pagar</b> ({@code estadoPago} nulo o PENDIENTE): el despacho debe filtrar
 * {@code estadoPago = PAGADO}.
 */
@Entity
@Immutable
@Table(name = "v_pedidos_en_curso")
public class PedidoEnCurso {

	@Id
	@Column(name = "id_pedido")
	private Integer idPedido;

	@Column(name = "codigo_pedido")
	private String codigoPedido;

	@Enumerated(EnumType.STRING)
	@Column(columnDefinition = "estado_pedido_enum")
	private EstadoPedido estado;

	@Column(name = "hora_recojo")
	private LocalTime horaRecojo;

	private BigDecimal total;

	/** Nombre y apellidos del estudiante. */
	private String estudiante;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado_pago", columnDefinition = "estado_pago_enum")
	private EstadoPago estadoPago;

	@Enumerated(EnumType.STRING)
	@Column(name = "metodo_pago", columnDefinition = "metodo_pago_enum")
	private MetodoPago metodoPago;

	@Column(name = "codigo_verificacion")
	private String codigoVerificacion;

	protected PedidoEnCurso() {
	}

	public Integer getIdPedido() { return idPedido; }
	public String getCodigoPedido() { return codigoPedido; }
	public EstadoPedido getEstado() { return estado; }
	public LocalTime getHoraRecojo() { return horaRecojo; }
	public BigDecimal getTotal() { return total; }
	public String getEstudiante() { return estudiante; }
	public EstadoPago getEstadoPago() { return estadoPago; }
	public MetodoPago getMetodoPago() { return metodoPago; }
	public String getCodigoVerificacion() { return codigoVerificacion; }

}
