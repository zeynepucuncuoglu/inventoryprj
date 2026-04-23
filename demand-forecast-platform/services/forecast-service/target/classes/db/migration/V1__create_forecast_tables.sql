CREATE TABLE forecast_jobs (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    product_id   UUID         NOT NULL,
    sku          VARCHAR(50)  NOT NULL,
    horizon_days INTEGER      NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    model_used   VARCHAR(50),
    mae          NUMERIC(10,4),
    error_message VARCHAR(1000),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_forecast_jobs PRIMARY KEY (id),
    CONSTRAINT ck_forecast_jobs_status CHECK (
        status IN ('PENDING','RUNNING','COMPLETED','FAILED')
    ),
    CONSTRAINT ck_forecast_jobs_horizon CHECK (horizon_days BETWEEN 1 AND 365)
);

CREATE TABLE forecast_points (
    job_id             UUID           NOT NULL,
    forecast_date      DATE           NOT NULL,
    predicted_quantity NUMERIC(12,4)  NOT NULL,
    lower_bound        NUMERIC(12,4)  NOT NULL,
    upper_bound        NUMERIC(12,4)  NOT NULL,

    CONSTRAINT fk_forecast_points_job FOREIGN KEY (job_id)
        REFERENCES forecast_jobs (id) ON DELETE CASCADE,
    CONSTRAINT ck_forecast_points_qty CHECK (predicted_quantity >= 0)
);

CREATE INDEX idx_forecast_jobs_product_id     ON forecast_jobs (product_id);
CREATE INDEX idx_forecast_jobs_status         ON forecast_jobs (status);
CREATE INDEX idx_forecast_jobs_product_status ON forecast_jobs (product_id, status);
CREATE INDEX idx_forecast_jobs_updated_at     ON forecast_jobs (updated_at DESC);
CREATE INDEX idx_forecast_points_job          ON forecast_points (job_id);
