# Troubleshooting Guide

## `docker compose up --build` fails or hangs

**Backend container keeps restarting / never becomes healthy**
1. `docker compose logs backend` - by far the most common cause is Flyway failing because
   Postgres wasn't actually ready yet. The `backend` service's `depends_on` uses
   `condition: service_healthy` for exactly this reason; if you still see connection
   refused errors, check `docker compose logs postgres` for its own startup errors first.
2. `Caused by: ... password authentication failed` - your `.env`'s `POSTGRES_PASSWORD`
   doesn't match what the `postgres` container was *originally* initialized with. Postgres
   only applies `POSTGRES_PASSWORD` on first init of the data volume - if you changed the
   password after the volume already existed, either `docker compose down -v` (destroys
   data) or manually `ALTER USER` inside the running container.

**`kafka` never reports healthy**
- The healthcheck runs `kafka-topics --bootstrap-server localhost:9092 --list` inside the
  container; on slower machines the default `start_period: 30s` may not be enough for
  Kafka to finish its own startup after Zookeeper. Check `docker compose logs kafka` - if
  it's still initializing, just wait; Compose will keep retrying per the healthcheck's
  `retries: 10`.

**Port already in use (`bind: address already in use`)**
- Something on your host is already using port 80/5432/6379/9090/3000/9094. Either stop
  that process or change the relevant `*_PORT` variable in `.env`.

## App loads but nothing works

**Frontend loads but every API call fails with a network error**
- You likely opened `frontend/index.html` directly via `file://` instead of going through
  Nginx (`http://localhost`). The frontend calls relative `/api/v1/...` paths that only
  resolve correctly when served from the same origin Nginx is proxying. Use
  `http://localhost` (port 80), not the file path.

**`401 Unauthorized` on every authenticated request**
- Check the browser console/localStorage for `usp_access_token` - if login never
  succeeded, there's nothing to send. Also check whether the token expired
  (`JWT_EXPIRATION_MS`, default 1 hour) - the frontend's `js/api.js` transparently retries
  once via `/api/v1/auth/refresh`, but only if a refresh token is also present.

**`403 Forbidden` when deleting/editing someone else's link**
- Working as intended - ownership is enforced in `UrlServiceImpl.loadOwned()`. Only the
  creating user or an `ADMIN` can modify a link.

**`429 Too Many Requests`**
- You've hit the rate limiter (`RATE_LIMIT_DEFAULT_CAPACITY` requests per
  `RATE_LIMIT_DEFAULT_REFILL_SECONDS` window, keyed by API key or IP). The
  `/verify-password` endpoint has a much tighter limit (5/60s) by design - it's the
  brute-force target for password-protected links. Check the `Retry-After` response header.

## Redirects / analytics

**Redirect returns 404 for a code I just created**
- The redirect cache (`urlLookup`) is populated on first read, not on write, so this
  should not happen for a genuinely existing code - double check the short code was
  copied correctly (case-sensitive) and that the link hasn't been soft-deleted.

**Clicked a link, but analytics still shows 0 clicks a while later**
1. Click tracking is asynchronous by design (redirect -> Kafka -> consumer -> Postgres);
   allow a few seconds.
2. Check `docker compose logs backend` for consumer errors - if a click event keeps
   failing, it'll retry a few times (`app.kafka.retry.max-attempts`) then land on the
   `dead-letter` topic. You can inspect that topic directly:
   ```bash
   docker compose exec kafka kafka-console-consumer \
     --bootstrap-server localhost:9092 --topic dead-letter --from-beginning
   ```
3. Remember the analytics endpoint caches non-zero results for `app.cache.analytics-ttl-seconds`
   (default 60s) - a *zero-click* result is deliberately never cached (see
   `AnalyticsServiceImpl`), but once real clicks exist, subsequent reads can be up to that
   TTL stale.

**Password-protected link redirects to a blank/broken page**
- The redirect controller 302s to `{APP_BASE_URL}/protected.html?code=...`. If
  `APP_BASE_URL` doesn't match the origin your browser is actually using (e.g. you're on
  `http://localhost` but `APP_BASE_URL` is set to something else), you'll land on the
  wrong host. Keep `APP_BASE_URL` aligned with whatever origin Nginx is actually serving.

## Tests

**`mvn verify` hangs or fails with Docker-related errors**
- Integration tests use Testcontainers, which needs a working Docker daemon reachable
  from wherever `mvn` runs. On some setups (rootless Docker, remote Docker hosts) you may
  need `DOCKER_HOST`/`TESTCONTAINERS_*` environment variables - see
  https://www.testcontainers.org/features/configuration/.

**`mvn test` fails on a class that looks unrelated to what you changed**
- Run just that test class (`mvn test -Dtest=ClassName`) for a clearer stack trace - most
  failures in this codebase are either a Mockito stubbing mismatch (check the method
  signature you're stubbing matches exactly) or a validator regression.

## Observability

**Grafana dashboard panels show "No data"**
- Confirm Prometheus is actually scraping the backend: http://localhost:9090/targets
  should show `url-shortener-backend` as `UP`. If it's down, check
  `http://localhost:8080/actuator/prometheus` responds directly first.
- The dashboard only includes metrics this codebase genuinely emits (see
  `AI_ENGINEERING/08_validation_report.md`) - it deliberately does not include a Kafka
  consumer-lag panel, since that metric isn't wired up (would need `KafkaClientMetrics`
  binding, out of scope for this build).

## Still stuck?

Check `docker compose ps` for container status, `docker compose logs -f <service>` for
live logs, and `/actuator/health` (with `show-details: always` under the `docker` profile)
for a component-by-component health breakdown of the backend itself.
