-- ============================================================================
-- PROYECTO    : Gestión Cafetín — UCV
-- ARCHIVO     : 02_gestion_cafetin_datos.sql
-- DESCRIPCIÓN : Datos de prueba para validar el modelo físico
-- REQUISITO   : ejecutar primero 01_gestion_cafetin_schema.sql
-- ============================================================================

SET search_path TO cafetin;

-- ----------------------------------------------------------------------------
-- USUARIOS (RF-01, RF-02)
-- Las contraseñas van como hash de ejemplo; en producción las genera BCrypt.
-- ----------------------------------------------------------------------------

INSERT INTO usuario (nombre, apellidos, correo, contrasena, telefono, tipo_usuario) VALUES
    ('Rosa',     'Quispe Flores',    'rosa.quispe@cafetin.pe',      '$2a$10$hashEjemploDueno00000', '987654321', 'DUENO'),
    ('Carmen',   'Díaz Morales',     'carmen.diaz@cafetin.pe',      '$2a$10$hashEjemploTrab100000', '912345678', 'TRABAJADOR'),
    ('Luis',     'Ramos Chávez',     'luis.ramos@cafetin.pe',       '$2a$10$hashEjemploTrab200000', '913456789', 'TRABAJADOR'),
    ('Eliú',     'Barrera Martínez', 'ebarreram@ucvvirtual.edu.pe', '$2a$10$hashEjemploEst1000000', '914567890', 'ESTUDIANTE'),
    ('María',    'Quiroz Vásquez',   'mquirozv@ucvvirtual.edu.pe',  '$2a$10$hashEjemploEst2000000', '915678901', 'ESTUDIANTE'),
    ('Diego',    'León Paredes',     'dleonp@ucvvirtual.edu.pe',    '$2a$10$hashEjemploEst3000000', '916789012', 'ESTUDIANTE');

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
    ('Distribuidora Nor-Sur',  '044123456', 'ventas@norsur.pe',   'Av. España 1234, Trujillo'),
    ('Panadería San Martín',   '044234567', 'pedidos@sanmartin.pe','Jr. Bolívar 567, Trujillo'),
    ('Snacks del Norte SAC',   '044345678', 'contacto@snorte.pe', 'Av. América 890, Trujillo');

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

INSERT INTO inventario (id_producto, cantidad_disponible, stock_minimo) VALUES
    (1, 40, 10),   -- Menú del día
    (2,  6,  6),   -- Tamal criollo      -> ya está en el mínimo
    (3, 12,  5),   -- Pan con chicharrón
    (4,  2,  8),   -- Empanada de ají    -> por debajo del mínimo
    (5, 20,  8),   -- Chicha morada
    (6,  3,  5),   -- Jugo de piña       -> por debajo del mínimo
    (7, 41, 12),   -- Café pasado
    (8, 24, 10),   -- Galletas de avena
    (9, 30, 10);   -- Papas fritas

-- ----------------------------------------------------------------------------
-- PEDIDOS (RF-05 .. RF-14)
-- El total de cada pedido lo calcula solo el trigger trg_calcular_total_pedido.
-- ----------------------------------------------------------------------------

-- Pedido 1: pagado y ya entregado
INSERT INTO pedido (codigo_pedido, id_estudiante, id_trabajador, hora_recojo, estado)
VALUES ('PED-0425', 4, 2, '10:20', 'ENTREGADO');

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
    (1, 1, 1, 8.00),
    (1, 5, 1, 2.00);

INSERT INTO pago (id_pedido, monto, metodo_pago, estado, referencia)
VALUES (1, 10.00, 'YAPE', 'PENDIENTE', 'YP-884512');

INSERT INTO recibo (id_pago, codigo_verificacion, verificado, fecha_verificacion)
VALUES (1, 'DR-0425-7781', TRUE, CURRENT_TIMESTAMP);

-- Pedido 2: pagado, en preparación
INSERT INTO pedido (codigo_pedido, id_estudiante, id_trabajador, hora_recojo, estado)
VALUES ('PED-0426', 5, 2, '12:50', 'EN_PREPARACION');

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
    (2, 2, 2, 4.00),
    (2, 7, 1, 2.50);

INSERT INTO pago (id_pedido, monto, metodo_pago, estado, referencia)
VALUES (2, 10.50, 'PLIN', 'PENDIENTE', 'PL-220931');

INSERT INTO recibo (id_pago, codigo_verificacion)
VALUES (2, 'DR-0426-3390');

-- Pedido 3: recién creado, aún sin pagar
INSERT INTO pedido (codigo_pedido, id_estudiante, hora_recojo, estado)
VALUES ('PED-0427', 6, '13:10', 'PENDIENTE');

INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES
    (3, 3, 1, 5.50),
    (3, 8, 2, 2.50);

INSERT INTO pago (id_pedido, monto, metodo_pago, estado)
VALUES (3, 10.50, 'EFECTIVO', 'PENDIENTE');

-- ----------------------------------------------------------------------------
-- NOTIFICACIONES AL ESTUDIANTE (RF-08)
-- ----------------------------------------------------------------------------

INSERT INTO notificacion (id_usuario, id_pedido, tipo, mensaje) VALUES
    (4, 1, 'PEDIDO_ENTREGADO',  'Tu pedido PED-0425 fue entregado. ¡Buen provecho!'),
    (5, 2, 'PEDIDO_CONFIRMADO', 'Tu pedido PED-0426 fue confirmado y está en preparación.');
