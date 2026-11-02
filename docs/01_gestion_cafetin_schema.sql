-- ============================================================================
-- PROYECTO      : Gestión Cafetín
--                 Aplicación para la gestión de pedidos y stock de las
--                 cafeterías de la Universidad César Vallejo
-- CURSO         : Ingeniería de Software
-- ARCHIVO       : 01_gestion_cafetin_schema.sql
-- DESCRIPCIÓN   : Modelo físico de la base de datos (PostgreSQL 16)
-- ============================================================================
-- El modelo deriva del diagrama de clases: 14 clases -> 14 tablas.
-- La herencia se implementa con especialización (tabla por subtipo):
--   usuario      -> estudiante / trabajador / dueno
--   notificacion -> alerta_stock
-- ============================================================================

DROP SCHEMA IF EXISTS cafetin CASCADE;
CREATE SCHEMA cafetin;
SET search_path TO cafetin;

-- ============================================================================
-- 1. DOMINIOS Y TIPOS ENUMERADOS
-- ============================================================================

CREATE TYPE tipo_usuario_enum   AS ENUM ('ESTUDIANTE', 'TRABAJADOR', 'DUENO');
CREATE TYPE turno_enum          AS ENUM ('MANANA', 'TARDE', 'NOCHE');
CREATE TYPE estado_pedido_enum  AS ENUM ('PENDIENTE', 'CONFIRMADO', 'EN_PREPARACION',
                                         'LISTO', 'ENTREGADO', 'CANCELADO');
CREATE TYPE metodo_pago_enum    AS ENUM ('YAPE', 'PLIN', 'TARJETA', 'EFECTIVO');
CREATE TYPE estado_pago_enum    AS ENUM ('PENDIENTE', 'PAGADO', 'FALLIDO', 'REEMBOLSADO');
CREATE TYPE tipo_notif_enum     AS ENUM ('PEDIDO_CONFIRMADO', 'PEDIDO_LISTO',
                                         'PEDIDO_ENTREGADO', 'PEDIDO_CANCELADO',
                                         'STOCK_BAJO', 'STOCK_AGOTADO');

-- ============================================================================
-- 2. PAQUETE 1 — GESTIÓN DE CUENTAS Y ACCESO   (RF-01, RF-02, RF-03)
-- ============================================================================

-- Tabla base de la jerarquía de usuarios (clase abstracta Usuario)
CREATE TABLE usuario (
    id_usuario      SERIAL          PRIMARY KEY,
    nombre          VARCHAR(60)     NOT NULL,
    apellidos       VARCHAR(80)     NOT NULL,
    correo          VARCHAR(120)    NOT NULL,
    contrasena      VARCHAR(255)    NOT NULL,   -- se almacena el hash, nunca texto plano (RNF-04)
    telefono        VARCHAR(15),
    tipo_usuario    tipo_usuario_enum NOT NULL, -- discriminador de la especialización
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    fecha_registro  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    token_recupera  VARCHAR(255),               -- RF-03: restablecer contraseña
    token_expira    TIMESTAMP,

    CONSTRAINT uq_usuario_correo    UNIQUE (correo),
    CONSTRAINT ck_usuario_correo    CHECK (correo LIKE '%@%.%'),
    CONSTRAINT ck_usuario_nombre    CHECK (LENGTH(TRIM(nombre)) >= 2)
);

COMMENT ON TABLE  usuario              IS 'Clase abstracta Usuario. Tabla base de la especialización.';
COMMENT ON COLUMN usuario.contrasena   IS 'Hash BCrypt de la contraseña (RNF-04).';
COMMENT ON COLUMN usuario.tipo_usuario IS 'Discriminador: indica en qué tabla hija está el registro.';

-- Subtipo: Estudiante (RF-01)
CREATE TABLE estudiante (
    id_usuario      INTEGER         PRIMARY KEY,
    codigo          VARCHAR(15)     NOT NULL,
    carrera         VARCHAR(80)     NOT NULL,
    ciclo           SMALLINT        NOT NULL,

    CONSTRAINT fk_estudiante_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario) ON DELETE CASCADE,
    CONSTRAINT uq_estudiante_codigo  UNIQUE (codigo),
    CONSTRAINT ck_estudiante_ciclo   CHECK (ciclo BETWEEN 1 AND 12)
);

-- Subtipo: Trabajador (RF-02)
CREATE TABLE trabajador (
    id_usuario      INTEGER         PRIMARY KEY,
    codigo_empleado VARCHAR(15)     NOT NULL,
    turno           turno_enum      NOT NULL,
    fecha_ingreso   DATE            NOT NULL DEFAULT CURRENT_DATE,

    CONSTRAINT fk_trabajador_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario) ON DELETE CASCADE,
    CONSTRAINT uq_trabajador_codigo  UNIQUE (codigo_empleado)
);

-- Subtipo: Dueño (RF-02)
CREATE TABLE dueno (
    id_usuario      INTEGER         PRIMARY KEY,
    cargo           VARCHAR(60)     NOT NULL DEFAULT 'Propietario',
    ruc             VARCHAR(11),

    CONSTRAINT fk_dueno_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario) ON DELETE CASCADE,
    CONSTRAINT ck_dueno_ruc     CHECK (ruc IS NULL OR ruc ~ '^[0-9]{11}$')
);

-- ============================================================================
-- 3. PAQUETE 4 — PRODUCTOS E INVENTARIO   (RF-15 .. RF-19)
-- ============================================================================

CREATE TABLE categoria (
    id_categoria    SERIAL          PRIMARY KEY,
    nombre          VARCHAR(50)     NOT NULL,
    descripcion     VARCHAR(200),
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_categoria_nombre UNIQUE (nombre)
);

CREATE TABLE proveedor (
    id_proveedor    SERIAL          PRIMARY KEY,
    nombre          VARCHAR(100)    NOT NULL,
    telefono        VARCHAR(15)     NOT NULL,
    correo          VARCHAR(120),
    direccion       VARCHAR(200),
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_proveedor_nombre UNIQUE (nombre)
);

-- Producto (RF-15, RF-19)
CREATE TABLE producto (
    id_producto     SERIAL          PRIMARY KEY,
    id_categoria    INTEGER         NOT NULL,
    id_proveedor    INTEGER,                    -- opcional: productos de preparación propia
    nombre          VARCHAR(100)    NOT NULL,
    descripcion     VARCHAR(250),
    precio          NUMERIC(8,2)    NOT NULL,
    imagen_url      VARCHAR(300),
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    fecha_registro  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_producto_categoria FOREIGN KEY (id_categoria)
        REFERENCES categoria (id_categoria) ON DELETE RESTRICT,
    CONSTRAINT fk_producto_proveedor FOREIGN KEY (id_proveedor)
        REFERENCES proveedor (id_proveedor) ON DELETE SET NULL,
    CONSTRAINT uq_producto_nombre    UNIQUE (nombre),
    CONSTRAINT ck_producto_precio    CHECK (precio > 0)
);

COMMENT ON COLUMN producto.activo IS 'RF-19: deshabilitar un producto lo oculta del catálogo sin borrarlo.';

-- Inventario: composición 1 a 1 con Producto (RF-16, RF-17, RF-18)
CREATE TABLE inventario (
    id_inventario       SERIAL      PRIMARY KEY,
    id_producto         INTEGER     NOT NULL,
    cantidad_disponible INTEGER     NOT NULL DEFAULT 0,
    stock_minimo        INTEGER     NOT NULL DEFAULT 5,
    fecha_actualizacion TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inventario_producto FOREIGN KEY (id_producto)
        REFERENCES producto (id_producto) ON DELETE CASCADE,
    CONSTRAINT uq_inventario_producto UNIQUE (id_producto),  -- impone la cardinalidad 1:1
    CONSTRAINT ck_inventario_cantidad CHECK (cantidad_disponible >= 0),
    CONSTRAINT ck_inventario_minimo   CHECK (stock_minimo >= 0)
);

COMMENT ON CONSTRAINT uq_inventario_producto ON inventario
    IS 'Garantiza la relación 1:1 del diagrama de clases (Producto controla Inventario).';
COMMENT ON CONSTRAINT ck_inventario_cantidad ON inventario
    IS 'RF-16: impide que el stock quede negativo al descontar una venta.';

-- ============================================================================
-- 4. PAQUETE 2 Y 3 — PEDIDOS, PAGOS Y DESPACHO   (RF-04 .. RF-14)
-- ============================================================================

CREATE TABLE pedido (
    id_pedido       SERIAL              PRIMARY KEY,
    codigo_pedido   VARCHAR(12)         NOT NULL,    -- código visible: PED-0427
    id_estudiante   INTEGER             NOT NULL,    -- RF-05: quién lo realiza
    id_trabajador   INTEGER,                         -- RF-11: quién lo atiende (nulo hasta confirmar)
    fecha_hora      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hora_recojo     TIME                NOT NULL,    -- franja elegida por el estudiante
    estado          estado_pedido_enum  NOT NULL DEFAULT 'PENDIENTE',
    total           NUMERIC(10,2)       NOT NULL DEFAULT 0,
    observacion     VARCHAR(250),

    CONSTRAINT fk_pedido_estudiante FOREIGN KEY (id_estudiante)
        REFERENCES estudiante (id_usuario) ON DELETE RESTRICT,
    CONSTRAINT fk_pedido_trabajador FOREIGN KEY (id_trabajador)
        REFERENCES trabajador (id_usuario) ON DELETE SET NULL,
    CONSTRAINT uq_pedido_codigo     UNIQUE (codigo_pedido),
    CONSTRAINT ck_pedido_total      CHECK (total >= 0)
);

COMMENT ON COLUMN pedido.estado IS 'RF-08, RF-11, RF-12, RF-14: ciclo de vida del pedido.';

-- DetallePedido: composición con Pedido (RF-05)
CREATE TABLE detalle_pedido (
    id_detalle      SERIAL          PRIMARY KEY,
    id_pedido       INTEGER         NOT NULL,
    id_producto     INTEGER         NOT NULL,
    cantidad        SMALLINT        NOT NULL,
    precio_unitario NUMERIC(8,2)    NOT NULL,       -- precio histórico, no el actual del producto
    subtotal        NUMERIC(10,2)   GENERATED ALWAYS AS (cantidad * precio_unitario) STORED,

    CONSTRAINT fk_detalle_pedido   FOREIGN KEY (id_pedido)
        REFERENCES pedido (id_pedido) ON DELETE CASCADE,   -- composición: se borra con el pedido
    CONSTRAINT fk_detalle_producto FOREIGN KEY (id_producto)
        REFERENCES producto (id_producto) ON DELETE RESTRICT,
    CONSTRAINT uq_detalle_producto UNIQUE (id_pedido, id_producto),
    CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_detalle_precio   CHECK (precio_unitario > 0)
);

COMMENT ON COLUMN detalle_pedido.precio_unitario
    IS 'Se copia el precio al momento de la compra: si el producto sube de precio, el pedido histórico no cambia.';

-- Pago: relación 1 a 1 con Pedido (RF-06, RF-20)
CREATE TABLE pago (
    id_pago         SERIAL              PRIMARY KEY,
    id_pedido       INTEGER             NOT NULL,
    monto           NUMERIC(10,2)       NOT NULL,
    metodo_pago     metodo_pago_enum    NOT NULL,
    estado          estado_pago_enum    NOT NULL DEFAULT 'PENDIENTE',
    fecha           TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    referencia      VARCHAR(60),        -- código de operación de la pasarela

    CONSTRAINT fk_pago_pedido FOREIGN KEY (id_pedido)
        REFERENCES pedido (id_pedido) ON DELETE CASCADE,
    CONSTRAINT uq_pago_pedido UNIQUE (id_pedido),   -- impone la cardinalidad 1:1
    CONSTRAINT ck_pago_monto  CHECK (monto > 0)
);

-- Recibo: relación 1 a 1 con Pago (RF-07, RF-13)
CREATE TABLE recibo (
    id_recibo           SERIAL          PRIMARY KEY,
    id_pago             INTEGER         NOT NULL,
    codigo_verificacion VARCHAR(20)     NOT NULL,   -- lo que el trabajador verifica al entregar
    fecha_emision       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verificado          BOOLEAN         NOT NULL DEFAULT FALSE,
    fecha_verificacion  TIMESTAMP,

    CONSTRAINT fk_recibo_pago  FOREIGN KEY (id_pago)
        REFERENCES pago (id_pago) ON DELETE CASCADE,
    CONSTRAINT uq_recibo_pago  UNIQUE (id_pago),
    CONSTRAINT uq_recibo_codigo UNIQUE (codigo_verificacion)
);

COMMENT ON COLUMN recibo.codigo_verificacion
    IS 'RF-13: el trabajador lo contrasta con el que muestra el estudiante.';

-- ============================================================================
-- 5. NOTIFICACIONES Y ALERTAS   (RF-08, RF-17)
-- ============================================================================

CREATE TABLE notificacion (
    id_notificacion SERIAL          PRIMARY KEY,
    id_usuario      INTEGER         NOT NULL,       -- destinatario
    id_pedido       INTEGER,                        -- nulo en alertas de stock
    tipo            tipo_notif_enum NOT NULL,
    mensaje         VARCHAR(250)    NOT NULL,
    fecha           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    leida           BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_notificacion_usuario FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario) ON DELETE CASCADE,
    CONSTRAINT fk_notificacion_pedido  FOREIGN KEY (id_pedido)
        REFERENCES pedido (id_pedido) ON DELETE CASCADE
);

-- AlertaStock: subtipo de Notificacion (RF-17)
CREATE TABLE alerta_stock (
    id_notificacion INTEGER     PRIMARY KEY,
    id_inventario   INTEGER     NOT NULL,
    nivel_detectado INTEGER     NOT NULL,
    atendida        BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_alerta_notificacion FOREIGN KEY (id_notificacion)
        REFERENCES notificacion (id_notificacion) ON DELETE CASCADE,
    CONSTRAINT fk_alerta_inventario   FOREIGN KEY (id_inventario)
        REFERENCES inventario (id_inventario) ON DELETE CASCADE,
    CONSTRAINT ck_alerta_nivel        CHECK (nivel_detectado >= 0)
);

COMMENT ON TABLE alerta_stock
    IS 'Especialización de Notificacion: hereda mensaje y fecha, agrega el nivel detectado.';

-- ============================================================================
-- 6. ÍNDICES   (RNF-02, RNF-03: consultas rápidas en hora punta)
-- ============================================================================

CREATE INDEX idx_usuario_correo       ON usuario (correo);
CREATE INDEX idx_producto_categoria   ON producto (id_categoria);
CREATE INDEX idx_producto_activo      ON producto (activo) WHERE activo = TRUE;
CREATE INDEX idx_pedido_estudiante    ON pedido (id_estudiante);
CREATE INDEX idx_pedido_estado        ON pedido (estado);
CREATE INDEX idx_pedido_fecha         ON pedido (fecha_hora DESC);
CREATE INDEX idx_detalle_pedido       ON detalle_pedido (id_pedido);
CREATE INDEX idx_detalle_producto     ON detalle_pedido (id_producto);
CREATE INDEX idx_pago_fecha           ON pago (fecha DESC);
CREATE INDEX idx_notif_usuario_leida  ON notificacion (id_usuario, leida);

-- ============================================================================
-- 7. REGLAS DE NEGOCIO AUTOMÁTICAS
-- ============================================================================

-- RF-16: al confirmarse el pago, descontar el stock de cada producto del pedido
CREATE OR REPLACE FUNCTION fn_descontar_stock()
RETURNS TRIGGER AS $$
DECLARE
    v_detalle RECORD;
BEGIN
    IF NEW.estado = 'PAGADO' AND (OLD.estado IS DISTINCT FROM 'PAGADO') THEN
        FOR v_detalle IN
            SELECT id_producto, cantidad
            FROM detalle_pedido
            WHERE id_pedido = NEW.id_pedido
        LOOP
            UPDATE inventario
            SET cantidad_disponible = cantidad_disponible - v_detalle.cantidad,
                fecha_actualizacion = CURRENT_TIMESTAMP
            WHERE id_producto = v_detalle.id_producto;
        END LOOP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_descontar_stock
    AFTER UPDATE ON pago
    FOR EACH ROW
    EXECUTE FUNCTION fn_descontar_stock();

-- RF-17: generar alerta cuando el stock llega al mínimo, y RF-19: ocultar si se agota
CREATE OR REPLACE FUNCTION fn_alerta_stock_minimo()
RETURNS TRIGGER AS $$
DECLARE
    v_nombre   VARCHAR(100);
    v_dueno    INTEGER;
    v_notif    INTEGER;
    v_tipo     tipo_notif_enum;
    v_mensaje  VARCHAR(250);
BEGIN
    IF NEW.cantidad_disponible <= NEW.stock_minimo
       AND NEW.cantidad_disponible < OLD.cantidad_disponible THEN

        SELECT nombre INTO v_nombre FROM producto WHERE id_producto = NEW.id_producto;

        IF NEW.cantidad_disponible = 0 THEN
            v_tipo    := 'STOCK_AGOTADO';
            v_mensaje := 'El producto "' || v_nombre || '" se agotó y se ocultó del catálogo.';
            UPDATE producto SET activo = FALSE WHERE id_producto = NEW.id_producto;
        ELSE
            v_tipo    := 'STOCK_BAJO';
            v_mensaje := 'Quedan ' || NEW.cantidad_disponible || ' unidades de "' || v_nombre
                         || '" (mínimo ' || NEW.stock_minimo || '). Contacta a tu proveedor.';
        END IF;

        -- Se notifica a todos los dueños registrados
        FOR v_dueno IN SELECT id_usuario FROM dueno LOOP
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

CREATE TRIGGER trg_alerta_stock_minimo
    AFTER UPDATE ON inventario
    FOR EACH ROW
    EXECUTE FUNCTION fn_alerta_stock_minimo();

-- RF-05: recalcular el total del pedido cada vez que cambia su detalle
CREATE OR REPLACE FUNCTION fn_calcular_total_pedido()
RETURNS TRIGGER AS $$
DECLARE
    v_pedido INTEGER;
BEGIN
    v_pedido := COALESCE(NEW.id_pedido, OLD.id_pedido);

    UPDATE pedido
    SET total = COALESCE((SELECT SUM(subtotal) FROM detalle_pedido WHERE id_pedido = v_pedido), 0)
    WHERE id_pedido = v_pedido;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_calcular_total_pedido
    AFTER INSERT OR UPDATE OR DELETE ON detalle_pedido
    FOR EACH ROW
    EXECUTE FUNCTION fn_calcular_total_pedido();

-- ============================================================================
-- 8. VISTAS DE CONSULTA   (RF-04, RF-10, RF-21, RF-22)
-- ============================================================================

-- RF-04: catálogo que ve el estudiante, con disponibilidad en tiempo real
CREATE VIEW v_catalogo_disponible AS
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
        END                     AS estado_stock
FROM producto p
JOIN categoria  c ON c.id_categoria = p.id_categoria
JOIN inventario i ON i.id_producto  = p.id_producto
WHERE p.activo = TRUE;

-- RF-10: pedidos que ve el trabajador en el panel de despacho
CREATE VIEW v_pedidos_en_curso AS
SELECT  pe.id_pedido,
        pe.codigo_pedido,
        pe.estado,
        pe.hora_recojo,
        pe.total,
        u.nombre || ' ' || u.apellidos  AS estudiante,
        pg.estado                       AS estado_pago,
        pg.metodo_pago,
        r.codigo_verificacion
FROM pedido pe
JOIN estudiante e ON e.id_usuario = pe.id_estudiante
JOIN usuario    u ON u.id_usuario = e.id_usuario
LEFT JOIN pago  pg ON pg.id_pedido = pe.id_pedido
LEFT JOIN recibo r ON r.id_pago    = pg.id_pago
WHERE pe.estado IN ('PENDIENTE', 'CONFIRMADO', 'EN_PREPARACION', 'LISTO');

-- RF-21: ingresos agrupados por día
CREATE VIEW v_ingresos_diarios AS
SELECT  DATE(pg.fecha)      AS dia,
        COUNT(*)            AS pedidos_pagados,
        SUM(pg.monto)       AS ingreso_total,
        ROUND(AVG(pg.monto), 2) AS ticket_promedio
FROM pago pg
WHERE pg.estado = 'PAGADO'
GROUP BY DATE(pg.fecha);

-- RF-22: ranking de productos más vendidos
CREATE VIEW v_productos_mas_vendidos AS
SELECT  p.id_producto,
        p.nombre,
        c.nombre                    AS categoria,
        SUM(d.cantidad)             AS unidades_vendidas,
        SUM(d.subtotal)             AS ingreso_generado
FROM detalle_pedido d
JOIN producto  p ON p.id_producto  = d.id_producto
JOIN categoria c ON c.id_categoria = p.id_categoria
JOIN pedido   pe ON pe.id_pedido   = d.id_pedido
JOIN pago     pg ON pg.id_pedido   = pe.id_pedido
WHERE pg.estado = 'PAGADO'
GROUP BY p.id_producto, p.nombre, c.nombre;

-- RF-17: tablero de reposición para el dueño
CREATE VIEW v_alertas_reposicion AS
SELECT  p.id_producto,
        p.nombre                AS producto,
        i.cantidad_disponible,
        i.stock_minimo,
        pr.nombre               AS proveedor,
        pr.telefono             AS telefono_proveedor,
        i.fecha_actualizacion
FROM inventario i
JOIN producto   p  ON p.id_producto  = i.id_producto
LEFT JOIN proveedor pr ON pr.id_proveedor = p.id_proveedor
WHERE i.cantidad_disponible <= i.stock_minimo;

-- ============================================================================
-- FIN DEL ESQUEMA
-- ============================================================================
