package io.crypto.realtime.consumer.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.JsonData;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.List;

public class ElasticsearchService implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchService.class);
    private final ElasticsearchClient client;

    public ElasticsearchService(String connString) {
        this.client = createClient(connString);
    }

    private ElasticsearchClient createClient(String connString) {
        URI uri = URI.create(connString);
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        String host = uri.getHost() == null ? "localhost" : uri.getHost();
        int port = (uri.getPort() == -1) ? ("https".equalsIgnoreCase(scheme) ? 443 : 9200) : uri.getPort();
        RestClient lowLevel = RestClient.builder(new HttpHost(host, port, scheme)).build();
        RestClientTransport transport = new RestClientTransport(lowLevel, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    public void waitUntilReady() {
        for (int i = 0; i < 30; i++) {
            try {
                if (client.ping().value()) {
                    log.info("Elasticsearch is reachable");
                    return;
                }
            } catch (Exception ignored) {}
            try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
        throw new RuntimeException("Elasticsearch not reachable after ~30s");
    }

    public void ensureIndexExists(String index) throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(b -> b.index(index))).value();
        if (!exists) {
            client.indices().create(CreateIndexRequest.of(b -> b.index(index)));
            log.info("Index '{}' created", index);
        } else {
            log.info("Index '{}' already exists", index);
        }
    }

    public void bulkInsert(String index, List<BulkOperation> operations) throws IOException {
        BulkRequest request = new BulkRequest.Builder().operations(operations).build();
        BulkResponse response = client.bulk(request);
        if (response.errors()) {
            long errors = response.items().stream().filter(i -> i.error() != null).count();
            log.warn("Bulk completed with {} error item(s)", errors);
        } else {
            log.info("Inserted {} documents", response.items().size());
        }
    }

    @Override
    public void close() throws IOException {
        client._transport().close();
    }
}
