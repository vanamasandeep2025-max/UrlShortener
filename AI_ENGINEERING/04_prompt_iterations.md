# 04 — Prompt Iterations

Concrete before/after examples where the first pass was reviewed, found wanting, and
revised - rather than accepted as-is. Full traceability (requirement -> validation) for
each is in `05_ai_traceability.md`; this document focuses specifically on *how the prompt
or approach changed* between iterations.

## Iteration 1 — Rate limiting dependency

**First pass prompt**: "Implement distributed rate limiting with Bucket4j backed by
Redis (per the architecture plan)."

**Issue found on review**: Bucket4j's Redis integration (`bucket4j-redis`) requires wiring
a `LettuceBasedProxyManager` against a raw Lettuce `RedisClient`/`StatefulRedisConnection`
with a specific byte-codec setup - not simply autowiring Spring Data Redis's existing
connection factory. Getting this exactly right depends on matching the correct
`bucket4j-redis` 8.x API surface, which could not be verified against a live compiler in
this environment. Shipping a plausible-looking but subtly wrong integration for a security
control (rate limiting) was judged an unacceptable risk.

**Revised prompt**: "Drop the Bucket4j-Redis dependency. Implement the same distributed
fixed-window behavior directly against `StringRedisTemplate` with a small, self-contained
Lua script for the atomic INCR+EXPIRE+check, executed via `RedisScript`/`execute()` - an
API surface with much higher confidence of being used correctly."

**Result**: `cache/RedisRateLimiter.java` + `scripts/rate_limiter.lua`, unit-tested with a
mocked `StringRedisTemplate` (`RedisRateLimiterTest`). Simpler, fewer dependencies, and the
actual rate-limiting semantics (capacity per window, keyed by API key or IP, fail-open on
Redis outage) are unchanged from the original design intent.

## Iteration 2 — Bulk update + stale persistence context

**First pass prompt**: "Add a repository method to atomically increment `urls.click_count`
and a scheduled sweep that soft-deletes expired links via a bulk JPQL update."

**First-pass output**: `@Modifying @Query("UPDATE Url u SET ...")` methods with no further
configuration.

**Issue found on review**: while writing `UrlRepositoryIntegrationTest`, tracing through
what `findById()` would return immediately after one of these bulk updates in the *same*
persistence context revealed a real bug: JPQL bulk updates write directly to the database
and bypass the first-level (persistence-context) cache. A subsequent `findById()` call
within the same transaction/`EntityManager` would silently return the stale pre-update
in-memory entity rather than reflecting the write - not just a test artifact, but a real
correctness issue for any production code path that reads a `Url` again after calling
`incrementClickCount` in the same transaction.

**Revised prompt**: "Add `clearAutomatically = true` to both `@Modifying` bulk-update
methods so the persistence context is cleared after the write, and add integration test
coverage that specifically re-reads the entity afterward to prove it's no longer stale."

**Result**: `UrlRepository.incrementClickCount` / `softDeleteExpiredUrls` fixed;
`UrlRepositoryIntegrationTest#incrementClickCountIsAtomicAndPersists` and
`#softDeleteExpiredUrlsOnlyTouchesExpiredLiveRows` assert the post-update read is correct.

## Iteration 3 — Analytics cache poisoning

**First pass prompt**: "Cache the analytics aggregation query result keyed by short code,
TTL from config, since it's read far more often than it changes."

**First-pass output**: plain `@Cacheable(cacheNames = "analytics", key = "#shortCode")`.

**Issue found on review**: while designing the end-to-end integration test (create link ->
click it -> poll analytics until the click shows up), it became clear the *first* poll,
which would almost certainly land before the async Kafka pipeline finished processing the
click, would cache a **zero-click** result for the full TTL (60s default) - meaning the
test (and, more importantly, any real user checking analytics moments after sharing a
freshly created link) would not see the first click reflected until the cache entry
expired, even though the underlying data was already correct.

**Revised prompt**: "Don't cache an all-zero analytics snapshot - it's exactly the state
most likely to change imminently. Use the cache's `unless` condition to skip caching when
`totalClicks == 0`."

**Result**: `AnalyticsServiceImpl.buildAnalytics` now carries
`@Cacheable(..., unless = "#result.totalClicks == 0")`, with a comment explaining why. This
is a genuine production improvement, not merely a test workaround - it also fixed the
integration test, which is what surfaced it.

## Pattern across all three iterations

In each case, the *first* AI-generated pass was plausible, idiomatic-looking Spring code
that would likely pass a superficial read-through - the defects were only found by tracing
concrete execution paths (what does the persistence context actually contain here; what
does the *first* poll of a freshly-created resource actually see) rather than trusting that
correct-looking code is correct. That tracing is the engineer-review step this whole
`/AI_ENGINEERING` folder exists to make visible.
