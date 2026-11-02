package pe.edu.ucv.cafetin.comun.error;

import org.springframework.http.HttpStatus;

/**
 * Códigos de error del contrato. La app y el panel deciden qué mostrar según el código,
 * no según el texto del mensaje. Agregar uno nuevo es un cambio de contrato: avisa al equipo.
 */
public enum CodigoError {

	/** Campos del cuerpo o parámetros que no cumplen las validaciones. */
	VALIDACION(HttpStatus.BAD_REQUEST),
	/** JSON mal formado o con tipos incorrectos. */
	CUERPO_INVALIDO(HttpStatus.BAD_REQUEST),
	/** Parámetro de ruta o de consulta con formato incorrecto (p. ej. id no numérico). */
	PARAMETRO_INVALIDO(HttpStatus.BAD_REQUEST),
	/** Falta el token o es inválido / expiró (lo emite la seguridad, Fase 2). */
	NO_AUTENTICADO(HttpStatus.UNAUTHORIZED),
	/** El rol no tiene acceso a la ruta (RNF-05; lo emite la seguridad, Fase 2). */
	ACCESO_DENEGADO(HttpStatus.FORBIDDEN),
	/** El recurso no existe o no pertenece al usuario. */
	NO_ENCONTRADO(HttpStatus.NOT_FOUND),
	/** Violación de una regla de unicidad o integridad (p. ej. correo ya registrado). */
	CONFLICTO(HttpStatus.CONFLICT),
	/** No hay stock para completar el pago (CHECK cantidad_disponible >= 0). */
	STOCK_INSUFICIENTE(HttpStatus.CONFLICT),
	/** Cambio de estado del pedido no permitido por la máquina de estados. */
	TRANSICION_INVALIDA(HttpStatus.CONFLICT),
	/** Regla de negocio incumplida (p. ej. entregar sin recibo verificado, código que no coincide). */
	REGLA_NEGOCIO(HttpStatus.CONFLICT),
	/** La pasarela rechazó el cobro (CU-02, M2): el pedido se conserva y se puede reintentar. */
	PAGO_RECHAZADO(HttpStatus.PAYMENT_REQUIRED),
	/** Endpoint del contrato aún sin implementar. */
	NO_IMPLEMENTADO(HttpStatus.NOT_IMPLEMENTED),
	/** Error inesperado; el detalle queda en el log, nunca en la respuesta. */
	ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus estado;

	CodigoError(HttpStatus estado) {
		this.estado = estado;
	}

	public HttpStatus estado() {
		return estado;
	}

}
