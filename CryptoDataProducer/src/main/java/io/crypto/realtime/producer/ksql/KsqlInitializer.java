package io.crypto.realtime.producer.ksql;

import io.crypto.realtime.producer.btc.BtcProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KsqlInitializer {

    private KsqlInitializer() {}

    private static final Logger log = LoggerFactory.getLogger(BtcProducer.class.getSimpleName());

    public static void initBinanceAvgPerMin(String ksqlUrl,
                                        String sourceTopic,   // np. "crypto.realtime.data.btc"
                                        String sinkTopic) {   // np. "btc_avg_1m"
        KsqlDbClient ksql = new KsqlDbClient(ksqlUrl);

        String script = String.join("\n",
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
                ");",
                "",
                "CREATE TABLE IF NOT EXISTS BTC_AVG_1M",
                "  WITH (KAFKA_TOPIC='" + sinkTopic + "', VALUE_FORMAT='JSON') AS",
                "SELECT",
                "  SYMBOL,",
                "  WINDOWSTART AS WINDOW_START,",
                "  WINDOWEND   AS WINDOW_END,",
                "  AVG(PRICE)  AS AVG_PRICE",
                "FROM BINANCE_TRADES_RAW",
                "WINDOW TUMBLING (SIZE 1 MINUTE)",
                "GROUP BY SYMBOL",
                "EMIT CHANGES;"
        );

        String response = ksql.execute(script);
        log.info("ksqlDB init response: {}", response);
    }
}
