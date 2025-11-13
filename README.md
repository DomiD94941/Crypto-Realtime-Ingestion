# Crypto-Realtime-Ingestion
Built data pipelines to stream live BTC/USDT trades from Binance into Kafka, process them with ksqlDB, and load analytics-ready documents into Elasticsearch.

---

# Kafka Connection Setup

This project uses **Apache Kafka** as the main streaming backbone.  
Use the details below to connect local services or external tools (e.g. Conduktor, custom consumers).

---

 
## Standard Fields

| Field                    | Value                        |
|--------------------------|------------------------------|
| **Host**                 | `127.0.0.1`                  |
| **Port**                 | `9092`                       |
| **Security protocol**    | `PLAINTEXT`                  |
| **Raw data topic**       | `crypto.realtime.data.btc`   |
| **Aggregate data topic** | `btc.avg.per.minute`         |

---

# Elasticsearch Connection Setup

Elasticsearch is used as the analytics store for trade events and ksqlDB aggregates.

---

## Connection Details

- **Elasticsearch URL** (default): `http://localhost:9200`  
- **Default index**: `crypto`  
- **Alternative index (aggregates)**: `btc_aggregates`  

---

## Standard Fields

| Field        | Value                      |
|-------------|----------------------------|
| **Host**    | `localhost`                |
| **Port**    | `9200`                     |
| **Scheme**  | `http`                     |
| **Index**   | `crypto`                   |

---

# Docker & Services

All dependencies (Kafka, ksqlDB, Elasticsearch, Kibana, Conduktor, Postgres) are started via **Docker Compose**.

---

## Start infrastructure

```bash
docker compose up -d
```

```
run BtcProducer.java
```

```
run BtcConsumer.java
```

## Create table and view in ksqlDB
In Conductor Console go into:
1. Kafka Connect
2. Manage cluster
3. My Local Kafka Cluster
4. ksqlDB
5. Add a ksqlDB
6. Create connection

| Field            | Value                       |
|------------------|-----------------------------|
| **Name**         | `btc-data`                  |
| **Technical ID** | `btc-data`                  |
| **URL**          | `http://ksqldb-server:8088` |
