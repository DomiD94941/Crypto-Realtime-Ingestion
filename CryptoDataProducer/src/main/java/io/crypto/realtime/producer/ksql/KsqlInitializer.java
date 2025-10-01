package io.crypto.realtime.producer.ksql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to bootstrap required ksqlDB objects.
 *
 * Responsible for:
 * - Creating the source stream (backed by Kafka topic with raw Binance trades).
 * - Creating the final aggregated table (average BTC price per minute).
 *
 * This class should be called once at application startup to ensure that
 * the ksqlDB environment is ready before streaming begins.
 */
public final class KsqlInitializer {

    // Prevent instantiation
    private KsqlInitializer() {}

    // Logger for this initializer
    private static final Logger log = LoggerFactory.getLogger(KsqlInitializer.class);

    /**
     * Creates a ksqlDB source stream mapping the raw Kafka topic.
     *
     * @param ksqlUrl     Base URL of ksqlDB (e.g., http://localhost:8088).
     * @param sourceTopic Kafka topic name containing raw Binance trade events.
     */
    public static void createSourceStream(String ksqlUrl, String sourceTopic) {
        KsqlDbClient ksql = new KsqlDbClient(ksqlUrl);

        // KSQL statement to map raw trades into a ksqlDB stream
        String ddl = String.join("\n",
                "CREATE STREAM IF NOT EXISTS BINANCE_TRADES_RAW (",
                "  SYMBOL        VARCHAR KEY,",
                "  EVENTTYPE     VARCHAR,",
                "  EVENTTIME     BIGINT,",
                "  TRADEID       BIGINT,",
                "  TRADETIME     BIGINT,",
                "  ISBUYERMAKER  BOOLEAN,",
                "  PRICE         DOUBLE,",
                "  QUANTITY      DOUBLE",
                ") WITH (",
                "  KAFKA_TOPIC='" + sourceTopic + "',",
                "  VALUE_FORMAT='JSON',",
                "  TIMESTAMP='TRADETIME'",
                ");"
        );

        log.info("Creating ksqlDB source stream for topic={}", sourceTopic);
        String response = ksql.execute(ddl);
        log.debug("ksqlDB response (create stream): {}", response);
    }

    /**
     * Creates the final aggregated table that computes average BTC price per minute.
     *
     * @param ksqlUrl   Base URL of ksqlDB (e.g., http://localhost:8088).
     * @param sinkTopic Kafka topic where the final aggregated results will be published.
     */
    public static void createFinalAvgTable(String ksqlUrl, String sinkTopic) {
        KsqlDbClient ksql = new KsqlDbClient(ksqlUrl);

        // CTAS statement: aggregate average price in 1-minute tumbling windows
        String ctas = String.join("\n",
                "CREATE TABLE IF NOT EXISTS BTC_AVG_1M_FINAL",
                "  WITH (KAFKA_TOPIC='" + sinkTopic + "', VALUE_FORMAT='JSON') AS",
                "SELECT",
                "  SYMBOL,",
                "  TIMESTAMPTOSTRING(WINDOWSTART, 'yyyy-MM-dd HH:mm:ss', 'Europe/Warsaw') AS WINDOW_START,",
                "  TIMESTAMPTOSTRING(WINDOWEND,   'yyyy-MM-dd HH:mm:ss', 'Europe/Warsaw') AS WINDOW_END,",
                "  AVG(PRICE) AS AVG_PRICE",
                "FROM BINANCE_TRADES_RAW",
                "WINDOW TUMBLING (SIZE 1 MINUTE)",
                "GROUP BY SYMBOL",
                "EMIT FINAL;"
        );

        log.info("Creating ksqlDB final avg table with sinkTopic={}", sinkTopic);
        String response = ksql.execute(ctas);
        log.debug("ksqlDB response (create final table): {}", response);
    }
}
