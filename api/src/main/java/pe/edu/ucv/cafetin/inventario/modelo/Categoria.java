package pe.edu.ucv.cafetin.inventario.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Categoría de productos (Desayunos, Bebidas, …). */
@Entity
@Table(name = "categoria")
public class Categoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_categoria")
	private Integer id;

	@Column(nullable = false, length = 50, unique = true)
	private String nombre;

	@Column(length = 200)
	private String descripcion;

	@Column(nullable = false)
	private boolean activo = true;

	protected Categoria() {
	}

	public Categoria(String nombre, String descripcion) {
		this.nombre = nombre;
		this.descripcion = descripcion;
	}

	public Integer getId() { return id; }

	public String getNombre() { return nombre; }
	public void setNombre(String nombre) { this.nombre = nombre; }

	public String getDescripcion() { return descripcion; }
	public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

	public boolean isActivo() { return activo; }
	public void setActivo(boolean activo) { this.activo = activo; }

}
