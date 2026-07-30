# Scenario 1 (Greenfield) — URL Expiration

## Requirement

From the spec: `CreateUrlRequest` accepts an optional `expiryDate`; `PATCH /api/v1/urls/{id}`
updates it later; an expired link must stop working. Nothing else specified - in particular,
the spec doesn't say whether an expired link should 404, or return something more specific,
or whether expired-but-undeleted rows should ever be cleaned up.

## AI prompt

> "Implement URL expiration end-to-end: accept an optional expiry at creation, allow it to
> be updated or cleared later, and make sure an expired link actually stops redirecting.
> Decide what an expired-but-not-deleted link should look like to a caller, and justify it."

## Generated code

- `Url.expiresAt` (`Instant`, nullable) + `Url.isExpired()` helper.
- `CreateUrlRequest.expiryDate`, `UpdateExpiryRequest.expiresAt` (nullable - `null` means
  "clear the expiry," documented in the DTO's own Javadoc rather than left implicit).
- `UrlServiceImpl.updateExpiry` - ownership-checked, evicts the redirect cache on change.

## Engineer refinement: the caching pitfall considered and designed around

The obvious first instinct for the redirect lookup is: fetch the row, check
`isExpired()`, throw if expired, cache the result. That's wrong the moment you combine it
with `@Cacheable` - if a link is fetched (and cached) while still valid, then expires
later, a **cache hit** on a subsequent redirect would skip the method body entirely,
including the expiry check, and keep serving a 302 to an expired link until the cache TTL
(default 1 hour) elapses. Caching *the lookup* is correct; caching *the expiry decision* is
not, because the decision's correctness depends on "now," which changes on every call
regardless of whether the underlying row came from cache or Postgres.

This was caught during design, before it was implemented the wrong way, precisely because
it's the same class of pitfall as the JPA bulk-update staleness bug documented in
`04_prompt_iterations.md` Iteration 2 (found *after* shipping, in that case) - once that
pattern ("a time- or write-dependent decision placed behind a boundary that doesn't know
state changed") was recognized once, it became something to actively check for elsewhere,
including here.

**Design actually shipped**: `UrlRedirectTarget` (the cached DTO) carries `expiresAt`
itself and exposes `isExpired()` as a method evaluated at read time; `resolveForRedirect`
(the `@Cacheable` method) makes no expiry decision at all - it purely fetches (cache-or-DB)
and returns the record, cached or not. `RedirectController` calls `target.isExpired()` on
every single request, and throws `UrlExpiredException` (mapped to `410 Gone`) itself. The
interface Javadoc on `UrlService.resolveForRedirect` states this explicitly, so the
constraint is documented at the point future maintainers would look, not just in this file.

**410 vs 404 decision**: an expired link returns `410 Gone`, not `404 Not Found` - the
resource *did* exist and the URL is semantically correct, it's just no longer available,
which is exactly what 410 means in HTTP. A permanently unknown short code returns `404`.
This distinction is visible to API clients and documented in `docs/ARCHITECTURE.md`.

**Cleanup decision**: expired-but-undeleted rows are not required to disappear
immediately (lazy check at read time is sufficient for correctness), but a background
`@Scheduled` sweep (`config/ExpiredUrlSweeper`, default every 15 minutes) soft-deletes them
so list views and future analytics queries don't accumulate long-expired, never-revisited
links indefinitely. This was an engineer addition beyond the literal requirement, justified
by "list URLs" needing to stay usable over time - flagged here as scope the AI added
proactively rather than something explicitly requested, so it's visible as a judgment call.

## Tests

- `UrlServiceImplTest#updateExpiryThrowsNotFoundForMissingUrl`
- `UrlServiceImplTest#resolveForRedirectReturnsTargetCarryingExpiryForCallerToCheck` -
  specifically asserts the target object exposes its own `isExpired()` rather than the
  service pre-deciding it, i.e. asserts the caching-safe design holds, not just the feature.
- `RedirectControllerTest#returns410ForExpiredLinkAndDoesNotTrackClick` - also asserts no
  click event is published for an expired link (an expired link isn't a valid redirect, so
  it shouldn't count as a click).
- `UrlRepositoryIntegrationTest#softDeleteExpiredUrlsOnlyTouchesExpiredLiveRows` - the
  sweeper only touches rows that are actually expired and not already deleted.

## Validation

All four tests above pass in isolation (unit/MockMvc tests need no external services;
the repository test needs Docker for Testcontainers - see `08_validation_report.md`). The
caching design specifically was validated by reasoning through the exact scenario that
would expose a bug in the naive approach (fetch while valid -> cache populated -> expire ->
redirect again before TTL) and confirming the shipped design's `isExpired()` call happens
on every request regardless of cache hit/miss, not just by re-reading the code and assuming
it's correct.
