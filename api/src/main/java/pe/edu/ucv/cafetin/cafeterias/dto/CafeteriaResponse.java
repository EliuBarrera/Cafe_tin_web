package pe.edu.ucv.cafetin.cafeterias.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cafetín de la plataforma")
public record CafeteriaResponse(
		@Schema(example = "1") Integer id,
		@Schema(example = "Cafetín principal") String nombre,
		@Schema(example = "Campus UCV, pabellón A") String ubicacion) {
}
