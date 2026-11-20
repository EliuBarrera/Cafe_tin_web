-- ============================================================================
-- V11__cafeterias.sql  (módulo Pedidos · Eliú Barrera · rango V10–V19)
-- Varios cafetines en una sola instalación (decisión del 19/11/2026).
--   1. Tabla cafeteria. Los datos que ya existen pasan a la cafetería 1.
--   2. id_cafeteria en categoria, producto, proveedor, pedido, trabajador y dueno:
--      cada cafetín gestiona sus categorías sin tocar las de otro.
--   3. Nombres de categoría, producto y proveedor únicos por cafetería, no globales.
--   4. Un pedido solo lleva productos de su cafetería (trigger en detalle_pedido).
--   5. La alerta de stock va solo a los dueños de la cafetería del producto.
--   6. Las vistas exponen id_cafeteria (al final, como exige CREATE OR REPLACE VIEW).
-- Alta de un cafetín nuevo: docs/alta-cafeteria.sql.
-- NO EDITAR una vez aplicada: cualquier cambio va en una nueva V<n>.
-- ============================================================================

CREATE TABLE cafeteria (
    id_cafeteria    SERIAL          PRIMARY KEY,
    nombre          VARCHAR(80)     NOT NULL,
    ubicacion       VARCHAR(150),
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    fecha_registro  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_cafeteria_nombre UNIQUE (nombre),
    CONSTRAINT ck_cafeteria_nombre CHECK (LENGTH(TRIM(nombre)) >= 2)
);

COMMENT ON TABLE cafeteria IS 'Cafetín que vende en la plataforma; cada uno con su dueño, productos y cobros.';

-- Lo que ya existe es del primer cafetín.
INSERT INTO cafeteria (id_cafeteria, nombre, ubicacion)
VALUES (1, 'Cafetín principal', 'Campus UCV');
SELECT setval(pg_get_serial_sequence('cafeteria', 'id_cafeteria'), 1);

ALTER TABLE categoria  ADD COLUMN id_cafeteria INTEGER;
ALTER TABLE producto   ADD COLUMN id_cafeteria INTEGER;
ALTER TABLE proveedor  ADD COLUMN id_cafeteria INTEGER;
ALTER TABLE pedido     ADD COLUMN id_cafeteria INTEGER;
ALTER TABLE trabajador ADD COLUMN id_cafeteria INTEGER;
ALTER TABLE dueno      ADD COLUMN id_cafeteria INTEGER;

UPDATE categoria  SET id_cafeteria = 1;
UPDATE producto   SET id_cafeteria = 1;
UPDATE proveedor  SET id_cafeteria = 1;
UPDATE pedido     SET id_cafeteria = 1;
UPDATE trabajador SET id_cafeteria = 1;
UPDATE dueno      SET id_cafeteria = 1;

ALTER TABLE categoria  ALTER COLUMN id_cafeteria SET NOT NULL,
    ADD CONSTRAINT fk_categoria_cafeteria  FOREIGN KEY (id_cafeteria) REFERENCES cafeteria (id_cafeteria) ON DELETE RESTRICT;
ALTER TABLE producto   ALTER COLUMN id_cafeteria SET NOT NULL,
    ADD CONSTRAINT fk_producto_cafeteria   FOREIGN KEY (id_cafeteria) REFERENCES cafeteria (id_cafeteria) ON DELETE RESTRICT;
ALTER TABLE proveedor  ALTER COLUMN id_cafeteria SET NOT NULL,
    ADD CONSTRAINT fk_proveedor_cafeteria  FOREIGN KEY (id_cafeteria) REFERENCES cafeteria (id_cafeteria) ON DELETE RESTRICT;
ALTER TABLE pedido     ALTER COLUMN id_cafeteria SET NOT NULL,
    ADD CONSTRAINT fk_pedido_cafeteria     FOREIGN KEY (id_cafeteria) REFERENCES cafeteria (id_cafeteria) ON DELETE RESTRICT;
ALTER TABLE trabajador ALTER COLUMN id_cafeteria SET NOT NULL,
    ADD CONSTRAINT fk_trabajador_cafeteria FOREIGN KEY (id_cafeteria) REFERENCES cafeteria (id_cafeteria) ON DELETE RESTRICT;
ALTER TABLE dueno      ALTER COLUMN id_cafeteria SET NOT NULL,
    ADD CONSTRAINT fk_dueno_cafeteria      FOREIGN KEY (id_cafeteria) REFERENCES cafeteria (id_cafeteria) ON DELETE RESTRICT;

-- Nombres únicos por cafetería: dos cafetines pueden vender "Café pasado".
ALTER TABLE categoria DROP CONSTRAINT uq_categoria_nombre,
    ADD CONSTRAINT uq_categoria_nombre UNIQUE (id_cafeteria, nombre);
ALTER TABLE producto  DROP CONSTRAINT uq_producto_nombre,
    ADD CONSTRAINT uq_producto_nombre  UNIQUE (id_cafeteria, nombre);
ALTER TABLE proveedor DROP CONSTRAINT uq_proveedor_nombre,
    ADD CONSTRAINT uq_proveedor_nombre UNIQUE (id_cafeteria, nombre);

CREATE INDEX idx_categoria_cafeteria  ON categoria (id_cafeteria);
CREATE INDEX idx_producto_cafeteria   ON producto (id_cafeteria);
CREATE INDEX idx_proveedor_cafeteria  ON proveedor (id_cafeteria);
CREATE INDEX idx_pedido_cafeteria     ON pedido (id_cafeteria, estado);
CREATE INDEX idx_trabajador_cafeteria ON trabajador (id_cafeteria);
CREATE INDEX idx_dueno_cafeteria      ON dueno (id_cafeteria);

-- ----------------------------------------------------------------------------
-- Un pedido solo lleva productos de su cafetería, y un producto solo usa
-- categorías y proveedores de la suya.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_producto_misma_cafeteria()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT id_cafeteria FROM categoria WHERE id_categoria = NEW.id_categoria) <> NEW.id_cafeteria
       OR (NEW.id_proveedor IS NOT NULL
           AND (SELECT id_cafeteria FROM proveedor WHERE id_proveedor = NEW.id_proveedor) <> NEW.id_cafeteria) THEN
        RAISE EXCEPTION 'La categoría o el proveedor del producto % son de otra cafetería', NEW.nombre
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_producto_misma_cafeteria
    BEFORE INSERT OR UPDATE OF id_categoria, id_proveedor, id_cafeteria ON producto
    FOR EACH ROW
    EXECUTE FUNCTION fn_producto_misma_cafeteria();

CREATE OR REPLACE FUNCTION fn_detalle_misma_cafeteria()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT id_cafeteria FROM producto WHERE id_producto = NEW.id_producto)
       IS DISTINCT FROM (SELECT id_cafeteria FROM pedido WHERE id_pedido = NEW.id_pedido) THEN
        RAISE EXCEPTION 'El producto % no es de la cafetería del pedido %', NEW.id_producto, NEW.id_pedido
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_detalle_misma_cafeteria
    BEFORE INSERT OR UPDATE OF id_producto, id_pedido ON detalle_pedido
    FOR EACH ROW
    EXECUTE FUNCTION fn_detalle_misma_cafeteria();

-- ----------------------------------------------------------------------------
-- RF-17: la alerta va a los dueños de la cafetería del producto (antes, a todos).
-- El resto de la función es igual a V1.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_alerta_stock_minimo()
RETURNS TRIGGER AS $$
DECLARE
    v_nombre     VARCHAR(100);
    v_cafeteria  INTEGER;
    v_dueno      INTEGER;
    v_notif      INTEGER;
    v_tipo       tipo_notif_enum;
    v_mensaje    VARCHAR(250);
BEGIN
    IF NEW.cantidad_disponible = 0 AND OLD.cantidad_disponible > 0 THEN
        v_tipo := 'STOCK_AGOTADO';
    ELSIF NEW.cantidad_disponible > 0
          AND NEW.cantidad_disponible <= NEW.stock_minimo
          AND OLD.cantidad_disponible >  NEW.stock_minimo THEN
        v_tipo := 'STOCK_BAJO';
    END IF;

    IF v_tipo IS NOT NULL THEN
        SELECT nombre, id_cafeteria INTO v_nombre, v_cafeteria
        FROM producto WHERE id_producto = NEW.id_producto;

        IF v_tipo = 'STOCK_AGOTADO' THEN
            v_mensaje := 'El producto "' || v_nombre || '" se agotó. Recarga el stock.';
        ELSE
            v_mensaje := 'Quedan ' || NEW.cantidad_disponible || ' unidades de "' || v_nombre
                         || '" (mínimo ' || NEW.stock_minimo || '). Contacta a tu proveedor.';
        END IF;

        FOR v_dueno IN SELECT id_usuario FROM dueno WHERE id_cafeteria = v_cafeteria LOOP
            INSERT INTO notificacion (id_usuario, tipo, mensaje)
            VALUES (v_dueno, v_tipo, v_mensaje)
            RETURNING id_notificacion INTO v_notif;

            INSERT INTO alerta_stock (id_notificacion, id_inventario, nivel_detectado)
            VALUES (v_notif, NEW.id_inventario, NEW.cantidad_disponible);
        END LOOP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ----------------------------------------------------------------------------
-- Vistas: mismas columnas que en V1 y, al final, id_cafeteria.
-- v_ingresos_diarios y v_productos_mas_vendidos agrupan además por cafetería.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_catalogo_disponible AS
SELECT  p.id_producto,
        p.nombre,
        p.descripcion,
        p.precio,
        c.nombre                AS categoria,
        i.cantidad_disponible,
        CASE
            WHEN i.cantidad_disponible = 0                  THEN 'AGOTADO'
            WHEN i.cantidad_disponible <= i.stock_minimo    THEN 'POCAS UNIDADES'
            ELSE 'DISPONIBLE'
        END                     AS estado_stock,
        p.id_cafeteria
FROM producto p
JOIN categoria  c ON c.id_categoria = p.id_categoria
JOIN inventario i ON i.id_producto  = p.id_producto
WHERE p.activo = TRUE;

CREATE OR REPLACE VIEW v_pedidos_en_curso AS
SELECT  pe.id_pedido,
        pe.codigo_pedido,
        pe.estado,
        pe.hora_recojo,
        pe.total,
        u.nombre || ' ' || u.apellidos  AS estudiante,
        pg.estado                       AS estado_pago,
        pg.metodo_pago,
        r.codigo_verificacion,
        pe.id_cafeteria
FROM pedido pe
JOIN estudiante e ON e.id_usuario = pe.id_estudiante
JOIN usuario    u ON u.id_usuario = e.id_usuario
LEFT JOIN pago  pg ON pg.id_pedido = pe.id_pedido
LEFT JOIN recibo r ON r.id_pago    = pg.id_pago
WHERE pe.estado IN ('PENDIENTE', 'CONFIRMADO', 'EN_PREPARACION', 'LISTO');

-- Cambia el GROUP BY: se recrea en vez de CREATE OR REPLACE.
DROP VIEW v_ingresos_diarios;
CREATE VIEW v_ingresos_diarios AS
SELECT  DATE(pg.fecha)      AS dia,
        COUNT(*)            AS pedidos_pagados,
        SUM(pg.monto)       AS ingreso_total,
        ROUND(AVG(pg.monto), 2) AS ticket_promedio,
        pe.id_cafeteria
FROM pago pg
JOIN pedido pe ON pe.id_pedido = pg.id_pedido
WHERE pg.estado = 'PAGADO'
GROUP BY DATE(pg.fecha), pe.id_cafeteria;

DROP VIEW v_productos_mas_vendidos;
CREATE VIEW v_productos_mas_vendidos AS
SELECT  p.id_producto,
        p.nombre,
        c.nombre                    AS categoria,
        SUM(d.cantidad)             AS unidades_vendidas,
        SUM(d.subtotal)             AS ingreso_generado,
        p.id_cafeteria
FROM detalle_pedido d
JOIN producto  p ON p.id_producto  = d.id_producto
JOIN categoria c ON c.id_categoria = p.id_categoria
JOIN pedido   pe ON pe.id_pedido   = d.id_pedido
JOIN pago     pg ON pg.id_pedido   = pe.id_pedido
WHERE pg.estado = 'PAGADO'
GROUP BY p.id_producto, p.nombre, c.nombre, p.id_cafeteria;

CREATE OR REPLACE VIEW v_alertas_reposicion AS
SELECT  p.id_producto,
        p.nombre                AS producto,
        i.cantidad_disponible,
        i.stock_minimo,
        pr.nombre               AS proveedor,
        pr.telefono             AS telefono_proveedor,
        i.fecha_actualizacion,
        p.id_cafeteria
FROM inventario i
JOIN producto   p  ON p.id_producto  = i.id_producto
LEFT JOIN proveedor pr ON pr.id_proveedor = p.id_proveedor
WHERE i.cantidad_disponible <= i.stock_minimo;
