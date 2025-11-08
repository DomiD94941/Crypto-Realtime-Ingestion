package io.crypto.realtime.producers.ksql;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Simple REST client for interacting with a ksqlDB server.
 * Provides a method to execute arbitrary KSQL statements via the ksqlDB REST API.
 * Intended to be used by producers or initializers to create streams, tables,
 * or perform other KSQL operations programmatically.
 */
public class KsqlDbClient {

    private static final Logger log = LoggerFactory.getLogger(KsqlDbClient.class);

    // HTTP client for REST requests
    private final OkHttpClient http = new OkHttpClient();

    // JSON serializer/deserializer
    private final ObjectMapper mapper = new ObjectMapper();

    // Base URL of the ksqlDB server (e.g., http://localhost:8088)
    private final String baseUrl;

    /**
     * Creates a new KsqlDbClient instance.
     *
     * @param baseUrl The base URL of the ksqlDB server (without trailing slash).
     */
    public KsqlDbClient(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl);
    }

    /**
     * Executes a KSQL statement against the ksqlDB REST API.
     *
     * @param ksqlScript The KSQL command or script to execute.
     * @return The raw JSON response body from ksqlDB as a String.
     * @throws RuntimeException if the request fails or ksqlDB returns an error.
     */
    public String execute(String ksqlScript) {
        long startNanos = System.nanoTime();

        try {
            // Wrap the query into a proper JSON payload
            String payload = mapper.writeValueAsString(new KsqlRequest(ksqlScript));

            Request req = new Request.Builder()
                    .url(baseUrl + "/ksql")
                    .post(RequestBody.create(payload, MediaType.parse("application/json")))
                    .build();

            if (log.isDebugEnabled()) {
                // Avoid logging the full script if it might contain secrets; log length instead.
                log.debug("Sending KSQL request to {}/ksql (scriptLength={} chars)", baseUrl, ksqlScript.length());
            }

            // Execute synchronously and handle response
            try (Response resp = http.newCall(req).execute()) {
                long tookMs = (System.nanoTime() - startNanos) / 1_000_000;
                int code = resp.code();

                if (!resp.isSuccessful()) {
                    String errBody = resp.body() != null ? resp.body().string() : ("HTTP " + code);
                    log.error("ksqlDB request failed (status={} took={}ms). Error body: {}", code, tookMs, errBody);
                    throw new RuntimeException("ksqlDB error: " + errBody);
                }

                String body = resp.body() != null ? resp.body().string() : "";
                log.info("ksqlDB request succeeded (status={} took={}ms)", code, tookMs);
                if (log.isDebugEnabled()) {
                    log.debug("ksqlDB response body length: {} chars", body.length());
                }
                return body;
            }
        } catch (IOException e) {
            long tookMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.error("Failed to call ksqlDB REST API (took={}ms): {}", tookMs, e.getMessage(), e);
            throw new RuntimeException("Failed to call ksqlDB REST API", e);
        }
    }

    /**
     * Simple request wrapper for KSQL execution.
     * Generates a JSON object in the form:
     * {
     *   "ksql": "...",
     *   "streamsProperties": {}
     * }
     */
    static class KsqlRequest {
        public final String ksql;
        public final Object streamsProperties = new java.util.HashMap<>();
        KsqlRequest(String ksql) { this.ksql = ksql; }
    }
}
