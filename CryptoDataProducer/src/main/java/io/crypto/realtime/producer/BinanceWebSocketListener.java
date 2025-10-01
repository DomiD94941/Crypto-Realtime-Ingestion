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
 * WebSocket listener for the Binance trade stream.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Parse incoming JSON messages into {@link TradeEvent}.</li>
 *   <li>Transform them into a normalized JSON payload with descriptive fields.</li>
 *   <li>Publish to Kafka with the trading symbol as the key.</li>
 * </ul>
 * Logging strategy:
 * <ul>
 *   <li>INFO: connection lifecycle + successful send (without full payloads).</li>
 *   <li>DEBUG: payload sizes and raw response lengths.</li>
 *   <li>ERROR: parsing, WebSocket, and Kafka send failures with stack traces.</li>
 * </ul>
 */
public class BinanceWebSocketListener extends WebSocketListener {

    private static final Logger log =
            LoggerFactory.getLogger(BinanceWebSocketListener.class);

    private final KafkaProducer<String, String> producer;      // Kafka producer instance
    private final String topic;                                // Kafka topic name
    private final ObjectMapper mapper = new ObjectMapper();    // JSON mapper (Jackson)

    /**
     * @param producer Kafka producer used to publish messages.
     * @param topic    Kafka topic where trade events will be sent.
     */
    public BinanceWebSocketListener(KafkaProducer<String, String> producer, String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        log.info("Connected to Binance WebSocket (code={} message={})",
                response.code(), safeMsg(response.message()));
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        try {
            // Parse incoming raw JSON into TradeEvent
            TradeEvent ev = mapper.readValue(text, TradeEvent.class);

            // Convert to normalized JSON with descriptive field names
            String payload = toCleanJson(ev);

            // Create Kafka record (key = symbol)
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, ev.symbol, payload);

            // Send asynchronously to Kafka
            producer.send(record, (meta, ex) -> {
                if (ex != null) {
                    log.error("Kafka send failed (topic={}, key={}, msgSize={}B): {}",
                            topic, record.key(), payload.length(), ex.getMessage(), ex);
                } else {
                    // Keep INFO concise, move verbose details to DEBUG
                    log.info("Kafka send ok (topic={}, partition={}, offset={}, ts={})",
                            meta.topic(), meta.partition(), meta.offset(), meta.timestamp());
                    if (log.isDebugEnabled()) {
                        log.debug("Sent record (key={}, valueSize={}B)", record.key(), payload.length());
                    }
                }
            });

        } catch (Exception e) {
            // Include a short preview of the text for troubleshooting without spamming logs
            String preview = text.length() > 256 ? text.substring(0, 256) + "..." : text;
            log.error("Failed to parse Binance message (size={}B, preview={}): {}",
                    text.length(), preview, e.getMessage(), e);
        }
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, ByteString bytes) {
        // Delegate binary frames by decoding as UTF-8
        onMessage(webSocket, bytes.utf8());
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, @NotNull String reason) {
        log.info("Closing WebSocket (code={}, reason={})", code, reason);
        webSocket.close(1000, null); // Normal closure
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, Throwable t, Response response) {
        if (response != null) {
            log.error("WebSocket failure (httpCode={}, httpMsg={}): {}",
                    response.code(), safeMsg(response.message()), t.getMessage(), t);
        } else {
            log.error("WebSocket failure (no HTTP response): {}", t.getMessage(), t);
        }
    }

    /**
     * Converts a TradeEvent into a simplified JSON string with descriptive property names.
     *
     * @param ev TradeEvent object.
     * @return JSON string.
     * @throws JsonProcessingException if serialization fails.
     */
    private String toCleanJson(TradeEvent ev) throws JsonProcessingException {
        return mapper.createObjectNode()
                .put("eventType", ev.eventType)
                .put("eventTime", ev.eventTime)
                .put("symbol", ev.symbol)
                .put("tradeId", ev.tradeId)
                .put("tradeTime", ev.tradeTime)
                .put("isBuyerMaker", ev.isBuyerMaker)
                .put("price", ev.price)         // BigDecimal -> JSON number
                .put("quantity", ev.quantity)   // BigDecimal -> JSON number
                .toString();
    }

    private static String safeMsg(String msg) {
        return msg == null ? "" : msg;
    }
}
