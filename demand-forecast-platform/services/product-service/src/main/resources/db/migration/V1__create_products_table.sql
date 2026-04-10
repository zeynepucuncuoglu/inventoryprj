-- V1: initial products table
-- Version column enables optimistic locking at DB level

CREATE TABLE products (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    name            VARCHAR(255)    NOT NULL,
    sku             VARCHAR(50)     NOT NULL,
    category        VARCHAR(100)    NOT NULL,
    price           NUMERIC(12, 2)  NOT NULL,
    stock_quantity  INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    version         BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_price CHECK (price >= 0),
    CONSTRAINT ck_products_stock CHECK (stock_quantity >= 0)
);

CREATE INDEX idx_products_category ON products (category);
CREATE INDEX idx_products_sku ON products (sku);
