# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Trust any extra local CA certs dropped into backend/certs/*.crt before hitting Maven
# Central. Needed on networks where a local proxy/antivirus (e.g. corporate TLS inspection,
# Avast/Kaspersky "web shield" scanning, etc.) re-signs outbound HTTPS - the host OS trusts
# that CA already, but a fresh container's JDK truststore doesn't. Empty by default (see
# backend/certs/README.md); harmless no-op when there's nothing to import.
COPY certs/ /tmp/certs/
RUN for cert in /tmp/certs/*.crt; do \
      [ -f "$cert" ] || continue; \
      keytool -importcert -noprompt -trustcacerts \
        -alias "local-$(basename "$cert" .crt)" \
        -file "$cert" \
        -keystore "$JAVA_HOME/lib/security/cacerts" \
        -storepass changeit; \
    done

# Cache dependencies separately from source so a source-only change doesn't re-download the world.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
COPY checkstyle.xml .
RUN mvn -B -q clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/url-shortener-platform.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8080

ENV JAVA_OPTS=""
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
