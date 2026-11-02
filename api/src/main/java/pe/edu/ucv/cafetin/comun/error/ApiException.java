package pe.edu.ucv.cafetin.comun.error;

/**
 * Excepción de negocio que se convierte en {@link ErrorRespuesta} con el estado HTTP de su
 * {@link CodigoError}. Los servicios lanzan las subclases; los controllers no capturan nada.
 */
public class ApiException extends RuntimeException {

	private final CodigoError codigo;

	public ApiException(CodigoError codigo, String mensaje) {
		super(mensaje);
		this.codigo = codigo;
	}

	public CodigoError getCodigo() {
		return codigo;
	}

	/** 404: el recurso no existe o no pertenece al usuario. */
	public static class NoEncontrado extends ApiException {
		public NoEncontrado(String mensaje) {
			super(CodigoError.NO_ENCONTRADO, mensaje);
		}
	}

	/** 409: transición de estado no permitida (la lanza solo la máquina de estados). */
	public static class TransicionInvalida extends ApiException {
		public TransicionInvalida(String mensaje) {
			super(CodigoError.TRANSICION_INVALIDA, mensaje);
		}
	}

	/** 409: regla de negocio incumplida. */
	public static class ReglaNegocio extends ApiException {
		public ReglaNegocio(String mensaje) {
			super(CodigoError.REGLA_NEGOCIO, mensaje);
		}
	}

	/** 501: endpoint del contrato sin implementar todavía. */
	public static class NoImplementado extends ApiException {
		public NoImplementado(String requisito) {
			super(CodigoError.NO_IMPLEMENTADO, "Pendiente de implementar (" + requisito + ")");
		}
	}

}
