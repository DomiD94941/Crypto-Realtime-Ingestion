package io.crypto.realtime.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A WebSocket listener that connects to the Binance trade stream,
 * parses incoming JSON messages into {@link TradeEvent} objects,
 * transforms them into a clean JSON format with descriptive field names,
 * and publishes the result to a Kafka topic.
 */

public class BinanceWebSocketListener extends WebSocketListener {

    private final KafkaProducer<String, String> producer; // Kafka producer instance
    private final String topic;                           // Kafka topic name
    private final ObjectMapper mapper = new ObjectMapper(); // JSON mapper (Jackson)
    private final Logger log = LoggerFactory.getLogger(BinanceWebSocketListener.class.getSimpleName());

    /**
     * Constructor to initialize the WebSocket listener.
     *
     * @param producer Kafka producer used to publish messages
     * @param topic    Kafka topic where trade events will be sent
     */

    public BinanceWebSocketListener(KafkaProducer<String, String> producer, String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        log.info("Connected to Binance WebSocket");
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        try {
            // Parse incoming raw JSON into TradeEvent object
            TradeEvent ev = mapper.readValue(text, TradeEvent.class);

            // Convert the TradeEvent into a clean JSON string with descriptive field names
            String payload = toCleanJson(ev);

            // Create a Kafka record (key = symbol, value = JSON payload)
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, ev.symbol, payload);

            // Send asynchronously to Kafka
            producer.send(record, (meta, ex) -> {
                if (ex != null) {
                    log.error("Kafka send failed: {}", ex.getMessage());
                } else {
                    log.info(
                            "SENT: key={} value={} \nTOPIC={} || PARTITION={} || OFFSET={} || TIMESTAMP={}",
                            record.key(),
                            record.value(),
                            meta.topic(),
                            meta.partition(),
                            meta.offset(),
                            meta.timestamp()
                    );
                }
            });

        } catch (Exception e) {
            log.error("Failed to parse Binance message: {}", e.getMessage());
        }
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, ByteString bytes) {
        onMessage(webSocket, bytes.utf8());
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, @NotNull String reason) {
        log.info("Closing WebSocket: {} / {}", code, reason);
        webSocket.close(1000, null); // normal closure
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, Throwable t, Response response) {
        log.error("WebSocket failure: {}", t.getMessage());
    }

    /**
     * Converts a TradeEvent object into a simplified JSON string
     * with descriptive property names (e.g. "price" instead of "p").
     *
     * @param ev TradeEvent object
     * @return JSON string
     * @throws JsonProcessingException if serialization fails
     */

    private String toCleanJson(TradeEvent ev) throws JsonProcessingException {
        return mapper.createObjectNode()
                .put("eventType", ev.eventType)
                .put("eventTime", ev.eventTime)
                .put("symbol", ev.symbol)
                .put("tradeId", ev.tradeId)
                .put("tradeTime", ev.tradeTime)
                .put("isBuyerMaker", ev.isBuyerMaker)
                .put("price", ev.price)       // BigDecimal -> JSON number
                .put("quantity", ev.quantity) // BigDecimal -> JSON number
                .toString();
    }
}
