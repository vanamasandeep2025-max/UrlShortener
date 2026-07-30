# URL Shortener Platform

An enterprise-grade URL shortening platform: create short links, redirect at scale, track
click analytics (browser/OS/device/country/referrer/daily trend), and manage everything
through a JWT + API-key secured REST API and a small Bootstrap dashboard.

Built as a demonstration of **AI-assisted, engineer-controlled software engineering** — see
[`/AI_ENGINEERING`](AI_ENGINEERING/) for the full requirement-to-code traceability, prompts
used, what was accepted/rejected/modified, and why.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring Security, Spring Data JPA, Spring Validation, Spring Actuator |
| Data | PostgreSQL 16, Flyway, Redis 7 |
| Messaging | Apache Kafka (+ Zookeeper) |
| Frontend | HTML5, CSS3, vanilla JavaScript, Bootstrap 5 |
| Infra | Docker, Docker Compose, Nginx, Prometheus, Grafana |
| CI/CD | GitHub Actions |
| Testing | JUnit 5, Mockito, Testcontainers, k6 |

## Quick start

```bash
git clone <this-repo>
cd url-shortener-platform
cp .env.example .env   # optional - safe defaults ship out of the box
docker compose up --build
```

Then open:

- **App**: http://localhost — sign in with `demo` / `Demo@12345` (or `admin` / `Admin@12345`)
- **Swagger UI**: http://localhost/swagger-ui.html
- **Grafana**: http://localhost:3000 (`admin` / `admin` by default)
- **Prometheus**: http://localhost:9090

That single command brings up Postgres, Redis, Kafka+Zookeeper, the Spring Boot backend
(Flyway migrations run automatically on startup), Nginx serving the frontend + reverse
proxy, Prometheus, and Grafana (pre-provisioned with a dashboard).

For anything beyond the happy path — running the backend outside Docker, running tests,
troubleshooting a specific container — see the docs below.

## API at a glance

```
POST   /api/v1/auth/register              Register + receive a token pair
POST   /api/v1/auth/login                 Log in
POST   /api/v1/auth/refresh               Exchange a refresh token
POST   /api/v1/urls                       Shorten a URL
GET    /api/v1/urls                       List (paginated, sortable, filterable, searchable)
DELETE /api/v1/urls/{id}                  Soft-delete
PATCH  /api/v1/urls/{id}                  Update/clear expiry
GET    /api/v1/urls/{shortCode}/analytics Click analytics
POST   /api/v1/urls/{shortCode}/verify-password  Unlock a password-protected link
POST   /api/v1/api-keys                   Create an API key (X-API-Key auth)
GET    /{shortCode}                       302 redirect (public)
```

Full interactive docs at `/swagger-ui.html`; a ready-to-import collection is at
[`docs/postman_collection.json`](docs/postman_collection.json).

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — component diagram, redirect/shorten/analytics
  sequence diagrams, caching strategy, Kafka retry/DLQ flow, design patterns used.
- [Deployment Guide](docs/DEPLOYMENT.md) — Docker Compose in depth, configuration
  reference, running in production, scaling notes.
- [Developer Guide](docs/DEVELOPER_GUIDE.md) — running the backend/frontend outside
  Docker, package structure, coding standards, running the test suite.
- [Troubleshooting Guide](docs/TROUBLESHOOTING.md) — common failure modes and fixes.
- [AI Engineering](AI_ENGINEERING/) — the requirement analysis, task breakdown, prompts,
  review checklist, risk analysis, validation report, and three worked scenarios
  (greenfield, brownfield, ambiguous-requirement) that document how AI assistance was
  used and reviewed throughout this build.

## Repository layout

```
backend/    Spring Boot 3 / Java 21 (Maven)
frontend/   HTML5 / CSS3 / vanilla JS / Bootstrap 5, served by Nginx
infra/      nginx.conf, prometheus.yml, Grafana provisioning + dashboard
perf/       k6 load test for the redirect hot path
docs/       Architecture, deployment, developer, troubleshooting docs + Postman collection
AI_ENGINEERING/  Requirement-to-code traceability and the three required scenarios
.github/workflows/ci.yml  Compile -> static analysis -> unit tests -> integration tests -> Docker build
```

## What's genuinely tested vs. what's config-only

In the spirit of honest engineering documentation (see `AI_ENGINEERING/08_validation_report.md`
for the full breakdown, including three real bugs the validation pass found and fixed):
core domain logic (URL service, auth, JWT, short-code generation, rate limiting, redirect
controller) has real unit + integration test coverage, including an end-to-end Testcontainers
test that exercises the full shorten -> redirect -> Kafka -> analytics pipeline. Measured
JaCoCo coverage is **37.5% instruction / 35.5% line** - real depth on the highest-value 20%
of the codebase, not the spec's 90% target (see `AI_ENGINEERING/05_ai_traceability.md` Task 10
for why that tradeoff was made deliberately). Checkstyle, PMD, and SpotBugs all run for real
and were exercised during this build's own validation pass. OWASP Dependency-Check and
SonarCloud are wired into the Maven build but need network/external-service access, so
they're CI-only, not something you can fully verify offline.

## License

Provided as-is for evaluation/demonstration purposes.
