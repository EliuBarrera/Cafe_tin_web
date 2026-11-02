package pe.edu.ucv.cafetin.cuentas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/** Subtipo Estudiante (RF-01): realiza, paga y recoge pedidos. */
@Entity
@Table(name = "estudiante")
@DiscriminatorValue("ESTUDIANTE")
@PrimaryKeyJoinColumn(name = "id_usuario")
public class Estudiante extends Usuario {

	@Column(nullable = false, length = 15, unique = true)
	private String codigo;

	@Column(nullable = false, length = 80)
	private String carrera;

	/** Ciclo de 1 a 12 (CHECK en la base). */
	@Column(nullable = false)
	private Short ciclo;

	protected Estudiante() {
	}

	public Estudiante(String nombre, String apellidos, String correo, String contrasenaHash,
			String codigo, String carrera, short ciclo) {
		super(nombre, apellidos, correo, contrasenaHash);
		this.codigo = codigo;
		this.carrera = carrera;
		this.ciclo = ciclo;
	}

	@Override
	public TipoUsuario getTipoUsuario() { return TipoUsuario.ESTUDIANTE; }

	public String getCodigo() { return codigo; }
	public void setCodigo(String codigo) { this.codigo = codigo; }

	public String getCarrera() { return carrera; }
	public void setCarrera(String carrera) { this.carrera = carrera; }

	public Short getCiclo() { return ciclo; }
	public void setCiclo(Short ciclo) { this.ciclo = ciclo; }

}
