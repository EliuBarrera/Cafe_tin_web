package pe.edu.ucv.cafetin.cuentas.modelo;

import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

/**
 * Clase abstracta Usuario (tabla {@code usuario}). Especialización tabla por subtipo (JOINED):
 * {@link Estudiante}, {@link Trabajador}, {@link Dueno}. Hibernate escribe el discriminador
 * {@code tipo_usuario} (enum de PostgreSQL) según la subclase; el rol se lee con {@link #getTipoUsuario()}.
 *
 * <p><b>Ojo con {@code instanceof}:</b> una relación LAZY hacia {@code Usuario} (p. ej.
 * {@code notificacion.getUsuario()}) devuelve un proxy de la clase base, y {@code instanceof Dueno}
 * da {@code false} aunque sea un dueño. Usa {@link #getTipoUsuario()} o {@code Hibernate.unproxy(...)}.
 */
@Entity
@Table(name = "usuario")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo_usuario", discriminatorType = DiscriminatorType.STRING,
		columnDefinition = "tipo_usuario_enum")
public abstract class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_usuario")
	private Integer id;

	@Column(nullable = false, length = 60)
	private String nombre;

	@Column(nullable = false, length = 80)
	private String apellidos;

	@Column(nullable = false, length = 120, unique = true)
	private String correo;

	/** Hash BCrypt, nunca la contraseña en claro (RNF-04). */
	@Column(nullable = false, length = 255)
	private String contrasena;

	@Column(length = 15)
	private String telefono;

	@Column(nullable = false)
	private boolean activo = true;

	/** DEFAULT CURRENT_TIMESTAMP de la base (hora de Lima). */
	@Generated
	@Column(name = "fecha_registro", insertable = false, updatable = false)
	private LocalDateTime fechaRegistro;

	/** RF-03: hash del token de recuperación, nunca el token en claro. */
	@Column(name = "token_recupera", length = 255)
	private String tokenRecupera;

	@Column(name = "token_expira")
	private LocalDateTime tokenExpira;

	/** RF-08: token FCM del último dispositivo del usuario. */
	@Column(name = "fcm_token", length = 255)
	private String fcmToken;

	protected Usuario() {
	}

	protected Usuario(String nombre, String apellidos, String correo, String contrasenaHash) {
		this.nombre = nombre;
		this.apellidos = apellidos;
		this.correo = correo;
		this.contrasena = contrasenaHash;
	}

	/** Rol del usuario; coincide con el discriminador {@code tipo_usuario}. */
	public abstract TipoUsuario getTipoUsuario();

	public Integer getId() { return id; }

	public String getNombre() { return nombre; }
	public void setNombre(String nombre) { this.nombre = nombre; }

	public String getApellidos() { return apellidos; }
	public void setApellidos(String apellidos) { this.apellidos = apellidos; }

	public String getCorreo() { return correo; }
	public void setCorreo(String correo) { this.correo = correo; }

	public String getContrasena() { return contrasena; }
	public void setContrasena(String contrasenaHash) { this.contrasena = contrasenaHash; }

	public String getTelefono() { return telefono; }
	public void setTelefono(String telefono) { this.telefono = telefono; }

	public boolean isActivo() { return activo; }
	public void setActivo(boolean activo) { this.activo = activo; }

	public LocalDateTime getFechaRegistro() { return fechaRegistro; }

	public String getTokenRecupera() { return tokenRecupera; }
	public void setTokenRecupera(String tokenRecuperaHash) { this.tokenRecupera = tokenRecuperaHash; }

	public LocalDateTime getTokenExpira() { return tokenExpira; }
	public void setTokenExpira(LocalDateTime tokenExpira) { this.tokenExpira = tokenExpira; }

	public String getFcmToken() { return fcmToken; }
	public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

}
