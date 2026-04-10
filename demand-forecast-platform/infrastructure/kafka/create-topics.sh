#!/bin/bash
# Runs once at startup to create all Kafka topics.
# Partitioning by product_id keeps ordering guarantees per product.

set -e

KAFKA_HOST="kafka:9092"

echo "Waiting for Kafka to be ready..."
cub kafka-ready -b $KAFKA_HOST 1 30

echo "Creating Kafka topics..."

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic product.events \
  --partitions 3 \
  --replication-factor 1

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic order.events \
  --partitions 6 \
  --replication-factor 1

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic forecast.requested \
  --partitions 3 \
  --replication-factor 1

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic forecast.completed \
  --partitions 3 \
  --replication-factor 1

kafka-topics --bootstrap-server $KAFKA_HOST --create --if-not-exists \
  --topic notification.alerts \
  --partitions 3 \
  --replication-factor 1

echo "Topics created successfully:"
kafka-topics --bootstrap-server $KAFKA_HOST --list
