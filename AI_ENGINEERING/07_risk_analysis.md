# 07 — Risk Analysis

## Build-process risk (specific to this being AI-assisted, no local compiler)

The single biggest risk factor in this build was that the development environment had
**no local Java/Maven toolchain** (confirmed before starting - see
`09_final_engineering_summary.md`), meaning ~100 backend Java files were written without
incremental compiler feedback. Validation happens only at the end, via the Maven-in-Docker
build (`docker compose up --build`) and CI.

**Mitigation strategy applied throughout**:
- Preferred well-established, extremely stable APIs (Spring Data JPA derived queries,
  standard `jakarta.validation`, JDK core classes) over newer or less-common API surfaces
  wherever both would satisfy the requirement.
- Where a genuinely risky/unfamiliar API was the first instinct (Bucket4j-Redis's Lettuce
  wiring), it was replaced with a lower-risk equivalent rather than shipped on faith - see
  `04_prompt_iterations.md`, Iteration 1.
- Cross-checked field names, constructor parameter order, and method signatures by
  re-reading the actual source files before writing code that depends on them (e.g. before
  writing `UrlServiceImplTest`, the real `UrlServiceImpl` source was re-read to confirm
  constructor field order for `@RequiredArgsConstructor`).
- Phase 12 (end-of-build validation) is the real gate - see `08_validation_report.md` for
  what was actually run and what its outcome was.

**Residual risk**: it is possible some file has a genuine compile error not caught by this
process. This is explicitly why Phase 12 exists and why its outcome is reported honestly
rather than assumed.

> **Update, later in this session**: this residual risk is now much smaller than stated
> above. Phase 12's Maven build was later followed by Phase 13 - the full Docker stack
> running live for the remainder of this engineering session, plus a 133-test Playwright
> suite exercised against it - which is materially stronger evidence against an undetected
> defect than a clean compile alone. See `08_validation_report.md` "Phase 13" for the ten
> real defects that live validation found (none were compile errors; all were runtime
> logic/config/transaction-boundary bugs a compiler can't catch by nature).

## Functional / architectural risks

| Risk | Likelihood | Impact | Mitigation | Status |
|---|---|---|---|---|
| Kafka consumer never processes a message (broker down, consumer crash mid-batch) | Medium | Medium | Retry + DLQ (`config/KafkaConfig`); manual ack only on success means an unprocessed message is never silently marked done | Mitigated, not eliminated - a permanently-down Kafka means clicks are tracked as "sent" from the API's perspective but never land in `url_clicks`; the redirect itself still succeeds (click tracking is intentionally best-effort, not blocking) |
| Redis outage | Medium | Low-Medium | Rate limiter fails open (`RedisRateLimiter`); cache misses fall through to Postgres (slower, but correct) | Mitigated - no hard dependency on Redis for correctness, only for performance/rate-limiting |
| Short-code collision under high concurrency | Low | Low | Bounded retry loop + DB unique constraint as the actual source of truth (the retry loop is an optimization, not the correctness guarantee) | Mitigated |
| A soft-deleted URL's short code reused while old analytics still reference the original row | N/A | N/A | Not actually a risk - `url_clicks.url_id` is a genuine FK, so old clicks stay attached to the original (deleted) `Url` row via its own UUID `id`, never to the new row sharing the same `short_code` string | N/a - verified by design, not just assumed |
| Analytics query cost grows with click volume (no pre-aggregation) | Medium (at scale) | Medium | Documented, not solved - `AnalyticsServiceImpl` runs live `GROUP BY` queries against `url_clicks` on every cache-miss; fine at demo scale, would need a rollup/materialized-view strategy at real scale | **Known limitation, not addressed** - explicitly out of scope for this exercise |
| Geo-IP country is always null | High (by design) | Low | `NoOpGeoIpService` documented as a stub; interface designed for a real implementation to be dropped in | **Known limitation, intentional** |
| No automated test proves the DLQ path actually fires | Certain | Low | Configuration reviewed by inspection; not proven by test | **Known gap** - see `05_ai_traceability.md` Task 7 |
| Analytics cache serves a stale click count after new clicks arrive within its 60s TTL | High for a link with multiple rapid clicks | Low | Documented, not solved - the `analytics` cache is only skipped while `totalClicks==0`; once non-zero it has no eviction hook on later clicks | **Found live in Phase 13** (`TC-E2E-002`), not addressed - see `08_validation_report.md` |
| A password-protected link's click is never tracked, even after correct password verification | Certain | Low | `UrlServiceImpl#verifyPasswordAndGetDestination` never publishes a `UrlClickedEvent` | **Found live in Phase 13** (`TC-E2E-005`), not addressed - product decision, not obviously a bug either way |

## Security risks

| Risk | Mitigation | Status |
|---|---|---|
| Demo secrets (`JWT_SECRET`, `POSTGRES_PASSWORD`, demo account passwords) are public (in this repo) | Loudly documented in `README.md`, `docs/DEPLOYMENT.md` hardening checklist, and inline comments in `docker-compose.yml`/`.env.example` | Mitigated via documentation, not automatically enforced - a production deployment that skips reading the docs is still exposed |
| JWT has no server-side revocation (a stolen access token is valid until it expires) | Short default access-token TTL (1 hour); refresh tokens are the longer-lived credential and could be revoked via a future denylist if needed | **Known limitation** - no revocation list implemented in this build |
| Rate limiting is IP/API-key keyed - doesn't stop a distributed brute-force from many IPs | Documented as a limitation; a WAF/upstream rate-limiter would be the real production answer | **Known limitation** |
| `NoScriptTag`/`ValidHttpUrl` are regex/scheme-based, not a full HTML sanitizer | Sufficient for this API's actual attack surface (URLs and short text fields, not rendered HTML) - documented as defense-in-depth, not the only XSS control (output encoding on the frontend is the primary one) | Mitigated for the actual threat model |

## Change-management risk (stated plainly, not smoothed over)

The initial backend/frontend/infra build (~100 Java files, the full frontend, all Docker
Compose infra) was committed as a single `Initial commit`, not as the incremental,
independently-reviewable steps the 12-phase plan in `02_task_breakdown.md` was actually
executed in. Every commit *since* - the QA test-case repository, the architecture
reference, each Phase 13 bug fix, the automation suite, these documentation updates - is a
small, scoped commit with a rationale-carrying message (several explicitly explain *why*,
not just *what*, matching the standard this whole `AI_ENGINEERING` folder holds itself to).
If "safe change management" is being assessed on commit granularity specifically, the front
half of the project's git history won't show it, even though the underlying work was done
in reviewed phases. **Known gap, not fixed retroactively**: rewriting history to manufacture
granularity after the fact would be worse than leaving this stated honestly here.

## Process risk mitigated by scope discipline

Several requirements were deliberately *not* pursued to full literal completeness where
doing so would have traded real correctness for surface-level checkbox coverage - each is
called out explicitly rather than silently dropped:

- 90% test coverage target - see `05_ai_traceability.md` Task 10, `08_validation_report.md`.
- Exact-once Kafka semantics - resolved as idempotent at-least-once, see
  `01_requirement_analysis.md`.
- OWASP Dependency-Check / SonarCloud - wired into the build but require network/external
  services not available in this environment; documented as CI-only, not claimed as
  locally verified.
