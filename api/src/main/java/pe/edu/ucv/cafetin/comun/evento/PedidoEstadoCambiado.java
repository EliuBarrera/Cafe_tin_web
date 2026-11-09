package pe.edu.ucv.cafetin.comun.evento;

import pe.edu.ucv.cafetin.comun.modelo.TipoNotificacion;

/**
 * Aviso al estudiante de que su pedido cambió de estado (RF-08). Lo publica el despacho al guardar la
 * fila en {@code notificacion}; {@code cuentas} lo envía por push solo si la transacción se confirma.
 */
public record PedidoEstadoCambiado(Integer idUsuario, Integer idPedido, TipoNotificacion tipo, String mensaje) {
}
