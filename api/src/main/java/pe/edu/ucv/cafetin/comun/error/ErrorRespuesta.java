package pe.edu.ucv.cafetin.comun.error;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Formato único de error de la API: {@code {codigo, mensaje, detalles}}.
 * Lo usan todos los módulos (y la seguridad para 401/403). Ver {@link CodigoError}.
 */
@Schema(description = "Error de la API")
public record ErrorRespuesta(
		@Schema(description = "Código estable para que los clientes decidan qué hacer", example = "VALIDACION")
		String codigo,
		@Schema(description = "Mensaje en español para mostrar al usuario", example = "Revisa los campos marcados")
		String mensaje,
		@Schema(description = "Errores por campo (solo en VALIDACION); vacío en el resto")
		List<CampoError> detalles) {

	public static ErrorRespuesta de(CodigoError codigo, String mensaje) {
		return new ErrorRespuesta(codigo.name(), mensaje, List.of());
	}

	@Schema(description = "Error de un campo")
	public record CampoError(
			@Schema(example = "items[0].cantidad") String campo,
			@Schema(example = "debe ser mayor que 0") String mensaje) {
	}

}
