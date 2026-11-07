-- ============================================================================
-- V10__secuencia_codigo_pedido.sql  (módulo Pedidos · Eliú Barrera · rango V10–V19)
-- Secuencia para pedido.codigo_pedido = 'PED-' || nextval (RF-05), sin relleno fijo.
-- Empieza en 428 para seguir al seed (PED-0423 … PED-0427). Una secuencia evita
-- que dos pedidos simultáneos reciban el mismo código.
-- NO EDITAR una vez aplicada: cualquier cambio va en una nueva V<n>.
-- ============================================================================

CREATE SEQUENCE seq_codigo_pedido START WITH 428;

COMMENT ON SEQUENCE seq_codigo_pedido IS 'RF-05: número del código visible del pedido (PED-<n>).';
