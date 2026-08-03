# ─── Stage 1: Build / install deps ───────────────────────────
FROM python:3.12-slim AS builder

WORKDIR /app
COPY requirements.txt .

# CmdStan derlemesi için gereken C++ toolchain — sadece bu stage'de kalır,
# runtime image'a kopyalanmaz.
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install into a venv so we can copy it cleanly to the final stage
RUN python -m venv /opt/venv && \
    /opt/venv/bin/pip install --no-cache-dir --upgrade pip && \
    /opt/venv/bin/pip install --no-cache-dir -r requirements.txt

# CmdStan'ı bu Linux build ortamının içinde, sabit versiyonla derle.
# Sürüm CI workflow'larındaki install_cmdstan() çağrısıyla eşleşmeli.
RUN /opt/venv/bin/python -c \
    "from cmdstanpy import install_cmdstan; install_cmdstan(version='2.35.0', dir='/opt/cmdstan')"

# ─── Stage 2: Runtime ─────────────────────────────────────────
FROM python:3.12-slim AS runtime

# Non-root user
RUN useradd -m -u 1001 appuser
USER appuser

WORKDIR /app

COPY --from=builder /opt/venv /opt/venv
COPY --from=builder --chown=appuser:appuser /opt/cmdstan /opt/cmdstan
COPY app ./app

ENV PATH="/opt/venv/bin:$PATH"
ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1
ENV CMDSTAN=/opt/cmdstan/cmdstan-2.35.0

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
