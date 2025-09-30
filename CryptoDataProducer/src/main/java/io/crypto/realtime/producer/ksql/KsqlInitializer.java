package io.crypto.realtime.producer.ksql;

import io.crypto.realtime.producer.btc.BtcProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KsqlInitializer {

    private KsqlInitializer() {}

    private static final Logger log = LoggerFactory.getLogger(BtcProducer.class.getSimpleName());

    public static void createSourceStream(String ksqlUrl, String sourceTopic) {
        KsqlDbClient ksql = new KsqlDbClient(ksqlUrl);
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
        log.info("ksqlDB response (create stream): {}", ksql.execute(ddl));
    }


    public static void createFinalAvgTable(String ksqlUrl, String sinkTopic) {
        KsqlDbClient ksql = new KsqlDbClient(ksqlUrl);
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
        log.info("ksqlDB response (create final table): {}", ksql.execute(ctas));
    }

}
