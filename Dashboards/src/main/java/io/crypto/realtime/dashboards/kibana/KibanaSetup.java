package io.crypto.realtime.dashboards.kibana;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KibanaSetup {

    private static final Logger log = LoggerFactory.getLogger(KibanaSetup.class);

    /**
     * Creates a Data View in Kibana for the given index
     */
    public static void createDataView(String kibanaUrl, String apiKey, String indexName, String timeField) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            log.info("Creating Kibana data view for index: {}", indexName);

            String payload = String.format("""
                {
                  "data_view": {
                    "title": "%s",
                    "name": "%s",
                    "timeFieldName": "%s"
                  }
                }
                """, indexName, indexName, timeField);

            HttpPost post = new HttpPost(kibanaUrl + "/api/data_views/data_view");
            post.setHeader("kbn-xsrf", "true");
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Authorization", "ApiKey " + apiKey);
            post.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));

            client.execute(post).close();
            log.info("Data view created (or already exists)");
        } catch (Exception e) {
            log.error("Error creating data view", e);
        }
    }

    /**
     * Creates a BTC visualization and dashboard in Kibana automatically
     */
    public static void createDashboard(String kibanaUrl, String apiKey, String indexName) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {

            // Step 1: Create Visualization
            log.info("Creating BTC price visualization...");

            String visPayload = """
                {
                  "attributes": {
                    "title": "BTC Price Over Time",
                    "visualizationType": "lnsXY",
                    "state": {
                      "datasourceStates": {
                        "indexpattern": {
                          "layers": {
                            "layer1": {
                              "columns": {
                                "x": {
                                  "dataType": "date",
                                  "label": "@timestamp",
                                  "operationType": "date_histogram",
                                  "sourceField": "@timestamp"
                                },
                                "y": {
                                  "dataType": "number",
                                  "label": "avg.price",
                                  "operationType": "average",
                                  "sourceField": "price"
                                }
                              },
                              "columnOrder": ["x", "y"]
                            }
                          }
                        }
                      },
                      "visualization": {
                        "layers": [{ "layerId": "layer1", "accessors": ["y"], "xAccessor": "x" }]
                      }
                    }
                  }
                }
                """;

            HttpPost visReq = new HttpPost(kibanaUrl + "/api/saved_objects/visualization");
            visReq.setHeader("kbn-xsrf", "true");
            visReq.setHeader("Content-Type", "application/json");
            visReq.setHeader("Authorization", "ApiKey " + apiKey);
            visReq.setEntity(new StringEntity(visPayload, ContentType.APPLICATION_JSON));
            client.execute(visReq).close();

            // Step 2: Create Dashboard
            log.info("Creating BTC Realtime Dashboard...");

            String dashPayload = """
                {
                  "attributes": {
                    "title": "BTC Realtime Dashboard",
                    "panelsJSON": "[]"
                  }
                }
                """;

            HttpPost dashReq = new HttpPost(kibanaUrl + "/api/saved_objects/dashboard");
            dashReq.setHeader("kbn-xsrf", "true");
            dashReq.setHeader("Content-Type", "application/json");
            dashReq.setHeader("Authorization", "ApiKey " + apiKey);
            dashReq.setEntity(new StringEntity(dashPayload, ContentType.APPLICATION_JSON));
            client.execute(dashReq).close();

            log.info("BTC Realtime Dashboard created successfully!");

        } catch (Exception e) {
            log.error("Error creating Kibana dashboard", e);
        }
    }
}
