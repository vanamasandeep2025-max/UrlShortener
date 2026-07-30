# Software Requirements Specification — URL Shortener Platform

**Document type:** As-built SRS, reverse-engineered from the running codebase (not a pre-implementation spec).
**Repo:** `url-shortener-platform` (Spring Boot 3.3 / Java 21, PostgreSQL 16, Redis 7, Kafka, Nginx, Prometheus, Grafana).
**Method:** Every claim below was verified by direct source inspection (`backend/src/main/java`, `frontend/`, `infra/`, `docs/`, `automation/`, `AI_ENGINEERING/`) and, where noted, by live validation against the running `docker compose` stack and its PostgreSQL database in this session.
**Convention:** Where a capability the template below normally expects does **not** exist in this codebase, this document says so explicitly (`Not implemented`) rather than describing a hypothetical. That is a deliberate choice, not an omission — see the Assumptions and Open Questions sections.

---

## 1. Executive Summary

**Business problem.** Teams need to shorten long URLs into short, shareable links, control who can follow them (optional password), know when a link should stop working (optional expiry), and understand how a link performs (click analytics) — with programmatic access for automation and audit-grade traceability for compliance-minded operators.

**Solution overview.** A self-contained, containerized platform: a Spring Boot REST API (JWT + API-key auth), a PostgreSQL system of record, a Redis-backed cache/rate-limiter, a Kafka-based asynchronous click-analytics pipeline, a small Bootstrap/vanilla-JS dashboard served by Nginx, and a Prometheus/Grafana observability stack — all brought up with one `docker compose up --build`.

**Objectives** (as evidenced by what was actually built):
- Sub-100ms redirects under load (asserted by `perf/redirect-load-test.js`'s k6 threshold: p95 < 100ms at 1000 req/s).
- Durable, auditable link lifecycle (soft-delete, immutable append-only audit log, Flyway-versioned schema).
- Click analytics that survive redirect-path load (async via Kafka, not synchronous with the redirect).
- Dual authentication for humans (JWT) and machines (API keys) with independent revocation.
- Honest, documented engineering trade-offs rather than papered-over gaps (see `AI_ENGINEERING/`, `docs/ARCHITECTURE.md`'s CURRENT-vs-PROPOSED framing, and §22 Risks below).

**Business value.** A drop-in internal link-shortening service that a team can run today for demos, internal tooling, or campaign links, with a documented, credible path to production hardening (§19, §22) rather than a black box.

**Success criteria (as measurable today):**
| Criterion | Evidence |
|---|---|
| Redirect p95 < 100ms @ 1000 req/s | `perf/redirect-load-test.js` threshold `redirect_duration_ms: p(95)<100` |
| Unit test suite green | 62/62 unit tests passing per `AI_ENGINEERING/08_validation_report.md` |
| E2E UI/API coverage | 131 Playwright test cases, 128 passing out of the box (`automation/`) |
| No SQL injection on user-controlled URL/search fields | Verified live in this session: a `'; DROP TABLE urls; --` payload is stored as inert text via parameterized JPA queries |
| Audit trail completeness | Verified live: every login/URL-create/API-key-create action in this session produced a matching `audit_logs` row with correct JSON `details` |

---

## 2. Scope

### In Scope
- User self-registration and JWT-based login/refresh (USER role only).
- Short link creation with optional expiry and optional password protection.
- Public redirect resolution (`GET /{shortCode}`).
- Click analytics: total clicks, unique visitors, daily trend, browser/OS/device/country/referrer breakdowns.
- Link management: list (paginated/searchable/filterable/sortable), soft-delete, expiry update.
- API keys: create (one-time secret reveal), list, revoke — for programmatic `/api/v1/urls` access via `X-API-Key`.
- Immutable audit logging of security- and data-relevant actions.
- Rate limiting (default + a stricter limiter on password-verify attempts).
- Observability: structured JSON logs, correlation IDs, Micrometer/Prometheus metrics, a provisioned Grafana dashboard.
- Interactive API docs (Swagger UI / OpenAPI).

### Out of Scope (confirmed absent from the codebase)
- Email, SMS, push, or webhook notifications of any kind.
- SSO / OAuth2 / any third-party identity provider.
- Payment processing or billing.
- Multi-tenant organizations/teams (ownership is per-user only; roles are a flat `USER`/`ADMIN`).
- Custom/vanity short codes chosen by the user (only system-generated codes).
- Per-user quotas or plan tiers.
- TLS termination anywhere in the stack (Nginx serves plain HTTP:80 today).
- A UI or API for admins to manage other users (no `GET /api/v1/users`, no admin console).
- File upload/download, visual regression testing, i18n/l10n (English only, hardcoded UI strings).

### Future Scope (from `docs/ARCHITECTURE.md`'s Future Enhancements list and `AI_ENGINEERING/05_ai_traceability.md`)
1. TLS at the edge (listed first/highest priority in ARCHITECTURE.md).
2. Real GeoIP provider (`GeoIpService` currently a `NoOpGeoIpService` stub — country is always null).
3. Custom/vanity short codes; per-user quotas (flagged in `docs/test-cases/url-lifecycle-test-cases.md` per ARCHITECTURE.md's citation).
4. Token revocation list / logout-everywhere (currently: a stolen access token is valid until its 1h expiry — an explicit, documented design trade-off, not a gap).
5. Kafka consumer-lag alerting (Grafana dashboard deliberately excludes this panel today).
6. Circuit breaker around the Kafka producer/consumer path.
7. Notification integrations (email/webhook) reacting to existing domain events.
8. AWS migration path (Route53 → CloudFront → ALB → ECS Fargate → RDS/ElastiCache/MSK) — fully diagrammed in ARCHITECTURE.md as PROPOSED, not built.

### Assumptions
- Single-region, single-environment deployment via Docker Compose is the only deployment topology that exists today; Kubernetes/ECS are design targets, not artifacts in this repo.
- The `demo`/`admin` seeded accounts (`SEED_DEMO_DATA=true` by default) are acceptable for a demo/dev environment and are expected to be disabled before any real deployment (per `docs/DEPLOYMENT.md`'s hardening checklist).
- "Enterprise-grade" (the README's own description) is scoped to engineering rigor (tests, CI, structured docs, honest trade-off records) rather than to compliance certifications, which are explicitly not pursued (§20).

### Dependencies
- PostgreSQL 16 (system of record), Redis 7 (cache + rate limiter, fail-open if unreachable), Kafka + Zookeeper (async click pipeline), Nginx (reverse proxy + static frontend host), Prometheus + Grafana (metrics/dashboards) — all declared in `docker-compose.yml` with explicit `depends_on`/healthcheck ordering.

---

## 3. User Personas

| Persona | In this app | Goals | Responsibilities | Pain Points | Permissions |
|---|---|---|---|---|---|
| **Standard User** | `UserRole.USER` (self-registered, or seeded `demo`/`Demo@12345`) | Shorten URLs quickly; protect sensitive links; see how many people clicked | Manages only their own links and API keys | No way to recover a forgotten link password except deleting/recreating it; no bulk operations; page size is fixed at 20 with no UI control | CRUD + analytics on **own** URLs only (`loadOwned()` enforces `url.user.id == self`); own API keys only |
| **Administrator** | `UserRole.ADMIN` (only the seeded `admin`/`Admin@12345` account — no self-service path to become admin) | Oversee link data across all users when needed | Same dashboard as a Standard User, but service-layer checks let admin bypass ownership on URL delete/patch/analytics | No dedicated admin UI or user-management screen exists; "admin" only manifests as a bypassed ownership check, not a different experience | Read/delete/patch/analytics on **any** user's URLs (service-layer `isAdmin` bypass); **no** admin bypass on API keys (always owner-only) |
| **API Consumer (Power User)** | Any USER, authenticating via `X-API-Key` instead of the dashboard | Automate link creation/lookup from scripts/CI/other services | Creates and rotates their own API keys; must store the one-time-shown secret themselves | The secret is shown exactly once at creation with no recovery path; scopes (`URL_READ,URL_WRITE`) are recorded but **not enforced** anywhere in code today | Same as Standard User, via `X-API-Key` header instead of a JWT |
| **Guest / Anonymous** | Unauthenticated visitor | Follow a short link to its destination, or unlock a password-protected one | None (no account) | Gets a generic password prompt with no context about who owns the link; a wrong password gives no lockout feedback beyond the stricter rate limit | Can hit `GET /{shortCode}` (public) and `POST /api/v1/urls/{code}/verify-password` (public, rate-limited to 5/60s) only |
| **Support User** | **Not implemented** | — | — | — | There is no third role or support-specific permission set anywhere in `UserRole`, `SecurityConfig`, or the service layer. Documenting this explicitly rather than inventing one. |
| **System Administrator (Operator)** | Not an app role — the person running `docker compose`, Grafana, and Prometheus | Deploy, monitor, and troubleshoot the stack | Rotates `JWT_SECRET`/DB passwords before real use, watches the Grafana dashboard, reads `docs/TROUBLESHOOTING.md` when a container misbehaves | No Alertmanager — must be actively watching Grafana/logs, nothing pages them; no Kafka consumer-lag panel | Infra-level access only (docker, Grafana admin/admin default, Prometheus UI) — orthogonal to the app's own USER/ADMIN roles |

---

## 4. Functional Requirements

Each requirement below cites the exact source location so it can be re-verified against the code at any time.

### FR-001 — User Registration
**Description:** Self-service account creation issuing a token pair immediately.
**Source:** `AuthController.register` → `POST /api/v1/auth/register`; `UserRegistrationService`.
**Business Rules:**
- New accounts are always created with `role=USER` — there is no client-controlled way to obtain `ADMIN` (`UserRegistrationService.createUser` hardcodes the role).
- Username and email uniqueness are case-insensitive and scoped to *live* (non-soft-deleted) rows only — partial unique indexes `uq_users_username_active`/`uq_users_email_active` on `LOWER(...)  WHERE deleted_at IS NULL`.
- `UserRegistrationService` is deliberately a separate `@Transactional` bean from `AuthServiceImpl` specifically so the user row is committed before `AuditService`'s `REQUIRES_NEW` audit write tries to reference it — this ordering was a real bug found and fixed during development (documented in `automation/README.md`).
**Acceptance Criteria:**
1. Given a unique username/email and a password meeting the complexity rule, when registering, then a `201` with `accessToken`/`refreshToken`/`tokenType`/`expiresInMs` is returned and an `audit_logs` row `USER_REGISTERED` is written.
2. Given a username or email already in use by a live account, then `409 Conflict` (`DuplicateResourceException`).
3. Given an invalid field, then `400` with a `validationErrors` map keyed by field name.
**Priority:** Must-have.
**Dependencies:** None (entry point).
**Edge Cases:** Re-registering a username that belonged to a soft-deleted account succeeds (the partial index only guards live rows) — this is intentional, not a bug.
**Error Handling:** See §4 Global error table in §9/§17.
**Validation Rules:** See §8.

### FR-002 — User Login
**Source:** `AuthController.login` → `POST /api/v1/auth/login`.
**Business Rules:** Generic `"Invalid username or password"` message on any failure (unknown username, wrong password, or disabled account) — deliberately no user-enumeration. `LOGIN_FAILURE` audit entries record the specific reason (`unknown_username`/`bad_password`/`account_disabled`) server-side even though the client never sees it.
**Acceptance Criteria:**
1. Valid credentials → `200` + token pair + `LOGIN_SUCCESS` audit row.
2. Invalid credentials or disabled account → `401`, generic message, `LOGIN_FAILURE` audit row with the real reason.
**Priority:** Must-have. **Dependencies:** FR-001.

### FR-003 — Token Refresh
**Source:** `AuthController.refresh` → `POST /api/v1/auth/refresh`.
**Business Rules:** A refresh token presented where an access token is expected (or vice versa) is rejected via the `type` claim (`"access"`/`"refresh"`) — `JwtAuthenticationFilter` only authenticates `"access"`-typed tokens; `refresh()` only accepts `"refresh"`-typed ones. No revocation list exists — a valid, unexpired refresh token for a still-`enabled` user always succeeds.
**Acceptance Criteria:** Valid refresh token + active user → new token pair (`200`). Wrong token type, expired, malformed, or now-disabled user → `401` (`InvalidTokenException`).
**Priority:** Should-have.

### FR-004 — Create Short URL
**Source:** `UrlController.createUrl` → `POST /api/v1/urls`; `UrlServiceImpl.createUrl`.
**Business Rules:**
- Short code: 7-char Base62 (`SHORT_CODE_LENGTH`, default 7; ~3.5 trillion combinations), `SecureRandom`-generated by default (`RandomShortCodeGenerator`; a `HashBasedShortCodeGenerator` alternative exists, selectable via `SHORT_CODE_STRATEGY`), retried up to 5 times on collision, then `IllegalStateException`.
- Optional password → BCrypt-hashed (same encoder as user passwords); optional expiry (`Instant`, no constraint — absence means no expiry).
- URL itself is validated against SSRF/XSS-adjacent risks (see FR-004 validators below), **not** checked for liveness/reachability.
- Writes `URL_CREATED` audit log; publishes `UrlCreatedEvent` (Kafka `url-created` topic) only *after* the DB transaction commits (`@TransactionalEventListener(AFTER_COMMIT)`).
**Acceptance Criteria:**
1. Valid URL (+ optional password/expiry) → `201`, `UrlResponse` body, `shortUrl` = `{APP_BASE_URL}/{shortCode}`.
2. Malformed URL, disallowed scheme (`javascript:`/`data:`/`file:`/`ftp:`), or a URL/password containing a script-injection pattern → `400`.
3. Unauthenticated request → `401`.
**Priority:** Must-have. **Dependencies:** FR-001/002 or a valid API key.
**Edge Cases:** All 5 short-code generation attempts collide → `500`-class `IllegalStateException` (not specifically mapped to a friendlier status in `GlobalExceptionHandler` — falls into the generic `Exception → 500` handler).

### FR-005 — List / Search / Filter / Sort URLs
**Source:** `UrlController.listUrls` → `GET /api/v1/urls`; `UrlSpecifications`.
**Business Rules:** Non-admins only ever see their own links (`ownedBy(userId)` always applied unless admin). `status=ACTIVE|EXPIRED|ALL` (default `ALL`); `search` does a case-insensitive substring match against `originalUrl` OR `shortCode`. Default page size 20, default sort `createdAt`.
**Known gap (flagged, not fixed):** **No maximum page size is enforced.** `Pageable` binds directly from client-supplied `page`/`size`/`sort` with no upper bound and no sortable-field allow-list — a client can request an arbitrarily large page or sort by any entity property.
**Acceptance Criteria:** Returns a `PageResponse` envelope (`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`) — deliberately not Spring's native `Page` JSON shape.
**Priority:** Must-have.

### FR-006 — Update Link Expiry
**Source:** `UrlController.updateUrlExpiry` → `PATCH /api/v1/urls/{id}`.
**Business Rules:** `expiresAt=null` explicitly clears the expiry (removes it); owner-or-admin only. Evicts both the `urlLookup` and `analytics` Redis caches for that code. Writes `URL_EXPIRY_UPDATED` audit log.
**Acceptance Criteria:** Owner/admin, valid id → `200`. Non-owner, non-admin → `403`. Unknown id → `404`.
**Priority:** Should-have.

### FR-007 — Soft-Delete Link
**Source:** `UrlController.deleteUrl` → `DELETE /api/v1/urls/{id}`.
**Business Rules:** Sets `deletedAt`, never a hard delete; the vacated `short_code` becomes reusable (partial unique index only covers live rows). Evicts caches; writes `URL_DELETED` audit log.
**Acceptance Criteria:** Owner/admin → `204`. Non-owner → `403`. Unknown → `404`.
**Priority:** Should-have.

### FR-008 — Password-Protected Links
**Source:** `RedirectController.redirect` (gate) + `UrlController.verifyPassword` → `POST /api/v1/urls/{shortCode}/verify-password`; `UrlServiceImpl.verifyPasswordAndGetDestination`.
**Business Rules:**
- A protected link's `GET /{shortCode}` never redirects to the destination directly — it 302s to `{APP_BASE_URL}/protected.html?code={shortCode}`, and this visit is **not** counted as a click.
- `verify-password` is public but rate-limited to **5 attempts / 60 seconds** per caller (hardcoded, not env-configurable) — the brute-force mitigation, distinct from the default 100/60s limiter on everything else.
- **Confirmed gap:** a *successful* password verification also does **not** publish a `UrlClickedEvent` — password-protected links are never reflected in click analytics at all, success or failure. This is a genuine, confirmed product gap (independently corroborated by `automation/README.md`'s bug #4), not a misunderstanding.
**Acceptance Criteria:** Correct password → `200 {"originalUrl": "..."}`. Wrong password → `401`. Blank password → `400`. Unknown code → `404`. 6th+ attempt within 60s → `429` with `Retry-After`.
**Priority:** Must-have.

### FR-009 — Redirect Resolution
**Source:** `RedirectController.redirect` → `GET /{shortCode}` (public, root-mapped, proxied through Nginx's catch-all `@redirect_backend` location).
**Business Rules:** `resolveForRedirect` is `@Cacheable(cacheNames="urlLookup")` with a 1-hour TTL. Expired links return `410 Gone` with a message naming the expiry timestamp, **without** tracking a click. Soft-deleted or never-existing codes → `404`.
**Acceptance Criteria:** Live, non-expired, non-protected link → `302` to `originalUrl`, click tracked (async). Expired → `410`, not tracked. Unknown → `404`. Protected → `302` to the password prompt, not tracked.
**Priority:** Must-have. **Performance target:** p95 < 100ms at 1000 req/s (k6-asserted, §15).

### FR-010 — Click Analytics
**Source:** `UrlController.getAnalytics` → `GET /api/v1/urls/{shortCode}/analytics`; `AnalyticsServiceImpl`; async pipeline in `UrlClickedEventConsumer`/`UrlClickIngestionService`.
**Business Rules:**
- Populated asynchronously: redirect → Kafka `url-clicked` → consumer parses User-Agent (browser/OS/device-type) and (stubbed) GeoIP → `UrlClickIngestionService.recordClick` inserts a `url_clicks` row **and** increments `urls.click_count` in the **same transaction**, guarded by a unique `event_id` for exactly-once idempotency under redelivery.
- Owner-or-admin only.
- Response is `@Cacheable(cacheNames="analytics", key="#shortCode", unless="totalClicks==0")` with a **60-second TTL**.
- **Confirmed, live, unfixed staleness gap:** because nothing evicts the `analytics` cache when a *new* click lands (only URL delete/expiry-update evict it), a link's reported `totalClicks` can lag up to 60 seconds behind the true, always-correct Postgres state if the analytics endpoint was polled once inside that window. Verified in this session's own database inspection to be consistent with `urls.click_count` vs. `COUNT(*) FROM url_clicks` matching exactly at the database layer — the discrepancy, when observed, is in the cached API response, not the underlying data.
**Acceptance Criteria:** Returns `totalClicks`, `uniqueVisitors` (distinct `ip_hash`), `dailyClicks`, and browser/OS/device/country/referrer breakdowns. Verified live in this session: 3 clicks from 3 distinct user agents produced exactly `totalClicks=3`, correct per-browser/OS/device-type counts, and `country` empty (GeoIP stub).
**Priority:** Must-have.

### FR-011 — API Key Management
**Source:** `ApiKeyController` → `POST/GET/DELETE /api/v1/api-keys[/{id}]`.
**Business Rules:** Plaintext key = `usk_` + 64 hex chars (256 bits of `SecureRandom` entropy); hashed with plain **SHA-256** (not BCrypt — a deliberate, documented choice, since the key already carries far more entropy than a human password, and a slow hash "buys nothing but latency on every authenticated request"). Plaintext is returned exactly once, at creation, and never persisted or re-derivable. `scopes` field exists (`URL_READ,URL_WRITE` default) but is **not enforced** anywhere in code. No admin bypass on revoke — always owner-only.
**Acceptance Criteria:** Create → `201` with one-time `plaintextKey`. List → `200`, active keys only, `plaintextKey` always null. Revoke → `204` (owner) / `403` (non-owner) / `404`.
**Priority:** Should-have.

### FR-012 — Rate Limiting *(cross-cutting)*
**Source:** `RateLimitFilter`, `cache/RedisRateLimiter`, `scripts/rate_limiter.lua`.
**Business Rules:** Hand-rolled fixed-window counter via a single atomic Redis Lua script (INCR + conditional EXPIRE + capacity check) — Bucket4j was evaluated and explicitly rejected (`AI_ENGINEERING/04_prompt_iterations.md`). Default bucket: 100 requests/60s, keyed by IP or API-key prefix. Stricter bucket on `/verify-password`: 5/60s, hardcoded. **Fails open** if Redis is unreachable (traffic is allowed through, not blocked) — a deliberate availability-over-strictness trade-off.
**Acceptance Criteria:** Exceeding the applicable bucket → `429` + `Retry-After` header, written directly by the filter (ahead of `DispatcherServlet`).
**Priority:** Must-have.

### FR-013 — Audit Logging *(cross-cutting)*
**Source:** `AuditService`, table `audit_logs`.
**Business Rules:** Runs in `Propagation.REQUIRES_NEW` so an audit write survives the rollback of the operation it's recording, and is never itself lost to an unrelated later rollback. Audit-write failures are swallowed (logged only) — never allowed to break the primary business operation. Table is append-only by convention (DB comment states this; not enforced by a DB-level trigger/permission). Observed actions: `USER_REGISTERED`, `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `URL_CREATED`, `URL_DELETED`, `URL_EXPIRY_UPDATED`, `API_KEY_CREATED`, `API_KEY_REVOKED`.
**Acceptance Criteria:** Verified live in this session — every login, URL creation, and API-key creation performed produced a matching row with correct `entity_type`/`entity_id`/`details` JSON and timestamp ordering.
**Priority:** Must-have (compliance-adjacent, though no formal compliance regime is targeted — §20).

### FR-014 — RBAC / Ownership Enforcement *(cross-cutting)*
**Source:** `AuthenticatedUser`, `UrlServiceImpl.loadOwned()`, `ApiKeyServiceImpl`.
**Business Rules:** **Not** implemented via `@PreAuthorize`/method security. Enforced entirely in the service layer as explicit `isAdmin`-or-`ownerId==currentUserId` checks. Two roles only: `USER`, `ADMIN`. Admin bypasses ownership on URL delete/patch/analytics; **never** on API keys.
**Acceptance Criteria:** Non-owner, non-admin attempting to delete/patch/view-analytics-of another user's URL → `403` via `RestAccessDeniedHandler` (JSON body, not Spring's default HTML/redirect).
**Priority:** Must-have.

---

## 5. User Stories

| ID | Story | Acceptance Criteria | Priority | Points | Definition of Done |
|---|---|---|---|---|---|
| US-001 | As a **visitor**, I want to register an account, so that I can start shortening my own links. | See FR-001 AC. | Must | 3 | Registers, receives tokens, row visible in `users`, `USER_REGISTERED` audit row present. |
| US-002 | As a **registered user**, I want to log in, so that I can access my dashboard. | See FR-002 AC. | Must | 2 | Valid login returns tokens; invalid returns generic 401; `LOGIN_SUCCESS`/`LOGIN_FAILURE` audit rows correct. |
| US-003 | As a **logged-in user**, I want my session to refresh without re-entering my password, so that I stay logged in across a work session. | See FR-003 AC. | Should | 2 | Refresh token exchanges for a new pair; wrong token type rejected. |
| US-004 | As a **user**, I want to shorten a long URL, so that I can share it more easily. | See FR-004 AC. | Must | 3 | `201` + short code resolves via redirect; audit + Kafka `url-created` event confirmed. |
| US-005 | As a **user**, I want to optionally password-protect a link, so that only people I share the password with can follow it. | See FR-008 AC. | Must | 5 | Protected link 302s to prompt, not destination; correct password unlocks; wrong password rejected; 6th attempt/60s throttled. |
| US-006 | As a **user**, I want to optionally set an expiry date, so that a link stops working automatically after a campaign ends. | See FR-004/FR-009 AC. | Should | 2 | Past-expiry link returns `410`, not `404` or a redirect. |
| US-007 | As a **user**, I want to see, search, and filter all my links, so that I can find and manage them later. | See FR-005 AC. | Must | 3 | Search matches destination or code; status filter correctly separates active/expired. |
| US-008 | As a **user**, I want to update or clear a link's expiry after creation, so that I can extend a campaign without recreating the link. | See FR-006 AC. | Should | 2 | PATCH with a new date or `null` behaves as documented; cache evicted (verified by immediate re-fetch reflecting the change). |
| US-009 | As a **user**, I want to delete a link I no longer need, so that it stops resolving. | See FR-007 AC. | Should | 1 | Deleted link 404s on redirect; row remains in DB with `deleted_at` set (soft delete, confirmed live). |
| US-010 | As a **user**, I want to see click analytics for a link, so that I can measure its reach. | See FR-010 AC. | Must | 5 | Browser/OS/device/country/referrer/daily breakdowns render; verified live with 3 real clicks from 3 distinct user agents. |
| US-011 | As a **developer/API consumer**, I want to create an API key, so that I can automate link creation from my own scripts. | See FR-011 AC. | Should | 3 | One-time secret shown; subsequent `X-API-Key` calls succeed; key never recoverable after creation. |
| US-012 | As an **anonymous visitor**, I want to click a short link and land on the real destination quickly, so that the redirect feels invisible. | See FR-009 AC. | Must | 3 | p95 redirect latency < 100ms at 1000 req/s (k6-verified). |
| US-013 | As an **administrator**, I want to manage any user's link if needed, so that I can help with support requests without needing their password. | Admin bypass on delete/patch/analytics, confirmed in `UrlServiceImpl`. | Could | 2 | Admin can delete/patch/view-analytics of a non-owned link; a non-admin cannot. |

---

## 6. Detailed Workflows

Only the flows that genuinely exist in the code are enumerated (e.g., there is no "cancellation flow" for creating a URL — it's a single synchronous POST — so that column is marked N/A rather than invented).

### 6.1 Create Short Link
- **Happy path:** authenticated request → validate URL/password → generate unique code (1st attempt) → persist → commit → publish `UrlCreatedEvent` post-commit → `201`.
- **Alternative flow:** short-code collision on attempt 1–4 → regenerate → succeed by attempt 5.
- **Failure flow:** invalid URL/password/script-injection pattern → `400`, nothing persisted.
- **Retry flow:** all 5 generation attempts collide → `IllegalStateException` → `500`; client may simply retry the whole request (new random draws).
- **Cancellation flow:** N/A (synchronous, no multi-step wizard to abandon).
- **Recovery flow:** N/A — no partial state can be left behind (single transaction).
- **Sequence:** Client → `UrlController` → `UrlServiceImpl` → `ShortCodeGeneratorFactory`/`UrlRepository` (loop) → Postgres commit → `ApplicationEventPublisher` (after commit) → `DomainEventKafkaBridge` → `KafkaEventPublisher` → Kafka `url-created` (fire-and-forget).

### 6.2 Redirect + Click Tracking
- **Happy path:** `GET /{code}` → cache hit or DB lookup → not expired/protected → `302` → in-process event → Kafka `url-clicked` (async, fire-and-forget from the redirect's perspective) → consumer parses UA/Geo → `recordClick` (transactional insert + counter increment, idempotent on `event_id`) → optional `AnalyticsRecordedEvent` to the `analytics` topic.
- **Alternative flow (protected link):** `302` to `protected.html?code=`, **no click recorded** (confirmed gap, see FR-008/FR-010).
- **Alternative flow (verify-password success):** `200` with destination URL, client-side JS then navigates — **still no click recorded** (confirmed gap).
- **Failure flow (expired):** `410 Gone`, no click recorded.
- **Failure flow (unknown code):** `404`, no click recorded.
- **Retry flow (consumer error):** `DefaultErrorHandler` retries the Kafka message up to 2 more times (3 total attempts, 1s fixed backoff) before routing the raw record to the literal `dead-letter` topic via `DeadLetterPublishingRecoverer`. Offset is only committed after successful processing (manual ack).
- **Recovery flow:** none automated for messages that land on `dead-letter` — `docs/TROUBLESHOOTING.md` documents manually inspecting it with `kafka-console-consumer`; there is no reprocessing tool.
- **Sequence diagram:** see `docs/ARCHITECTURE.md`'s "Redirect + Click-Tracking" Mermaid sequence diagram (18-step numbered walkthrough).

### 6.3 Password-Protected Link Verification
- **Happy path:** guest hits protected link → prompt page → submits password → `POST .../verify-password` → BCrypt match → `200` with destination → client JS navigates.
- **Failure flow:** wrong password → `401`, inline "Incorrect password" error, no lockout beyond rate limiting.
- **Retry flow:** up to 5 attempts/60s per caller; 6th → `429` + `Retry-After`.
- **Recovery flow:** N/A — no password-reset mechanism for link passwords exists; the link owner would need to delete and recreate the link.

### 6.4 API Key Lifecycle
- **Happy path:** authenticated user → create key → one-time plaintext shown → use via `X-API-Key` on subsequent calls.
- **Failure flow:** unknown/revoked/expired key on `X-API-Key` → request proceeds as *anonymous* (filter fails silently, does not itself 401 — downstream `401`/`403` happens only if the endpoint requires auth).
- **Cancellation/Recovery:** revoke (`DELETE`) is the only "cancellation"; there is no way to recover a lost plaintext key — only revoke and create a new one.

---

## 7. UI Requirements

All five real pages, from direct inspection of `frontend/*.html` and live screenshots captured this session.

### 7.1 `login.html` — Sign in / Register
**Purpose:** Entry point; single card with tab-switched Sign-in/Register forms.
**Components/Fields:** Sign-in: Username (text), Password (password). Register: Username, Email, Password (with inline hint: "At least 8 characters, with upper, lower and a digit"). Demo credentials shown inline on the sign-in form.
**Buttons:** "Sign in" / "Create account" (submit); tab buttons "Sign in"/"Register" (client-side toggle, no navigation).
**Validation:** HTML5 `required` on all fields, `type="email"` on email; real validation happens server-side (§8) — a failed submit shows a toast, not inline field errors.
**Navigation:** Already-authenticated visitors are bounced straight to `index.html` on load (`auth.js`, `isLoggedIn()` check) — the login page never shows to a logged-in session.
**Responsive/Accessibility/Loading/Error/Empty states:** Bootstrap 5 responsive card (works down to mobile width, not specifically audited for a11y beyond Bootstrap defaults — `automation/README.md` notes a frontend responsive/a11y bug was found and fixed during test-suite development, specifics not detailed further in the extraction). No loading spinner on submit — button simply waits for the fetch. Error state: a dismissible toast with the server's message. No "empty state" concept on this page.

### 7.2 `index.html` — Dashboard
**Purpose:** Create links; view/search/filter/paginate/manage own links.
**Components/Fields:** "Shorten a URL" form — URL (required, `type="url"`), Expiry (`datetime-local`, optional), Password (optional). "Your links" table — Short URL (+ "protected" badge + Copy), Destination, Clicks, Created, Expires (+ "expired" badge), Actions (Analytics link, Edit-expiry button, Delete button). Status filter dropdown (All/Active/Expired), search box (debounced 350ms).
**Buttons:** Shorten (submit), per-row Copy/Analytics/Edit expiry/Delete, pagination buttons, Sign out.
**Validation:** Client-side `required`/`type` only; real rules server-side (§8). Delete uses a native `confirm()` dialog ("Delete this link? This cannot be undone.").
**Navigation:** `requireLogin()` bounces unauthenticated visitors to `login.html`. Analytics link → `analytics.html?code={shortCode}`.
**Loading state:** table shows "Loading..." row while fetching. **Empty state:** "No links yet - shorten one above." **Error state:** table row shows the server's error message in red.
**Edit-expiry modal:** Bootstrap modal, `datetime-local` input (blank = clear expiry), Cancel/Save.

### 7.3 `analytics.html` — Click Analytics
**Purpose:** Per-link analytics view, read-only.
**Components:** Total clicks / Unique visitors stat tiles; Daily clicks, Browsers, Operating systems, Device types, Countries, Referrers — each a simple CSS bar chart (no charting library) or "No data yet".
**Navigation:** "← Back to links" to `index.html`. **Loading/Empty:** each panel independently shows "No data yet" until real data exists (verified: all panels showed 0/"No data yet" for an unclicked link, then populated correctly after 3 real clicks in this session).

### 7.4 `api-keys.html` — API Key Management
**Purpose:** Create/list API keys.
**Components/Fields:** "Create an API key" — Name (required text). "Your API keys" table — Name, Prefix, Scopes, Last used, Created, Revoke action.
**Success state:** a dismissible green alert showing the one-time plaintext secret ("Key created - copy it now, it will not be shown again"), verified live this session.
**Loading/Empty:** "Loading..." row, then populated table; no explicit "no keys yet" copy was observed distinctly from the loading state in source (minor UI gap — not a functional one).

### 7.5 `protected.html` — Password Prompt
**Purpose:** Gate a password-protected link's destination.
**Components:** Password field (autofocus), Continue button, inline error ("Incorrect password. Try again.").
**Edge case handled in JS:** if the page is reached with no `?code=` query param, the form is hidden and "No link code provided." is shown instead.
**Navigation on success:** full page navigation (`window.location.href`) to the real destination — not an SPA transition.

### 7.6 Swagger UI (`/swagger-ui.html`)
Auto-generated from Spring's OpenAPI annotations (springdoc) — not a hand-built screen, but a real, reachable, in-scope UI surface (verified live, screenshot captured this session) documenting all endpoints grouped by controller (`Redirect`, `Auth`, `API Keys`, and — not screenshotted but present per the endpoint table in §1 of the extraction — `URLs`).

---

## 8. Field-Level Validation

| Field | Form/Endpoint | Required | Type | Min | Max | Pattern / Allowed Values | Default | Error Message | Business Validation |
|---|---|---|---|---|---|---|---|---|---|
| `username` | Register | Yes | String | 3 | 50 | `^[a-zA-Z0-9_.-]+$` | — | "username may only contain letters, digits, underscore, dot and dash" | Case-insensitive unique among live users |
| `email` | Register | Yes | String (email) | — | 255 | RFC email format (`@Email`) | — | standard Bean Validation email message | Case-insensitive unique among live users |
| `password` | Register | Yes | String | 8 | 72 | `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$` | — | "password must contain at least one lowercase letter, one uppercase letter and one digit" | BCrypt-hashed; 72-char cap matches BCrypt's own input limit |
| `username` | Login | Yes | String | — | — | none | — | — | No complexity check on login, only presence |
| `password` | Login | Yes | String | — | — | none | — | — | — |
| `refreshToken` | Refresh | Yes | String | — | — | none | — | — | Must decode as a valid, unexpired, `type=refresh` JWT |
| `url` | Create URL | Yes | String | — | 8192 | must parse as `java.net.URL`; scheme ∈ {http, https}; host non-blank; rejects `<script`, `javascript:`, `data:text/html`, inline `on*=` handlers, `<iframe` | — | "url must not be blank" / custom validator messages | Not checked for reachability/liveness |
| `expiryDate` | Create URL | No | Instant (ISO-8601) | — | — | any future or past instant accepted by the DTO itself (no `@Future` constraint found) | null | — | A past `expiryDate` at creation time would make the link immediately `410` on first access — not blocked by validation |
| `password` | Create URL | No | String | 4 | 72 | none beyond length | null (= public link) | "password must be between 4 and 72 characters" | BCrypt-hashed if present |
| `expiresAt` | Update expiry (PATCH) | No | Instant | — | — | none | — | — | `null` explicitly clears the expiry (documented in the DTO's own Javadoc) |
| `password` | Verify link password | Yes | String | — | — | none | — | — | Compared via `BCryptPasswordEncoder.matches` |
| `name` | Create API key | Yes | String | — | 100 | none | — | — | — |
| `expiresAt` | Create API key | No | Instant | — | — | none | — | — | — |
| `search` | List URLs | No | String (query param) | — | — | none | — | — | Case-insensitive substring match on destination or code |
| `status` | List URLs | No | String (query param) | — | — | `ACTIVE` \| `EXPIRED` \| `ALL` | `ALL` | — | Any other value is silently treated as no filter |
| `page`/`size`/`sort` | List URLs | No | int/int/string (query params) | — | **none enforced** | any Spring-bindable value | `page=0`, `size=20`, `sort=createdAt` | — | **No max-size cap or sortable-field allow-list — a confirmed gap, see FR-005** |

---

## 9. API Requirements

All endpoints are versioned under `/api/v1` except the public redirect. Base error shape for every non-2xx response (`ErrorResponse`, `GlobalExceptionHandler`):
```json
{ "timestamp": "2026-...", "status": 400, "error": "Bad Request", "message": "...", "path": "/api/v1/...", "correlationId": "...", "validationErrors": { "field": "message" } }
```
`correlationId` is always the inbound-or-minted `X-Request-Id` (`CorrelationIdFilter`, first in the security filter chain). Rate-limit `429` responses are written directly by `RateLimitFilter` in the same shape, before reaching the controller layer.

| ID | Method | Path | Auth | Request | Success | Errors |
|---|---|---|---|---|---|---|
| API-001 | POST | `/api/v1/auth/register` | Public | `RegisterRequest` | `201 AuthResponse` | `400`, `409` |
| API-002 | POST | `/api/v1/auth/login` | Public | `LoginRequest` | `200 AuthResponse` | `400`, `401` |
| API-003 | POST | `/api/v1/auth/refresh` | Public | `RefreshTokenRequest` | `200 AuthResponse` | `400`, `401` |
| API-004 | POST | `/api/v1/urls` | JWT or API key | `CreateUrlRequest` | `201 UrlResponse` | `400`, `401` |
| API-005 | GET | `/api/v1/urls` | JWT or API key | `search`, `status`, `page`, `size`, `sort` (query) | `200 PageResponse<UrlResponse>` | `401` |
| API-006 | DELETE | `/api/v1/urls/{id}` | JWT or API key | path `id` (UUID) | `204` | `401`, `403`, `404` |
| API-007 | PATCH | `/api/v1/urls/{id}` | JWT or API key | `UpdateExpiryRequest` | `200 UrlResponse` | `401`, `403`, `404` |
| API-008 | GET | `/api/v1/urls/{shortCode}/analytics` | JWT or API key | path `shortCode` | `200 AnalyticsResponse` | `401`, `403`, `404` |
| API-009 | POST | `/api/v1/urls/{shortCode}/verify-password` | Public (rate-limited 5/60s) | `VerifyUrlPasswordRequest` | `200 {"originalUrl": "..."}` | `400`, `401`, `404`, `429` |
| API-010 | GET | `/{shortCode}` | Public | path `shortCode` | `302` redirect | `404`, `410` |
| API-011 | POST | `/api/v1/api-keys` | JWT or API key | `CreateApiKeyRequest` | `201 ApiKeyResponse` (with one-time `plaintextKey`) | `400`, `401` |
| API-012 | GET | `/api/v1/api-keys` | JWT or API key | — | `200 List<ApiKeyResponse>` | `401` |
| API-013 | DELETE | `/api/v1/api-keys/{id}` | JWT or API key | path `id` (UUID) | `204` | `401`, `403`, `404` |

**Headers:** `Authorization: Bearer <JWT>` or `X-API-Key: usk_...` for protected endpoints; `X-Request-Id` optionally supplied by the client and always echoed back. **Rate limiting:** 100 req/60s default (IP- or API-key-prefix-keyed), 5 req/60s on API-009 — both fail-open if Redis is down. **Idempotency:** click ingestion is idempotent on `event_id` internally; the public write endpoints (create URL, create API key) have **no client-supplied idempotency-key mechanism** — a retried POST creates a duplicate resource. **Pagination:** API-005 only, `page`/`size`/`sort`, no cap (§4/§8 gap). **Filtering/Sorting:** API-005 only (`search`, `status`, `sort`). Full interactive contract: `/v3/api-docs` (OpenAPI JSON) and `/swagger-ui.html`; a Postman collection also ships at `docs/postman_collection.json`.

---

## 10. Database Requirements

PostgreSQL 16, schema managed exclusively by Flyway (`spring.jpa.hibernate.ddl-auto=validate` — Hibernate never auto-migrates). 5 versioned migrations, all applied cleanly (verified live: `flyway_schema_history` shows versions 1–5, all `success=true`).

| Entity | Key Attributes | PK | FK | Notable Indexes/Constraints |
|---|---|---|---|---|
| `users` | username, email, password_hash, role, enabled, created/updated/deleted_at | `id` (UUID) | — | Partial unique on `LOWER(username)`/`LOWER(email)` WHERE `deleted_at IS NULL`; `CHECK role IN ('USER','ADMIN')` |
| `urls` | short_code, original_url, user_id, password_hash, click_count, created/updated/expires/deleted_at | `id` (UUID) | `user_id → users.id` (CASCADE) | Partial unique on `short_code WHERE deleted_at IS NULL`; trigram GIN on `original_url`; `CHECK char_length(original_url)<=8192`; `CHECK click_count>=0`; indexes on `user_id`, `(user_id, created_at DESC)`, partial on `expires_at` |
| `url_clicks` | url_id, event_id, clicked_at, ip_address, ip_hash, user_agent, browser(+version), os(+version), device_type, country, referrer, correlation_id | `id` (UUID) | `url_id → urls.id` (CASCADE) | **Unique on `event_id`** (idempotency backstop); `CHECK device_type IN (...)`; indexes on `(url_id, clicked_at DESC)`, `(url_id, ip_hash)` |
| `api_keys` | user_id, key_prefix, key_hash, name, scopes, last_used_at, expires_at, revoked_at | `id` (UUID) | `user_id → users.id` (CASCADE) | Unique on `key_hash`; partial index on `user_id WHERE revoked_at IS NULL` |
| `audit_logs` | actor_user_id, actor_type, action, entity_type, entity_id, details (jsonb), ip_address, correlation_id | `id` (UUID) | `actor_user_id → users.id` (SET NULL) | GIN on `details`; indexes on `actor_user_id`, `(entity_type, entity_id)`, `created_at DESC`, `action`; table comment declares it append-only (convention, not DB-enforced) |

**Audit fields:** every table has `created_at`; `users`/`urls` also have `updated_at` (kept current by both a `@PreUpdate` hook *and* a DB trigger, `set_updated_at()` — redundant by design). **Soft-delete strategy:** `users` and `urls` use a nullable `deleted_at` timestamp; partial unique indexes scope uniqueness to live rows only, so a deleted username/email/short-code becomes reusable. `url_clicks`, `api_keys` (via `revoked_at`), and `audit_logs` do not soft-delete in the same sense — clicks and audit rows are never deleted at all; API keys are revoked (timestamped), not deleted. **Data retention policy:** **none implemented** — no scheduled purge of soft-deleted rows, expired links, old clicks, or audit history exists anywhere in the codebase (confirmed no such job/cron/scheduled task beyond `ExpiredUrlSweeper`, which only *flags* — it doesn't delete — expired links on a 15-minute interval, and even that sweep's exact behavior wasn't independently re-verified beyond the extraction agent's source read).

---

## 11. Business Rules

**Validation:** see §8 in full. **Workflow:** short-code collision retry (max 5 attempts); password-protected links never redirect directly (always via prompt); expired links 410 rather than redirect or 404; soft-delete over hard-delete everywhere a user-facing "delete" exists. **Permission:** ownership-or-admin gates all URL mutation/read-analytics; API keys are always owner-only, no admin override. **Notification:** none exist (§7/§12 — a business rule by absence). **Data:** IP addresses are stored **both** hashed (`ip_hash`, for unique-visitor counting) **and** in plaintext (`ip_address`) — confirmed live; this is a genuine privacy-posture fact worth surfacing to any reviewer, not full anonymization. **Compliance:** none targeted (§20).

---

## 12. Notifications

**Email:** Not implemented. **SMS:** Not implemented. **Push:** Not implemented. **In-app:** Toasts only (client-side, ephemeral, triggered by the frontend JS on API success/failure — e.g. "Link created", "Copied to clipboard" — not a persisted notification system). **Webhooks:** Not implemented. **Retry policy:** N/A — there is nothing to retry. `docs/ARCHITECTURE.md`'s own "External Integrations" section confirms this in the same words: no email, SMS, push, webhooks, payment gateway, or external identity provider exist anywhere in the codebase, and explicitly proposes (but does not build) a future notification package reacting to the existing domain events (`UrlCreatedEvent`, audit events) via the Observer pattern already in place.

---

## 13. Security Requirements

| Area | As-built |
|---|---|
| **Authentication** | JWT (HS256, `jjwt` 0.12.6) for humans; `X-API-Key` (SHA-256-hashed, `usk_`-prefixed, 256-bit) for machines. Both populate the same `AuthenticatedUser` principal. |
| **Authorization / RBAC** | Two flat roles (`USER`/`ADMIN`); enforced in the **service layer**, not via `@PreAuthorize`/method security. No `Support`/other role exists. |
| **JWT details** | Claims: `iss`, `sub`, `uid`, `role`, `type`, `iat`, `exp`. Access token 1h, refresh token 24h (both env-configurable). No revocation list — a stolen access token is valid until natural expiry (documented, deliberate trade-off). |
| **OAuth / SSO** | Not implemented. |
| **Session timeout** | Governed entirely by JWT expiry (1h/24h) — there is no server-side session store to time out. |
| **Password policy** | Register: min 8 / max 72 chars, upper+lower+digit required, regex-enforced. Link passwords: 4–72 chars, no complexity rule. BCrypt (default strength) for both. |
| **Encryption** | BCrypt for passwords; SHA-256 for API keys and IP-hashing. **No TLS anywhere in the stack today** (Nginx plain HTTP:80) — the single biggest starred gap in `docs/ARCHITECTURE.md`'s own Future Enhancements list. |
| **Secrets management** | Plain environment variables (`docker-compose.yml`/`.env`), with a loudly-commented insecure demo default for `JWT_SECRET` that must be changed before real use. No vault/KMS integration. |
| **Audit logs** | See FR-013 — immutable-by-convention, `REQUIRES_NEW`-isolated, failure-tolerant. |
| **OWASP Top 10** | `docs/ARCHITECTURE.md` maintains an explicit mitigation-status table per category (verified this session for injection: parameterized JPA queries confirmed safe against a live SQL-injection payload). CSRF is deliberately disabled (bearer-token API, no cookies) with a documented rationale. |
| **Rate limiting** | See FR-012. |
| **Security headers** | Only `X-Frame-Options: DENY` (Spring Security's own default, not custom-configured). **No CSP, no HSTS, no `X-Content-Type-Options` anywhere** — confirmed absent from both `SecurityConfig` and `nginx.conf`. |
| **Input sanitization** | Custom `@ValidHttpUrl` (scheme/host/length checks) and `@NoScriptTag` (regex-based XSS-pattern rejection) validators on the URL field. |

---

## 14. Non-Functional Requirements

| Category | Statement |
|---|---|
| Performance | Redirect path p95 < 100ms at 1000 req/s (k6-asserted). |
| Scalability | Backend is stateless by design (JWT/API-key auth, no server-side session, no in-memory rate-limit state — Redis-backed) and horizontally scalable; `docker compose up --scale backend=3` works today behind Nginx's round-robin upstream, and the Kafka consumer group scales to the topic's 3 partitions. **Deployed today: 1 backend instance.** |
| Availability | No documented uptime SLA; single-instance-by-default deployment has no failover for Postgres/Redis/Kafka as configured. Rate limiter and (implicitly) cache fail open rather than fail the request when Redis is down. |
| Reliability | Kafka producer: `acks=all`, idempotent producer, 10 retries. Consumer: manual-ack, 3-attempt retry + DLQ. Click-count/detail-row writes are transactionally coupled and idempotent (§FR-010). |
| Maintainability | Constructor-injection-only (`@RequiredArgsConstructor`+`final`), documented coding standards (`docs/DEVELOPER_GUIDE.md`), Checkstyle/PMD/SpotBugs in CI (non-blocking today). |
| Extensibility | Strategy pattern for short-code generation (`random`/`hash`, pluggable); documented 5-step recipe for adding a new Kafka event type (`docs/DEVELOPER_GUIDE.md`). |
| Portability | Fully Dockerized, no cloud-specific code; a PROPOSED (undiagrammed-in-code) AWS migration path exists only as documentation. |
| Accessibility | Bootstrap 5 defaults only; no dedicated WCAG audit; one a11y bug found and fixed during Playwright suite development (specifics not detailed in source). |
| Usability | Minimal, functional Bootstrap UI; no onboarding/help text beyond inline hints and demo credentials on the login page. |
| Localization / Internationalization | **Not implemented** — all UI strings are hardcoded English; no `i18n` resource bundles found. |

---

## 15. Performance Requirements

| Requirement | Value | Source |
|---|---|---|
| Max response time (redirect) | p95 < 100ms | `perf/redirect-load-test.js` threshold `redirect_duration_ms: p(95)<100` |
| Concurrent load tested | Ramps to 1000 req/s sustained for 2 minutes | k6 `ramping-arrival-rate` scenario, `preAllocatedVUs=200`, `maxVUs=1000` |
| Error budget under load | `redirect_errors` and `http_req_failed` both `< 1%` | Same k6 thresholds |
| Transactions per second | 1000/s (redirect path only — not independently asserted for write endpoints) | Same |
| Caching strategy | `urlLookup` (Redis, 1h TTL) for redirect resolution; `analytics` (Redis, 60s TTL, skipped only while zero) for analytics reads | `application.yml` `app.cache.*`, `AnalyticsServiceImpl`, `UrlServiceImpl.resolveForRedirect` |
| Load test scope | **Redirect path only** — no k6/load test exists for create-URL, list, or auth endpoints | `perf/` directory contents |

---

## 16. Logging & Monitoring

| Capability | Status |
|---|---|
| Application logs | Structured JSON (Logback/Spring), correlation ID (`X-Request-Id`) in every log line via MDC |
| Audit logs | Postgres `audit_logs` table (FR-013) |
| Metrics | Micrometer → Prometheus (`/actuator/prometheus`), including 2 custom counters (`urlshortener_urls_created_total`, `urlshortener_urls_clicked_total`) |
| Distributed tracing | Trace/span IDs exist (`management.tracing.sampling.probability=1.0`) but **no Jaeger/Zipkin UI is deployed** — traces are generated but not visualized ("partial" per `docs/ARCHITECTURE.md`'s own three-tier implemented/partial/not-implemented breakdown) |
| Health checks | `/actuator/health`, `/actuator/health/liveness` (used by the backend's own Docker healthcheck); Nginx's `/healthz` is a static 200, independent of backend health |
| Alerts | **Not implemented** — no Alertmanager, no paging, nothing watches Grafana for you |
| Dashboards | One provisioned Grafana dashboard, "URL Shortener Platform - Overview", 6 panels: HTTP request rate, HTTP p95 latency by URI, URLs created/clicked per 5m, Redis cache hit ratio, JVM heap by pool, HikariCP active+pending connections. **Deliberately excludes** a Kafka consumer-lag panel (that metric isn't wired up). |

---

## 17. Error Handling

Global shape and per-exception status mapping are in §9. Summary by category:
- **Validation errors** (`400`): Bean Validation (`MethodArgumentNotValidException`/`ConstraintViolationException`) and malformed JSON (`HttpMessageNotReadableException`) both produce the same `ErrorResponse` shape, the former with a populated `validationErrors` map.
- **Business errors:** `404` (not found), `409` (duplicate username/email), `410` (expired link), `401` (bad credentials/token/link-password), `403` (ownership violation).
- **System errors:** any uncaught `Exception` → `500`, generic message, no stack trace leaked to the client, but logged server-side with full detail.
- **Network failures / timeouts:** not specifically handled at the API layer beyond standard container/connection timeouts (HikariCP `connection-timeout=5000ms`; Nginx `proxy_read_timeout` 10s on `/api/`).
- **Retry strategy:** only the Kafka consumer has an explicit, configured retry (3 attempts, 1s fixed backoff) before DLQ; there is no client-facing retry guidance/idempotency-key mechanism for POST endpoints (§9).
- **Fallback / graceful degradation:** Redis unreachable → rate limiter fails open (allows traffic) rather than failing closed; this is the only implemented fallback behavior found. There is no fallback for Postgres or Kafka being unreachable — those are hard dependencies (`docker-compose.yml` `depends_on: condition: service_healthy` on the backend).

---

## 18. Reporting & Analytics

- **Dashboards:** the per-link Analytics page (§7.3) is the only user-facing reporting surface; Grafana (§16) is the operator-facing equivalent.
- **Reports:** no scheduled/exportable reports exist.
- **KPIs:** total clicks, unique visitors (by distinct `ip_hash`), daily click trend, browser/OS/device/country/referrer distribution — all per-link, none aggregated across a user's whole link portfolio.
- **Charts:** simple CSS bar charts (no charting library dependency) on the analytics page; Prometheus/Grafana time series for the ops dashboard.
- **Export to CSV/PDF:** **not implemented** anywhere.
- **Audit reports:** no UI exists to browse `audit_logs` — it is a database table only, queryable directly (as done in this session) but not surfaced through any screen or API endpoint.

---

## 19. Deployment Requirements

| Environment | As-built |
|---|---|
| Development | `mvn spring-boot:run` against `localhost`-default config (`docs/DEVELOPER_GUIDE.md`); frontend must be served through a proxy (not opened via `file://`, since relative `/api/...` calls would break). |
| Testing | `mvn test` (unit), `mvn verify` (adds Testcontainers integration tests), `.github/workflows/ci.yml`. |
| UAT | Not a distinct named environment in this repo — `docker compose up --build` is the same artifact used for demo/UAT/local. |
| Production | Not deployed anywhere as part of this repo; `docs/DEPLOYMENT.md`'s 8-item hardening checklist is the documented gap-list before that would be appropriate (replace `JWT_SECRET`/DB password, disable `SEED_DEMO_DATA`, add TLS termination, restrict CORS, move to managed Postgres/Redis/Kafka, wire a real GeoIP provider, run OWASP Dependency-Check/SonarCloud for real, and re-validate CSRF/JWT threat model if cookie-based auth is ever introduced). |
| CI/CD | `.github/workflows/ci.yml`: compile → static-analysis (Checkstyle/PMD/SpotBugs) → unit-tests (+JaCoCo) → integration-tests (Testcontainers) → docker-build (`push:false`, no registry push, no CD stage exists). A separate `.github/workflows/automation-tests.yml` runs the full Playwright suite against a live `docker compose` stack on PRs touching relevant paths and nightly at `03:00 UTC`. |
| Rollback strategy | **Not implemented/documented** — no blue-green, no versioned-image rollback runbook; Flyway migrations have no down-migration story either. |
| Backup strategy | **Not implemented** — no automated Postgres backup/snapshot job exists in this repo. |
| Disaster recovery | **Not implemented/documented.** |

---

## 20. Compliance

None of the following are targeted or implemented; stating this explicitly rather than describing generic controls that don't exist here:
- **GDPR:** partially relevant by accident (BCrypt/hashed data) but no data-subject-access/erasure workflow, no consent management, no documented lawful basis, and — notably — **raw IP addresses are retained in plaintext** alongside the hash (§10/§13), which would need remediation before any real GDPR claim.
- **PCI DSS:** N/A — no payment data is ever handled.
- **SOC2:** No formal controls program, though the audit-log/RBAC/CI foundations are the kind of evidence a SOC2 effort would eventually build on.
- **HIPAA:** N/A — no health data.
- **WCAG / Accessibility:** No formal audit; Bootstrap-default accessibility only (§14).

---

## 21. Assumptions

(Consolidated from §2 and inline throughout.) Docker Compose is the only real deployment target today. The seeded demo/admin accounts and default secrets are dev-only and expected to be rotated before any non-local use. "Enterprise-grade" refers to engineering rigor, not compliance certification. GeoIP, notifications, SSO, and TLS are all treated as known, intentional, documented gaps rather than oversights — each has a specific "PROPOSED" callout in `docs/ARCHITECTURE.md`.

---

## 22. Risks

| Risk | Category | Evidence | Mitigation status |
|---|---|---|---|
| Analytics undercounts/lags after real traffic | Technical | Confirmed live and in `automation/README.md`: `analytics` cache never evicts on new clicks (up to 60s staleness); password-protected links' clicks are never counted at all, even on successful unlock | **Unfixed**, documented |
| No TLS anywhere | Security | `infra/nginx/nginx.conf` plain HTTP:80; confirmed absent | **Unfixed**, top item on ARCHITECTURE.md's Future Enhancements |
| Plaintext IP retention | Security/Privacy | `url_clicks.ip_address` populated with the real IP alongside `ip_hash`, confirmed live this session | **Unfixed**, undocumented as a gap in the repo's own docs (this SRS is the first place it's flagged) |
| No rate/size cap on `GET /api/v1/urls` | Technical/Operational | No max page size or sortable-field allow-list in `UrlController.listUrls` | **Unfixed**, confirmed via source read |
| Stolen JWT valid until expiry | Security | No revocation list; explicit, accepted trade-off in `docs/ARCHITECTURE.md` | Accepted risk (1h blast-radius bound), not a gap to fix silently |
| Redis outage → rate limiting disabled | Availability/Security | `RedisRateLimiter` fails open by design | Accepted trade-off (availability over strictness) |
| Kafka DLQ has no reprocessing tool | Operational | `docs/TROUBLESHOOTING.md`: manual `kafka-console-consumer` inspection only | Documented, unfixed |
| No backup/DR strategy | Operational | Confirmed absent from `docker-compose.yml`/`docs/` | Out of scope for current phase |
| Untested DLQ routing path | Technical/Test-coverage | `AI_ENGINEERING/05_ai_traceability.md` Task 7: judged not worth the added Testcontainers complexity | Accepted, documented test gap |
| Low overall code coverage vs. typical enterprise bar | Technical | 37.5% instruction / 35.5% line JaCoCo; CI gate only requires 25% line | Accepted, explicitly justified in `05_ai_traceability.md` Task 10 as depth-over-breadth on the highest-value 20% |

---

## 23. Open Questions

1. Should API key `scopes` (`URL_READ`/`URL_WRITE`) actually be enforced, given the column exists but nothing checks it today?
2. Should the analytics-cache staleness gap (§FR-010) be fixed by adding an eviction hook on new clicks, or is a bounded 60s staleness acceptable for the product's actual use cases?
3. Should password-protected-link clicks be counted at all (§FR-008/FR-010), and if so, at prompt-view time, at successful-unlock time, or both?
4. Is retaining raw IP addresses (`ip_address`, not just `ip_hash`) a conscious product decision, or should it be dropped/shortened-retention for privacy posture (§13/§22)?
5. What is the actual target deployment environment (stays on Docker Compose vs. the PROPOSED AWS path in `docs/ARCHITECTURE.md`)? This materially changes §19/§22 priorities.
6. Is a 25%-line-coverage CI gate (vs. the "90%" language in the original spec that `AI_ENGINEERING/01_requirement_analysis.md` records as an identified ambiguity) an accepted permanent target, or a temporary one?
7. Should `GET /api/v1/urls` gain a max-page-size cap and a sortable-field allow-list before any public/adversarial exposure?

---

## 24. Traceability Matrix

| Business Req | Functional Req | User Story | API | DB Entity | UI Screen | Test Coverage |
|---|---|---|---|---|---|---|
| Self-service onboarding | FR-001 | US-001 | API-001 | `users` | `login.html` (Register tab) | `AuthServiceImplTest`; Playwright `auth/register.spec.ts` (10+ cases) |
| Secure sign-in | FR-002 | US-002 | API-002 | `users`, `audit_logs` | `login.html` (Sign in tab) | `AuthServiceImplTest`; Playwright `auth/login.spec.ts` (11 cases) |
| Session continuity | FR-003 | US-003 | API-003 | `users` | (implicit, no dedicated screen) | `JwtTokenProviderTest`; Playwright `auth/session.spec.ts` (7 cases) |
| Link creation | FR-004 | US-004 | API-004 | `urls`, `audit_logs` | `index.html` (create form) | `UrlServiceImplTest`, `RandomShortCodeGeneratorTest`, `ValidHttpUrlValidatorTest`, `NoScriptTagValidatorTest`; Playwright `functional/url-crud.spec.ts` (13), `api/urls.api.spec.ts` (12) |
| Access control on links | FR-006, FR-007, FR-014 | US-008, US-009, US-013 | API-006, API-007 | `urls` | `index.html` (edit-expiry modal, delete) | `UrlServiceImplTest` (ownership/admin-bypass cases); Playwright `auth/rbac.spec.ts` (11) |
| Link discovery | FR-005 | US-007 | API-005 | `urls` | `index.html` (search/filter/paginate) | Playwright `ui/dashboard-ui.spec.ts` (8) |
| Confidential sharing | FR-008 | US-005 | API-009, API-010 | `urls` | `protected.html` | `UrlServiceImplTest` (password verify cases); Playwright `e2e/shorten-redirect-analytics.spec.ts` (password-gated cases within the 5) |
| Reliable redirection | FR-009 | US-012 | API-010 | `urls` | (n/a — server redirect) | `RedirectControllerTest`; `perf/redirect-load-test.js` (k6, p95<100ms) |
| Performance insight | FR-010 | US-010 | API-008 | `url_clicks`, `urls` | `analytics.html` | `integration/UrlShortenerFlowIntegrationTest` (Testcontainers, full pipeline); Playwright `e2e/shorten-redirect-analytics.spec.ts` (5) |
| Programmatic access | FR-011 | US-011 | API-011, API-012, API-013 | `api_keys`, `audit_logs` | `api-keys.html` | `ApiKeyHasherTest`; Playwright `functional/api-keys-ui.spec.ts` (4), `api/api-keys.api.spec.ts` (7) |
| Abuse prevention | FR-012 | (cross-cutting, no dedicated story) | all | (Redis, not a DB entity) | (n/a) | `RedisRateLimiterTest`; Playwright `security/rate-limit.spec.ts` |
| Traceability/compliance-readiness | FR-013 | (cross-cutting) | all mutating endpoints | `audit_logs` | (n/a — no audit UI) | Verified live this session (direct DB query cross-check); no dedicated automated test class found for audit-log content itself beyond incidental coverage |

---

## 25. Test Readiness

| Test type | What exists today |
|---|---|
| **Manual testing** | `docs/test-cases/url-lifecycle-test-cases.md` (exists; not deeply inspected in this pass, referenced by `ARCHITECTURE.md` as flagging missing-feature gaps like custom codes/quotas). Live manual DB validation was performed in this session (see this document's Executive Summary evidence table). |
| **Playwright automation** | 131 test cases across `auth/`, `functional/`, `ui/`, `e2e/`, `api/`, `validation/`, `security/` specs; 128 pass out of the box; 3 don't (1 deliberately `test.skip`'d — true JWT-expiry testing needs the signing secret; 2 rate-limit cases only pass against default, non-CI-overridden limits). Page Object Model (`pages/`), typed API client (`api/`), read-only Postgres assertion helper (`helpers/db.ts`), Faker-backed data generation. Runs in CI via `.github/workflows/automation-tests.yml` (chromium+firefox+webkit+api projects, nightly + PR-triggered). |
| **API testing** | Dedicated `tests/api/` Playwright specs (27 cases: 8 auth, 12 urls, 7 api-keys) plus `docs/postman_collection.json` for manual/exploratory use. |
| **Performance testing** | `perf/redirect-load-test.js` (k6), redirect path only, ramping to 1000 req/s, asserting p95<100ms and <1% error rate. **Not run in CI** — a manual/ops tool. No load test exists for write endpoints (create URL, register/login). |
| **Security testing** | Playwright `security/` specs (rate-limit, security-headers — 6 cases total); OWASP Dependency-Check and SonarCloud are wired into the Maven build (`pom.xml`) but require network/credentials, so they run in principle but weren't exercised in the offline validation pass documented in `AI_ENGINEERING/08_validation_report.md`. Live SQL-injection and audit-trail spot checks were performed manually in this session (see Executive Summary). |
| **Regression testing** | The full backend (JUnit) + Playwright suite together function as the regression suite; both run in CI on relevant changes. |
| **User Acceptance Testing** | No distinct UAT environment or sign-off process exists in this repo — `docker compose up --build` is the same artifact for demo, dev, and (nominal) UAT. |

---

## Functional Requirements Summary

14 functional requirements (FR-001–FR-014) covering auth (register/login/refresh), the full URL lifecycle (create/list/update-expiry/soft-delete/redirect), password protection, click analytics, API key management, and three cross-cutting concerns (rate limiting, audit logging, RBAC). All 14 are implemented and verified against source; two (FR-008/FR-010's click-tracking gap on protected links, and FR-005's unbounded pagination) carry confirmed, documented gaps rather than being fully spec-compliant.

## Non-Functional Requirements Summary
Performance (p95<100ms redirect @1000rps, k6-verified), stateless horizontal scalability (designed-in, single-instance-deployed-today), Kafka reliability guarantees (idempotent producer, retry+DLQ consumer), documented-but-unenforced maintainability/extensibility standards, Docker-only portability, Bootstrap-default accessibility, and explicitly **no** localization/internationalization.

## MVP Scope
Everything in §2 "In Scope" already exists and is running — there is no smaller "MVP" subset left to build; if anything, the natural MVP-vs-later split retroactively is: **MVP** = register/login, create/redirect, basic list — **added on top** = password protection, expiry, analytics, API keys, rate limiting, audit logging, full observability stack. All of it ships together today.

## Phase 2 Enhancements
TLS at the edge; real GeoIP; custom/vanity codes; per-user quotas; token revocation list; Kafka consumer-lag alerting + circuit breaker; notification integrations; the AWS migration path; a max-page-size cap and sortable-field allow-list on `GET /api/v1/urls`; an eviction hook fixing the analytics-cache staleness gap; click-tracking for password-protected links.

## Estimated Development Complexity
**Medium-high** for what exists: a modular-monolith Spring Boot backend with a real async messaging pipeline (Kafka, not just a queue), dual auth schemes, Testcontainers-backed integration tests, and a 131-case E2E suite — all genuinely built and passing, not scaffolded. Remaining Phase 2 items are individually small-to-medium (TLS, GeoIP swap, pagination cap) except the AWS migration, which is a substantial infra project of its own.

## Suggested Technology Stack
Already built, not hypothetical: **Backend** Java 21 / Spring Boot 3.3 / Spring Security / Spring Data JPA / Spring Validation / Spring Actuator. **Data** PostgreSQL 16 + Flyway, Redis 7. **Messaging** Apache Kafka + Zookeeper. **Frontend** HTML5/CSS3/vanilla JS/Bootstrap 5. **Infra** Docker/Docker Compose, Nginx, Prometheus, Grafana. **CI/CD** GitHub Actions. **Testing** JUnit 5, Mockito, Testcontainers, k6, Playwright/TypeScript.

## Suggested System Architecture
As documented in `docs/ARCHITECTURE.md`: a modular, event-augmented monolith. Client → Nginx (static frontend + reverse proxy + redirect passthrough) → Spring Boot backend (controllers → services → repositories, JWT/API-key auth filters, rate-limit filter) → PostgreSQL (system of record) + Redis (cache/rate-limit) synchronously, and Kafka (click events, fire-and-forget from the redirect hot path, consumed asynchronously into analytics) — with Prometheus scraping `/actuator/prometheus` and Grafana visualizing it. See ARCHITECTURE.md for the full Mermaid diagrams (component, ER, sequence ×3, deployment topology, PROPOSED AWS target).

## Suggested Testing Strategy
Already implemented, worth continuing as-is: unit tests close to the code (Mockito-mocked service/controller layers) for fast feedback; Testcontainers integration tests for anything touching real Postgres/Kafka/Redis semantics; k6 for the one path with a hard performance NFR; Playwright E2E across UI+API+security concerns, run both on PR and nightly against a full live stack. Gaps worth closing next: a load test for write endpoints, an automated DLQ-routing test, and raising the JaCoCo floor incrementally rather than all at once.

## Suggested Project Milestones
Retroactive, based on `AI_ENGINEERING/02_task_breakdown.md`'s actual 12-phase delivery order: (1) scaffold, (2) database schema, (3) core domain/API, (4) security, (5) cache/messaging, (6) frontend, (7) automated tests, (8) infra (Docker/Nginx/Prometheus/Grafana), (9) CI/CD, (10) documentation, (11) AI-engineering traceability docs, (12) final validation pass. All 12 phases are complete in this repo today.

## Executive Review Checklist
- [ ] Decide on the 7 Open Questions (§23) before treating any of them as settled.
- [ ] Confirm whether the plaintext-IP-retention finding (§13/§22) requires immediate remediation before any external exposure.
- [ ] Confirm whether the analytics-cache-staleness and protected-link-click-tracking gaps (§FR-008/FR-010) are acceptable as-is or need a fix before relying on analytics for decisions.
- [ ] Decide the real target deployment environment (Docker Compose vs. the PROPOSED AWS path) — this reprioritizes almost every item in §19/§22.
- [ ] Sign off on the 25%-JaCoCo-line CI gate as a permanent bar, or set a plan to raise it.
- [ ] Confirm no compliance regime (GDPR/PCI/SOC2/HIPAA) is actually required for this system's real users/data before treating §20 as closed.
- [ ] Decide whether `GET /api/v1/urls`'s unbounded pagination (§FR-005/§8) is acceptable for the system's real exposure (internal-only vs. public-facing).

---

*Compiled by direct source inspection and live database/API validation in a single working session. Where this document says a capability is "not implemented" or a bug is "confirmed unfixed," that was independently verified against the actual code and/or the running system — not inferred from documentation alone.*
