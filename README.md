# Crypto-Realtime-Ingestion
Real-time cryptocurrency data ingestion pipeline built with WebSocket and Apache Kafka, extended with ksqlDB transformations and Elasticsearch for analytics.


# Generate your es password

docker exec -it crypto-realtime-ingestion-elasticsearch-1 bin/elasticsearch-reset-password -u elastic

# Generate your kibana api key 
curl -X POST "http://localhost:9200/_security/api_key" -u elastic:${ELASTICSEARCH_PASSWORD} -H "Content-Type: application/json" -d '{"name": "my-api-key"}'

# Generate your kibana service token 

docker exec -it crypto-realtime-ingestion-elasticsearch-1 bin/elasticsearch-service-tokens create elastic/kibana kibana-service-token