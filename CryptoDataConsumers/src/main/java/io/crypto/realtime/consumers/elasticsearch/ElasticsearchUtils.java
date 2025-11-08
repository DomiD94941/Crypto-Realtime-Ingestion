package io.crypto.realtime.consumers.elasticsearch;

import com.google.gson.JsonParser;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.HashMap;
import java.util.Map;

public class ElasticsearchUtils {

    public static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--")) {
                String key = a.substring(2);
                String val;
                int eq = key.indexOf('=');
                if (eq >= 0) {
                    val = key.substring(eq + 1);
                    key = key.substring(0, eq);
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    val = args[++i];
                } else val = "true";
                m.put(key, val);
            }
        }
        return m;
    }

    public static String get(Map<String, String> m, String key, String def) {
        String v = m.get(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public static String computeDocId(ConsumerRecord<String, String> rec) {
        try {
            return JsonParser.parseString(rec.value())
                    .getAsJsonObject()
                    .get("meta").getAsJsonObject()
                    .get("id").getAsString();
        } catch (Exception ignore) {
            return rec.topic() + "_" + rec.partition() + "_" + rec.offset();
        }
    }
}
