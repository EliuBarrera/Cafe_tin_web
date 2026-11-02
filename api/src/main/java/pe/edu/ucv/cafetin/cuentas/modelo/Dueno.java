package pe.edu.ucv.cafetin.cuentas.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/** Subtipo Dueño (RF-02): administra productos, stock, proveedores e ingresos (RNF-05). */
@Entity
@Table(name = "dueno")
@DiscriminatorValue("DUENO")
@PrimaryKeyJoinColumn(name = "id_usuario")
public class Dueno extends Usuario {

	@Column(nullable = false, length = 60)
	private String cargo = "Propietario";

	/** 11 dígitos o nulo (CHECK en la base). */
	@Column(length = 11)
	private String ruc;

	protected Dueno() {
	}

	public Dueno(String nombre, String apellidos, String correo, String contrasenaHash) {
		super(nombre, apellidos, correo, contrasenaHash);
	}

	@Override
	public TipoUsuario getTipoUsuario() { return TipoUsuario.DUENO; }

	public String getCargo() { return cargo; }
	public void setCargo(String cargo) { this.cargo = cargo; }

	public String getRuc() { return ruc; }
	public void setRuc(String ruc) { this.ruc = ruc; }

}
