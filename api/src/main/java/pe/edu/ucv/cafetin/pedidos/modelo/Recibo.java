package pe.edu.ucv.cafetin.pedidos.modelo;

import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;

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
 * Recibo digital de un pago (1:1, RF-07). El trabajador contrasta {@code codigoVerificacion}
 * (formato {@code DR-<nº>-<4 dígitos>}) con el que muestra el estudiante antes de entregar (RF-13, RF-14).
 */
@Entity
@Table(name = "recibo")
public class Recibo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_recibo")
	private Integer id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_pago", nullable = false, unique = true)
	private Pago pago;

	@Column(name = "codigo_verificacion", nullable = false, length = 20, unique = true)
	private String codigoVerificacion;

	@Generated
	@Column(name = "fecha_emision", insertable = false, updatable = false)
	private LocalDateTime fechaEmision;

	@Column(nullable = false)
	private boolean verificado = false;

	@Column(name = "fecha_verificacion")
	private LocalDateTime fechaVerificacion;

	protected Recibo() {
	}

	public Recibo(Pago pago, String codigoVerificacion) {
		this.pago = pago;
		this.codigoVerificacion = codigoVerificacion;
	}

	public Integer getId() { return id; }

	public Pago getPago() { return pago; }

	public String getCodigoVerificacion() { return codigoVerificacion; }

	public LocalDateTime getFechaEmision() { return fechaEmision; }

	public boolean isVerificado() { return verificado; }

	public LocalDateTime getFechaVerificacion() { return fechaVerificacion; }

	/** RF-13: marca el recibo como verificado en el momento indicado. */
	public void marcarVerificado(LocalDateTime cuando) {
		this.verificado = true;
		this.fechaVerificacion = cuando;
	}

}
