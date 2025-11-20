package io.crypto.realtime.producers.btc;

import io.crypto.realtime.producers.BinanceWebSocketListener;
import io.crypto.realtime.producers.kafka.KafkaTopicCreator;
import io.crypto.realtime.producers.kafka.KafkaProducerFactory;
import io.crypto.realtime.producers.ksql.KsqlInitializer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class BtcProducer {

    // Binance WebSocket endpoint for BTC/USDT trades
    private static final String BINANCE_WS = "wss://stream.binance.com:9443/ws/btcusdt@trade";

    // Kafka topic for raw trade events
    private static final String TOPIC = "crypto.realtime.data.btc";

    // Kafka sink topic for aggregated results (avg per minute)
    private static final String SINK_TOPIC = "btc.avg.per.minute";

    private static final Logger log = LoggerFactory.getLogger(BtcProducer.class.getSimpleName());

    private static String getBootstrapServer() {
        String bs = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bs == null || bs.isBlank()) {
            bs = System.getenv("SPRING_KAFKA_BOOTSTRAP_SERVERS");
        }
        if (bs == null || bs.isBlank()) {
            bs = "kafka:19092"; // default for compose
        }
        return bs;
    }

    private static String getKsqlUrl() {
        String url = System.getenv("KSQL_URL");
        if (url == null || url.isBlank()) {
            url = "http://ksqldb-server:8088"; // in compose
        }
        return url;
    }

    public static void main(String[] args) {

        String bootstrapServer = getBootstrapServer();
        String ksqlUrl = getKsqlUrl();

        log.info("Starting BTC Producer (bootstrap={}, ksqlUrl={})", bootstrapServer, ksqlUrl);

        // Ensure topics exist before starting (idempotent creation)
        KafkaTopicCreator.createIfNotExists(bootstrapServer, TOPIC, 3, (short) 1);
        KafkaTopicCreator.createIfNotExists(bootstrapServer, SINK_TOPIC, 3, (short) 1);

        // Initialize KSQL streams and tables for downstream analytics
        KsqlInitializer.createSourceStream(ksqlUrl, TOPIC);
        KsqlInitializer.createFinalAvgTable(ksqlUrl, SINK_TOPIC);

        // Create Kafka Producer using shared factory
        KafkaProducer<String, String> producer = KafkaProducerFactory.createProducer(bootstrapServer);

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        Request request = new Request.Builder().url(BINANCE_WS).build();

        BinanceWebSocketListener listener = new BinanceWebSocketListener(producer, TOPIC);
        WebSocket ws = client.newWebSocket(request, listener);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            try { ws.close(1000, "App closed"); } catch (Exception ignored) {}

            producer.flush();
            producer.close();

            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdown();
        }));
    }
}
