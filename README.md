# Crypto Realtime Ingestion – README

This project provides a complete real-time data pipeline for **BTC/USDT trades** using Binance WebSocket streams, Apache Kafka, ksqlDB, Elasticsearch, and Docker Compose.

---

# Architecture Overview

**Binance WebSocket → Kafka → ksqlDB → Elasticsearch → Kibana**

* Live BTC/USDT trades are ingested into Kafka.
* ksqlDB processes and aggregates the data (e.g., 1-minute averages).
* Processed data is sent to Elasticsearch.
* Kibana visualizes real-time analytics.

All services run locally using **Docker Compose**.

---

# Quick Start

## Build the producer + consumer Docker image

```bash
docker build -t crypto-image .
```

---

## Start full infrastructure

```bash
docker compose up -d
```

This launches:

* Kafka
* ksqlDB
* Elasticsearch
* Kibana
* Conduktor
* Postgres (if used)

---

## Kafka Connection Setup

Use the following parameters for CLI tools, Conduktor, or custom applications.

| Field                    | Value                      |
| ------------------------ | -------------------------- |
| **Host**                 | `127.0.0.1`                |
| **Port**                 | `9092`                     |
| **Security protocol**    | `PLAINTEXT`                |
| **Raw data topic**       | `crypto.realtime.data.btc` |
| **Aggregate data topic** | `btc.avg.per.minute`       |

---

## Run Producer in a container

```bash
docker run --network=host crypto-image producer
```

---

## Run Consumer in a container
```bash
docker run --network=host crypto-image consumer
```

---

# ksqlDB Setup

To create tables, streams, and materialized views you need access to ksqlDB inside Conduktor.

---

## Steps in Conduktor platform

1. Go to **Kafka Connect**
2. Open **Manage Cluster**
3. Select **My Local Kafka Cluster**
4. Navigate to **ksqlDB**
5. Click **Add a ksqlDB**
6. Fill the following:

| Field            | Value                       |
| ---------------- | --------------------------- |
| **Name**         | `btc-data`                  |
| **Technical ID** | `btc-data`                  |
| **URL**          | `http://ksqldb-server:8088` |

After creation, you can run ksql commands.

---

# Elasticsearch Setup

Default values for local cluster:

* **URL:** `http://localhost:9200`
* **Default index:** `crypto`
* **Aggregates index:** `btc_aggregates`

## Standard fields

| Field      | Value       |
| ---------- | ----------- |
| **Host**   | `localhost` |
| **Port**   | `9200`      |
| **Scheme** | `http`      |
| **Index**  | `crypto`    |

You can verify index creation:

```bash
curl http://localhost:9200/_cat/indices?v
```

---

# Visualizing Data in Kibana

Once Elasticsearch receives data:

1. Open **[http://localhost:5601](http://localhost:5601)**
2. Go to **Stack Management → Index Patterns**
3. Create index pattern:
   * `crypto*` for raw data
   * `btc_aggregates*` for aggregated metrics
4. Open **Discover** or **Dashboards**

---

# Status

This README is updated and reflects the current working version of your infrastructure.
