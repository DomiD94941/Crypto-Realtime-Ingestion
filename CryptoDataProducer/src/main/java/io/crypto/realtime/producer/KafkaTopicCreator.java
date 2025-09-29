package io.crypto.realtime.producer;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.errors.TopicExistsException;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public final class KafkaTopicCreator {

    private KafkaTopicCreator() {} // utility class – prevent instantiation

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
            // Check if topic already exists
            if (topicExists(admin, topicName)) return;

            NewTopic newTopic = new NewTopic(topicName, partitions, replication);
            if (configs != null && !configs.isEmpty()) {
                newTopic.configs(configs);
            }

            CreateTopicsResult result = admin.createTopics(Collections.singletonList(newTopic));
            // Wait synchronously – if someone else creates the topic in the meantime, we catch TopicExistsException
            result.values().get(topicName).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                // Topic was created by someone else at the same time – that's fine
                return;
            }
            throw new RuntimeException("Failed to create topic: " + topicName, e);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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

    private static boolean topicExists(AdminClient admin, String topicName) {
        try {
            return admin.listTopics().names().get().contains(topicName);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while listing topics", ie);
        } catch (ExecutionException ee) {
            throw new RuntimeException("Failed to list topics", ee);
        }
    }
}
