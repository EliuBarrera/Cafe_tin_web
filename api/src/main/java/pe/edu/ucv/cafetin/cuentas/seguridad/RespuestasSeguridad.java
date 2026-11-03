package pe.edu.ucv.cafetin.cuentas.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.edu.ucv.cafetin.comun.error.CodigoError;
import pe.edu.ucv.cafetin.comun.error.ErrorRespuesta;
import tools.jackson.databind.ObjectMapper;

/**
 * Responde 401 y 403 con el formato {@code {codigo, mensaje, detalles}}. Estos errores salen de los
 * filtros, antes de llegar a {@code ManejadorErrores}.
 */
@Component
class RespuestasSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final ObjectMapper json;

	RespuestasSeguridad(ObjectMapper json) {
		this.json = json;
	}

	@Override
	public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException e)
			throws IOException {
		escribir(respuesta, CodigoError.NO_AUTENTICADO, "Inicia sesión para continuar");
	}

	@Override
	public void handle(HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException e)
			throws IOException {
		escribir(respuesta, CodigoError.ACCESO_DENEGADO, "No tienes permiso para realizar esta acción");
	}

	private void escribir(HttpServletResponse respuesta, CodigoError codigo, String mensaje) throws IOException {
		respuesta.setStatus(codigo.estado().value());
		respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
		respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
		respuesta.getWriter().write(json.writeValueAsString(ErrorRespuesta.de(codigo, mensaje)));
	}

}
