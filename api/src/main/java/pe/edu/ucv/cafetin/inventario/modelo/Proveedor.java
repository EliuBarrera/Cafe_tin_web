package pe.edu.ucv.cafetin.inventario.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Proveedor de productos; su teléfono aparece en las alertas de reposición (RF-17). */
@Entity
@Table(name = "proveedor")
public class Proveedor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_proveedor")
	private Integer id;

	@Column(nullable = false, length = 100, unique = true)
	private String nombre;

	@Column(nullable = false, length = 15)
	private String telefono;

	@Column(length = 120)
	private String correo;

	@Column(length = 200)
	private String direccion;

	@Column(nullable = false)
	private boolean activo = true;

	protected Proveedor() {
	}

	public Proveedor(String nombre, String telefono) {
		this.nombre = nombre;
		this.telefono = telefono;
	}

	public Integer getId() { return id; }

	public String getNombre() { return nombre; }
	public void setNombre(String nombre) { this.nombre = nombre; }

	public String getTelefono() { return telefono; }
	public void setTelefono(String telefono) { this.telefono = telefono; }

	public String getCorreo() { return correo; }
	public void setCorreo(String correo) { this.correo = correo; }

	public String getDireccion() { return direccion; }
	public void setDireccion(String direccion) { this.direccion = direccion; }

	public boolean isActivo() { return activo; }
	public void setActivo(boolean activo) { this.activo = activo; }

}
