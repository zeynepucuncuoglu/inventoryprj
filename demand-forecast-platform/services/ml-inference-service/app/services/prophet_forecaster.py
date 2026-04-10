import pandas as pd
import numpy as np
from prophet import Prophet
from datetime import date, timedelta

from app.schemas.forecast_schemas import (
    ForecastRequest, ForecastResponse, ForecastPoint, SalesDataPoint
)


def run_prophet_forecast(request: ForecastRequest) -> ForecastResponse:
    """
    Runs Facebook Prophet on the provided historical sales data.

    Design decisions:
    - We hold out the last 20% of data for validation (MAE calculation).
    - yearly_seasonality=auto lets Prophet decide based on data length.
    - We add a custom floor of 0 since demand can't be negative.
    """
    df = _to_prophet_df(request.historical_data)

    # Train/validation split
    split_idx = int(len(df) * 0.8)
    train_df = df.iloc[:split_idx]
    val_df = df.iloc[split_idx:]

    model = Prophet(
        yearly_seasonality="auto",
        weekly_seasonality=True,
        daily_seasonality=False,
        interval_width=0.95,
        changepoint_prior_scale=0.05,  # regularization — prevents overfitting on short series
    )
    model.add_country_holidays(country_name="US")
    model.fit(train_df)

    # MAE on validation set
    mae = _calculate_mae(model, val_df) if len(val_df) > 0 else 0.0

    # Full forecast
    future = model.make_future_dataframe(periods=request.horizon_days, freq="D")
    future["floor"] = 0
    forecast = model.predict(future)

    # Extract only the future predictions (not the historical fitted values)
    forecast_only = forecast.tail(request.horizon_days)

    forecast_points = [
        ForecastPoint(
            date=row["ds"].date(),
            predicted_quantity=max(0.0, round(row["yhat"], 2)),
            lower_bound=max(0.0, round(row["yhat_lower"], 2)),
            upper_bound=max(0.0, round(row["yhat_upper"], 2)),
        )
        for _, row in forecast_only.iterrows()
    ]

    return ForecastResponse(
        product_id=request.product_id,
        sku=request.sku,
        model_used="prophet",
        forecast=forecast_points,
        mae=round(mae, 4),
    )


def _to_prophet_df(data_points: list[SalesDataPoint]) -> pd.DataFrame:
    df = pd.DataFrame([
        {"ds": pd.Timestamp(dp.date), "y": dp.quantity, "floor": 0.0}
        for dp in data_points
    ])
    return df.sort_values("ds").reset_index(drop=True)


def _calculate_mae(model: Prophet, val_df: pd.DataFrame) -> float:
    future = val_df[["ds", "floor"]].copy()
    predictions = model.predict(future)
    actuals = val_df["y"].values
    predicted = np.clip(predictions["yhat"].values, 0, None)
    return float(np.mean(np.abs(actuals - predicted)))
