
Real-time cryptocurrency ingestion pipeline that streams live BTC/USDT trades from Binance into Apache Kafka, enriches them with ksqlDB, and persists analytics-friendly documents in Elasticsearch.

## Table of contents
- [Architecture](#architecture)
- [Repository layout](#repository-layout)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Build the Java services](#build-the-java-services)
- [Run the data pipeline](#run-the-data-pipeline)
    - [Start the BTC trade producer](#start-the-btc-trade-producer)
    - [Run the Elasticsearch consumer](#run-the-elasticsearch-consumer)
    - [Inspect data products](#inspect-data-products)
- [Configuration reference](#configuration-reference)
- [Troubleshooting](#troubleshooting)
- [Next steps](#next-steps)

## Architecture
```
Binance WebSocket ──> BtcProducer ──> Kafka ──> ksqlDB ──> btc.avg.per.minute topic
                                        │                         │
                                        └─────────────────────────┘
                                                        │
                                              BtcConsumer (bulk writes)
                                                        │
                                                 Elasticsearch ──> Kibana dashboards
```

Key responsibilities:

| Component | Description |
| --- | --- |
| `BtcProducer` | Connects to Binance's BTC/USDT trade stream, normalizes payloads, and produces them to `crypto.realtime.data.btc`. It also idempotently ensures Kafka topics exist and bootstraps the required ksqlDB stream/table. |
| Kafka + Conduktor | Kafka (single broker) acts as the durable backbone. Conduktor Console is bundled for topic inspection and schema debugging. |
| ksqlDB | Transforms the raw trade stream into a tumbling-window aggregation (`btc.avg.per.minute`) that emits the final average price per minute. |
| `BtcConsumer` | Consumes raw trade events (by default), batches them into Elasticsearch bulk requests, and stores documents in the `crypto` index. |
| Elasticsearch + Kibana | Provide fast search/analytics and visualization on top of the ingested trade documents. |

## Repository layout
```
Crypto-Realtime-Ingestion/
├── CryptoDataProducers/   # Java producer module (Binance -> Kafka)
├── CryptoDataConsumers/   # Java consumer module (Kafka -> Elasticsearch)
├── docker-compose.yaml    # Local Kafka, ksqlDB, Elasticsearch, Kibana, Conduktor, Postgres
├── build.gradle           # Shared Gradle config (Java 17)
├── gradlew*               # Gradle wrapper
└── settings.gradle        # Declares producer & consumer subprojects
```

## Prerequisites
- JDK 17+
- Docker Engine 24+ and Docker Compose V2
- Access to the public internet when running Gradle for the first time (to download dependencies)
- Optional: IntelliJ IDEA or VS Code for easier execution/debugging of the Java modules

## Quick start
1. Clone the repository and move into it.
2. Start the supporting infrastructure:
   ```bash
   docker compose up -d
   ```
   This launches Kafka, ksqlDB, Elasticsearch, Kibana, Postgres, and Conduktor Console. Allow a minute for services to stabilize.
3. Verify containers are healthy:
   ```bash
   docker compose ps
   ```
    - Kafka broker should expose `9092` on your localhost.
    - ksqlDB REST API is available on `http://localhost:8088`.
    - Elasticsearch listens on `http://localhost:9200` (security disabled for local use).
    - Kibana UI is accessible at `http://localhost:5601`.
    - Conduktor Console runs on `http://localhost:8080` (default credentials: username `admin`, password `admin`).

## Build the Java services
Use the Gradle wrapper to compile the producer and consumer modules:
```bash
./gradlew :CryptoDataProducers:build
./gradlew :CryptoDataConsumers:build
```
The build files target Java 17 and pull dependencies such as Kafka clients, OkHttp, Jackson, and the Elasticsearch Java SDK.

If you prefer to work from an IDE, simply import the Gradle project. Both modules expose `main` methods that can be launched directly.

## Run the data pipeline

### Start the BTC trade producer
The producer requires connectivity to Binance and Kafka. After the infrastructure is running:

- **From an IDE:** Run `io.crypto.realtime.producers.btc.BtcProducer`. The class will:
    1. Create the Kafka topics (`crypto.realtime.data.btc`, `btc.avg.per.minute`) if needed.
    2. Initialize the ksqlDB stream/table used for minute-level BTC averages.
    3. Open a WebSocket to Binance and stream trades to Kafka.

- **From the command line:** Because the subproject uses the plain `java` Gradle plugin, the simplest CLI option is to ask Gradle to execute the class with the runtime classpath resolved on the fly:
  ```bash
  ./gradlew :CryptoDataProducers:build \
            :CryptoDataProducers:run --args=""
  ```
  If your Gradle distribution does not expose a `run` task, apply the `application` plugin to the module or run the class from your IDE instead.

### Run the Elasticsearch consumer
The consumer supports a few CLI flags (prefixed with `--`) to override defaults such as the Kafka bootstrap server, source topic, target Elasticsearch index, and consumer group ID. With the infrastructure running:

- **Default settings:**
  ```bash
  ./gradlew :CryptoDataConsumers:build \
            :CryptoDataConsumers:run --args="--bootstrap 127.0.0.1:9092 --es http://localhost:9200"
  ```
  The command above consumes the raw trade topic and writes to the `crypto` index.

- **Alternate configuration example:**
  ```bash
  ./gradlew :CryptoDataConsumers:build \
            :CryptoDataConsumers:run --args="--bootstrap 127.0.0.1:9092 --topic btc.avg.per.minute --index btc_aggregates --es http://localhost:9200"
  ```
  This variant consumes the aggregated ksqlDB output instead of the raw stream.

> NOTE; When stopping the applications, use `Ctrl+C`. Both the producer and consumer register shutdown hooks that close WebSocket, Kafka, and Elasticsearch resources gracefully.

### Inspect data products
- **Kafka topics:** Use Conduktor Console (`http://localhost:8080`) to browse topics, partitions, and sample payloads.
- **ksqlDB:** The REST API (`http://localhost:8088`) exposes endpoints such as `/info` and `/query`. You can also use the Confluent CLI or ksqlDB CLI to run `SELECT * FROM BTC_AVG_1_MIN_FINAL EMIT CHANGES LIMIT 10;`.
- **Elasticsearch documents:**
  ```bash
  curl -s 'http://localhost:9200/crypto/_search?size=5' | jq
  ```
  The index mapping includes the `eventTime` field as an `epoch_millis` date for easier time-based aggregations.
- **Kibana:** Import a data view pointing to the `crypto*` index and build visualizations/dashboards over fields such as `price`, `quantity`, and `eventTime`.

## Configuration reference
| Location | Purpose |
| --- | --- |
| `CryptoDataProducers/src/main/java/io/crypto/realtime/producers/btc/BtcProducer.java` | Kafka bootstrap server, Binance WebSocket endpoint, source/sink topic names. |
| `CryptoDataProducers/src/main/java/io/crypto/realtime/producers/ksql/KsqlInitializer.java` | ksqlDB stream & table DDL for raw trades and 1-minute averages. |
| `CryptoDataProducers/src/main/java/io/crypto/realtime/producers/KafkaTopicCreator.java` | Idempotent Kafka topic creation helper. |
| `CryptoDataConsumers/src/main/java/io/crypto/realtime/consumers/btc/BtcConsumer.java` | Parses CLI arguments, polls Kafka, batches Elasticsearch bulk operations. |
| `CryptoDataConsumers/src/main/java/io/crypto/realtime/consumers/elasticsearch/ElasticsearchService.java` | Elasticsearch client, index creation, bulk insert implementation. |
| `docker-compose.yaml` | Service definitions for Kafka, ksqlDB, Conduktor, PostgreSQL (Conduktor metadata), Elasticsearch, and Kibana. |