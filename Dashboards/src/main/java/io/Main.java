package io.crypto.realtime.dashboards;

import io.crypto.realtime.dashboards.btc.BtcDashboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            String kibanaUrl = System.getenv().getOrDefault("KIBANA_URL", "http://kibana:5601");
            String apiKey = System.getenv().getOrDefault("KIBANA_API_KEY", "changeme");
            String indexName = System.getenv().getOrDefault("INDEX_NAME", "crypto");

            BtcDashboard dashboard = new BtcDashboard(kibanaUrl, apiKey, indexName);
            dashboard.setup();

            log.info("Dashboard successfully created for index: {}", indexName);
        } catch (Exception e) {
            log.error("Failed to create dashboard", e);
        }
    }
}
