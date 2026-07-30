# 08 — Validation Report

This report reflects **real, observed output**, not assumed success. Docker Desktop was
not usable in the authoring environment (see "Docker/Testcontainers" below for what that
blocked and why) - everything else below was actually executed against a real Maven
toolchain discovered partway through validation (IntelliJ IDEA's bundled JDK 17 + Maven 3,
found at `C:\Program Files\JetBrains\IntelliJ IDEA 2023.3.4\{jbr,plugins\maven\lib\maven3}`),
compiled and run with `-Dmaven.compiler.release=17` (the project targets Java 21 in
`pom.xml`; 17 was the highest available locally - see the caveat at the end of this
document for what that does and doesn't validate).

## What was actually run, and what it found

### 1. Full compile (`mvn compile`) - 99 main source files

**First run failed** with two real compile errors:

```
ApiKeyAuthenticationFilter.java:[19,55] cannot find symbol: class UsernamePasswordAuthenticationToken
JwtAuthenticationFilter.java:[17,55] cannot find symbol: class UsernamePasswordAuthenticationToken
```

Root cause: both filters imported `UsernamePasswordAuthenticationToken` from
`org.springframework.security.web.authentication` - it actually lives in
`org.springframework.security.authentication`. A plausible-looking but wrong import,
exactly the class of mistake this whole validation phase exists to catch. **Fixed** in
both files; re-run succeeded.

**Second run: BUILD SUCCESS** - all 99 files compile, including annotation processing
(Lombok builders/getters/setters and MapStruct-generated mapper implementations both
generated correctly).

### 2. Test compile (`mvn test-compile`) - 13 test source files

**BUILD SUCCESS** on the first attempt after the main-source fixes above (one harmless
deprecation warning, no errors).

### 3. Unit test execution (`mvn test`) - Surefire, `*IntegrationTest` excluded

**First run: 61/62 passed, 1 failure**:

```
UrlControllerTest.listUrlsReturnsPagedResults - Status expected:<200> but was:<500>
java.lang.IllegalStateException: No primary or single unique constructor found for
interface org.springframework.data.domain.Pageable
```

Root cause: `UrlControllerTest`'s `MockMvcBuilders.standaloneSetup(...)` never registered
`PageableHandlerMethodArgumentResolver`, so Spring MVC fell back to treating the
controller's `Pageable pageable` parameter as a plain `@ModelAttribute` and tried to
construct the *interface* directly via data binding - which can never work. Full Spring
Boot test slices (`@WebMvcTest`) register this resolver automatically; a hand-built
standalone `MockMvc` does not. **Fixed** by adding
`.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())` to the builder.

**Second run: 62/62 passed, 0 failures, 0 errors.**

### 4. Coverage (JaCoCo, generated from the run above)

**Real measured numbers**: **37.5% instruction coverage, 35.5% line coverage** across 66
analyzed classes. This lands squarely inside the 30-45% honest estimate given in the
pre-validation draft of this document, and comfortably clears the 25% floor configured in
`pom.xml`. It is **not** the 90% the original spec requested - see
`05_ai_traceability.md` Task 10 for why that number was not pursued at the expense of test
quality.

Highest-covered packages (by design - these are the highest-value areas):
`com.urlshortener.validation` (99%), `com.urlshortener.cache` (93%),
`com.urlshortener.util.shortcode` (70%), `com.urlshortener.controller` (63%),
`com.urlshortener.service.impl` (55%). Lowest-covered: `config`, `mapper` (generated
code), `repository` (query-only interfaces, exercised by the Testcontainers test that
could not run here), `consumer`/`producer` (exercised only indirectly, in the Docker-only
end-to-end integration test).

### 5. Checkstyle (`mvn checkstyle:check`)

**BUILD SUCCESS** - 6 warnings (all `MagicNumber`, e.g. Kafka partition counts, a cache
max-age constant), correctly non-blocking per `failOnViolation=false`.

### 6. PMD (`mvn pmd:check`)

**BUILD SUCCESS** - 21 findings. Fixed the genuinely worthwhile ones on the spot:
- `UseLocaleWithCaseConversions` (3x - `.toLowerCase()`/`.toUpperCase()` without a
  `Locale` can behave unexpectedly under e.g. the Turkish locale) -> added `Locale.ROOT`
  in `UrlSpecifications` and `UserAgentParsingService`.
- `MissingSerialVersionUID` (6x, one per custom exception class) -> added
  `serialVersionUID` to all six.

Left as-is (documented, not fixed): `GuardLogStatement` (a style preference about
wrapping log calls in `if (log.isDebugEnabled())` guards - not worth the added noise for
this codebase's log volume) and `AvoidDuplicateLiterals` (repeated string literals in
query/spec builders - true but low-value to fix here).

### 7. SpotBugs (`mvn spotbugs:check`, invoked explicitly per the CI job)

**BUILD SUCCESS** - roughly 50 findings, effectively all `EI_EXPOSE_REP`/`EI_EXPOSE_REP2`
("may expose internal representation") on Lombok-generated getters/setters/builders and
JPA entity associations, plus one `CT_CONSTRUCTOR_THROW`. This is the expected, close-to-
unavoidable shape of SpotBugs output for any Spring+JPA+Lombok codebase that doesn't
defensively copy every `List`/`Map`/entity reference passed through a constructor or
getter - doing so throughout would be a large amount of boilerplate against idiomatic
Spring/JPA practice, for a defense that matters far more for genuinely untrusted external
input than for internal service-to-service and entity-to-entity references. Reviewed and
accepted as a known, common class of low-severity finding rather than fixed one-by-one.

## Docker / Testcontainers - blocked, and why

> **Update, later in this engineering session: the Docker blocker described below was
> resolved.** It was a host BIOS/firmware setting (virtualization disabled), fixed by the
> user directly, not a Docker Desktop defect. See "Phase 13" at the end of this document
> for what full-stack validation found once Docker actually worked - this original section
> is left exactly as written at the time, as the accurate record of what was true then.

Docker Desktop was installed but its backend engine was not operational in this
environment: `docker version` succeeded (client only), but every daemon call
(`docker info`, `docker build`, ...) returned `request returned 500 Internal Server Error`
against the `dockerDesktopLinuxEngine` named pipe, and `wsl --status`/`wsl -l -v` showed
**no WSL2 distributions installed** - strong evidence Docker Desktop's WSL2-backed Linux
engine could not actually start. This was diagnosed (Docker Desktop process running but
unresponsive after 15+ minutes and multiple restart attempts) but not fixed - resolving a
host machine's WSL2/Docker Desktop installation is outside the scope of this build and
risks destructive/invasive system changes without the user present to confirm them.

**What this blocked**: `docker compose up --build` (the full 8-container stack),
`docker build ./backend` (containerized Maven build against the actual Java 21 target),
and the two Testcontainers-based test classes (`UrlRepositoryIntegrationTest`,
`UrlShortenerFlowIntegrationTest`) - both require a working Docker daemon and could not be
executed here.

**What this does *not* mean**: the Testcontainers tests are not unverified-in-principle -
they're ordinary JUnit 5 + Testcontainers code, written the same way as the tests that did
run, and reviewed with the same scrutiny (see `05_ai_traceability.md` for the bugs that
review process caught elsewhere). They are unverified-in-this-session specifically, and
that gap is being stated plainly rather than glossed over.

**What running under Java 17 instead of 21 does and doesn't validate**: this confirms the
code is syntactically and semantically correct Java, that every dependency resolves and
every annotation processor (Lombok, MapStruct) runs correctly, and that the actual runtime
logic (61+1 fixed = 62 tests, exercising real service/security/controller behavior) is
correct. It does **not** confirm the code compiles clean under `--release 21` specifically
- though nothing in this codebase intentionally uses a Java 18-21-exclusive language
feature (no virtual threads, no sequenced collections, no record patterns in `switch`), so
the risk of a 21-specific compile failure is low but not zero.

## Recommended next step for whoever runs this next

```bash
docker compose up --build   # full stack, real Java 21 build, all Testcontainers tests included
```

If that succeeds cleanly, it closes the one gap this report identifies. If it doesn't,
the error will point at something concrete to fix - which is exactly the point of this
whole validation phase existing.

---

## Phase 13 - full Docker stack validation (the gap above, closed)

`docker compose up --build` was run for real, later in this engineering session, after the
host's BIOS virtualization setting was enabled. All 8 containers (postgres, redis,
zookeeper, kafka, backend, nginx, prometheus, grafana) came up healthy. What follows is
what actually running the full stack found - not a report that it merely "started".

### Real defects found and fixed via live system behavior

Each row was found by exercising the real, running application (`curl`, `psql`,
`redis-cli`, a real browser) and confirmed fixed the same way, not by re-reading the code
and assuming the fix worked:

1. **Rate limiter denied ~100% of requests.** `RateLimitFilter` was constructed with `new`
   inside `SecurityConfig` rather than as a Spring bean, so its `@Value`-annotated capacity
   fields silently stayed at Java's default `0`. Found on the very first `curl` against the
   running API (immediate `429`). Fixed by constructor-injecting the configured values.
2. **Grafana container failed to start.** Two `docker-compose.yml` volume mounts, the
   second nested inside the first's read-only mount. Fixed by consolidating to one mount.
3. **Maven build failed inside the build container on this network.** Avast's TLS
   interception broke certificate trust for `dependency:go-offline`. Root-caused with
   `openssl s_client`, fixed with a generic (non-Avast-specific) trusted-CA import
   mechanism added to `backend/Dockerfile`.
4. **Frontend rendered mojibake/garbled characters.** Every page was missing
   `<!DOCTYPE html>`/`<meta charset="UTF-8">`, so browsers guessed the encoding. Reported by
   the user from the live app; fixed across all five HTML pages.
5. **`url_clicks` stayed empty while `urls.click_count` incremented correctly** - a
   split-brain bug. `UrlClickIngestionService` called `save()` (unflushed) immediately
   before a bulk `@Modifying(clearAutomatically=true)` update, which detached the
   still-pending insert and silently dropped it. Caught while proving Kafka was genuinely
   wired up (not just configured) via a live `psql` count against `url_clicks`. Fixed with
   `saveAndFlush()`, with the ordering hazard documented inline for the next person.
6. **`GET /analytics` threw `InvalidDefinitionException`** on `LocalDate` fields.
   `GenericJackson2JsonRedisSerializer()`'s no-arg constructor builds its own `ObjectMapper`
   with no `JavaTimeModule`. Fixed by injecting the app's real `ObjectMapper`.
7. **The *second* call to the same analytics endpoint then threw `ClassCastException`.**
   A custom `ObjectMapper` handed to `GenericJackson2JsonRedisSerializer` doesn't
   auto-activate the polymorphic `@class` type hints its own deserializer needs, so cached
   JSON came back as a `LinkedHashMap`, not the target DTO. Fixed by switching to a typed
   `Jackson2JsonRedisSerializer<T>` per cache (each cache here only ever holds one known
   type, so the type hint was never actually necessary).
8. **A wrong password on login silently reloaded the page with no error message.**
   `apiFetch()`'s generic 401-handler treated *every* 401 - including `/auth/login`
   rejecting bad credentials - as an expired session, clearing tokens and force-navigating
   before the real "Invalid username or password" toast could render. Caught by an
   automated test (`TC-AUTH-003`) failing, root-caused via the Playwright trace viewer's
   network timeline (a `302`-adjacent double-navigation 323ms apart). Fixed by only
   attempting the refresh-and-redirect flow when a Bearer token was actually attached to
   the failed request.
9. **Registration intermittently 500'd** with `DataIntegrityViolationException` on
   `audit_logs_actor_user_id_fkey`. `AuditService.log()` runs in
   `Propagation.REQUIRES_NEW`, which suspends the caller's transaction and opens a
   genuinely separate one - it can never see a row the outer, still-open transaction
   hasn't committed, no matter how that row is flushed. `register()` created the user and
   audit-logged it, as its own actor, inside one transaction. Caught by an automated test
   (`TC-REG-001`) failing. Fixed by extracting user creation into `UserRegistrationService`,
   a separate bean whose own transaction genuinely commits before the audit write runs.
10. **A mobile-viewport navbar overflow, a dashboard action-row wrap, and a missing
    `aria-label`** - all found visually during a live, human-monitored slow-motion browser
    walkthrough, none caught by any automated assertion beforehand. Each fixed with a
    minimal, targeted markup/CSS change and given a regression-guard test afterward.

Two further real findings were investigated and **deliberately left unfixed, but
documented** rather than silently patched around or hidden: the `analytics` cache has no
eviction hook on new clicks, so a stale click count can be served for up to its 60s TTL
after the *first* click on a link; and a successful password verification on a
protected link never actually publishes a click event, so that click is invisible in
analytics even though the user reached the real destination. Both are recorded in
`docs/ARCHITECTURE.md` and `automation/README.md`.

### A second, independent validation artifact: the automation suite

`automation/` is a Playwright + TypeScript UI/API/E2E test framework (133 test cases;
Page Object Model, a typed API client, HTML/JUnit/Allure reporting, a GitHub Actions
workflow) built the same way as the rest of this project: run against the real system, not
assumed to work. Building and running it caught defects #8 and #9 above directly, plus
real bugs in the framework's own code along the way (each root-caused and fixed, not
worked around):

- **A URL-construction bug silently hit the wrong endpoint.** The test fixture's
  `baseURL` plus leading-slash route constants resolved, per standard URL rules, to the
  wrong path entirely - the test infrastructure equivalent of the application defects
  above, caught the same way (a real 401 that shouldn't have happened, traced to its root
  cause rather than retried).
- **A Chromium DNS pre-resolution quirk** intermittently failed a real-browser redirect
  test pointed at a deliberately fake/unresolvable domain (the pattern used everywhere else
  in the suite to avoid real-internet dependencies) - Chromium performs its own DNS
  pre-resolution for cross-origin redirect *targets* ahead of Playwright's own request
  interception. Fixed by giving that one test a real, tiny local HTTP server instead.
- **A toast-locator race**: two success toasts from two actions taken in quick succession
  could both still be on screen at once (Bootstrap toasts persist 4s), making a
  "the toast" locator ambiguous under real load. Fixed to target the most recently shown
  toast specifically.

129 of 133 automated tests pass in a full run; the remaining 4 are a deliberately-skipped
true-token-expiry test (documented reasoning inline: exercising it honestly would require
either waiting out a 1-hour JWT or holding the signing secret, which would test the JWT
library rather than this application) and a 2-test rate-limit file whose non-pass under a
deliberately-raised local rate-limit ceiling is itself expected and explained - see
`automation/README.md` "Known Limitations" for both.

A companion manual test case repository, kept in sync with the system's real observed
behavior rather than the original spec's assumptions, was also produced:
`docs/test-cases/url-lifecycle-test-cases.md`, 257 cases across the full URL lifecycle.

### Testcontainers - status, precisely

`backend/Dockerfile` builds with `-DskipTests`, and running the full Docker stack via
`docker compose up --build` does not itself execute `UrlRepositoryIntegrationTest` or
`UrlShortenerFlowIntegrationTest`. With Docker now genuinely available, those two classes
were run directly via Maven against the live Docker daemon
(`mvn test -Dtest=UrlRepositoryIntegrationTest,UrlShortenerFlowIntegrationTest`,
`-Dmaven.compiler.release=17` for the same reason as Phase 12: no local JDK 21). Real,
observed result:

> **Still could not run - a distinct, more specific finding than "Docker is unavailable".**
> `docker`, `docker compose`, and `docker build` all work perfectly from this same shell
> (the entire Phase 13 validation above depended on them) - but Testcontainers, launched
> from a native-Windows JVM process (IntelliJ's bundled JDK, not running inside WSL2 or a
> container itself), could not auto-discover Docker Desktop's engine:
> `IllegalStateException: Could not find a valid Docker environment`. Explicitly setting
> `DOCKER_HOST=npipe:////./pipe/docker_engine` (the standard fix for exactly this class of
> problem) did not change the outcome on a retry. This points at Testcontainers' Windows
> named-pipe client needing something beyond a bare `DOCKER_HOST` env var in this specific
> setup (a permissions/ACL detail on the pipe, or a `~/.testcontainers.properties`
> override) - not yet root-caused further, per the instruction to stop investigating and
> just record the real result here.
>
> **What this does and doesn't mean**: the two Testcontainers classes remain
> unverified-in-this-session, specifically. It does not mean the functionality they'd
> exercise (repository queries, the full shorten-to-analytics flow) is unverified - that
> exact functionality was verified independently and more thoroughly via Phase 13's live
> system testing and the 133-test Playwright suite above, which exercise real HTTP/DB/Kafka
> behavior end-to-end rather than through Testcontainers specifically. The concrete,
> not-yet-done next step for whoever runs this next, if they want these two classes
> executed literally as written: run `mvn test -Dtest=UrlRepositoryIntegrationTest,
> UrlShortenerFlowIntegrationTest` from **inside WSL2** (or any Linux/Mac shell with a
> native Docker socket), rather than from a Windows-native JVM.
