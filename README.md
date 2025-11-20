# Crypto Realtime Ingestion

This project provides a complete real-time data pipeline for **BTC/USDT trades** using:

**Binance WebSocket → Kafka → ksqlDB → Elasticsearch → Kibana**

All services run locally using **Docker Compose**. The BTC producer and consumer are packaged as a single Docker image (`crypto-image`) and can be started manually via a Docker Compose profile.

---

## Architecture Overview

**Data flow**

```text
Binance WS (btcusdt@trade)
        ↓
   Kafka topic: crypto.realtime.data.btc
        ↓
       ksqlDB (aggregations, windowing)
        ↓
   Kafka topic: btc.avg.per.minute
        ↓
 Elasticsearch index: btc_data
        ↓
        Kibana dashboards
```

Main components:

- **Kafka** – message broker for raw & aggregated trade events
- **ksqlDB** – streaming SQL engine (aggregates, windows, transformations)
- **Elasticsearch** – stores processed data for search & analytics
- **Kibana** – visualizes time-series metrics and dashboards
- **Conduktor** – GUI for Kafka cluster management & ksqlDB
- **PostgreSQL** – backing store for Conduktor
- **Producer** – reads Binance WebSocket, pushes events to Kafka
- **Consumer** – reads from Kafka and pushes docs into Elasticsearch

---

## 1. Build application image

Build the image that contains both **producer** and **consumer**:

```bash
docker build -t crypto-image .
```

The image will be used by Docker Compose for the `producer` and `consumer` services.

---

## 2. Start core infrastructure (without producer/consumer)

The `producer` and `consumer` services are under the `manual` profile, so a plain `docker compose up` starts **only the infrastructure**.

```bash
docker compose up -d
```

This launches:

- `kafka`
- `ksqldb-server`
- `elasticsearch`
- `kibana`
- `conduktor-console`
- `postgresql`

Useful ports on your host:

| Service           | URL / Host              |
| ----------------- | ----------------------- |
| Kafka (external)  | `127.0.0.1:9092`        |
| ksqlDB HTTP       | `http://localhost:8088` |
| Elasticsearch     | `http://localhost:9200` |
| Kibana            | `http://localhost:5601` |
| Conduktor Console | `http://localhost:8080` |
| PostgreSQL        | `localhost:5432`        |

---

## 3. Kafka connection details

Depending on where you connect **from**, use the right bootstrap address:

| Where?                            | Bootstrap servers | Notes                                       |
| --------------------------------- | ----------------- | ------------------------------------------- |
| **From host (Windows / CLI / IDE)** | `127.0.0.1:9092`  | External listener exposed by Docker         |
| **From Docker containers (Compose)** | `kafka:19092`     | Internal listener on the Docker network     |

Other Kafka parameters:

| Field                    | Value                      |
| ------------------------ | -------------------------- |
| **Security protocol**    | `PLAINTEXT`                |
| **Raw data topic**       | `crypto.realtime.data.btc` |
| **Aggregate data topic** | `btc.avg.per.minute`       |

The project’s Java code automatically figures out which bootstrap server to use based on environment variables:

- `KAFKA_BOOTSTRAP_SERVERS`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`

Inside Docker Compose these are set to `kafka:19092`.  
From the host you can override them, e.g.:

```bash
set KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092
```

(or the equivalent on Linux/macOS).

---

## 4. Run producer and consumer manually (Docker Compose profile)

Once infrastructure is running (`docker compose up -d`), you can start the producer and consumer using the **manual** profile:

```bash
# Start only producer
docker compose --profile manual up -d producer

# Start only consumer
docker compose --profile manual up -d consumer

# Or start both at once
docker compose --profile manual up -d producer consumer
```

Check logs:

```bash
docker compose logs -f producer
docker compose logs -f consumer
```

Stop them (without tearing down infra):

```bash
docker compose --profile manual stop producer consumer
```

Stop everything **including** Kafka, ES, etc.:

```bash
docker compose down
```

Stop everything and remove volumes (start from scratch):

```bash
docker compose down -v
```

---

## 5. Running producer/consumer directly (IDE / local JVM)

If you want to run the Java apps directly from IntelliJ (without their Docker containers), while leaving the infrastructure in Docker:

1. Make sure `docker compose up -d` is running (Kafka, ES, ksqlDB, etc.).  
2. Set environment variables for your run configuration:

   - **Producer (BtcProducer)**:
     - `KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092`
     - `KSQL_URL=http://localhost:8088`

   - **Consumer (BtcConsumer)**:
     - `KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092`
     - `ES_URL=http://localhost:9200`

3. Run the desired `main` class from IntelliJ.

The helper methods in code (`getBootstrapServer()`, `getKsqlUrl()`, `getEsUrl()`) will pick those values up.

---

## 6. ksqlDB Setup (via Conduktor)

To define streams, tables, and materialized views in ksqlDB you can use Conduktor.

1. Open Conduktor: **http://localhost:8080**
2. Go to **Manage Cluster**
3. Select **My Local Kafka Cluster**
4. Navigate to **ksqlDB**
5. Click **Add a ksqlDB**
6. Fill the following:

| Field            | Value                       |
| ---------------- | --------------------------- |
| **Name**         | `btc-data`                  |
| **Technical ID** | `btc-data`                  |
| **URL**          | `http://ksqldb-server:8088` |

After creating it, you can run ksql commands that:

- Create a source stream on `crypto.realtime.data.btc`
- Create aggregations into `btc.avg.per.minute`

(Exact ksql statements live next to the Java code / ksql scripts in the repo.)

---

## 7. Elasticsearch Setup

Default values for the local cluster:

- **URL:** `http://localhost:9200`
- **Default index for BTC data:** `btc_data`

Standard connection parameters:

| Field      | Value       |
| ---------- | ----------- |
| **Host**   | `localhost` |
| **Port**   | `9200`      |
| **Scheme** | `http`      |
| **Index**  | `btc_data`  |

You can verify index creation with:

```bash
curl http://localhost:9200/_cat/indices?v
```

The `BtcConsumer` will:

1. Wait until Elasticsearch is reachable.
2. Ensure the index exists (creating it if necessary).
3. Write documents based on the records read from Kafka.

---

## 8. Visualizing data in Kibana

Once Elasticsearch starts receiving documents in the `btc_data` index:

1. Open **http://localhost:5601**
2. Go to **Stack Management → Index Patterns / Data Views**
3. Create a new data view:
   - Name: e.g. `BTC Data`
   - Index pattern: `btc_data*`
4. Open **Discover** to inspect ingested docs.
5. Create visualizations and dashboards using `btc_data` as data source.

---

## 9. Adding more cryptocurrencies (naming convention)

The pipeline is coin-agnostic – you can easily add other symbols like **ETH/USDT**, **ETC/USDT**, etc. Use the same naming pattern by replacing the symbol (`btc` → `eth`, `etc`, …).

**Conventions:**

| Element               | Pattern                           | Example BTC                | Example ETH               | 
|---------------------  |-----------------------------------|----------------------------|---------------------------|
| Binance WS stream     | `<symbol>usdt@trade`              | `btcusdt@trade`            | `ethusdt@trade`           |
| Raw Kafka topic       | `crypto.realtime.data.<symbol>`   | `crypto.realtime.data.btc` | `crypto.realtime.data.eth`|
| Aggregate Kafka topic | `<symbol>.avg.per.minute`         | `btc.avg.per.minute`       | `eth.avg.per.minute`      | 
| Elasticsearch index   | `<symbol>_data`                   | `btc_data`                 | `eth_data`                |
| Kibana index pattern  | `<symbol>_data*`                  | `btc_data*`                | `eth_data*`               |

To add a new coin you typically:

1. Create a new producer class (e.g., `EthProducer`) with its Binance WS URL and topics.
2. Create a matching consumer (e.g., `EthConsumer`) with its own index (`eth_data`).
3. Define corresponding ksqlDB streams/tables for aggregations.
4. Add dashboards in Kibana (`eth_data*`).

---

## 10. Useful Docker commands

A small cheat sheet for day-to-day work:

```bash
# Show running containers
docker ps

# Follow logs for a specific service
docker compose logs -f kafka
docker compose logs -f ksqldb-server
docker compose logs -f elasticsearch
docker compose logs -f producer
docker compose logs -f consumer

# Restart only Kafka
docker compose restart kafka

# Recreate producer container
docker compose --profile manual up --force-recreate producer
```

---

## Status

This README reflects the current working setup with:

- Infrastructure in Docker Compose
- Producer & consumer run as **manual profile** services
- Environment-variable–driven configuration for Kafka, ksqlDB, and Elasticsearch
- Support for extending the pipeline to additional cryptocurrencies.

