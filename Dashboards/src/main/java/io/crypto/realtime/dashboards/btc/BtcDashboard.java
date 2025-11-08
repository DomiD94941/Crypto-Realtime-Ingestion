package io.crypto.realtime.dashboards.btc;

import io.crypto.realtime.dashboards.kibana.KibanaSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BtcDashboard {

    private static final Logger log = LoggerFactory.getLogger(BtcDashboard.class);

    private final String kibanaUrl;
    private final String apiKey;
    private final String indexName;

    public BtcDashboard(String kibanaUrl, String apiKey, String indexName) {
        this.kibanaUrl = kibanaUrl;
        this.apiKey = apiKey;
        this.indexName = indexName;
    }

    public void setup() {
        log.info("Setting up BTC Kibana dashboard...");

        // Step 1: create data view
        KibanaSetup.createDataView(kibanaUrl, apiKey, indexName, "@timestamp");

        // Step 2: import or create dashboard
        KibanaSetup.createDashboard(kibanaUrl, apiKey, indexName);

        log.info("BTC Kibana dashboard setup complete.");
    }
}
