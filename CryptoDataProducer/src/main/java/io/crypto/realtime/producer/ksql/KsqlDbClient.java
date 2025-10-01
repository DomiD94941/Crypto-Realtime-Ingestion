package io.crypto.realtime.producer.ksql;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.io.IOException;
import java.util.Objects;

/**
 * Simple REST client for interacting with a ksqlDB server.
 *
 * Provides a method to execute arbitrary KSQL statements via the ksqlDB REST API.
 * Intended to be used by producers or initializers to create streams, tables,
 * or perform other KSQL operations programmatically.
 */
public class KsqlDbClient {

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
        try {
            // Wrap the query into a proper JSON payload
            String payload = mapper.writeValueAsString(new KsqlRequest(ksqlScript));

            Request req = new Request.Builder()
                    .url(baseUrl + "/ksql")
                    .post(RequestBody.create(payload, MediaType.parse("application/json")))
                    .build();

            // Execute synchronously and handle response
            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    // Read error details if available
                    String err = resp.body() != null ? resp.body().string() : ("HTTP " + resp.code());
                    throw new RuntimeException("ksqlDB error: " + err);
                }
                return resp.body() != null ? resp.body().string() : "";
            }
        } catch (IOException e) {
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
