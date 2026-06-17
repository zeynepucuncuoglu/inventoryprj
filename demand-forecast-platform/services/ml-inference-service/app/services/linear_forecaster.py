import pandas as pd
import numpy as np
from datetime import date, timedelta

from app.schemas.forecast_schemas import (
    ForecastRequest, ForecastResponse, ForecastPoint
)


def run_prophet_forecast(request: ForecastRequest) -> ForecastResponse:
    """
    Linear trend + weekly seasonality forecaster using numpy/sklearn.
    Replaces Prophet to avoid CmdStan compilation requirement in Docker.
    Same interface, same output schema.
    """
    df = _to_df(request.historical_data)

    # Train/validation split (80/20)
    split_idx = max(1, int(len(df) * 0.8))
    train_df = df.iloc[:split_idx]
    val_df = df.iloc[split_idx:]

    # Fit linear trend on training data
    x_train = np.arange(len(train_df))
    y_train = train_df["y"].values.astype(float)
    coeffs = np.polyfit(x_train, y_train, deg=1)  # slope + intercept

    # Weekly seasonality: compute mean by day-of-week on training set
    train_df = train_df.copy()
    train_df["dow"] = pd.to_datetime(train_df["ds"]).dt.dayofweek
    dow_means = train_df.groupby("dow")["y"].mean()
    global_mean = y_train.mean() if y_train.mean() > 0 else 1.0
    dow_factors = {dow: (mean / global_mean) for dow, mean in dow_means.items()}

    # MAE on validation set
    mae = 0.0
    if len(val_df) > 0:
        x_val = np.arange(len(train_df), len(train_df) + len(val_df))
        y_pred_val = np.polyval(coeffs, x_val)
        y_pred_val = np.clip(y_pred_val, 0, None)
        mae = float(np.mean(np.abs(val_df["y"].values.astype(float) - y_pred_val)))

    # Generate future forecast
    last_date = pd.to_datetime(df["ds"].iloc[-1])
    n_history = len(df)
    forecast_points = []

    for i in range(request.horizon_days):
        future_date = last_date + timedelta(days=i + 1)
        x = n_history + i
        trend = np.polyval(coeffs, x)
        dow = future_date.dayofweek
        seasonal_factor = dow_factors.get(dow, 1.0)
        predicted = max(0.0, round(trend * seasonal_factor, 2))
        std = max(0.5, abs(predicted) * 0.15)  # 15% confidence interval
        forecast_points.append(ForecastPoint(
            date=future_date.date(),
            predicted_quantity=predicted,
            lower_bound=max(0.0, round(predicted - 1.96 * std, 2)),
            upper_bound=round(predicted + 1.96 * std, 2),
        ))

    return ForecastResponse(
        product_id=request.product_id,
        sku=request.sku,
        model_used="linear-trend",
        forecast=forecast_points,
        mae=round(mae, 4),
    )


def _to_df(data_points) -> pd.DataFrame:
    df = pd.DataFrame([
        {"ds": pd.Timestamp(dp.date), "y": float(dp.quantity)}
        for dp in data_points
    ])
    return df.sort_values("ds").reset_index(drop=True)
