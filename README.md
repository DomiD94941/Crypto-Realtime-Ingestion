# Crypto-Realtime-Ingestion
Real-time cryptocurrency data ingestion pipeline built with WebSocket and Apache Kafka, extended with ksqlDB transformations and Elasticsearch for analytics.

* **CryptoDataProducer** – a Kotlin application that opens a Binance WebSocket, turns every trade into a Kafka record, and optionally manages the ksqlDB streams/tables needed for derived topics.
* **CryptoDataConsumer** – a Kotlin consumer that reads either the raw trade topic or the ksqlDB aggregation topic and writes documents in bulk to Elasticsearch with simple retry/backoff logic.
* **docker-compose.yaml** – a convenience stack for local development that runs Kafka, ZooKeeper, ksqlDB server, Elasticsearch, Kibana, and Conduktor Console so you can explore the topics and dashboards without extra setup.

## Requirements

Before you start, make sure you have:

* **Docker Desktop or Docker Engine** with Compose v2 support (the `docker compose` CLI).
* **Java Development Kit 17 or newer** for running the Gradle wrappers in the producer and consumer services.
* **Git** if you plan to clone the repository rather than download a release archive.
* Internet access the first time you build, so Gradle can resolve dependencies and Docker can pull images.

## Getting started

1. **Clone the repository and boot the infrastructure**
   ```bash
   git clone https://github.com/<your-org>/Crypto-Realtime-Ingestion.git
   cd Crypto-Realtime-Ingestion
   docker compose up -d
   ```
   This single command set will download the necessary Docker images and start all supporting services. Wait until `docker compose ps` shows the containers as `healthy`/`running`.

2. **Verify the stack**
   * Kafka broker exposed on `localhost:9092`.
   * ksqlDB server available at `http://localhost:8088`.
   * Elasticsearch responding at `http://localhost:9200`.
   * Kibana UI running at `http://localhost:5601`.
   * Conduktor Console at `http://localhost:8080` for browsing topics.
  
   
3. **Build and run the producer**
   ```bash
   cd CryptoDataProducer
   chmod +x gradlew   # one-time per clone
   ./gradlew build
   ./gradlew run   # or run io.crypto.realtime.producer.btc.BtcProducer from your IDE
   ```
   The producer will begin streaming trades into the `crypto.realtime.data.btc` topic and execute the ksqlDB statements bundled with the project.

4. **Build and run the consumer**
   ```bash
   cd ../CryptoDataConsumer
   chmod +x gradlew
   ./gradlew build
   ./gradlew run   # runs io.crypto.realtime.consumer.btc.ElasticsearchBtcConsumer
   ```
   You can override default CLI arguments (bootstrap servers, topic name, Elasticsearch URL/index) by supplying them to the Gradle run command: `./gradlew run --args="--bootstrap=127.0.0.1:9092 --es=http://localhost:9200 --topic=crypto.realtime.data.btc --index=crypto"`.
