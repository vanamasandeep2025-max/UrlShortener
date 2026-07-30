# 10 — Phase 13 Brownfield Evidence & AI-Assisted Execution Patterns

## Why this document exists

`scenarios/02_brownfield_browser_detection.md` is a *simulated* brownfield exercise,
written inside what was actually a greenfield build - useful for showing the reasoning
pattern, but not real brownfield work against a system that already existed and was
already running. Phase 13 (see `08_validation_report.md`, `09_final_engineering_summary.md`)
*was* real brownfield work: ten defects found and fixed in a live, already-deployed system,
each requiring identifying impacted modules in an unfamiliar-at-the-moment codebase before
touching anything. This document consolidates that evidence in one place, plus one
prompt-iteration example Phase 13 produced that `04_prompt_iterations.md` predates, plus an
explicit statement of the oversight pattern applied throughout - all three previously
implicit in the session transcript rather than recorded here.

---

## Part 1 — Genuine brownfield reasoning: three defects, in full

The following three were chosen for detailed treatment because each required reasoning
*across* an architectural boundary (transaction propagation, persistence-context lifecycle,
cache serialization strategy) rather than a local, single-file fix - the same bar
`scenarios/02` sets for what counts as real brownfield reasoning.

### 13a — Registration's transaction-boundary bug

**Symptom**: `POST /auth/register` intermittently 500'd with
`DataIntegrityViolationException: ... audit_logs_actor_user_id_fkey`.

**Impacted-module identification, in order actually followed**:

| Class | Role in the investigation |
|---|---|
| `GlobalExceptionHandler` | Confirmed this was an *unhandled* exception (fell to the generic 500 handler), not a validation failure - ruled out the obvious "bad request" explanation first |
| `AuthServiceImpl#register` | Where the user was created and audit-logged, both inside one `@Transactional` method |
| `AuditService#log` | Found to run under `Propagation.REQUIRES_NEW` - the actual root cause lives here, not in `register()` itself |
| `UserRepository` / Postgres `audit_logs_actor_user_id_fkey` | Confirmed via `psql` that the FK genuinely references a row that, from the audit transaction's perspective, doesn't exist yet |

**Architecture reasoning**: `REQUIRES_NEW` suspends the caller's transaction and opens a
second one, typically on a second physical connection. A second transaction can never see
a row the first, still-open transaction hasn't committed - not a bug in Postgres, not
fixable by flushing harder, a genuine consequence of how transaction propagation works.
The only correct fix is ensuring the user's own transaction actually *commits* before the
audit write runs, which means user creation needs its own transaction boundary, not a
nested one. Extracted into `UserRegistrationService` - a new bean, deliberately not a new
method on `AuthServiceImpl`, because a self-invoked `@Transactional` method bypasses
Spring's proxy and wouldn't have created a real boundary at all (the same self-invocation
pitfall the codebase already documents for `UrlClickIngestionService`, found and avoided
here without needing to relearn it).

**Verified**: registration re-tested live post-fix; the unit test suite
(`AuthServiceImplTest`) updated to mock the new collaborator rather than the old inline
construction, confirming the test suite's understanding of the class's dependencies stayed
accurate.

---

### 13b — The click-persistence split-brain

**Symptom**: `urls.click_count` incremented correctly on every redirect; `url_clicks`
stayed completely empty. Found while proving Kafka was genuinely wired up, not simply
configured - the exact kind of claim that's easy to leave unverified in an AI-assisted
build and exactly why it was checked with `psql`, not just re-read code.

**Impacted-module identification**:

| Class | Role |
|---|---|
| `RedirectController` | Ruled out first - confirmed the `UrlClickedEvent` was genuinely being published (Kafka topic inspected directly via `kafka-console-consumer`) |
| `UrlClickedEventConsumer` | Confirmed the consumer was genuinely receiving and processing messages (structured logs showed successful processing per message) |
| `UrlClickIngestionService#recordClick` | Where the actual persistence happened - `save()` on a new `UrlClick`, immediately followed by a bulk `@Modifying(clearAutomatically=true)` update |
| `UrlRepository#incrementClickCount` | The bulk update whose `clearAutomatically=true` was the actual trigger - it detaches every entity in the persistence context, including a still-unflushed new one |

**Architecture reasoning**: `save()` on a new JPA entity schedules an `INSERT`, it doesn't
execute one - the actual write is deferred to the next flush, normally at transaction
commit. `clearAutomatically=true` on the bulk update runs *before* that commit, inside the
same transaction, and detaches the pending, not-yet-flushed `UrlClick` from the persistence
context - Hibernate then has no reason to ever send that `INSERT`, and no exception is
thrown, because nothing has actually gone wrong from Hibernate's perspective. This is a
genuinely subtle JPA lifecycle interaction, not a typo - the kind of bug that survives code
review by eye because both individual statements are correct in isolation. Fixed with
`saveAndFlush()`, forcing the `INSERT` to happen before the bulk update can discard it.

**Verified**: live redirect, confirmed a real row landed in `url_clicks` with correctly
parsed browser/OS/device fields, matching `click_count`'s new value exactly.

---

### 13c — The cache serializer's two-step failure (and the iteration this produced)

**Symptom, first pass**: `GET /analytics` threw `InvalidDefinitionException` on
`LocalDate` fields.

**Symptom, second pass, after the first fix**: the *same* endpoint's second call then threw
`ClassCastException: LinkedHashMap cannot be cast to AnalyticsResponse`.

This is the prompt-iteration example `04_prompt_iterations.md` predates - recorded here in
that document's own format for consistency:

**First pass prompt**: "The analytics cache is throwing `InvalidDefinitionException` on
`LocalDate` - `GenericJackson2JsonRedisSerializer()`'s no-arg constructor builds its own
`ObjectMapper` with no `JavaTimeModule`. Fix by injecting the app's real `ObjectMapper`."

**Issue found on review of that fix's own result**: the write path was verified live and
worked - but reading the *same* cached value back threw `ClassCastException`. Re-reasoning
about `GenericJackson2JsonRedisSerializer`'s actual contract (not just its constructor
signature) surfaced the real mechanism: its no-arg constructor doesn't just supply a
default `ObjectMapper`, it also mutates that mapper to activate polymorphic `@class`
type-hint embedding, which is what lets its deserializer know what concrete class to
reconstruct. Passing in a *copy* of the app's own `ObjectMapper` fixed the missing
`JavaTimeModule` but never activated that type-hint behavior, so cached JSON round-tripped
with no type information at all - deserializing to a generic `LinkedHashMap` by default.

**Revised prompt**: "Don't fix the generic serializer further. Each of the two caches here
(`urlLookup`, `analytics`) only ever holds one known, fixed type - replace
`GenericJackson2JsonRedisSerializer` with a typed `Jackson2JsonRedisSerializer<T>` per
cache, which needs no type-hint metadata at all because the target type is already known
at cache-definition time."

**Result**: `CacheConfig` rewritten to build two typed serializers instead of one generic
one. Verified live across three consecutive calls to the same analytics endpoint (write,
first read, second read) with no exception, plus the original `LocalDate` fix retained.

**Why this is the more interesting iteration to record**: Iterations 1-3 in
`04_prompt_iterations.md` all involved catching a problem *before* it shipped, on review of
a first draft. This one shipped, was live-tested, appeared to work (the write path
genuinely was fixed), and only failed on a *second* call - a class of bug that pure code
review, and even a single successful test, would not have caught. It's the concrete
argument for why Phase 13's live, repeated-call validation matters as its own category of
evidence, not a redundant re-check of what unit tests already covered.

---

## Part 2 — The remaining Phase 13 defects, summarized

The other seven (rate limiter DI, Grafana volume mount, Avast TLS interception, frontend
encoding, the login 401-swallowing bug, the dashboard layout/a11y issues, plus the three
bugs found while building the automation suite itself) are documented with full root-cause
narrative in `08_validation_report.md`'s Phase 13 section and are not repeated here - this
document exists to add the *depth* treatment for the three richest examples, not to
duplicate the full list.

---

## Part 3 — Controlled oversight, made explicit

`02_task_breakdown.md` documents the oversight pattern for the original build (a plan
required and approved before code). Phase 13 and everything after it followed the same
underlying pattern - engineer sets scope and approves consequential actions, AI executes
and reports within that scope - just never stated as a pattern anywhere. Concrete instances
from this session:

- **Explicit confirmation sought before installing new software**: ffmpeg was not installed
  silently when a video-editing task needed it - the need was stated, then installed.
- **A real trade-off surfaced for a decision, not resolved unilaterally**: trimming a video
  "without changing any format" collided with the video's actual keyframe spacing (32s
  apart, nowhere near the requested 60s cut point). Three concrete options were presented
  with their exact consequences (lossless-but-64s, frame-accurate-but-re-encoded,
  lossless-but-32s) rather than silently picking one.
- **Confirmation sought before every push to a remote repository**, every time, throughout
  the session - not just once at the start.
- **The engineer redirected AI execution mid-task at least once, explicitly**: while
  debugging why Testcontainers couldn't discover Docker Desktop from a native-Windows JVM,
  the instruction "only update the document" stopped further investigation immediately -
  the AI recorded the real, incomplete result honestly (Testcontainers still doesn't run,
  here's why, here's the actual next step) rather than continuing an unscoped debugging
  session or quietly finishing it anyway.
- **Autonomous execution within a clearly scoped task**: once a defect's root cause was
  identified, the fix-verify-commit cycle for each Phase 13 bug ran without needing
  step-by-step approval - the approval gate was at task/scope boundaries (what to work on,
  whether to push), not at every individual edit, matching the "engineer approves outputs,
  AI executes within tasks" split this rubric describes.

## What this document does not claim

This is not a claim that every Phase 13 action was reviewed with the same rigor as the
three defects detailed in Part 1 - some fixes (the UI layout/a11y issues, for instance)
were smaller, single-file changes verified by direct visual confirmation rather than deep
cross-module reasoning, and are accurately represented as such in `08_validation_report.md`
rather than inflated to match the depth of the three chosen here.
