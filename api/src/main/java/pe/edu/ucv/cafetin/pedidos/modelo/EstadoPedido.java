package pe.edu.ucv.cafetin.pedidos.modelo;

/**
 * Tipo PostgreSQL {@code estado_pedido_enum}. Las transiciones válidas las decide una sola clase
 * (CLAUDE.md, "Estados del pedido"); no cambies el estado directamente desde otros servicios.
 */
public enum EstadoPedido {
	PENDIENTE, CONFIRMADO, EN_PREPARACION, LISTO, ENTREGADO, CANCELADO
}
