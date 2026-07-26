-- =============================================================================
--  V1 — Esquema inicial de Pocket
-- =============================================================================

-- -----------------------------------------------------------------------------
--  USUARIO
--  Identificación anónima por dispositivo (RF-01).
--  email y password_hash quedan nulos hasta que el usuario vincule cuenta (RF-03).
-- -----------------------------------------------------------------------------
CREATE TABLE usuario (
    id              BINARY(16)      NOT NULL,
    device_uuid     VARCHAR(64)     NOT NULL,
    email           VARCHAR(255)    NULL,
    password_hash   VARCHAR(255)    NULL,
    fecha_alta      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uk_usuario_device UNIQUE (device_uuid),
    CONSTRAINT uk_usuario_email  UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- -----------------------------------------------------------------------------
--  CATEGORIA
--  Lista fija, seedeada en V2 (RF-34, RF-35).
-- -----------------------------------------------------------------------------
CREATE TABLE categoria (
    id      INT             NOT NULL AUTO_INCREMENT,
    nombre  VARCHAR(60)     NOT NULL,
    icono   VARCHAR(40)     NOT NULL,
    color   VARCHAR(7)      NOT NULL,
    orden   INT             NOT NULL,
    CONSTRAINT pk_categoria PRIMARY KEY (id),
    CONSTRAINT uk_categoria_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- -----------------------------------------------------------------------------
--  COMPRA_FINANCIADA
--  Padre de las cuotas. Una compra genera N filas en `gasto` (RF-22).
-- -----------------------------------------------------------------------------
CREATE TABLE compra_financiada (
    id                  BINARY(16)      NOT NULL,
    usuario_id          BINARY(16)      NOT NULL,
    categoria_id        INT             NOT NULL,
    descripcion         VARCHAR(255)    NULL,
    monto_total         DECIMAL(12,2)   NOT NULL,
    cantidad_cuotas     INT             NOT NULL,
    fecha_compra        DATE            NOT NULL,
    fecha_alta          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_compra_financiada PRIMARY KEY (id),
    CONSTRAINT fk_compra_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario (id)   ON DELETE CASCADE,
    CONSTRAINT fk_compra_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    CONSTRAINT ck_compra_cuotas    CHECK (cantidad_cuotas BETWEEN 1 AND 60),
    CONSTRAINT ck_compra_monto     CHECK (monto_total > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- -----------------------------------------------------------------------------
--  GASTO
--  Tabla central. Una cuota es un gasto con compra_financiada_id no nulo.
--
--  fecha_gasto       → cuándo ocurrió realmente
--  fecha_imputacion  → a qué mes se le carga (RN-03)
--  Todos los reportes mensuales consultan por fecha_imputacion.
--
--  idempotency_key   → evita duplicados al reintentar el envío de un borrador
--                      offline (RF-41, RF-42)
-- -----------------------------------------------------------------------------
CREATE TABLE gasto (
    id                      BINARY(16)      NOT NULL,
    usuario_id              BINARY(16)      NOT NULL,
    categoria_id            INT             NOT NULL,
    compra_financiada_id    BINARY(16)      NULL,
    idempotency_key         BINARY(16)      NOT NULL,
    monto                   DECIMAL(12,2)   NOT NULL,
    descripcion             VARCHAR(255)    NULL,
    medio_pago              VARCHAR(20)     NOT NULL,
    origen                  VARCHAR(10)     NOT NULL,
    fecha_gasto             DATE            NOT NULL,
    fecha_imputacion        DATE            NOT NULL,
    nro_cuota               INT             NULL,
    fecha_alta              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_gasto PRIMARY KEY (id),
    CONSTRAINT fk_gasto_usuario   FOREIGN KEY (usuario_id)           REFERENCES usuario (id)           ON DELETE CASCADE,
    CONSTRAINT fk_gasto_categoria FOREIGN KEY (categoria_id)         REFERENCES categoria (id),
    CONSTRAINT fk_gasto_compra    FOREIGN KEY (compra_financiada_id) REFERENCES compra_financiada (id) ON DELETE CASCADE,
    CONSTRAINT uk_gasto_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_gasto_monto     CHECK (monto > 0),
    CONSTRAINT ck_gasto_cuota     CHECK (
        (compra_financiada_id IS NULL AND nro_cuota IS NULL)
        OR
        (compra_financiada_id IS NOT NULL AND nro_cuota >= 1)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Índice que sostiene prácticamente todas las consultas de la app:
-- resumen mensual, promedio histórico, detección de hormigas.
CREATE INDEX ix_gasto_usuario_periodo ON gasto (usuario_id, fecha_imputacion);

-- Acelera el agrupado por categoría dentro de un período.
CREATE INDEX ix_gasto_usuario_periodo_categoria ON gasto (usuario_id, fecha_imputacion, categoria_id);

-- Para levantar las cuotas de una compra al editarla o eliminarla.
CREATE INDEX ix_gasto_compra ON gasto (compra_financiada_id);


-- -----------------------------------------------------------------------------
--  INGRESO
--  Carga siempre manual (RF-32). Puede haber varios por período.
-- -----------------------------------------------------------------------------
CREATE TABLE ingreso (
    id              BINARY(16)      NOT NULL,
    usuario_id      BINARY(16)      NOT NULL,
    monto           DECIMAL(12,2)   NOT NULL,
    descripcion     VARCHAR(255)    NULL,
    periodo         DATE            NOT NULL,
    fecha_alta      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ingreso PRIMARY KEY (id),
    CONSTRAINT fk_ingreso_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_ingreso_monto CHECK (monto > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_ingreso_usuario_periodo ON ingreso (usuario_id, periodo);


-- -----------------------------------------------------------------------------
--  COTIZACION
--  Última cotización conocida del blue. Global, no por usuario.
--  Sirve de fallback si la API externa se cae (RF-40).
-- -----------------------------------------------------------------------------
CREATE TABLE cotizacion (
    id                      INT             NOT NULL AUTO_INCREMENT,
    valor_compra            DECIMAL(12,2)   NOT NULL,
    valor_venta             DECIMAL(12,2)   NOT NULL,
    fecha_actualizacion     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cotizacion PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
