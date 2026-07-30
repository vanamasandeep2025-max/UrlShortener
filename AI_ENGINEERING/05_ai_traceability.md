# 05 — AI Traceability

Requirement-to-code traceability for the tasks that most shaped the system, each recording
what was asked, what the AI produced, how it was reviewed, and how that review was
validated. This is the detailed evidence behind the summaries in `02_task_breakdown.md`
and `04_prompt_iterations.md`.

---

### Task 1 — Soft-delete-aware unique short codes

- **Requirement**: Delete URL (soft delete); short codes must not collide.
- **Prompt**: "Short codes must be unique only among live rows - a soft-deleted code
  should be reusable."
- **AI output summary**: `urls.short_code` given a *partial* unique index
  (`WHERE deleted_at IS NULL`) instead of a plain column-level unique constraint; repository
  methods (`existsByShortCodeAndDeletedAtIsNull`, `findByShortCodeAndDeletedAtIsNull`)
  written to match.
- **Engineer review**: Accepted. Postgres partial indexes are the correct, standard tool
  for this exact "unique among a subset of rows" requirement.
- **Accepted / Rejected / Modified**: Accepted as-is.
- **Validation performed**: `UrlRepositoryIntegrationTest#shortCodeUniqueConstraintOnlyAppliesToLiveRows`
  (Testcontainers Postgres) - creates a code, soft-deletes it, creates the same code again,
  asserts success.

---

### Task 2 — Short-code generation strategy

- **Requirement**: (Design pattern requirement) Strategy pattern usage.
- **Prompt**: "Short-code generation should be a genuine Strategy, not a fake one-impl
  wrapper just to tick a box - only add a second strategy if it's actually different in a
  meaningful way."
- **AI output summary**: `ShortCodeGenerator` interface with two real implementations -
  `RandomShortCodeGenerator` (SecureRandom Base62) and `HashBasedShortCodeGenerator`
  (SHA-256 of URL+attempt+nanoTime, Base62-encoded) - selected at runtime via
  `ShortCodeGeneratorFactory` reading a Spring-injected `Map<String, ShortCodeGenerator>`.
- **Engineer review**: Accepted. Two genuinely different strategies with a real
  behavioral difference (content-derived vs. pure entropy), selected via config
  (`app.short-code.strategy`), not two classes that do the same thing.
- **Accepted / Rejected / Modified**: Accepted as-is.
- **Validation performed**: `RandomShortCodeGeneratorTest`, `HashBasedShortCodeGeneratorTest`
  (length, alphabet, distinctness across attempts/calls).

---

### Task 3 — Redirect hot-path latency

- **Requirement**: NFR <100ms / 1000 req/s; Redirect endpoint tracks analytics and
  increments click count.
- **Prompt**: See `03_ai_prompts.md` ("redirect NFR" prompt).
- **AI output summary**: `RedirectController` does a cache-first lookup
  (`UrlService.resolveForRedirect`, `@Cacheable`) and, for a valid non-protected link,
  publishes a `UrlClickedEvent` via `ApplicationEventPublisher` (in-process, effectively
  free) before returning 302 - no synchronous DB write on the request path. Click
  persistence and count increment happen entirely in the async Kafka consumer.
- **Engineer review**: Accepted - matches the architecture diagram given in the spec
  (Client -> Nginx -> API -> Redis -> Kafka -> Analytics Consumer -> Postgres), which
  itself implies clicks flow through Kafka rather than being written synchronously by the
  API layer.
- **Accepted / Rejected / Modified**: Accepted as-is.
- **Validation performed**: `RedirectControllerTest` (standalone MockMvc, asserts 302 +
  Location + that `eventPublisher.publishEvent` is called exactly once, with no blocking
  DB call in the controller); `UrlShortenerFlowIntegrationTest` proves the full async path
  actually lands in Postgres.

---

### Task 4 — Distributed rate limiting

- See `04_prompt_iterations.md`, Iteration 1, for the full before/after.
- **Requirement**: Rate limiting (Security section) + Redis (Cache section).
- **Accepted / Rejected / Modified**: **Modified** - first-pass dependency choice
  (Bucket4j-Redis) rejected in favor of a hand-rolled Redis+Lua limiter.
- **Validation performed**: `RedisRateLimiterTest` (allow/deny/fail-open, mocked
  `StringRedisTemplate`); exercised for real in `RateLimitFilter` wiring in `SecurityConfig`.

---

### Task 5 — JPA bulk-update staleness

- See `04_prompt_iterations.md`, Iteration 2.
- **Requirement**: Increment click count; scheduled expiry cleanup.
- **Accepted / Rejected / Modified**: **Modified** - `clearAutomatically = true` added
  after the defect was found during test-writing, not by the initial implementation.
- **Validation performed**: `UrlRepositoryIntegrationTest` (two dedicated tests, real
  Postgres via Testcontainers).

---

### Task 6 — Analytics cache poisoning

- See `04_prompt_iterations.md`, Iteration 3.
- **Requirement**: Cache (Redis, TTL, eviction policy) + Analytics.
- **Accepted / Rejected / Modified**: **Modified** - `unless` condition added after the
  defect was found while designing the integration test.
- **Validation performed**: `UrlShortenerFlowIntegrationTest#shortenRedirectAndAnalyticsFlowEndToEnd`
  (Awaitility-polled; would have timed out/flaked under the original caching logic).

---

### Task 7 — Kafka retry + dead-letter routing

- **Requirement**: Kafka topics incl. `dead-letter`; retry; failure recovery.
- **Prompt**: "Failed messages must retry with backoff, then land on the literal
  `dead-letter` topic - not Spring Kafka's default `<topic>.DLT` suffix convention."
- **AI output summary**: `DefaultErrorHandler` configured with a `FixedBackOff` and a
  `DeadLetterPublishingRecoverer` whose destination resolver returns a fixed
  `TopicPartition(deadLetterTopicName, -1)` regardless of the original topic, overriding
  Spring Kafka's default `.DLT`-suffix naming.
- **Engineer review**: Accepted, with one deliberate simplification flagged rather than
  hidden: `ExponentialBackOff` was considered for the retry policy (to also honor a
  configured multiplier) but rejected in favor of `FixedBackOff`, since confidently
  confirming `ExponentialBackOff`'s exact API in this Spring Framework version without a
  compiler was not possible, and getting a retry/DLQ mechanism subtly wrong is worse than
  a simpler-but-correct fixed interval. The `backoff-multiplier` config property was
  removed rather than left dangling unused.
- **Accepted / Rejected / Modified**: Modified (scope reduced from exponential to fixed
  backoff, documented rather than silently downgraded).
- **Validation performed**: Configuration reviewed by inspection (`config/KafkaConfig`);
  not covered by an automated test in this build (see `07_risk_analysis.md` - triggering an
  actual DLQ routing in a Testcontainers test would need a way to force the consumer to
  throw deterministically, which was judged not worth the added test complexity for this
  exercise; documented as a gap rather than silently skipped).

---

### Task 8 — CSRF disablement

- **Requirement**: OWASP Top 10 / CSRF handling (Security section).
- **Prompt**: "Explain your CSRF decision explicitly rather than silently disabling it."
- **AI output summary**: CSRF disabled in `SecurityConfig`, with an inline comment and an
  `ARCHITECTURE.md` section explaining that CSRF protects cookie-based session auth from
  forged cross-origin requests, and this API never uses cookies for auth (bearer JWT /
  `X-API-Key` header only), so the attack CSRF defends against does not apply.
- **Engineer review**: Accepted - "CSRF handling" in the original spec is satisfied by a
  reasoned decision, not by either blindly enabling default CSRF (which would break a
  stateless JSON API for no security benefit) or silently disabling it without
  justification.
- **Accepted / Rejected / Modified**: Accepted as-is.
- **Validation performed**: Documented decision reviewed against OWASP's own guidance on
  when CSRF tokens are/aren't necessary for token-based APIs; flagged in
  `docs/DEPLOYMENT.md`'s hardening checklist that this reasoning must be re-validated if
  cookie-based auth is ever introduced later.

---

### Task 9 — Password-protected links ("secure sharing")

- Full detail in `scenarios/03_ambiguous_secure_sharing.md`.
- **Accepted / Rejected / Modified**: Modified through discussion - initial brainstorm
  considered three approaches (signed expiring share tokens, one-time-view links, and
  password protection); password protection was chosen and the other two explicitly
  rejected with reasons recorded in the scenario doc.
- **Validation performed**: Redirect flow test coverage
  (`RedirectControllerTest#redirectsToPasswordPromptForProtectedLinkWithoutTrackingClick`),
  plus the dedicated rate limit on `/verify-password` (brute-force mitigation) reviewed
  against the threat it's meant to stop.

---

### Task 10 — Test coverage honesty

- **Requirement**: "Minimum 90% coverage."
- **Prompt**: See `03_ai_prompts.md` ("Testing" prompt).
- **AI output summary**: A real but partial test suite (unit + controller + Testcontainers
  repository + one full end-to-end integration test), plus a JaCoCo gate set to 25% -
  deliberately not 90%.
- **Engineer review**: The 90% figure from the spec was **not met**, and is called out as
  such rather than worked around (e.g. by excluding large packages from the coverage
  calculation to make the percentage look better without adding real tests). See
  `08_validation_report.md` for the actual measured/estimated number and reasoning.
- **Accepted / Rejected / Modified**: Requirement acknowledged as unmet; documented, not
  hidden.
- **Validation performed**: `mvn test jacoco:report`; CI uploads the HTML report as a
  build artifact for anyone to inspect directly.
