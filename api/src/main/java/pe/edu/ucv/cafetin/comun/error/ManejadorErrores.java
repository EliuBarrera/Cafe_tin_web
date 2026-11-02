package pe.edu.ucv.cafetin.comun.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import pe.edu.ucv.cafetin.comun.error.ErrorRespuesta.CampoError;

/**
 * Convierte toda excepción en {@link ErrorRespuesta} con un estado HTTP coherente.
 * Nunca expone mensajes internos (SQL, stack traces): esos van al log.
 */
@RestControllerAdvice
public class ManejadorErrores {

	private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

	/** Nombre del CHECK que falla cuando un pago dejaría el stock negativo. */
	static final String CHECK_STOCK = "ck_inventario_cantidad";

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorRespuesta> negocio(ApiException e) {
		return responder(e.getCodigo(), e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorRespuesta> validacionCuerpo(MethodArgumentNotValidException e) {
		List<CampoError> campos = e.getBindingResult().getFieldErrors().stream()
				.map(f -> new CampoError(f.getField(), mensaje(f)))
				.toList();
		return validacion(campos);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ErrorRespuesta> validacionParametros(HandlerMethodValidationException e) {
		List<CampoError> campos = e.getParameterValidationResults().stream()
				.flatMap(r -> r.getResolvableErrors().stream()
						.map(err -> new CampoError(r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
				.toList();
		return validacion(campos);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorRespuesta> cuerpoInvalido(HttpMessageNotReadableException e) {
		return responder(CodigoError.CUERPO_INVALIDO, "El cuerpo de la petición no es un JSON válido o tiene tipos incorrectos");
	}

	@ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class })
	ResponseEntity<ErrorRespuesta> parametroInvalido(Exception e) {
		return responder(CodigoError.PARAMETRO_INVALIDO, "Parámetro con formato incorrecto o faltante");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorRespuesta> rutaInexistente(NoResourceFoundException e) {
		return responder(CodigoError.NO_ENCONTRADO, "La ruta no existe");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorRespuesta> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
		return ResponseEntity.status(405)
				.body(new ErrorRespuesta("METODO_NO_PERMITIDO", "Método HTTP no permitido en esta ruta", List.of()));
	}

	/**
	 * Violación de una restricción de la base. Si es el CHECK de stock (dos estudiantes pagando la
	 * última unidad, RNF-03), responde STOCK_INSUFICIENTE; si no, CONFLICTO (p. ej. UNIQUE).
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorRespuesta> integridad(DataIntegrityViolationException e) {
		String causa = String.valueOf(e.getMostSpecificCause().getMessage());
		if (causa.contains(CHECK_STOCK)) {
			return responder(CodigoError.STOCK_INSUFICIENTE, "Un producto de tu pedido se agotó");
		}
		log.info("Violación de integridad: {}", causa);
		return responder(CodigoError.CONFLICTO, "La operación entra en conflicto con datos existentes");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorRespuesta> inesperado(Exception e) {
		log.error("Error no controlado", e);
		return responder(CodigoError.ERROR_INTERNO, "Ocurrió un error inesperado. Intenta de nuevo");
	}

	// ----------------------------------------------------------------------

	private static ResponseEntity<ErrorRespuesta> validacion(List<CampoError> campos) {
		return ResponseEntity.status(CodigoError.VALIDACION.estado())
				.body(new ErrorRespuesta(CodigoError.VALIDACION.name(), "Revisa los campos marcados", campos));
	}

	private static ResponseEntity<ErrorRespuesta> responder(CodigoError codigo, String mensaje) {
		return ResponseEntity.status(codigo.estado()).body(ErrorRespuesta.de(codigo, mensaje));
	}

	private static String mensaje(FieldError f) {
		return f.getDefaultMessage() != null ? f.getDefaultMessage() : "valor inválido";
	}

}
