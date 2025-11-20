package io.crypto.realtime.consumers.btc;

import io.crypto.realtime.consumers.elasticsearch.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.json.JsonData;
import io.crypto.realtime.consumers.kafka.KafkaConsumerFactory;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BtcConsumer {
    private static final Logger log = LoggerFactory.getLogger(BtcConsumer.class);
    private static final String DEFAULT_TOPIC = "crypto.realtime.data.btc";
    private static final String DEFAULT_INDEX = "btc_data";
    private static final String DEFAULT_GROUP_PREFIX = "consumer-elasticsearch-demo-";

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

    private static String getEsUrl() {
        String url = System.getenv("ES_URL");
        if (url == null || url.isBlank()) {
            url = "http://elasticsearch:9200";
        }
        return url;
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> cli = ElasticsearchUtils.parseArgs(args);

        final String esUrl = ElasticsearchUtils.get(cli, "es", getEsUrl());
        final String bootstrap = ElasticsearchUtils.get(cli, "bootstrap", getBootstrapServer());
        final String topic = ElasticsearchUtils.get(cli, "topic", DEFAULT_TOPIC);
        final String indexName = ElasticsearchUtils.get(cli, "index", DEFAULT_INDEX);
        final String groupId = ElasticsearchUtils.get(cli, "group", DEFAULT_GROUP_PREFIX + System.currentTimeMillis());
        
        log.info("Starting BTC Consumer -> ES: {}, topic: {}", esUrl, topic);

        ElasticsearchService es = new ElasticsearchService(esUrl);
        KafkaConsumer<String, String> consumer = KafkaConsumerFactory.createConsumer(bootstrap, groupId);

        es.waitUntilReady();
        es.ensureIndexExists(indexName);

        AtomicBoolean running = new AtomicBoolean(true);
        Thread mainThread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown triggered");
            running.set(false);
            consumer.wakeup();
            try { mainThread.join(); } catch (InterruptedException ignored) {}
        }));

        try (consumer; es) {
            consumer.subscribe(Collections.singletonList(topic));

            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(3000));
                if (records.isEmpty()) continue;

                List<BulkOperation> operations = new ArrayList<>();
                for (ConsumerRecord<String, String> rec : records) {
                    try {
                        String id = ElasticsearchUtils.computeDocId(rec);
                        operations.add(new BulkOperation.Builder()
                                .index(i -> i.index(indexName).id(id).document(JsonData.fromJson(rec.value())))
                                .build());
                    } catch (Exception e) {
                        log.warn("Skipping record: {}", e.getMessage());
                    }
                }

                if (!operations.isEmpty()) {
                    es.bulkInsert(indexName, operations);
                    consumer.commitSync();
                }
            }
        } catch (WakeupException we) {
            log.info("Consumer wakeup – shutting down");
        } catch (Exception e) {
            log.error("Error in main loop", e);
        } finally {
            log.info("BTC Consumer stopped gracefully");
        }
    }
}
