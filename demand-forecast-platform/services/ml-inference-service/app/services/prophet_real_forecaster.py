import pandas as pd
from prophet import Prophet

from app.schemas.forecast_schemas import ForecastRequest, ForecastResponse, ForecastPoint


def run_prophet_real_forecast(request: ForecastRequest) -> ForecastResponse:
    """
    Real Prophet forecast — requires prophet package (CmdStan).
    Used when Prophet is installed (local dev).
    Falls back to linear model in CI/Docker where Prophet is not installed.
    """
    df = pd.DataFrame([
        {"ds": pd.Timestamp(dp.date), "y": float(dp.quantity)}
        for dp in request.historical_data
    ])

    # Train/validation split (80/20)
    split_idx = max(1, int(len(df) * 0.8))
    train_df = df.iloc[:split_idx]
    val_df = df.iloc[split_idx:]

    # MAE: train on first 80%, validate on remaining 20%
    mae_model = Prophet(
        yearly_seasonality=False,
        weekly_seasonality=True,
        daily_seasonality=False,
        interval_width=0.95,
    )
    mae_model.fit(train_df)

    mae = 0.0
    if len(val_df) > 0:
        val_forecast = mae_model.predict(val_df[["ds"]])
        mae = float(abs(val_df["y"].values - val_forecast["yhat"].values).mean())

    # Forecast: refit on full dataset for best accuracy
    model = Prophet(
        yearly_seasonality=False,
        weekly_seasonality=True,
        daily_seasonality=False,
        interval_width=0.95,
    )
    model.fit(df)

    future = model.make_future_dataframe(periods=request.horizon_days)
    forecast_df = model.predict(future)

    last_history_date = df["ds"].iloc[-1]
    future_rows = forecast_df[forecast_df["ds"] > last_history_date].head(request.horizon_days)

    forecast_points = [
        ForecastPoint(
            date=row["ds"].date(),
            predicted_quantity=max(0.0, round(float(row["yhat"]), 2)),
            lower_bound=max(0.0, round(float(row["yhat_lower"]), 2)),
            upper_bound=round(float(row["yhat_upper"]), 2),
        )
        for _, row in future_rows.iterrows()
    ]

    return ForecastResponse(
        product_id=request.product_id,
        sku=request.sku,
        model_used="prophet",
        forecast=forecast_points,
        mae=round(mae, 4),
    )
