-- ============================================================================
-- PROYECTO    : Gestión Cafetín — UCV
-- ARCHIVO     : V2__datos_prueba.sql  (migración Flyway, SOLO perfil dev)
-- ORIGEN      : docs/02_gestion_cafetin_datos.sql
-- DESCRIPCIÓN : Datos de prueba para desarrollo y demo
-- ============================================================================
-- Vive en db/datos-dev/, una ubicación que solo carga el perfil `dev`
-- (spring.flyway.locations). En test y producción no se aplica.
--
-- Diferencias con docs/02_gestion_cafetin_datos.sql:
--   1. Sin SET search_path (lo fija Flyway).
--   2. Contraseñas con hash BCrypt real. Contraseña de todos: Cafetin2026
--   3. Los pagos se insertan PENDIENTE y se actualizan a PAGADO, igual que hará
--      la API: así trg_descontar_stock descuenta el stock y los reportes
--      (v_ingresos_diarios, v_productos_mas_vendidos) tienen datos.
--   4. pago.monto se toma de pedido.total (calculado por el trigger).
--   5. Pedidos en varias fechas (15, 8 y 3 días atrás, y hoy) para los reportes
--      por día, semana y mes.
--   6. El pedido sin pagar (PED-0427) no tiene fila en pago.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- USUARIOS (RF-01, RF-02) — contraseña de todos: Cafetin2026
-- ----------------------------------------------------------------------------

INSERT INTO usuario (nombre, apellidos, correo, contrasena, telefono, tipo_usuario) VALUES
    ('Rosa',   'Quispe Flores',    'rosa.quispe@cafetin.pe',      '$2a$10$4jO2.9w4G1Qx0txWOuyhuuwZDBqr8VJZbbpuzaXm1tgyiF2R0wdpG', '987654321', 'DUENO'),
    ('Carmen', 'Díaz Morales',     'carmen.diaz@cafetin.pe',      '$2a$10$deuvdzXNeMeCULu57/A1zOtFS8XOMbCy61NQBOPGaD9B9Ya9u.uZq', '912345678', 'TRABAJADOR'),
    ('Luis',   'Ramos Chávez',     'luis.ramos@cafetin.pe',       '$2a$10$VYMO/9ufKQvR61ErU6TimuXAN09R84EOs1ien6QBdIfTqR/um9uR6', '913456789', 'TRABAJADOR'),
    ('Eliú',   'Barrera Martínez', 'ebarreram@ucvvirtual.edu.pe', '$2a$10$EehfCiUbwIxSAq2a0spC0OgGrFq6POKNyTbLk7R/QSYWQKpd7NdoC', '914567890', 'ESTUDIANTE'),
    ('María',  'Quiroz Vásquez',   'mquirozv@ucvvirtual.edu.pe',  '$2a$10$NyxqYQp3w4RvpafHVoY4i.2uvM/t/99Ic6AYIyLKixEIZEWzFwW9e', '915678901', 'ESTUDIANTE'),
    ('Diego',  'León Paredes',     'dleonp@ucvvirtual.edu.pe',    '$2a$10$AAzCTi5JFEoXFZvV7p5ip.n/jICZ7jCZfZjBOePu5/t1pMh3qnxBa', '916789012', 'ESTUDIANTE');

INSERT INTO dueno (id_usuario, cargo, ruc) VALUES
    (1, 'Propietaria', '10456789012');

INSERT INTO trabajador (id_usuario, codigo_empleado, turno) VALUES
    (2, 'TRB-001', 'MANANA'),
    (3, 'TRB-002', 'TARDE');

INSERT INTO estudiante (id_usuario, codigo, carrera, ciclo) VALUES
    (4, '2021045678', 'Ingeniería de Sistemas', 6),
    (5, '2022031245', 'Administración',         4),
    (6, '2020087654', 'Ingeniería Civil',       8);

-- ----------------------------------------------------------------------------
-- CATEGORÍAS Y PROVEEDORES
-- ----------------------------------------------------------------------------

INSERT INTO categoria (nombre, descripcion) VALUES
    ('Desayunos', 'Tamales, sándwiches y preparados de la mañana'),
    ('Bebidas',   'Refrescos, jugos naturales e infusiones'),
    ('Snacks',    'Galletas, frituras y productos envasados'),
    ('Menú',      'Almuerzo completo del día');

INSERT INTO proveedor (nombre, telefono, correo, direccion) VALUES
    ('Distribuidora Nor-Sur',  '044123456', 'ventas@norsur.pe',    'Av. España 1234, Trujillo'),
    ('Panadería San Martín',   '044234567', 'pedidos@sanmartin.pe','Jr. Bolívar 567, Trujillo'),
    ('Snacks del Norte SAC',   '044345678', 'contacto@snorte.pe',  'Av. América 890, Trujillo');

-- ----------------------------------------------------------------------------
-- PRODUCTOS E INVENTARIO (RF-15)
-- ----------------------------------------------------------------------------

INSERT INTO producto (id_categoria, id_proveedor, nombre, descripcion, precio) VALUES
    (4, NULL, 'Menú del día',          'Arroz con pollo, sopa y refresco',     8.00),
    (1, 2,    'Tamal criollo',         'Con salsa criolla y pan',              4.00),
    (1, 2,    'Pan con chicharrón',    'Con camote frito y sarsa',             5.50),
    (1, 2,    'Empanada de ají',       'Empanada de ají de gallina',           3.50),
    (2, 1,    'Chicha morada 400 ml',  'Preparada en casa',                    2.00),
    (2, 1,    'Jugo de piña 500 ml',   'Natural, sin azúcar añadida',          4.50),
    (2, NULL, 'Café pasado',           'Café de Chanchamayo',                  2.50),
    (3, 3,    'Galletas de avena',     'Paquete x 6 unidades',                 2.50),
    (3, 3,    'Papas fritas 40 g',     'Bolsa individual',                     2.00);

-- Stock ANTES de las ventas del seed; los pagos de abajo lo descuentan.
INSERT INTO inventario (id_producto, cantidad_disponible, stock_minimo) VALUES
    (1, 40, 10),   -- Menú del día
    (2,  6,  6),   -- Tamal criollo      -> queda en 4, bajo el mínimo
    (3, 12,  5),   -- Pan con chicharrón
    (4,  2,  8),   -- Empanada de ají    -> por debajo del mínimo
    (5, 20,  8),   -- Chicha morada
    (6,  3,  5),   -- Jugo de piña       -> por debajo del mínimo
    (7, 41, 12),   -- Café pasado
    (8, 24, 10),   -- Galletas de avena
    (9, 30, 10);   -- Papas fritas

-- ----------------------------------------------------------------------------
-- PEDIDOS (RF-05 .. RF-14)
-- El total lo calcula trg_calcular_total_pedido; el monto del pago se copia de él.
-- Cada pago entra PENDIENTE y pasa a PAGADO con UPDATE (dispara trg_descontar_stock).
-- ----------------------------------------------------------------------------

-- Pedido 1: hace 8 días, pagado y entregado
INSERT INTO pedido (codigo_pedido, id_estudiante, id_trabajador, fecha_hora, hora_recojo, estado)
VALUES ('PED-0425', 4, 2, CURRENT_TIMESTAMP - INTERVAL '8 days', '10:20', 'ENTREGADO');

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
    (1, 1, 1, 8.00),
    (1, 5, 1, 2.00);

INSERT INTO pago (id_pedido, monto, metodo_pago, estado, fecha, referencia)
SELECT id_pedido, total, 'YAPE', 'PENDIENTE', fecha_hora, 'YP-884512' FROM pedido WHERE id_pedido = 1;
UPDATE pago SET estado = 'PAGADO' WHERE id_pedido = 1;

INSERT INTO recibo (id_pago, codigo_verificacion, fecha_emision, verificado, fecha_verificacion)
SELECT id_pago, 'DR-0425-7781', fecha, TRUE, fecha + INTERVAL '15 minutes' FROM pago WHERE id_pedido = 1;

-- Pedido 2: hoy, pagado y en preparación
INSERT INTO pedido (codigo_pedido, id_estudiante, id_trabajador, hora_recojo, estado)
VALUES ('PED-0426', 5, 2, '12:50', 'EN_PREPARACION');

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
    (2, 2, 2, 4.00),
    (2, 7, 1, 2.50);

INSERT INTO pago (id_pedido, monto, metodo_pago, estado, referencia)
SELECT id_pedido, total, 'PLIN', 'PENDIENTE', 'PL-220931' FROM pedido WHERE id_pedido = 2;
UPDATE pago SET estado = 'PAGADO' WHERE id_pedido = 2;

INSERT INTO recibo (id_pago, codigo_verificacion)
SELECT id_pago, 'DR-0426-3390' FROM pago WHERE id_pedido = 2;

-- Pedido 3: hoy, recién armado y aún sin pagar (no tiene pago ni recibo)
INSERT INTO pedido (codigo_pedido, id_estudiante, hora_recojo, estado)
VALUES ('PED-0427', 6, '13:10', 'PENDIENTE');

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
    (3, 3, 1, 5.50),
    (3, 8, 2, 2.50);

-- Pedido 4: hace 15 días, pagado y entregado
INSERT INTO pedido (codigo_pedido, id_estudiante, id_trabajador, fecha_hora, hora_recojo, estado)
VALUES ('PED-0423', 6, 3, CURRENT_TIMESTAMP - INTERVAL '15 days', '13:00', 'ENTREGADO');

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
    (4, 1, 2, 8.00),
    (4, 5, 2, 2.00);

INSERT INTO pago (id_pedido, monto, metodo_pago, estado, fecha, referencia)
SELECT id_pedido, total, 'TARJETA', 'PENDIENTE', fecha_hora, 'TJ-551208' FROM pedido WHERE id_pedido = 4;
UPDATE pago SET estado = 'PAGADO' WHERE id_pedido = 4;

INSERT INTO recibo (id_pago, codigo_verificacion, fecha_emision, verificado, fecha_verificacion)
SELECT id_pago, 'DR-0423-4102', fecha, TRUE, fecha + INTERVAL '20 minutes' FROM pago WHERE id_pedido = 4;

-- Pedido 5: hace 3 días, pagado y entregado
INSERT INTO pedido (codigo_pedido, id_estudiante, id_trabajador, fecha_hora, hora_recojo, estado)
VALUES ('PED-0424', 4, 3, CURRENT_TIMESTAMP - INTERVAL '3 days', '16:30', 'ENTREGADO');

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
    (5, 7, 1, 2.50),
    (5, 8, 1, 2.50),
    (5, 9, 2, 2.00);

INSERT INTO pago (id_pedido, monto, metodo_pago, estado, fecha, referencia)
SELECT id_pedido, total, 'YAPE', 'PENDIENTE', fecha_hora, 'YP-902377' FROM pedido WHERE id_pedido = 5;
UPDATE pago SET estado = 'PAGADO' WHERE id_pedido = 5;

INSERT INTO recibo (id_pago, codigo_verificacion, fecha_emision, verificado, fecha_verificacion)
SELECT id_pago, 'DR-0424-6650', fecha, TRUE, fecha + INTERVAL '10 minutes' FROM pago WHERE id_pedido = 5;

-- ----------------------------------------------------------------------------
-- NOTIFICACIONES AL ESTUDIANTE (RF-08)
-- ----------------------------------------------------------------------------

INSERT INTO notificacion (id_usuario, id_pedido, tipo, mensaje) VALUES
    (4, 1, 'PEDIDO_ENTREGADO',  'Tu pedido PED-0425 fue entregado. ¡Buen provecho!'),
    (5, 2, 'PEDIDO_CONFIRMADO', 'Tu pedido PED-0426 fue confirmado y está en preparación.');
