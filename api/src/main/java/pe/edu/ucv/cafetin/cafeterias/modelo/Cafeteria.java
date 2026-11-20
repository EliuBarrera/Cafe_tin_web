package pe.edu.ucv.cafetin.cafeterias.modelo;

import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cafetín de la plataforma (V11). Se da de alta con {@code docs/alta-cafeteria.sql}. */
@Entity
@Table(name = "cafeteria")
public class Cafeteria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_cafeteria")
	private Integer id;

	@Column(nullable = false, length = 80, unique = true)
	private String nombre;

	@Column(length = 150)
	private String ubicacion;

	@Column(nullable = false)
	private boolean activo = true;

	@Generated
	@Column(name = "fecha_registro", insertable = false, updatable = false)
	private LocalDateTime fechaRegistro;

	protected Cafeteria() {
	}

	public Cafeteria(String nombre, String ubicacion) {
		this.nombre = nombre;
		this.ubicacion = ubicacion;
	}

	public Integer getId() { return id; }

	public String getNombre() { return nombre; }

	public String getUbicacion() { return ubicacion; }

	public boolean isActivo() { return activo; }

	public void setActivo(boolean activo) { this.activo = activo; }

	public LocalDateTime getFechaRegistro() { return fechaRegistro; }

}
