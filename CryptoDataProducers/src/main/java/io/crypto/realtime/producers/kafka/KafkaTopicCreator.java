package io.crypto.realtime.producers;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Utility for idempotently creating Kafka topics.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Idempotent: safe to call multiple times or concurrently.</li>
 *   <li>Fail fast with actionable logs.</li>
 *   <li>Do not fetch all topics to check existence (use describeTopics).</li>
 * </ul>
 */
public final class KafkaTopicCreator {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicCreator.class);

    private KafkaTopicCreator() {
    } // utility class – prevent instantiation

    /**
     * Creates a Kafka topic if it does not already exist. Idempotent.
     *
     * @param bootstrapServers Kafka broker address (e.g. "127.0.0.1:9092")
     * @param topicName        topic name
     * @param partitions       number of partitions
     * @param replication      replication factor (use 1 for local setups)
     * @param configs          optional topic configs (e.g. retention.ms)
     */
    public static void createIfNotExists(
            String bootstrapServers,
            String topicName,
            int partitions,
            short replication,
            Map<String, String> configs
    ) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient admin = AdminClient.create(props)) {

            if (topicExists(admin, topicName)) {
                log.info("Kafka topic already exists: {}", topicName);
                return;
            }

            NewTopic newTopic = new NewTopic(topicName, partitions, replication);
            if (configs != null && !configs.isEmpty()) {
                newTopic.configs(configs);
            }

            log.info("Creating Kafka topic '{}' (partitions={}, replication={}, configs={})",
                    topicName, partitions, replication, (configs == null ? "{}" : configs));

            CreateTopicsResult result = admin.createTopics(Collections.singletonList(newTopic));

            // Wait synchronously – handle potential race with another creator
            result.values().get(topicName).get();

            log.info("Kafka topic created: {}", topicName);

        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                // Created concurrently elsewhere – acceptable
                log.info("Kafka topic '{}' was created concurrently; continuing.", topicName);
                return;
            }
            log.error("Failed to create Kafka topic '{}': {}", topicName, e.getMessage(), e);
            throw new RuntimeException("Failed to create topic: " + topicName, e);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while creating Kafka topic '{}'", topicName, ie);
            throw new RuntimeException("Interrupted while creating topic: " + topicName, ie);
        }
    }

    public static void createIfNotExists(
            String bootstrapServers,
            String topicName,
            int partitions,
            short replication
    ) {
        createIfNotExists(bootstrapServers, topicName, partitions, replication, Collections.emptyMap());
    }

    /**
     * Simple existence check: list all topics and check if the given name is present.
     */
    private static boolean topicExists(AdminClient admin, String topicName) {
        try {
            return admin.listTopics()
                    .names()
                    .get()
                    .contains(topicName);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while listing topics", ie);
        } catch (ExecutionException ee) {
            throw new RuntimeException("Failed to list topics", ee);
        }
    }
}