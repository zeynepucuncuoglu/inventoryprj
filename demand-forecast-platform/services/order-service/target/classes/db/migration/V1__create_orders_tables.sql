-- Orders table
CREATE TABLE orders (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    customer_id  UUID         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT ck_orders_status CHECK (
        status IN ('PENDING','CONFIRMED','SHIPPED','DELIVERED','CANCELLED')
    )
);

-- Order items — child table, always queried through the parent order
CREATE TABLE order_items (
    order_id    UUID           NOT NULL,
    product_id  UUID           NOT NULL,
    sku         VARCHAR(50)    NOT NULL,
    quantity    INTEGER        NOT NULL,
    unit_price  NUMERIC(12,2)  NOT NULL,

    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_price    CHECK (unit_price >= 0)
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_status      ON orders (status);
CREATE INDEX idx_order_items_order  ON order_items (order_id);
