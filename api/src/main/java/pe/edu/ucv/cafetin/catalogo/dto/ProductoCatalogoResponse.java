package pe.edu.ucv.cafetin.catalogo.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/** Producto del catálogo que ve el estudiante (RF-04), desde la vista v_catalogo_disponible. */
@Schema(description = "Producto del catálogo con su disponibilidad")
public record ProductoCatalogoResponse(
		@Schema(example = "7") Integer idProducto,
		@Schema(example = "Café pasado") String nombre,
		@Schema(example = "Café de Chanchamayo") String descripcion,
		@Schema(example = "2.50") BigDecimal precio,
		@Schema(example = "Bebidas") String categoria,
		@Schema(example = "39") Integer cantidadDisponible,
		@Schema(description = "La app deshabilita la tarjeta si es AGOTADO",
				allowableValues = { "DISPONIBLE", "POCAS UNIDADES", "AGOTADO" }) String estadoStock) {
}
