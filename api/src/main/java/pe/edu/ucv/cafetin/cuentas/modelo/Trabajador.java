package pe.edu.ucv.cafetin.cuentas.modelo;

import java.time.LocalDate;

import org.hibernate.annotations.Generated;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/** Subtipo Trabajador (RF-02): confirma, prepara, verifica y entrega pedidos. */
@Entity
@Table(name = "trabajador")
@DiscriminatorValue("TRABAJADOR")
@PrimaryKeyJoinColumn(name = "id_usuario")
public class Trabajador extends Usuario {

	@Column(name = "codigo_empleado", nullable = false, length = 15, unique = true)
	private String codigoEmpleado;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "turno_enum")
	private Turno turno;

	/** DEFAULT CURRENT_DATE de la base. */
	@Generated
	@Column(name = "fecha_ingreso", insertable = false, updatable = false)
	private LocalDate fechaIngreso;

	protected Trabajador() {
	}

	public Trabajador(String nombre, String apellidos, String correo, String contrasenaHash,
			String codigoEmpleado, Turno turno) {
		super(nombre, apellidos, correo, contrasenaHash);
		this.codigoEmpleado = codigoEmpleado;
		this.turno = turno;
	}

	@Override
	public TipoUsuario getTipoUsuario() { return TipoUsuario.TRABAJADOR; }

	public String getCodigoEmpleado() { return codigoEmpleado; }
	public void setCodigoEmpleado(String codigoEmpleado) { this.codigoEmpleado = codigoEmpleado; }

	public Turno getTurno() { return turno; }
	public void setTurno(Turno turno) { this.turno = turno; }

	public LocalDate getFechaIngreso() { return fechaIngreso; }

}
