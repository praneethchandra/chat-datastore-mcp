# ========== Build Stage ==========
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -e -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -e -B -DskipTests package

# ========== Runtime Stage ==========
FROM eclipse-temurin:17-jre
ENV JAVA_OPTS=""     OTEL_SERVICE_NAME="chat-datastore"     OTEL_EXPORTER_OTLP_ENDPOINT="http://otel-collector:4317"     OTEL_TRACES_EXPORTER="otlp"     OTEL_METRICS_EXPORTER="otlp"     OTEL_LOGS_EXPORTER="otlp"     OTEL_RESOURCE_ATTRIBUTES="service.namespace=chat,service.version=0.1.0,deployment.environment=local"
# Download OTEL Java agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.8.0/opentelemetry-javaagent.jar /otel-javaagent.jar
WORKDIR /app
COPY --from=build /workspace/target/chat-datastore-mcp-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh","-c","java -javaagent:/otel-javaagent.jar $JAVA_OPTS -jar app.jar"]
