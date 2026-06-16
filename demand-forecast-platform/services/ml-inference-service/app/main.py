from fastapi import FastAPI, HTTPException
from prometheus_fastapi_instrumentator import Instrumentator
import logging
import time

from app.schemas.forecast_schemas import ForecastRequest, ForecastResponse
from app.services.prophet_forecaster import run_prophet_forecast as run_linear_forecast
from app.monitoring import record_prediction, record_data_quality_issue

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Prophet requires CmdStan — available locally but skipped in CI/Docker builds.
# Falls back to linear trend model automatically when Prophet is not installed.
PROPHET_AVAILABLE = False
try:
    from app.services.prophet_real_forecaster import run_prophet_real_forecast
    PROPHET_AVAILABLE = True
    logger.info("Prophet is available — using real Prophet model")
except ImportError:
    logger.info("Prophet not installed — using linear trend fallback")

app = FastAPI(
    title="ML Inference Service",
    description="Demand forecasting using Prophet / ARIMA models",
    version="1.0.0",
)

Instrumentator().instrument(app).expose(app, include_in_schema=False)


@app.get("/health")
def health():
    """Liveness probe — JVM/process çalışıyor mu?"""
    return {"status": "UP", "service": "ml-inference-service"}


@app.get("/health/ready")
def readiness():
    """
    Readiness probe — servis istek almaya hazır mı?
    Prophet kütüphanesi ve sklearn import'larını doğrular.
    K8s bu endpoint'i geçene kadar pod'a trafik göndermez.
    """
    try:
        import prophet  # noqa: F401
        import sklearn  # noqa: F401
        import pandas   # noqa: F401
        return {"status": "READY", "service": "ml-inference-service"}
    except ImportError as e:
        logger.error("Readiness check failed: %s", str(e))
        from fastapi import Response
        return Response(
            content=f'{{"status":"NOT_READY","error":"{str(e)}"}}',
            status_code=503,
            media_type="application/json"
        )


@app.post("/api/v1/forecast", response_model=ForecastResponse)
def forecast(request: ForecastRequest):
    """
    Accepts historical sales data and returns a demand forecast.
    Called internally by forecast-service only — not exposed via API Gateway.
    """
    logger.info(
        "Forecast request: product_id=%s sku=%s horizon=%d model=%s data_points=%d",
        request.product_id, request.sku, request.horizon_days,
        request.model, len(request.historical_data)
    )

    if request.model not in ("prophet", "linear"):
        raise HTTPException(status_code=400, detail=f"Unsupported model: {request.model}")

    if len(request.historical_data) < 7:
        record_data_quality_issue("insufficient_history")

    start_time = time.time()
    try:
        if request.model == "prophet" and PROPHET_AVAILABLE:
            logger.info("Running real Prophet forecast for product_id=%s", request.product_id)
            result = run_prophet_real_forecast(request)
        else:
            if request.model == "prophet":
                logger.warning("Prophet requested but not installed — falling back to linear model")
            result = run_linear_forecast(request)
        duration = time.time() - start_time

        record_prediction(
            model=request.model,
            mae=result.mae,
            predicted_values=[p.predicted_quantity for p in result.forecast],
            input_data_points=len(request.historical_data),
            duration_seconds=duration,
            success=True,
        )

        logger.info(
            "Forecast complete: product_id=%s mae=%.4f duration=%.2fs",
            request.product_id, result.mae, duration
        )
        return result

    except ValueError as e:
        record_prediction(model=request.model, mae=0, predicted_values=[],
                          input_data_points=len(request.historical_data),
                          duration_seconds=time.time() - start_time, success=False)
        logger.warning("Invalid forecast request: %s", str(e))
        raise HTTPException(status_code=422, detail=str(e))
    except Exception as e:
        record_prediction(model=request.model, mae=0, predicted_values=[],
                          input_data_points=len(request.historical_data),
                          duration_seconds=time.time() - start_time, success=False)
        logger.error("Forecast failed for product_id=%s: %s", request.product_id, str(e))
        raise HTTPException(status_code=500, detail="Forecast computation failed")
