package io.crypto.realtime.producer.btc;

import io.crypto.realtime.producer.BinanceWebSocketListener;
import io.crypto.realtime.producer.KafkaTopicCreator;
import io.crypto.realtime.producer.ksql.KsqlInitializer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class BtcProducer {

    // Kafka broker address
    private static final String BOOTSTRAP_SERVER = "127.0.0.1:9092";

    // Binance WebSocket endpoint for BTC/USDT trades
    private static final String BINANCE_WS = "wss://stream.binance.com:9443/ws/btcusdt@trade";

    // Kafka topic for raw trade events
    private static final String TOPIC = "crypto.realtime.data.btc";

    // Kafka sink topic for aggregated results (avg per minute)
    private static final String SINK_TOPIC = "btc.avg.per.minute";

    // Logger instance for structured logging
    private static final Logger log = LoggerFactory.getLogger(BtcProducer.class.getSimpleName());

    public static void main(String[] args) {

        // Ensure topics exist before starting (idempotent creation)
        KafkaTopicCreator.createIfNotExists(BOOTSTRAP_SERVER, TOPIC, 3, (short) 1);
        KafkaTopicCreator.createIfNotExists(BOOTSTRAP_SERVER, SINK_TOPIC, 3, (short) 1);

        // Initialize KSQL streams and tables for downstream analytics
        String ksqlUrl = "http://localhost:8088";
        KsqlInitializer.createSourceStream(ksqlUrl, TOPIC);
        KsqlInitializer.createFinalAvgTable(ksqlUrl, SINK_TOPIC);

        // Configure and create Kafka Producer
        KafkaProducer<String, String> producer = getKafkaProducer();

        // Configure OkHttp client for WebSocket (no timeout for streaming)
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        // WebSocket request to Binance stream
        Request request = new Request.Builder().url(BINANCE_WS).build();

        // Attach custom listener to forward Binance messages to Kafka
        BinanceWebSocketListener listener = new BinanceWebSocketListener(producer, TOPIC);
        WebSocket ws = client.newWebSocket(request, listener);

        // Register shutdown hook for graceful resource cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            try {
                ws.close(1000, "App closed"); // Close WebSocket gracefully
            } catch (Exception ignored) {}

            producer.flush();  // Ensure pending messages are delivered
            producer.close();  // Close Kafka producer

            client.connectionPool().evictAll(); // Clear HTTP connections
            client.dispatcher().executorService().shutdown(); // Stop internal threads
        }));
    }

    /**
     * Configure Kafka producer with recommended production settings:
     * - String serializers
     * - Strong delivery guarantees (acks=all)
     * - Idempotence enabled to prevent duplicates
     */
    @NotNull
    private static KafkaProducer<String, String> getKafkaProducer() {
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVER);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

        return new KafkaProducer<>(properties);
    }
}
