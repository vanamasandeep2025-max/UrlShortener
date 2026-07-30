# 06 — Review Checklist

The checklist applied to every non-trivial piece of AI-generated code in this build before
it was considered "accepted." Not every item applies to every file (e.g. a DTO has no
transaction boundary to check) - applied judgment, not mechanical box-ticking.

## Correctness

- [ ] Does this actually do what the requirement asked, not just something plausible?
- [ ] Trace at least one concrete execution path by hand (inputs -> state changes ->
      outputs), don't just read the code and nod along.
- [ ] For anything touching a transaction boundary: what runs inside vs. outside the
      transaction, and does that matter here? (Caught Task 5's bulk-update staleness this way.)
- [ ] For anything touching a cache: what's the very first read going to see, and is that
      a state worth caching? (Caught Task 6's analytics cache-poisoning this way.)
- [ ] For anything async (Kafka, `ApplicationEventPublisher`): what happens if the consumer
      never runs, runs twice, or throws halfway through?

## Security

- [ ] Does this endpoint/field need authentication? Authorization (ownership, not just
      "is logged in")?
- [ ] Any user input reaching a query, a redirect target, or a rendered page - validated/
      escaped/scheme-restricted appropriately? (`@ValidHttpUrl`, `@NoScriptTag`)
- [ ] Any secret (password, API key, JWT signing key) ever logged, returned in a response
      DTO, or stored in plaintext?
- [ ] Rate-limited where brute-forceable (e.g. `/verify-password`)?

## API design

- [ ] Does the response shape match what the spec's example JSON actually shows?
- [ ] Correct HTTP status codes and semantics (201 vs 200, 404 vs 410 vs 401 vs 403 vs 429)?
- [ ] Pagination/sorting/filtering behave sanely at the edges (empty result, invalid sort
      field, page beyond the last)?

## Dependencies and unverifiable APIs

- [ ] Is this dependency's API being used in a way that can be verified against
      well-established, stable, widely-documented usage - or is it a guess?
- [ ] If a guess: is there a lower-risk alternative that achieves the same requirement with
      higher confidence? (This is exactly what triggered Iteration 1 in
      `04_prompt_iterations.md` - Bucket4j-Redis replaced with a hand-rolled Lua script.)

## Consistency with the rest of the codebase

- [ ] Constructor injection, not field injection?
- [ ] Entities kept out of the controller/HTTP boundary?
- [ ] Does a new pattern duplicate an existing one that should have been reused instead?

## Honesty of claims

- [ ] Does a comment, docstring, or doc page claim something the code doesn't actually do
      (e.g. "exactly-once" where it's really "idempotent at-least-once")?
- [ ] Is test coverage described accurately rather than aspirationally? (See
      `08_validation_report.md`.)
- [ ] Are known gaps/limitations documented (e.g. `NoOpGeoIpService`, no automated DLQ
      test) rather than silently left implicit?

## How this was actually applied

This checklist was not run as a literal one-by-one gate on every file - that would be
theater for a build this size. It represents the standing questions that were actually
applied continuously during implementation, and are the direct source of the three
iterations documented in `04_prompt_iterations.md` and the ten tasks in
`05_ai_traceability.md`. The honest measure of whether this checklist did real work is
that it changed the shipped code in multiple places, not just that it exists as a document.
