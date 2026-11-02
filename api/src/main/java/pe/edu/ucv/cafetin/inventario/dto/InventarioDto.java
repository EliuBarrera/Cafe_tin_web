package pe.edu.ucv.cafetin.inventario.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** DTOs del paquete Inventario (RF-15, RF-17 a RF-19). Parte del contrato: no los cambies sin avisar. */
public final class InventarioDto {

	private InventarioDto() {
	}

	// ------------------------------------------------------------- productos

	@Schema(description = "Alta de producto con su stock inicial (RF-15)")
	public record ProductoRequest(
			@NotBlank @Size(max = 100) @Schema(example = "Chifles 50 g") String nombre,
			@Size(max = 250) String descripcion,
			@NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) @Schema(example = "2.00") BigDecimal precio,
			@NotNull @Schema(example = "3") Integer idCategoria,
			@Schema(description = "Nulo para productos de preparación propia", example = "3") Integer idProveedor,
			@Size(max = 300) String imagenUrl,
			@NotNull @Min(0) @Schema(example = "30") Integer stockInicial,
			@NotNull @Min(0) @Schema(example = "10") Integer stockMinimo) {
	}

	@Schema(description = "Edición de producto (RF-19). El stock se cambia con la recarga, no aquí")
	public record ProductoActualizarRequest(
			@NotBlank @Size(max = 100) String nombre,
			@Size(max = 250) String descripcion,
			@NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal precio,
			@NotNull Integer idCategoria,
			Integer idProveedor,
			@Size(max = 300) String imagenUrl,
			@NotNull @Min(0) Integer stockMinimo) {
	}

	@Schema(description = "Producto con su stock, para el panel del dueño")
	public record ProductoResponse(
			Integer id,
			String nombre,
			String descripcion,
			BigDecimal precio,
			Integer idCategoria,
			String categoria,
			Integer idProveedor,
			String proveedor,
			String imagenUrl,
			@Schema(description = "false = deshabilitado por el dueño (RF-19)") boolean activo,
			Integer cantidadDisponible,
			Integer stockMinimo,
			@Schema(allowableValues = { "DISPONIBLE", "POCAS UNIDADES", "AGOTADO" }) String estadoStock,
			LocalDateTime fechaActualizacionStock) {
	}

	@Schema(description = "Recarga manual de stock (RF-18): suma unidades a las disponibles")
	public record RecargaRequest(
			@NotNull @Positive @Schema(description = "Unidades que se agregan", example = "24") Integer cantidad) {
	}

	@Schema(description = "Stock de un producto después de la recarga")
	public record InventarioResponse(
			Integer idProducto,
			String producto,
			Integer cantidadDisponible,
			Integer stockMinimo,
			LocalDateTime fechaActualizacion) {
	}

	// ----------------------------------------------------- alertas de stock

	@Schema(description = "Alerta de stock generada por la base (RF-17), con el contacto del proveedor")
	public record AlertaResponse(
			Integer id,
			@Schema(allowableValues = { "STOCK_BAJO", "STOCK_AGOTADO" }) String tipo,
			String mensaje,
			LocalDateTime fecha,
			@Schema(description = "Stock cuando se generó la alerta") Integer nivelDetectado,
			@Schema(description = "Stock actual") Integer cantidadDisponible,
			Integer stockMinimo,
			boolean atendida,
			Integer idProducto,
			String producto,
			String proveedor,
			String telefonoProveedor) {
	}

	// --------------------------------------------- categorías y proveedores

	@Schema(description = "Alta o edición de categoría")
	public record CategoriaRequest(
			@NotBlank @Size(max = 50) @Schema(example = "Postres") String nombre,
			@Size(max = 200) String descripcion) {
	}

	@Schema(description = "Categoría de productos")
	public record CategoriaResponse(
			@Schema(example = "2") Integer id,
			@Schema(example = "Bebidas") String nombre,
			String descripcion,
			boolean activo) {
	}

	@Schema(description = "Alta o edición de proveedor")
	public record ProveedorRequest(
			@NotBlank @Size(max = 100) @Schema(example = "Distribuidora Nor-Sur") String nombre,
			@NotBlank @Pattern(regexp = "^[0-9]{6,15}$", message = "solo dígitos, de 6 a 15") @Schema(example = "044123456") String telefono,
			@Email @Size(max = 120) String correo,
			@Size(max = 200) String direccion) {
	}

	@Schema(description = "Proveedor")
	public record ProveedorResponse(
			Integer id,
			String nombre,
			String telefono,
			String correo,
			String direccion,
			boolean activo) {
	}

}
