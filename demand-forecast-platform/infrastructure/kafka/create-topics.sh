#!/bin/bash
# Runs once at startup to create all Kafka topics + Dead Letter Queues.
# DLQ pattern: her topic'in .DLT versiyonu — işlenemeyen mesajlar buraya düşer.

set -e

KAFKA_HOST="kafka:9092"

echo "Waiting for Kafka to be ready..."
cub kafka-ready -b $KAFKA_HOST 1 30

echo "Creating Kafka topics..."

# ── Ana topic'ler ──────────────────────────────────────────────────────────

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic product.events \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000    # 7 gün

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic order.events \
  --partitions 6 \
  --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic forecast.requested \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic forecast.completed \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic notification.alerts \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=604800000

# ── Dead Letter Queue topic'leri (.DLT suffix) ────────────────────────────
# İşlenemeyen mesajlar buraya yönlendirilir, kaybolmaz.
# 30 gün tutulur — inceleme ve tekrar işleme için yeterli süre.

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic product.events.DLT \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=2592000000   # 30 gün

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic order.events.DLT \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=2592000000

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic forecast.requested.DLT \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=2592000000

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic forecast.completed.DLT \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=2592000000

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic notification.alerts.DLT \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=2592000000

echo ""
echo "Tüm topic'ler oluşturuldu:"
kafka-topics --bootstrap-server $KAFKA_HOST --list
