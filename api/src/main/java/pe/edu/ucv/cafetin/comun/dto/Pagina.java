package pe.edu.ucv.cafetin.comun.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Página de resultados del contrato. No se expone {@code Page} de Spring directamente: su JSON
 * cambia entre versiones y arrastra campos internos.
 */
@Schema(description = "Página de resultados")
public record Pagina<T>(
		List<T> contenido,
		@Schema(description = "Número de página, desde 0", example = "0") int pagina,
		@Schema(example = "20") int tamano,
		@Schema(example = "57") long totalElementos,
		@Schema(example = "3") int totalPaginas) {

	public static <E, T> Pagina<T> de(Page<E> pagina, Function<E, T> mapeo) {
		return new Pagina<>(pagina.getContent().stream().map(mapeo).toList(), pagina.getNumber(),
				pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
	}

}
