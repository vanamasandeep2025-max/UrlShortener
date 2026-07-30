# Deployment Guide

## Prerequisites

- Docker Engine + Docker Compose v2 (`docker compose`, not the old `docker-compose`)
- ~4 GB free RAM for the full stack (Postgres, Redis, Kafka, Zookeeper, backend, Nginx,
  Prometheus, Grafana)
- Outbound internet access for the first build (Maven dependencies + base images)

## Local / demo deployment

```bash
cp .env.example .env       # optional - every value has a working default
docker compose up --build
```

This starts, in dependency order (via Compose healthchecks):

1. `postgres`, `redis`, `zookeeper` (no dependencies)
2. `kafka` (waits on `zookeeper`)
3. `backend` (waits on `postgres`, `redis`, `kafka` all reporting healthy) - runs Flyway
   migrations automatically on startup, then seeds two demo accounts (`demo`/`Demo@12345`,
   `admin`/`Admin@12345`) unless `SEED_DEMO_DATA=false`
4. `nginx` (waits on `backend`) - serves `frontend/` and reverse-proxies `/api/*`, Swagger,
   and short-code redirects to `backend`
5. `prometheus` (waits on `backend`), `grafana` (waits on `prometheus`)

Tear down with `docker compose down`; add `-v` to also drop the Postgres/Redis/Grafana
volumes (irreversible - deletes all data).

### Ports (all overridable via `.env`)

| Service | Default host port | Purpose |
|---|---|---|
| nginx | 80 | Main entry point (frontend + API) |
| backend | 8080 | Direct API access (bypassing Nginx), Swagger, Actuator |
| postgres | 5432 | Direct DB access for debugging |
| redis | 6379 | Direct cache access for debugging |
| kafka | 9094 | External (host) Kafka listener |
| prometheus | 9090 | Metrics UI |
| grafana | 3000 | Dashboards |

## Configuration reference

All configuration is environment-variable driven (12-Factor). See `.env.example` for the
full list with defaults. The most important ones to change for anything beyond local demo
use:

| Variable | Why it matters |
|---|---|
| `JWT_SECRET` | Ships with a demo-only default so `docker compose up --build` works with zero setup. **Must** be replaced (`openssl rand -base64 32`) before any real deployment - anyone who reads this repo knows the default. |
| `POSTGRES_PASSWORD` | Same reasoning - the default is a known value. |
| `SEED_DEMO_DATA` | Set to `false` outside local/demo use - the demo accounts' passwords are, again, public knowledge (they're in this README). |
| `CORS_ALLOWED_ORIGINS` | Restrict to your actual frontend origin(s) in production. |
| `RATE_LIMIT_DEFAULT_CAPACITY` / `RATE_LIMIT_DEFAULT_REFILL_SECONDS` | Tune to your expected traffic. |

## Scaling

- **Backend**: stateless by design (no session state, no local caches beyond JVM-internal
  ones) - safe to run multiple replicas behind a load balancer. `docker compose up --scale
  backend=3` works as-is behind Nginx's default round-robin upstream, though for production
  you'd put a real load balancer or Kubernetes Service in front instead of Compose's `--scale`.
- **Kafka consumer**: the analytics consumer group can be scaled horizontally up to the
  `url-clicked` topic's partition count (3, see `config/KafkaTopicConfig`) for parallel
  processing.
- **Postgres/Redis**: single-instance in this Compose file, intentionally - production
  would put these behind managed services (RDS/Cloud SQL, ElastiCache/Memorystore) or a
  properly operated cluster, out of scope for a demo Compose file.

## Running without Docker

See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for running the backend directly against
locally-installed Postgres/Redis/Kafka (useful for IDE debugging).

## Production hardening checklist

This repository is tuned for a realistic-but-demoable local deployment. Before running it
anywhere with real traffic or real data, at minimum:

- [ ] Replace `JWT_SECRET`, `POSTGRES_PASSWORD`, `GRAFANA_ADMIN_PASSWORD` with real secrets
      (ideally injected via a secrets manager, not `.env` in the image/compose file)
- [ ] Set `SEED_DEMO_DATA=false`
- [ ] Put TLS termination in front of Nginx (or terminate TLS at a cloud load balancer)
- [ ] Restrict `CORS_ALLOWED_ORIGINS` to real origins
- [ ] Point Postgres/Redis/Kafka at managed or properly-operated clusters with backups
- [ ] Wire a real `GeoIpService` implementation (MaxMind GeoIP2 or similar) - the shipped
      `NoOpGeoIpService` always returns null country, by design (see
      `util/geo/GeoIpService`)
- [ ] Run `mvn org.owasp:dependency-check-maven:check` and `mvn sonar:sonar` (both need
      network access and, for Sonar, a token) as part of your real release gate - this
      repo's CI wires them but you supply the credentials/server
- [ ] Review `docs/ARCHITECTURE.md`'s security-model section and re-validate the CSRF/JWT
      threat-model reasoning against your actual deployment (e.g. if you ever introduce
      cookie-based auth, CSRF protection must be re-enabled)
