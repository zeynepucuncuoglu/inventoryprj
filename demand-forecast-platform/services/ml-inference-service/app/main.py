from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
import logging

from app.schemas.forecast_schemas import ForecastRequest, ForecastResponse
from app.services.prophet_forecaster import run_prophet_forecast

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="ML Inference Service",
    description="Demand forecasting using Prophet / ARIMA models",
    version="1.0.0",
)


@app.get("/health")
def health():
    return {"status": "UP", "service": "ml-inference-service"}


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

    try:
        if request.model == "prophet":
            result = run_prophet_forecast(request)
        else:
            raise HTTPException(status_code=400, detail=f"Unsupported model: {request.model}")

        logger.info("Forecast complete: product_id=%s mae=%.4f", request.product_id, result.mae)
        return result

    except ValueError as e:
        logger.warning("Invalid forecast request: %s", str(e))
        raise HTTPException(status_code=422, detail=str(e))
    except Exception as e:
        logger.error("Forecast failed for product_id=%s: %s", request.product_id, str(e))
        raise HTTPException(status_code=500, detail="Forecast computation failed")
