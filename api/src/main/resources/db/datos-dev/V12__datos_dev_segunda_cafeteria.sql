-- ============================================================================
-- V12__datos_dev_segunda_cafeteria.sql  (SOLO perfil dev · Eliú Barrera · rango V10–V19)
-- Segundo cafetín para probar en desarrollo que cada dueño y trabajador ve solo
-- lo suyo y que el estudiante elige dónde pedir. Sin pedidos: el primero lo hace
-- quien pruebe. Contraseña de los dos usuarios: Cafetin2026 (mismo hash que Rosa en V2).
-- NO EDITAR una vez aplicada: cualquier cambio va en una nueva V<n>.
-- ============================================================================

INSERT INTO cafeteria (nombre, ubicacion) VALUES ('Cafetín Pabellón B', 'Campus UCV, pabellón B, primer piso');

INSERT INTO usuario (nombre, apellidos, correo, contrasena, telefono, tipo_usuario) VALUES
    ('Jorge', 'Salazar Vega',  'jorge.salazar@cafetin.pe', '$2a$10$4jO2.9w4G1Qx0txWOuyhuuwZDBqr8VJZbbpuzaXm1tgyiF2R0wdpG', '917000111', 'DUENO'),
    ('Paola', 'Cruz Medina',   'paola.cruz@cafetin.pe',    '$2a$10$4jO2.9w4G1Qx0txWOuyhuuwZDBqr8VJZbbpuzaXm1tgyiF2R0wdpG', '917000222', 'TRABAJADOR');

INSERT INTO dueno (id_usuario, cargo, id_cafeteria)
SELECT u.id_usuario, 'Propietario', c.id_cafeteria
FROM usuario u, cafeteria c
WHERE u.correo = 'jorge.salazar@cafetin.pe' AND c.nombre = 'Cafetín Pabellón B';

INSERT INTO trabajador (id_usuario, codigo_empleado, turno, id_cafeteria)
SELECT u.id_usuario, 'TRB-B01', 'MANANA', c.id_cafeteria
FROM usuario u, cafeteria c
WHERE u.correo = 'paola.cruz@cafetin.pe' AND c.nombre = 'Cafetín Pabellón B';

INSERT INTO categoria (nombre, descripcion, id_cafeteria)
SELECT v.nombre, v.descripcion, c.id_cafeteria
FROM cafeteria c,
     (VALUES ('Bebidas', 'Café, infusiones y jugos'),
             ('Snacks',  'Para llevar entre clases')) AS v (nombre, descripcion)
WHERE c.nombre = 'Cafetín Pabellón B';

INSERT INTO producto (id_categoria, nombre, descripcion, precio, id_cafeteria)
SELECT cat.id_categoria, v.nombre, v.descripcion, v.precio, cat.id_cafeteria
FROM (VALUES ('Bebidas', 'Café pasado',          'Café de Villa Rica',          3.00),
             ('Bebidas', 'Emoliente',            'Caliente, con linaza',        1.50),
             ('Snacks',  'Alfajor de maicena',   'Con manjar blanco',           2.00),
             ('Snacks',  'Chifles 50 g',         'De plátano verde, piuranos',  2.50))
       AS v (categoria, nombre, descripcion, precio)
JOIN categoria cat ON cat.nombre = v.categoria
JOIN cafeteria c ON c.id_cafeteria = cat.id_cafeteria AND c.nombre = 'Cafetín Pabellón B';

INSERT INTO inventario (id_producto, cantidad_disponible, stock_minimo)
SELECT p.id_producto, 25, 5
FROM producto p JOIN cafeteria c USING (id_cafeteria)
WHERE c.nombre = 'Cafetín Pabellón B';
