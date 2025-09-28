package io.crypto.realtime.producer.btc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jetbrains.annotations.NotNull;

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
        System.out.println("Connected to Binance WebSocket");
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
                    System.err.println("Kafka send failed: " + ex.getMessage());
                }
            });

        } catch (Exception e) {
            System.err.println("Failed to parse Binance message: " + e.getMessage());
        }
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, ByteString bytes) {
        // Binance usually sends text messages, but handle binary just in case
        onMessage(webSocket, bytes.utf8());
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, @NotNull String reason) {
        System.out.println("Closing WebSocket: " + code + " / " + reason);
        webSocket.close(1000, null); // normal closure
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, Throwable t, Response response) {
        System.err.println("WebSocket failure: " + t.getMessage());
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
