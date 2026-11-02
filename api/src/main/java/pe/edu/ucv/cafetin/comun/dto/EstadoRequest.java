package pe.edu.ucv.cafetin.comun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Activar o desactivar un recurso (producto, categoría, proveedor, trabajador) sin borrarlo. */
@Schema(description = "Activar o desactivar sin borrar")
public record EstadoRequest(@NotNull @Schema(example = "false") Boolean activo) {
}
