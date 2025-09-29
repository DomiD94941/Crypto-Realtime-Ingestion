package io.crypto.realtime.producer.ksql;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.io.IOException;
import java.util.Objects;

public class KsqlDbClient {
    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public KsqlDbClient(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl);
    }

    public String execute(String ksqlScript) {
        try {
            String payload = mapper.writeValueAsString(new KsqlRequest(ksqlScript));
            Request req = new Request.Builder()
                    .url(baseUrl + "/ksql")
                    .post(RequestBody.create(payload, MediaType.parse("application/json")))
                    .build();

            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    String err = resp.body() != null ? resp.body().string() : ("HTTP " + resp.code());
                    throw new RuntimeException("ksqlDB error: " + err);
                }
                return resp.body() != null ? resp.body().string() : "";
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to call ksqlDB REST API", e);
        }
    }

    // prosty holder na JSON { "ksql": "...", "streamsProperties": {} }
    static class KsqlRequest {
        public final String ksql;
        public final Object streamsProperties = new java.util.HashMap<>();
        KsqlRequest(String ksql) { this.ksql = ksql; }
    }
}
