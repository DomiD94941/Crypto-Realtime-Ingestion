FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY gradlew .
COPY gradle/ gradle/
COPY settings.gradle build.gradle .

RUN chmod +x gradlew
RUN ./gradlew --no-daemon dependencies || true

COPY CryptoDataProducers/ CryptoDataProducers/
COPY CryptoDataConsumers/ CryptoDataConsumers/

RUN ./gradlew clean shadowJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/CryptoDataProducers/build/libs/*-all.jar producer.jar
COPY --from=build /app/CryptoDataConsumers/build/libs/*-all.jar consumer.jar

COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh

ENTRYPOINT ["/app/start.sh"]
