from pydantic import BaseModel, Field
from typing import Literal
from datetime import date


class SalesDataPoint(BaseModel):
    date: date
    quantity: float = Field(ge=0, description="Units sold on this date")


class ForecastRequest(BaseModel):
    product_id: str
    sku: str
    horizon_days: int = Field(default=30, ge=1, le=365, description="Days to forecast ahead")
    historical_data: list[SalesDataPoint] = Field(
        min_length=14,
        description="At least 14 days of history required for reliable forecasts"
    )
    model: Literal["prophet", "arima"] = "prophet"


class ForecastPoint(BaseModel):
    date: date
    predicted_quantity: float
    lower_bound: float
    upper_bound: float


class ForecastResponse(BaseModel):
    product_id: str
    sku: str
    model_used: str
    forecast: list[ForecastPoint]
    mae: float = Field(description="Mean Absolute Error on validation split")
    confidence_interval: float = 0.95
