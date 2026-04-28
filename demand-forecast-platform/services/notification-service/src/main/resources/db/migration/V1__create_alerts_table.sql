CREATE TABLE alerts (
    id           UUID         NOT NULL,
    type         VARCHAR(30)  NOT NULL,
    severity     VARCHAR(20)  NOT NULL,
    product_id   VARCHAR(255) NOT NULL,
    sku          VARCHAR(50)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    message      TEXT         NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_alerts PRIMARY KEY (id)
);

CREATE INDEX idx_alerts_sku         ON alerts (sku);
CREATE INDEX idx_alerts_type        ON alerts (type);
CREATE INDEX idx_alerts_severity    ON alerts (severity);
CREATE INDEX idx_alerts_occurred_at ON alerts (occurred_at DESC);
