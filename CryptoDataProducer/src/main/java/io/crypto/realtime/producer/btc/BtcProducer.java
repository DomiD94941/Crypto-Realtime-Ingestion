package io.crypto.realtime.producer.btc;

import io.crypto.realtime.producer.BinanceWebSocketListener;
import io.crypto.realtime.producer.KafkaTopicCreator;
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

    private static final String BOOTSTRAP_SERVER = "127.0.0.1:9092";

    // Binance WebSocket endpoint for BTC/USDT trade stream
    private static final String BINANCE_WS = "wss://stream.binance.com:9443/ws/btcusdt@trade";

    // Kafka topic where trade events will be published
    private static final String TOPIC = "crypto.realtime.data.btc";

    // Logger for logging information and errors
    private static final Logger log = LoggerFactory.getLogger(BtcProducer.class.getSimpleName());

    public static void main(String[] args) {

        KafkaTopicCreator.createIfNotExists(BOOTSTRAP_SERVER, TOPIC, 3, (short) 1);

        // Kafka Producer configuration
        KafkaProducer<String, String> producer = getKafkaProducer();

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // disable timeout for streaming connection
                .build();

        Request request = new Request.Builder().url(BINANCE_WS).build();

        // Attach custom WebSocket listener that will forward messages to Kafka
        BinanceWebSocketListener listener = new BinanceWebSocketListener(producer, TOPIC);
        WebSocket ws = client.newWebSocket(request, listener);

        // Graceful shutdown hook
        // This ensures that when the application is terminated (Ctrl+C),
        // resources are cleaned up properly: WebSocket, Kafka producer, and HTTP client.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            try {
                ws.close(1000, "App closed"); // close WebSocket gracefully
            } catch (Exception ignored) {}
            producer.flush();  // flush any unsent Kafka messages
            producer.close();  // close Kafka producer
            client.connectionPool().evictAll(); // clear HTTP connections
            client.dispatcher().executorService().shutdown(); // shutdown internal executor
        }));

    }

    @NotNull
    private static KafkaProducer<String, String> getKafkaProducer() {
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVER); // Kafka broker address
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()); // key serializer
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()); // value serializer
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all"); // strongest delivery guarantee
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true"); // ensure no duplicates

        // Create Kafka producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);
        return producer;
    }
}
