# 02 — Task Breakdown

The engineer (user) reviewed the full requirement spec and, before any code was written,
required the AI assistant to enter a planning mode and produce a phased delivery plan for
explicit sign-off (rather than generating the entire system in one uncontrolled pass). The
approved plan - preserved verbatim in the session's plan record - broke the work into 12
phases, executed in order:

| Phase | Scope | Key files/artifacts |
|---|---|---|
| 1 | Scaffold | `pom.xml`, package structure, `application*.yml`, `logback-spring.xml`, `.gitignore` |
| 2 | Database | 5 Flyway migrations (`users`, `urls`, `url_clicks`, `audit_logs`, `api_keys`) |
| 3 | Core domain & API | Entities, DTOs, mappers, repositories, `UrlService`/`AnalyticsService`, controllers, custom validators, `GlobalExceptionHandler` |
| 4 | Security | JWT (access+refresh), API keys, RBAC, Redis-backed rate limiting, `SecurityConfig` |
| 5 | Cache & messaging | `CacheConfig` (per-cache TTL), Kafka topics/producer/consumer, UA parsing, DLQ+retry, correlation IDs, structured logging, custom metrics |
| 6 | Frontend | 5 static pages (index/login/analytics/protected/api-keys) + shared `api.js` |
| 7 | Tests | Unit (Mockito), standalone-MockMvc controller tests, Testcontainers repository + full-stack integration tests, k6 perf script |
| 8 | Infra | Backend `Dockerfile`, root `docker-compose.yml` (8 services), Nginx, Prometheus, Grafana provisioning |
| 9 | CI/CD | GitHub Actions: compile -> static analysis -> unit tests -> integration tests -> Docker build |
| 10 | Documentation | README, `ARCHITECTURE.md`, `DEPLOYMENT.md`, `DEVELOPER_GUIDE.md`, `TROUBLESHOOTING.md`, Postman collection |
| 11 | AI Engineering docs | This folder |
| 12 | End-to-end validation | `docker compose up --build` + real smoke test, reported honestly |

## Why this order

- **Database before code**: the schema is the contract everything else builds on; getting
  it wrong late is expensive to unwind.
- **Security folded into the same pass as the controllers it protects** (phases 3-4 were
  executed together in practice) rather than bolted on afterward - retrofitting auth onto
  an already-"complete" API tends to produce superficial `@PreAuthorize` annotations without
  real ownership-check logic.
- **Tests before infra**: validating the application logic in isolation (Mockito, MockMvc,
  Testcontainers) before wrapping it in Docker means a Docker build failure is more likely
  to be an infra problem, not a hidden logic bug surfacing for the first time.
- **Documentation last, validation dead last**: docs describe what was actually built, not
  what was planned to be built - writing them before the code stabilizes risks describing
  intentions rather than reality.

## Deviations from the original plan during execution

Two mid-flight corrections are worth recording here as evidence of active engineering
review rather than passive AI-output acceptance (full detail in `05_ai_traceability.md`):

1. The plan's Phase 4 named Bucket4j-Redis as the rate-limiting mechanism. During
   implementation this was replaced with a hand-rolled Redis+Lua fixed-window limiter -
   judged lower-risk given the inability to compile-check the Bucket4j/Lettuce integration
   step by step (see Task 4 in `05_ai_traceability.md`).
2. Two real bugs were caught by manual review *after* first-draft generation and before
   they shipped: a JPA bulk-update stale-cache issue (`UrlRepository`'s `@Modifying` queries
   needed `clearAutomatically = true`) and an analytics cache-poisoning issue (a freshly
   created link's zero-click analytics snapshot would otherwise get cached and hide the
   first real click for up to 60 seconds). Both are detailed in `05_ai_traceability.md`.
