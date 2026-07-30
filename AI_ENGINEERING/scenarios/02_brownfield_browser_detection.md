# Scenario 2 (Brownfield) — Enhance Analytics With Browser Detection

## Requirement

From the spec: analytics must include browser, OS, and device breakdowns. Framed as a
brownfield task - assume the click-tracking pipeline (redirect -> Kafka -> consumer ->
Postgres) already exists and needs to be *enhanced* to actually parse and classify the
`User-Agent` header, rather than designed from scratch.

## Prompt

> "The click pipeline already captures the raw User-Agent string per click. Add browser/OS/
> device classification without touching the redirect hot path - this is exactly the kind
> of enrichment that belongs in the async consumer, not the synchronous request."

## Impacted classes

| Class | Change |
|---|---|
| `util/useragent/UserAgentParsingService` (new) | Wraps `ua-parser` (`uap-java`); adds a device-type classification heuristic on top of it |
| `util/useragent/ParsedUserAgent` (new) | Small record: browser/version, OS/version, device type |
| `service/impl/UrlClickIngestionService` (existing, modified) | `recordClick(event, parsed, country)` now takes the parsed UA instead of the raw string, and persists `browser`, `browserVersion`, `os`, `osVersion`, `deviceType` columns |
| `consumer/UrlClickedEventConsumer` (existing, modified) | Calls `UserAgentParsingService.parse()` before delegating to `UrlClickIngestionService` |
| `entity/UrlClick`, `entity/DeviceType` (existing) | Already had the columns/enum from the original schema design - no migration needed for this enhancement, only the population logic |
| `repository/UrlClickRepository` (existing) | Already had `countByBrowser`/`countByOs`/`countByDeviceType` projections from the initial analytics design |
| `service/impl/AnalyticsServiceImpl` (existing) | Unchanged - it already read from these columns; this task is specifically about *populating* them correctly, not the read path |

This is a genuine brownfield shape: the schema, the read/query side, and the event
pipeline all pre-existed; the actual "enhancement" is narrowly the UA-parsing enrichment
step inserted into the consumer's processing, plus the classification heuristic.

## Architecture reasoning

**Why the consumer, not the redirect controller**: `RedirectController` already publishes
the raw `User-Agent` header as part of `UrlClickedEvent` (it has to read the header anyway
to forward it). Parsing a UA string against `ua-parser`'s regex database is CPU work with
no reason to happen on the synchronous 302 path - it belongs entirely in the async
consumer, consistent with the architectural principle established for click tracking in
general (see `docs/ARCHITECTURE.md`'s redirect-flow diagram). No change to the event
payload was needed; `UrlClickedEvent.userAgent` (the raw string) was already there.

**Why a heuristic, not `ua-parser`'s own device field**: `ua-parser`'s bundled
`device.family` values identify specific device *models* ("iPhone", "Nexus 5", ...), not
broad categories (`DESKTOP`/`MOBILE`/`TABLET`/`BOT`), and it doesn't reliably classify bots
at all. `UserAgentParsingService.classify()` implements a documented, honestly-imperfect
heuristic (substring checks for `bot`/`spider`/`crawl`, `ipad`/`tablet`, `mobi`/`iphone`/
`android`) on top of the raw UA string rather than trying to force `ua-parser`'s device
field into four buckets it wasn't designed to produce. This is called out as a known
simplification, not presented as a definitive classifier.

**Why this doesn't require an analytics API change**: because `AnalyticsServiceImpl` and
`UrlClickRepository`'s projections were already built against the `url_clicks.browser`/
`os`/`device_type` columns from the very first analytics implementation (the schema was
designed with this enrichment as a known future step, not discovered as a gap later), this
enhancement is purely additive to the consumer - no controller, DTO, or query changes.

## Regression testing

- The end-to-end integration test
  (`UrlShortenerFlowIntegrationTest#shortenRedirectAndAnalyticsFlowEndToEnd`) sends a real
  Chrome user-agent string on the click and asserts `analytics.browsers` contains
  `"Chrome"` - this is the regression check that the whole pipeline (redirect -> event ->
  consumer -> UA parsing -> persistence -> analytics read) still produces correct output
  after wiring in the parser, not just that the parser works in isolation.
- No existing test asserted anything about the *previous* (unparsed) behavior, since
  browser/OS/device columns were always part of the schema from Scenario 2's very first
  implementation in this build - there was no prior "dumb" version to regress against in
  practice. The brownfield framing here is about the codebase's *conceptual* seams (event
  pipeline already exists, only the enrichment step is new), which is exactly how a real
  brownfield enhancement to an already-shipped click pipeline would be scoped and tested.
- Manual trace: confirmed `UrlClickIngestionService.recordClick`'s idempotency check
  (`existsByEventId`) still runs *before* the UA-parsed fields are persisted, so redelivery
  safety established for the original click-tracking feature isn't weakened by adding
  fields to the same write.

## Known limitation (documented, not silently accepted)

The device-type heuristic will misclassify some real-world user agents (e.g. an Android
tablet whose UA happens to include "Mobile" gets bucketed as `MOBILE`). This is recorded
here and in `07_risk_analysis.md` rather than presented as a solved problem.
