package pe.edu.ucv.cafetin.pedidos.modelo;

/**
 * Tipo PostgreSQL {@code estado_pago_enum}. El pago se crea PENDIENTE y se actualiza a PAGADO:
 * solo ese UPDATE dispara {@code trg_descontar_stock}.
 */
public enum EstadoPago {
	PENDIENTE, PAGADO, FALLIDO, REEMBOLSADO
}
