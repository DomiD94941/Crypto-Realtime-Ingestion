FROM gradle:8.7-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle clean build -x test

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /app/CryptoDataConsumers/build/libs/*.jar consumer.jar
COPY --from=builder /app/CryptoDataProducers/build/libs/*.jar producer.jar
COPY --from=builder /app/Dashboards/build/libs/*.jar dashboards.jar

ENV APP=btc-consumer

ENTRYPOINT ["/bin/sh", "-c", "\
  echo 'Starting module: '$APP && \
  if [ \"$APP\" = 'btc-consumer' ]; then \
      java -jar consumer.jar; \
  elif [ \"$APP\" = 'btc-producer' ]; then \
      java -jar producer.jar; \
  elif [ \"$APP\" = 'dashboards' ]; then \
      java -jar dashboards.jar; \
  else \
      echo 'Unknown APP value: '$APP; exit 1; \
  fi"]
