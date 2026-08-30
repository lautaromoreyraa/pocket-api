-- =============================================================================
--  V4 — Gastos fijos
--
--  Un gasto fijo es una PLANTILLA, no un gasto: el alquiler, la luz, el
--  internet. No suma a ningún total por sí misma. Cada mes se materializa como
--  una fila de `gasto` común, con `origen = 'FIJO'`.
--
--  Es la misma forma que ya tiene `compra_financiada`:
--      compra_financiada  →  genera N gastos (las cuotas), todas de una (RF-22)
--      gasto_fijo         →  genera 1 gasto por mes, cuando el usuario lo tilda
--
--  La diferencia en el "cuándo" no es capricho: un fijo no tiene fin, así que
--  no se puede generar por adelantado, y un job programado crearía gastos que
--  el usuario nunca confirmó.
-- =============================================================================

-- -----------------------------------------------------------------------------
--  GASTO_FIJO
--  `activo = FALSE` es "pausado": la plantilla sigue existiendo pero no se
--  espera este mes. Las suscripciones se dan de baja y se vuelven a contratar;
--  borrar y recrear perdería el historial.
-- -----------------------------------------------------------------------------
CREATE TABLE gasto_fijo (
    id              BINARY(16)      NOT NULL,
    usuario_id      BINARY(16)      NOT NULL,
    categoria_id    INT             NOT NULL,
    descripcion     VARCHAR(255)    NULL,
    monto           DECIMAL(12,2)   NOT NULL,
    medio_pago      VARCHAR(20)     NOT NULL,
    dia_del_mes     INT             NOT NULL,
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    fecha_alta      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_gasto_fijo PRIMARY KEY (id),
    CONSTRAINT fk_gasto_fijo_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_gasto_fijo_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    -- Tope 28 a propósito: con 29, 30 o 31 habría que decidir qué pasa en
    -- febrero, y la complejidad no la paga nadie.
    CONSTRAINT ck_gasto_fijo_dia   CHECK (dia_del_mes BETWEEN 1 AND 28),
    CONSTRAINT ck_gasto_fijo_monto CHECK (monto > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_gasto_fijo_usuario ON gasto_fijo (usuario_id);


-- -----------------------------------------------------------------------------
--  GASTO ← GASTO_FIJO
-- -----------------------------------------------------------------------------
ALTER TABLE gasto
    ADD COLUMN gasto_fijo_id BINARY(16) NULL AFTER compra_financiada_id;

-- Sin acción referencial, a diferencia de `fk_gasto_compra`, que cascadea.
--
-- Borrar la plantilla NO puede borrar los meses ya pagados (RF-44), así que la
-- opción natural era ON DELETE SET NULL. MySQL no la admite acá: una columna
-- usada en un SET NULL no puede aparecer además en un CHECK (error 3823), y el
-- CHECK de abajo es el que impide que un gasto sea cuota y fijo a la vez.
--
-- Entre las dos, se conserva el CHECK: el desvinculado lo hace el servicio
-- antes de borrar, de forma explícita, y prefiero que "los gastos sobreviven a
-- la baja de la plantilla" se lea en el código a que ocurra por una acción
-- referencial que no aparece en ningún lado. Si alguien lo olvida, la FK falla
-- ruidosamente en vez de borrar historia en silencio.
ALTER TABLE gasto
    ADD CONSTRAINT fk_gasto_gasto_fijo FOREIGN KEY (gasto_fijo_id)
        REFERENCES gasto_fijo (id);

-- Red de seguridad contra registrar el mismo fijo dos veces el mismo mes. El
-- chequeo de negocio lo hace igual el servicio antes, para devolver un 409 con
-- mensaje en vez de un error de constraint. MySQL admite varios NULL en un
-- UNIQUE, así que los gastos que no son fijos no se estorban entre sí.
ALTER TABLE gasto
    ADD CONSTRAINT uk_gasto_fijo_periodo UNIQUE (gasto_fijo_id, fecha_imputacion);

-- Un fijo en cuotas no es un fijo, es una compra financiada: ya existe ese camino.
ALTER TABLE gasto
    ADD CONSTRAINT ck_gasto_fijo_no_cuota CHECK (
        gasto_fijo_id IS NULL OR compra_financiada_id IS NULL
    );
