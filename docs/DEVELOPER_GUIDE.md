# Developer Guide

## Package structure (backend)

```
com.urlshortener
├── controller     REST endpoints (thin - validation + delegation only)
├── service        Interfaces + service/impl business logic
├── repository     Spring Data JPA repositories + Specifications + projections
├── entity         JPA entities
├── dto            request/ and response/ - never expose entities over HTTP
├── mapper         MapStruct entity <-> DTO mappers
├── exception      Custom exceptions + GlobalExceptionHandler
├── config         Spring @Configuration classes (security, cache, Kafka, OpenAPI, ...)
├── security       JWT (jwt/), API keys (apikey/), filters, RBAC helpers
├── cache          Redis rate limiter
├── events         Domain event / Kafka payload classes (shared - see ARCHITECTURE.md)
├── consumer       @KafkaListener consumers (Template Method base + concrete impls)
├── producer       Kafka publishing (KafkaEventPublisher, DomainEventKafkaBridge)
├── audit          AuditService (append-only audit_logs writes)
├── metrics        Custom Micrometer counters
├── util           Short-code generation, UA parsing, geo lookup, IP hashing/resolution
└── validation      Custom Bean Validation constraints (@ValidHttpUrl, @NoScriptTag)
```

## Running outside Docker

You'll need local Postgres 16, Redis 7, and Kafka running (or reuse the ones from
`docker compose up postgres redis kafka zookeeper` and just run the backend on the host).

```bash
cd backend
export POSTGRES_HOST=localhost REDIS_HOST=localhost KAFKA_BOOTSTRAP_SERVERS=localhost:9092
mvn spring-boot:run
```

The default (`application.yml`, no profile) config already points at `localhost` for
everything, so if you're running Postgres/Redis/Kafka on their default ports locally, you
often don't need to export anything.

Frontend: it's static files with no build step - open `frontend/index.html` directly, or
serve the directory with any static file server. Note the frontend calls the API via
relative paths (`/api/v1/...`), so serve it from something that proxies `/api` to the
backend (e.g. run it through the Nginx container, or point a simple dev proxy at
`localhost:8080`) rather than opening it via `file://`.

## Running the test suite

```bash
cd backend
mvn test                 # unit tests only (fast, no Docker required)
mvn verify                # + integration tests (Testcontainers - needs Docker running)
mvn test jacoco:report    # unit tests + HTML coverage report at target/site/jacoco/index.html
```

Static analysis (matches the CI `static-analysis` job):

```bash
mvn checkstyle:check
mvn pmd:check
mvn compile test-compile com.github.spotbugs:spotbugs-maven-plugin:check
```

OWASP Dependency-Check and SonarCloud need network access to their respective
databases/servers and are not expected to run fully offline:

```bash
mvn org.owasp:dependency-check-maven:check   # downloads the NVD feed
mvn sonar:sonar -Dsonar.token=$SONAR_TOKEN   # needs a SonarCloud/SonarQube server
```

## Coding standards

- **SOLID / Clean Architecture**: controllers depend on service interfaces, not
  implementations; entities never leave the service layer; the `UrlRedirectTarget` record
  exists specifically so the hot redirect path doesn't leak a JPA entity into caching/HTTP.
- **Constructor injection only** (`@RequiredArgsConstructor` + `final` fields) - no field
  injection, so every class is constructible (and testable) with plain `new` + mocks.
- **DRY without premature abstraction**: e.g. two short-code strategies exist because
  they're both real and used (see `ARCHITECTURE.md`), not as a speculative "just in case"
  extension point.
- **Ownership checks live in the service layer**, not only in `@PreAuthorize` annotations -
  keeps authorization logic testable without spinning up Spring Security.
- Comments explain *why*, not *what* - see existing code for the intended density
  (sparse; only where a decision isn't obvious from reading the code itself).

## Adding a new Kafka-consumed event type

1. Add the payload class to `events/` (used as both the Spring `ApplicationEvent` payload
   and the Kafka message body - see `UrlClickedEvent` for the pattern).
2. If it originates from a service-layer write, publish it via `ApplicationEventPublisher`
   rather than calling Kafka directly (keeps the service layer Kafka-agnostic and testable).
3. Add a forwarding listener in `producer/DomainEventKafkaBridge` (use
   `@TransactionalEventListener(phase = AFTER_COMMIT)` if the publish happens inside a
   `@Transactional` method; a plain `@EventListener` otherwise).
4. Add the topic to `config/KafkaTopicConfig` and `application.yml`'s `app.kafka.topics`.
5. Extend `consumer/AbstractKafkaEventProcessor` for the consumer side; keep the actual
   transactional persistence in a separate `@Service` bean (see
   `service/impl/UrlClickIngestionService`) so `@Transactional` isn't silently skipped by
   self-invocation from the `@KafkaListener` method.

## Regenerating MapStruct mappers

MapStruct implementations are generated at compile time via the annotation processor
(configured in `pom.xml`) - there's nothing to run manually; `mvn compile` regenerates
`target/generated-sources/annotations/**/*MapperImpl.java` from the `@Mapper` interfaces
in `mapper/`.
