package pe.edu.ucv.cafetin.comun.config;

import java.util.Map;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import pe.edu.ucv.cafetin.comun.error.ErrorRespuesta;

/**
 * Datos generales del contrato OpenAPI ({@code docs/openapi.yaml}): título, seguridad JWT y
 * respuestas de error comunes a todas las operaciones.
 */
@Configuration
public class OpenApiConfig {

	static final String JWT = "bearerAuth";
	static final String REF_ERROR = "#/components/schemas/ErrorRespuesta";

	@Bean
	OpenAPI contrato() {
		return new OpenAPI()
				.info(new Info()
						.title("Gestión Cafetín — API REST")
						.version("1.0.0")
						.description("""
								API única para la app Android (estudiante) y el panel web (trabajador y dueño).
								Autenticación: `Authorization: Bearer <JWT>` obtenido en `POST /api/auth/login`.
								Errores: siempre `{codigo, mensaje, detalles}`; los clientes deciden por `codigo`.
								Precios y montos en soles (S/) con 2 decimales. Fechas y horas en hora de Lima."""))
				.servers(java.util.List.of(new Server().url("/").description("Mismo origen de la API")))
				.components(new Components().addSecuritySchemes(JWT, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(JWT));
	}

	/** Agrega a cada operación las respuestas de error comunes con el esquema {@link ErrorRespuesta}. */
	@Bean
	OpenApiCustomizer erroresComunes() {
		return openApi -> {
			Map<String, Schema> esquemas = ModelConverters.getInstance().read(ErrorRespuesta.class);
			esquemas.forEach(openApi.getComponents()::addSchemas);

			openApi.getPaths().forEach((ruta, item) -> item.readOperations().forEach(op -> {
				boolean publica = op.getSecurity() != null && op.getSecurity().isEmpty();
				agregar(op.getResponses(), "400", "Datos inválidos (VALIDACION, CUERPO_INVALIDO, PARAMETRO_INVALIDO)");
				if (!publica) {
					agregar(op.getResponses(), "401", "Sin token o token inválido (NO_AUTENTICADO)");
					agregar(op.getResponses(), "403", "El rol no tiene acceso (ACCESO_DENEGADO)");
				}
				if (ruta.contains("{")) {
					agregar(op.getResponses(), "404", "No existe o no te pertenece (NO_ENCONTRADO)");
				}
				agregar(op.getResponses(), "500", "Error inesperado (ERROR_INTERNO)");
			}));
		};
	}

	private static void agregar(io.swagger.v3.oas.models.responses.ApiResponses respuestas, String estado,
			String descripcion) {
		if (!respuestas.containsKey(estado)) {
			respuestas.addApiResponse(estado, new ApiResponse().description(descripcion)
					.content(new Content().addMediaType("application/json",
							new MediaType().schema(new Schema<>().$ref(REF_ERROR)))));
		}
	}

}
