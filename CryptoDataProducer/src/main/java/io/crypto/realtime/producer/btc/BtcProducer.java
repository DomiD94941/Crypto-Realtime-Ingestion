package io.crypto.realtime.producer.btc;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class BtcProducer {

    // Binance WebSocket endpoint for BTC/USDT trade stream
    private static final String BINANCE_WS = "wss://stream.binance.com:9443/ws/btcusdt@trade";

    // Kafka topic where trade events will be published
    private static final String TOPIC = "crypto.realtime.data";

    public static void main(String[] args) {

        // --- Kafka Producer configuration ---
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092"); // Kafka broker address
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()); // key serializer
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()); // value serializer
        props.setProperty(ProducerConfig.ACKS_CONFIG, "all"); // strongest delivery guarantee
        props.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true"); // ensure no duplicates

        // Create Kafka producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);


    }
}
