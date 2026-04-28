from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def _history(n_days: int):
    return [
        {"date": str(date(2024, 1, 1) + timedelta(days=i)), "quantity": 10.0 + i * 0.5}
        for i in range(n_days)
    ]


def test_health_returns_up():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.json()["service"] == "ml-inference-service"


def test_forecast_returns_correct_number_of_points():
    response = client.post("/api/v1/forecast", json={
        "product_id": "product-abc",
        "sku": "SKU-001",
        "horizon_days": 14,
        "model": "prophet",
        "historical_data": _history(30),
    })

    assert response.status_code == 200
    body = response.json()
    assert len(body["forecast"]) == 14


def test_forecast_response_schema():
    response = client.post("/api/v1/forecast", json={
        "product_id": "product-abc",
        "sku": "SKU-001",
        "horizon_days": 7,
        "model": "prophet",
        "historical_data": _history(30),
    })

    body = response.json()
    assert body["product_id"] == "product-abc"
    assert body["sku"] == "SKU-001"
    assert isinstance(body["mae"], float)
    assert body["mae"] >= 0

    point = body["forecast"][0]
    assert "date" in point
    assert "predicted_quantity" in point
    assert point["lower_bound"] <= point["predicted_quantity"] <= point["upper_bound"]


def test_forecast_predicted_quantities_are_non_negative():
    response = client.post("/api/v1/forecast", json={
        "product_id": "product-abc",
        "sku": "SKU-001",
        "horizon_days": 30,
        "model": "prophet",
        "historical_data": _history(60),
    })

    body = response.json()
    for point in body["forecast"]:
        assert point["predicted_quantity"] >= 0
        assert point["lower_bound"] >= 0


def test_forecast_accepts_minimum_14_days_history():
    response = client.post("/api/v1/forecast", json={
        "product_id": "product-min",
        "sku": "SKU-MIN",
        "horizon_days": 7,
        "model": "prophet",
        "historical_data": _history(14),
    })

    assert response.status_code == 200


def test_forecast_rejects_fewer_than_14_days_history():
    response = client.post("/api/v1/forecast", json={
        "product_id": "product-short",
        "sku": "SKU-SHORT",
        "horizon_days": 7,
        "model": "prophet",
        "historical_data": _history(13),
    })

    assert response.status_code == 422


def test_forecast_returns_400_for_unsupported_model():
    response = client.post("/api/v1/forecast", json={
        "product_id": "product-arima",
        "sku": "SKU-ARIMA",
        "horizon_days": 7,
        "model": "arima",
        "historical_data": _history(30),
    })

    assert response.status_code == 400
    assert "arima" in response.json()["detail"]


def test_forecast_rejects_horizon_above_365():
    response = client.post("/api/v1/forecast", json={
        "product_id": "product-long",
        "sku": "SKU-LONG",
        "horizon_days": 366,
        "model": "prophet",
        "historical_data": _history(30),
    })

    assert response.status_code == 422


def test_forecast_rejects_negative_quantity_in_history():
    history = _history(20)
    history[5]["quantity"] = -1.0

    response = client.post("/api/v1/forecast", json={
        "product_id": "product-neg",
        "sku": "SKU-NEG",
        "horizon_days": 7,
        "model": "prophet",
        "historical_data": history,
    })

    assert response.status_code == 422
