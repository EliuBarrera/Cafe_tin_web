package pe.edu.ucv.cafetin.cuentas.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.edu.ucv.cafetin.cuentas.modelo.TipoUsuario;
import pe.edu.ucv.cafetin.cuentas.modelo.Turno;

/** DTOs del paquete Cuentas (RF-01 a RF-03, RF-08). Parte del contrato: no los cambies sin avisar. */
public final class CuentasDto {

	private CuentasDto() {
	}

	@Schema(description = "Registro de un estudiante (RF-01)")
	public record RegistroEstudianteRequest(
			@NotBlank @Size(min = 2, max = 60) @Schema(example = "Eliú") String nombre,
			@NotBlank @Size(max = 80) @Schema(example = "Barrera Martínez") String apellidos,
			@NotBlank @Email @Size(max = 120) @Schema(example = "ebarreram@ucvvirtual.edu.pe") String correo,
			@NotBlank @Size(min = 8, max = 72) @Schema(example = "Cafetin2026") String contrasena,
			@Pattern(regexp = "^[0-9]{9}$", message = "debe tener 9 dígitos") @Schema(example = "914567890") String telefono,
			@NotBlank @Size(max = 15) @Schema(description = "Código de estudiante", example = "2021045678") String codigo,
			@NotBlank @Size(max = 80) @Schema(example = "Ingeniería de Sistemas") String carrera,
			@NotNull @Min(1) @Max(12) @Schema(example = "6") Short ciclo) {
	}

	@Schema(description = "Credenciales de inicio de sesión (RF-01, RF-02)")
	public record LoginRequest(
			@NotBlank @Email @Schema(example = "ebarreram@ucvvirtual.edu.pe") String correo,
			@NotBlank @Schema(example = "Cafetin2026") String contrasena) {
	}

	@Schema(description = "Token JWT y datos del usuario")
	public record TokenResponse(
			@Schema(description = "JWT para el encabezado Authorization: Bearer") String token,
			@Schema(description = "Segundos hasta que el token expira", example = "28800") long expiraEn,
			UsuarioResponse usuario) {
	}

	@Schema(description = "Usuario autenticado")
	public record UsuarioResponse(
			@Schema(example = "4") Integer id,
			@Schema(example = "Eliú") String nombre,
			@Schema(example = "Barrera Martínez") String apellidos,
			@Schema(example = "ebarreram@ucvvirtual.edu.pe") String correo,
			@Schema(description = "Rol: decide el menú en el panel y el acceso en la API") TipoUsuario rol) {
	}

	@Schema(description = "Pedir el enlace de recuperación de contraseña (RF-03)")
	public record RecuperarRequest(
			@NotBlank @Email @Schema(example = "ebarreram@ucvvirtual.edu.pe") String correo) {
	}

	@Schema(description = "Fijar una nueva contraseña con el token recibido (RF-03)")
	public record RestablecerRequest(
			@NotBlank @Schema(description = "Token del enlace de recuperación") String token,
			@NotBlank @Size(min = 8, max = 72) String nuevaContrasena) {
	}

	@Schema(description = "Alta de un trabajador por el dueño (RF-02)")
	public record TrabajadorRequest(
			@NotBlank @Size(min = 2, max = 60) @Schema(example = "Carmen") String nombre,
			@NotBlank @Size(max = 80) @Schema(example = "Díaz Morales") String apellidos,
			@NotBlank @Email @Size(max = 120) @Schema(example = "carmen.diaz@cafetin.pe") String correo,
			@NotBlank @Size(min = 8, max = 72) @Schema(description = "Contraseña inicial") String contrasena,
			@Pattern(regexp = "^[0-9]{9}$", message = "debe tener 9 dígitos") String telefono,
			@NotBlank @Size(max = 15) @Schema(example = "TRB-003") String codigoEmpleado,
			@NotNull Turno turno) {
	}

	@Schema(description = "Trabajador registrado")
	public record TrabajadorResponse(
			Integer id,
			String nombre,
			String apellidos,
			String correo,
			String telefono,
			String codigoEmpleado,
			Turno turno,
			LocalDate fechaIngreso,
			boolean activo) {
	}

	@Schema(description = "Token de Firebase Cloud Messaging del dispositivo (RF-08)")
	public record FcmTokenRequest(
			@NotBlank @Size(max = 255) String token) {
	}

	@Schema(description = "Notificación del usuario (RF-08); la app las consulta al abrirse por si FCM se retrasa")
	public record NotificacionResponse(
			Integer id,
			@Schema(description = "PEDIDO_CONFIRMADO, PEDIDO_LISTO, PEDIDO_ENTREGADO, PEDIDO_CANCELADO, STOCK_BAJO o STOCK_AGOTADO")
			String tipo,
			@Schema(example = "Tu pedido PED-0426 está listo") String mensaje,
			@Schema(description = "Pedido relacionado; nulo en alertas de stock") Integer idPedido,
			LocalDateTime fecha,
			boolean leida) {
	}

}
