# 03 — AI Prompts Used

Representative prompts actually used to direct implementation, grouped by phase. These are
the task-level directives given to the AI assistant during the build (not literal
copy-paste transcripts, but accurate to intent and structure) - each is expanded with its
outcome and review in `05_ai_traceability.md`. Full session-level planning prompts (the
ones that produced `02_task_breakdown.md`) are omitted here to avoid duplication.

## Database

> "Design the Flyway migrations for `users`, `urls`, `url_clicks`, `audit_logs`, and
> `api_keys`. Soft delete on users and urls. Short codes must be unique only among live
> (non-deleted) rows, not globally, so codes can be reused after deletion. Add whatever
> indexes the query patterns in the spec (search, pagination, analytics aggregation) will
> actually need - don't index speculatively."

## Core domain

> "Implement `UrlService.createUrl`: generate a short code via a pluggable strategy,
> retry on collision up to a bounded number of attempts, persist, and publish a domain
> event for anything that needs to react to creation (audit log, Kafka) without coupling
> the service directly to those concerns."

> "The redirect endpoint has a <100ms / 1000 req/s NFR attached to it in the spec. Design
> the redirect path so click tracking cannot add synchronous latency to the 302 response."

## Security

> "JWT for interactive users, a separate API-key mechanism (`X-API-Key` header) for
> programmatic clients, both feeding the same `Authentication` principal so downstream code
> doesn't care which one was used. Rate limiting must be correct across multiple stateless
> backend instances, not per-instance. Explain your CSRF decision explicitly rather than
> silently disabling it."

## Messaging

> "Wire the four required Kafka topics (`url-created`, `url-clicked`, `analytics`,
> `dead-letter`). The click consumer must be idempotent under redelivery, must not
> double-count a browser-side retry, and failed messages must retry with backoff before
> landing on the literal `dead-letter` topic - not Spring Kafka's default `.DLT` suffix
> convention, since the spec names an exact topic."

## Testing

> "Given this codebase is too large to genuinely reach 90% line coverage in the time
> available without writing shallow tests purely to inflate the number, prioritize real
> depth on the service layer, security, and the redirect path, plus one true end-to-end
> Testcontainers test proving the whole async pipeline (shorten -> redirect -> Kafka ->
> analytics) actually works. Report the real coverage number afterward, not a target."

## Infra

> "`docker-compose.yml` must bring up all eight required containers (postgres, backend,
> redis, kafka, zookeeper, prometheus, grafana, nginx) and the whole stack must come up
> with a single `docker compose up --build` and zero required manual configuration steps -
> ship safe demo-only defaults for secrets, but document loudly that they must change for
> any real deployment."

## Scenario prompts

The three required scenarios (`scenarios/`) each began from a single-sentence prompt taken
close to verbatim from the assignment:

- Greenfield: *"Implement URL expiration."*
- Brownfield: *"Enhance analytics by adding browser detection."*
- Ambiguous: *"Users should be able to share URLs securely."*

Each scenario document shows the full path from that one sentence to a reviewed,
tested implementation.
