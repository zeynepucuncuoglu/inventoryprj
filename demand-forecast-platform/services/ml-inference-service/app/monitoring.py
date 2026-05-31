"""
Model drift ve performans izleme modülü.

Prometheus'a şu metrikleri açar:
- Tahmin değerlerinin dağılımı (ortalama, std) → drift tespiti
- MAE (Mean Absolute Error) trendi → model kötüleşiyor mu?
- İstek sayısı ve latency (prometheus_fastapi_instrumentator zaten yapıyor)
- Veri kalitesi sorunları (eksik veri, aykırı değer)

Nasıl kullanılır?
  Tahmin yapıldıktan sonra record_prediction() çağır.
  Prometheus bu metrikleri /metrics endpoint'inden çeker.
  Grafana'da MAE trendi grafiği kur → belirgin artış = drift.
"""

from prometheus_client import Counter, Histogram, Gauge, Summary
import statistics
import threading
import time


# ── Sayaçlar ────────────────────────────────────────────────────────────────

forecast_requests_total = Counter(
    "ml_forecast_requests_total",
    "Toplam tahmin isteği sayısı",
    ["model", "status"]  # status: success | error
)

forecast_data_quality_issues = Counter(
    "ml_forecast_data_quality_issues_total",
    "Veri kalitesi sorunları (eksik veri, aykırı değer vb.)",
    ["issue_type"]  # missing_data | outlier | insufficient_history
)

# ── Histogramlar ─────────────────────────────────────────────────────────────

forecast_mae_histogram = Histogram(
    "ml_forecast_mae",
    "Tahmin MAE (Mean Absolute Error) dağılımı",
    ["model"],
    buckets=[0.01, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0]
)

forecast_prediction_value = Histogram(
    "ml_forecast_predicted_demand",
    "Tahmin edilen talep değerlerinin dağılımı — drift tespiti için",
    ["model"],
    buckets=[0, 10, 50, 100, 500, 1000, 5000, 10000]
)

forecast_input_data_points = Histogram(
    "ml_forecast_input_data_points",
    "Her tahmin isteğinde gelen geçmiş veri noktası sayısı",
    ["model"],
    buckets=[7, 14, 30, 60, 90, 180, 365]
)

forecast_duration_seconds = Histogram(
    "ml_forecast_duration_seconds",
    "Tahmin hesaplama süresi",
    ["model"],
    buckets=[0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0]
)

# ── Gauge'lar (anlık değer) ──────────────────────────────────────────────────

forecast_rolling_mae = Gauge(
    "ml_forecast_rolling_mae_gauge",
    "Son 100 tahmin için kayan ortalama MAE — drift alarmı için izle",
    ["model"]
)

forecast_rolling_std = Gauge(
    "ml_forecast_rolling_std_gauge",
    "Son 100 tahminin tahmin değeri standart sapması",
    ["model"]
)

forecast_error_rate = Gauge(
    "ml_forecast_error_rate_gauge",
    "Son 5 dakikadaki hata oranı",
    ["model"]
)

# ── Kayan pencere hesaplama ─────────────────────────────────────────────────

class _RollingWindow:
    """Son N değeri hafızada tutar, istatistik hesaplar."""

    def __init__(self, size: int = 100):
        self._values: list[float] = []
        self._lock = threading.Lock()
        self._size = size

    def add(self, value: float) -> None:
        with self._lock:
            self._values.append(value)
            if len(self._values) > self._size:
                self._values.pop(0)

    def mean(self) -> float:
        with self._lock:
            return statistics.mean(self._values) if self._values else 0.0

    def stdev(self) -> float:
        with self._lock:
            return statistics.stdev(self._values) if len(self._values) > 1 else 0.0


_mae_windows: dict[str, _RollingWindow] = {}
_pred_windows: dict[str, _RollingWindow] = {}


def _get_window(store: dict, model: str) -> _RollingWindow:
    if model not in store:
        store[model] = _RollingWindow(size=100)
    return store[model]


# ── Ana kayıt fonksiyonu ─────────────────────────────────────────────────────

def record_prediction(
    model: str,
    mae: float,
    predicted_values: list[float],
    input_data_points: int,
    duration_seconds: float,
    success: bool = True,
) -> None:
    """
    Tahmin tamamlandıktan sonra bu fonksiyonu çağır.

    Args:
        model:             "prophet" | "linear_regression"
        mae:               Modelin hata değeri
        predicted_values:  Tahmin edilen günlük değerler listesi
        input_data_points: Gelen geçmiş veri noktası sayısı
        duration_seconds:  Hesaplama süresi
        success:           Tahmin başarılı mı?
    """
    status = "success" if success else "error"

    forecast_requests_total.labels(model=model, status=status).inc()
    forecast_duration_seconds.labels(model=model).observe(duration_seconds)
    forecast_input_data_points.labels(model=model).observe(input_data_points)

    if not success:
        return

    # MAE metrikleri
    forecast_mae_histogram.labels(model=model).observe(mae)

    mae_window = _get_window(_mae_windows, model)
    mae_window.add(mae)
    forecast_rolling_mae.labels(model=model).set(mae_window.mean())

    # Tahmin değeri dağılımı (drift tespiti için)
    pred_window = _get_window(_pred_windows, model)
    for val in predicted_values:
        forecast_prediction_value.labels(model=model).observe(val)
        pred_window.add(val)

    forecast_rolling_std.labels(model=model).set(pred_window.stdev())


def record_data_quality_issue(issue_type: str) -> None:
    """
    Veri kalitesi sorunlarını kaydet.

    issue_type örnekleri:
      - "missing_data"         → geçmiş veri eksik
      - "outlier_detected"     → aykırı değer tespit edildi
      - "insufficient_history" → yeterli geçmiş veri yok (< 7 gün)
      - "future_date_in_input" → geçmişte gelecek tarih var
    """
    forecast_data_quality_issues.labels(issue_type=issue_type).inc()
