package pe.edu.ucv.cafetin.pedidos.modelo;

/**
 * Tipo PostgreSQL {@code metodo_pago_enum}. {@code OTRO} (V13) es solo de salida: un medio de
 * Mercado Pago que no es tarjeta ni Yape. EFECTIVO existe en la base pero la API lo rechaza.
 */
public enum MetodoPago {
	YAPE, PLIN, TARJETA, EFECTIVO, OTRO
}
