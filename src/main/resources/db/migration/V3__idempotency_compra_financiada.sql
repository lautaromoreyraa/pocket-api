-- =============================================================================
--  V3 — Idempotencia de las compras financiadas (RF-41)
--  Igual que en gasto: la clave la genera el cliente y evita duplicar la compra
--  cuando se reintenta el envío de un borrador offline.
--
--  Se agrega en tres pasos por si la tabla ya tiene filas de pruebas previas:
--  columna nullable → backfill con UUID() → NOT NULL + UNIQUE.
-- =============================================================================

ALTER TABLE compra_financiada
    ADD COLUMN idempotency_key BINARY(16) NULL AFTER usuario_id;

UPDATE compra_financiada
    SET idempotency_key = UUID_TO_BIN(UUID())
    WHERE idempotency_key IS NULL;

ALTER TABLE compra_financiada
    MODIFY COLUMN idempotency_key BINARY(16) NOT NULL;

ALTER TABLE compra_financiada
    ADD CONSTRAINT uk_compra_idempotency UNIQUE (idempotency_key);
