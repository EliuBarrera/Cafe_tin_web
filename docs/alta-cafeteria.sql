-- ============================================================================
-- Alta de un cafetín nuevo con su dueño (decisión del 19/11/2026: sin rol ADMIN).
--
-- Uso, con psql contra la rama de Neon que corresponda (main = producción):
--
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
--     -v nombre="'Cafetín Pabellón C'" -v ubicacion="'Campus UCV, pabellón C'" \
--     -v dueno_nombre="'Juana'" -v dueno_apellidos="'Torres Ruiz'" \
--     -v dueno_correo="'juana.torres@cafetin.pe'" -v dueno_clave="'una-clave-temporal'" \
--     -v ruc="NULL" \
--     -f docs/alta-cafeteria.sql
--
-- (Los textos van con comillas simples dentro de las dobles; ruc = NULL o '10456789012'.)
--
-- Después:
--   1. Entrega al dueño su correo y la clave temporal; que la cambie con "¿Olvidaste tu contraseña?".
--   2. El dueño entra al panel, crea sus categorías, productos y trabajadores.
--   3. Cuando esté disponible (fase 2), conecta su cuenta de Mercado Pago en "Cobros".
--
-- La contraseña se guarda con BCrypt (pgcrypto, mismo formato $2a$ que usa la API).
-- ============================================================================

\set ON_ERROR_STOP on
SET search_path = cafetin, public;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

BEGIN;

WITH nueva AS (
    INSERT INTO cafeteria (nombre, ubicacion)
    VALUES (:nombre, :ubicacion)
    RETURNING id_cafeteria
), usuario_nuevo AS (
    INSERT INTO usuario (nombre, apellidos, correo, contrasena, tipo_usuario)
    VALUES (:dueno_nombre, :dueno_apellidos, lower(:dueno_correo),
            public.crypt(:dueno_clave, public.gen_salt('bf', 10)), 'DUENO')
    RETURNING id_usuario
)
INSERT INTO dueno (id_usuario, cargo, ruc, id_cafeteria)
SELECT u.id_usuario, 'Propietario', :ruc, n.id_cafeteria
FROM usuario_nuevo u, nueva n;

-- Categorías de partida para que el dueño pueda registrar productos de inmediato.
INSERT INTO categoria (nombre, descripcion, id_cafeteria)
SELECT v.nombre, v.descripcion, c.id_cafeteria
FROM cafeteria c,
     (VALUES ('Desayunos', 'Preparados de la mañana'),
             ('Bebidas',   'Refrescos, jugos e infusiones'),
             ('Snacks',    'Productos envasados')) AS v (nombre, descripcion)
WHERE c.nombre = :nombre;

SELECT c.id_cafeteria, c.nombre, u.correo AS dueno
FROM cafeteria c
JOIN dueno d   ON d.id_cafeteria = c.id_cafeteria
JOIN usuario u ON u.id_usuario  = d.id_usuario
WHERE c.nombre = :nombre;

COMMIT;
