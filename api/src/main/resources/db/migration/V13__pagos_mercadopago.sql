-- ============================================================================
-- V13__pagos_mercadopago.sql  (módulo Pedidos · Eliú Barrera · rango V10–V19)
-- Cobro con Mercado Pago (Checkout Pro + OAuth por cafetín, decisión del 19/11/2026).
--   1. cafeteria: cuenta de Mercado Pago conectada por OAuth. Los tokens se guardan
--      cifrados por la API (AES-GCM, llave CAFETIN_CIFRADO_LLAVE): aquí solo hay texto opaco.
--   2. pago: preferencia de Checkout Pro, id del pago en Mercado Pago (único) y URL de pago.
--   3. metodo_pago_enum: 'OTRO' para medios de Mercado Pago que no son tarjeta ni Yape.
--   4. oauth_estado: 'state' de un solo uso del flujo OAuth, con expiración.
-- NO EDITAR una vez aplicada: cualquier cambio va en una nueva V<n>.
-- ============================================================================

ALTER TYPE metodo_pago_enum ADD VALUE IF NOT EXISTS 'OTRO';

ALTER TABLE cafeteria
    ADD COLUMN mp_user_id        BIGINT,
    ADD COLUMN mp_access_token   TEXT,
    ADD COLUMN mp_refresh_token  TEXT,
    ADD COLUMN mp_token_expira   TIMESTAMP,
    ADD COLUMN mp_conectado_en   TIMESTAMP,
    ADD CONSTRAINT ck_cafeteria_mp_completo CHECK (
        (mp_user_id IS NULL AND mp_access_token IS NULL AND mp_refresh_token IS NULL)
        OR (mp_user_id IS NOT NULL AND mp_access_token IS NOT NULL AND mp_conectado_en IS NOT NULL));

COMMENT ON COLUMN cafeteria.mp_access_token  IS 'Token OAuth de Mercado Pago del cafetín, cifrado por la API.';
COMMENT ON COLUMN cafeteria.mp_refresh_token IS 'Token de renovación, cifrado por la API.';

ALTER TABLE pago
    ADD COLUMN mp_preferencia_id VARCHAR(80),
    ADD COLUMN mp_pago_id        BIGINT,
    ADD COLUMN url_pago          VARCHAR(500),
    ADD CONSTRAINT uq_pago_mp_pago_id UNIQUE (mp_pago_id);

COMMENT ON COLUMN pago.url_pago IS 'Checkout Pro donde el estudiante paga; nulo con la pasarela simulada.';

CREATE TABLE oauth_estado (
    estado          VARCHAR(64)     PRIMARY KEY,
    id_cafeteria    INTEGER         NOT NULL,
    expira          TIMESTAMP       NOT NULL,

    CONSTRAINT fk_oauth_estado_cafeteria FOREIGN KEY (id_cafeteria)
        REFERENCES cafeteria (id_cafeteria) ON DELETE CASCADE
);

COMMENT ON TABLE oauth_estado IS 'State del OAuth de Mercado Pago: un solo uso, vence a los 10 minutos.';
