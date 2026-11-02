package pe.edu.ucv.cafetin.comun.modelo;

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
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import pe.edu.ucv.cafetin.cuentas.modelo.Usuario;
import pe.edu.ucv.cafetin.pedidos.modelo.Pedido;

/**
 * Notificación a un usuario (RF-08). Especialización JOINED: {@code AlertaStock} (inventario) agrega
 * el nivel de stock detectado. La tabla no tiene discriminador: Hibernate distingue el subtipo
 * por la existencia de la fila en {@code alerta_stock}.
 *
 * <p>Las alertas de stock las inserta el trigger {@code trg_alerta_stock_minimo}; Java solo las lee
 * y las marca como atendidas.
 */
@Entity
@Table(name = "notificacion")
@Inheritance(strategy = InheritanceType.JOINED)
public class Notificacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_notificacion")
	private Integer id;

	/** Destinatario. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_usuario", nullable = false)
	private Usuario usuario;

	/** Nulo en las alertas de stock. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_pedido")
	private Pedido pedido;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "tipo_notif_enum")
	private TipoNotificacion tipo;

	@Column(nullable = false, length = 250)
	private String mensaje;

	@Generated
	@Column(insertable = false, updatable = false)
	private LocalDateTime fecha;

	@Column(nullable = false)
	private boolean leida = false;

	protected Notificacion() {
	}

	public Notificacion(Usuario usuario, Pedido pedido, TipoNotificacion tipo, String mensaje) {
		this.usuario = usuario;
		this.pedido = pedido;
		this.tipo = tipo;
		this.mensaje = mensaje;
	}

	public Integer getId() { return id; }

	public Usuario getUsuario() { return usuario; }

	public Pedido getPedido() { return pedido; }

	public TipoNotificacion getTipo() { return tipo; }

	public String getMensaje() { return mensaje; }

	public LocalDateTime getFecha() { return fecha; }

	public boolean isLeida() { return leida; }
	public void setLeida(boolean leida) { this.leida = leida; }

}
