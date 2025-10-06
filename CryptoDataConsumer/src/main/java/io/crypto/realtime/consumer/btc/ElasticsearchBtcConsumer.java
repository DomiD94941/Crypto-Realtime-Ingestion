package io.crypto.realtime.consumer.btc;

import com.google.gson.JsonParser;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import co.elastic.clients.json.JsonData;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ElasticsearchConsumer {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConsumer.class);

    // Defaults (overridden by CLI flags)
    private static final String DEFAULT_ES_URL = "http://localhost:9200";
    private static final String DEFAULT_BOOTSTRAP = "127.0.0.1:9092";
    private static final String DEFAULT_TOPIC = "crypto.realtime.data.btc";
    private static final String DEFAULT_INDEX = "crypto";
    private static final String DEFAULT_GROUP_PREFIX = "consumer-elasticsearch-demo-";

    // Utils: CLI args parsing
    /**
     * Parse flags in the form of <code>--key=value</code> or <code>--key value</code>.
     * Unknown flags are accepted and stored verbatim; callers should retrieve only expected keys.
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String key = a.substring(2);
                String val;
                int eq = key.indexOf('=');
                if (eq >= 0) {
                    // --key=value form
                    val = key.substring(eq + 1);
                    key = key.substring(0, eq);
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    // --key value form
                    val = args[++i];
                } else {
                    // --flag (boolean true)
                    val = "true";
                }
                m.put(key, val);
            }
        }
        return m;
    }

    /** Retrieve a value from the map, falling back to the provided default if unset/blank. */
    private static String get(Map<String, String> m, String k, String def) {
        String v = m.get(k);
        return (v == null || v.isBlank()) ? def : v;
    }

    // Elasticsearch client
    /**
     * Build an {@link ElasticsearchClient} from a connection string.
     * <p>Supports URIs such as: http://host:9200 or https://host:443.</p>
     */
    private static ElasticsearchClient createElasticsearchClient(String connString) {
        URI uri = URI.create(connString);
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        String host = uri.getHost() == null ? "localhost" : uri.getHost();
        int port = (uri.getPort() == -1) ? ("https".equalsIgnoreCase(scheme) ? 443 : 9200) : uri.getPort();
        RestClient lowLevel = RestClient.builder(new HttpHost(host, port, scheme)).build();
        RestClientTransport transport = new RestClientTransport(lowLevel, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    /**
     * Block until Elasticsearch responds to a ping, or throw after ~30s.
     * Useful during containerized startup where ES may not be ready yet.
     */
    private static void waitForElasticsearch(ElasticsearchClient es) {
        for (int i = 0; i < 30; i++) {
            try {
                if (es.ping().value()) {
                    log.info("Elasticsearch is reachable");
                    return;
                }
            } catch (Exception ignored) {
                // Intentionally ignore and retry
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new RuntimeException("Elasticsearch not reachable after ~30s");
    }

    // Kafka consumer
    /** Create a Kafka consumer configured for manual offset commits and earliest offset reset. */
    private static KafkaConsumer<String, String> createKafkaConsumer(String bootstrap, String groupId) {
        Properties props = new Properties();
        props.setProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // new groups read from beginning
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");  // manual commit after successful bulk
        log.info("Kafka bootstrap: {}, groupId: {}", bootstrap, groupId);
        return new KafkaConsumer<>(props);
    }

    // Document ID derivation
    /**
     * Compute a stable document id.
     * <ul>
     *   <li>Prefer <code>meta.id</code> from the JSON payload for idempotency (upserts de-duplicate).</li>
     *   <li>Fallback to a Kafka-coordinate-based id for uniqueness when meta.id is absent or malformed.</li>
     * </ul>
     */
    private static String computeDocId(ConsumerRecord<String, String> rec) {
        try {
            return JsonParser.parseString(rec.value())
                    .getAsJsonObject()
                    .get("meta").getAsJsonObject()
                    .get("id").getAsString();
        } catch (Exception ignore) {
            // Fallback – unique by Kafka coordinates
            return rec.topic() + "_" + rec.partition() + "_" + rec.offset();
        }
    }

    /** Entry point. Wires clients, subscribes to topic, and runs the poll→bulk-index loop. */
    public static void main(String[] args) throws IOException {
        Map<String, String> cli = parseArgs(args);

        final String esUrl     = get(cli, "es", DEFAULT_ES_URL);
        final String bootstrap = get(cli, "bootstrap", DEFAULT_BOOTSTRAP);
        final String topic     = get(cli, "topic", DEFAULT_TOPIC);
        final String indexName = get(cli, "index", DEFAULT_INDEX);
        final String groupId   = get(cli, "group", DEFAULT_GROUP_PREFIX + System.currentTimeMillis());

        log.info("ES_URL: {}", esUrl);
        log.info("Index: {}", indexName);
        log.info("Topic: {}", topic);

        ElasticsearchClient es = createElasticsearchClient(esUrl);
        KafkaConsumer<String, String> consumer = createKafkaConsumer(bootstrap, groupId);

        // Ensure ES is up before starting consumption to avoid early failures
        waitForElasticsearch(es);

        AtomicBoolean running = new AtomicBoolean(true);
        Thread mainThread = Thread.currentThread();

        // Graceful shutdown: wakeup the consumer and join the main thread
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown hook triggered, calling consumer.wakeup()");
            running.set(false);
            consumer.wakeup();
            try { mainThread.join(); } catch (InterruptedException ignored) {}
        }));

        try (Closeable esClose = () -> { try { es._transport().close(); } catch (Exception ignored) {} };
             consumer) {

            // Create index if it does not exist yet (minimal happy-path setup)
            boolean exists = es.indices().exists(ExistsRequest.of(b -> b.index(indexName))).value();
            if (!exists) {
                // NOTE: In production, define mappings and settings explicitly and/or use index templates.
                es.indices().create(CreateIndexRequest.of(b -> b.index(indexName)));
                log.info("Index '{}' created", indexName);
            } else {
                log.info("Index '{}' already exists", indexName);
            }

            // Subscribe to the configured topic
            consumer.subscribe(Collections.singletonList(topic));
            log.info("Subscribed to topic: {}", topic);

            // === Main poll → bulk index loop ===
            while (running.get()) {
                // Poll with a finite timeout to allow wakeup to interrupt promptly
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(3000));
                int count = records.count();
                if (count == 0) continue;

                log.info("Received {} record(s)", count);

                BulkRequest.Builder bulk = new BulkRequest.Builder();
                int ops = 0, seen = 0, skipped = 0;

                for (ConsumerRecord<String, String> rec : records) {
                    seen++;
                    try {
                        final String docId = computeDocId(rec); // meta.id or Kafka-coordinate fallback

                        BulkOperation op = new BulkOperation.Builder()
                                .index(i -> i
                                        // IMPORTANT: consider using "indexName" here instead of DEFAULT_INDEX
                                        // to honor the CLI-provided index (left as-is to reflect current behavior).
                                        .index(DEFAULT_INDEX)
                                        .id(docId)
                                        // Key: supply the raw JSON payload using JsonData so ES treats it as-is
                                        .document(JsonData.fromJson(rec.value()))
                                )
                                .build();

                        bulk.operations(op);
                        ops++;

                    } catch (Exception ex) {
                        // Defensive: skip malformed JSON or builder errors; consider routing to a DLQ
                        skipped++;
                        log.warn("Skipping record (build/index error): {}", ex.getMessage());
                    }
                }

                log.info("Batch stats: seen={}, toIndex={}, skipped={}", seen, ops, skipped);

                if (ops > 0) {
                    // Execute bulk indexing; inspect per-item responses for partial failures
                    BulkResponse resp = es.bulk(bulk.build());
                    if (resp.errors()) {
                        long errs = resp.items().stream().filter(it -> it.error() != null).count();
                        log.warn("Bulk completed with {} error item(s)", errs);
                        resp.items().stream()
                                .filter(it -> it.error() != null)
                                .limit(3)
                                .forEach(it -> log.warn("Bulk error: {}", it.error().reason()));
                    } else {
                        log.info("Inserted {} document(s)", resp.items().size());
                    }
                    // Commit offsets only after bulk completes; ensures at-least-once semantics
                    consumer.commitSync();
                    log.info("Offsets committed");
                }
            }

        } catch (WakeupException we) {
            // Expected during shutdown; do not treat as error
            log.info("Consumer wakeup -> shutting down");
        } catch (Exception e) {
            // Catch-all: log and fall through to clean up
            log.error("Unexpected exception", e);
        } finally {
            try { es._transport().close(); } catch (Exception ignored) {}
            log.info("Consumer stopped gracefully");
        }
    }
}
