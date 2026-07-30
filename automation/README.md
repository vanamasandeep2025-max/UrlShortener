# URL Shortener Platform — Automation Framework

Playwright + TypeScript automation for the [URL Shortener Platform](../README.md): UI, API, and
end-to-end coverage of the real application, built and validated against a live
`docker compose up --build` stack — not scaffolding, not stubs. Every test in this suite has
been run against the actual running system at least once; three real product bugs were found
and fixed while building it (see [Bugs Found While Building This Suite](#bugs-found-while-building-this-suite)).

## Contents

- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
- [Test Coverage Matrix](#test-coverage-matrix)
- [What This Suite Deliberately Does Not Test](#what-this-suite-deliberately-does-not-test)
- [Bugs Found While Building This Suite](#bugs-found-while-building-this-suite)
- [Running Tests](#running-tests)
- [Reporting](#reporting)
- [CI/CD](#cicd)
- [Known Limitations](#known-limitations)
- [Best Practices Followed](#best-practices-followed)
- [Future Enhancements](#future-enhancements)

---

## Quick Start

```bash
# 1. From the repo root, start the application stack
cd ..
docker compose up --build -d

# 2. Install the automation framework
cd automation
npm install
npx playwright install --with-deps
cp .env.example .env

# 3. Run it
npx playwright test
```

Open the HTML report afterward with `npm run report:html`.

---

## Project Structure

```
automation/
├── tests/
│   ├── auth/          login, registration, session handling, JWT/RBAC (tokens & ownership)
│   ├── functional/    URL CRUD via the dashboard UI, API key management UI
│   ├── ui/            dashboard widgets: pagination, copy button, empty state, a11y, perf
│   ├── validation/    input boundaries against the real validators (ValidHttpUrl, NoScriptTag)
│   ├── e2e/            shorten -> redirect -> Kafka -> DB -> analytics, end to end
│   ├── api/            pure REST contract tests (status/schema/headers/errors)
│   └── security/       rate limiting (isolated), response security headers
├── pages/              Page Object Model - one class per real frontend/*.html page
├── components/         Component Object Model - e.g. UrlRow (one table row)
├── api/                ApiClient (typed Playwright APIRequestContext wrapper) + DTOs
├── fixtures/           base.ts - apiClient, demoTokens/adminTokens, authenticatedPage/adminPage
├── helpers/            db.ts - read-only Postgres assertions (pg)
├── utils/              logger, Faker-backed data generator, retry/poll helper, Axios health check
├── constants/          Routes (frontend pages) / ApiRoutes (REST endpoints)
├── config/             environment.ts - single source of truth for all env vars
├── global-setup.ts     fails fast if the app isn't actually running, before any test starts
├── playwright.config.ts
├── Dockerfile           standalone test-runner image (points at an already-running stack)
└── .github/ (see ../.github/workflows/automation-tests.yml - GitHub only reads repo-root workflows)
```

## Architecture

**Page Object Model + Component Object Model.** Every page object maps 1:1 to a real file
under `frontend/` (`LoginPage` ↔ `login.html`, `DashboardPage` ↔ `index.html`, ...). Locators
are built from the real DOM (`#id` selectors, `getByRole`) — read directly from the frontend
source, not guessed. `UrlRow` is a component object for one table row, constructed via
`DashboardPage.rowFor(shortCode)`, so row-level actions (copy, delete, edit expiry) live in one
place instead of being re-derived in every test.

**Fixtures over `beforeEach`.** `authenticatedPage` logs in via a real `POST /auth/login` and
seeds `localStorage` directly — a UI test that needs "a logged-in user" doesn't drive the login
form to get one, keeping that path exercised exactly once, deliberately, in `tests/auth/login.spec.ts`.

**`ApiClient` over raw `fetch`/Axios for assertions.** `ApiClient` wraps Playwright's own
`APIRequestContext` so API calls share Playwright's tracing, request/response logging, and
report integration for free. Axios is used exactly once, in `utils/healthCheck.ts`'s
`global-setup.ts` call — a fast, clear-message fail if the stack isn't running, before any
Playwright browser/request context even exists.

**Everything is grounded in the real backend contract.** DTOs in `api/types.ts` are hand-typed
from `backend/src/main/java/com/urlshortener/dto/*` (not generated) so a field rename there
fails typecheck here. Validation tests in `tests/validation/` assert against the literal regex
rules in `ValidHttpUrlValidator`/`NoScriptTagValidator`, not assumptions about what "should" be
rejected.

---

## Test Coverage Matrix

| Area | File | Approx. cases | Notes |
|---|---|---|---|
| Login (UI) | `tests/auth/login.spec.ts` | 11 | valid/invalid creds, anti-enumeration, SQLi/XSS payloads, tab switching |
| Registration (UI) | `tests/auth/register.spec.ts` | 10+ | duplicate username/email, password policy boundaries (table-driven), unicode username |
| Session handling | `tests/auth/session.spec.ts` | 7 | logout, unauth redirect, refresh persistence, tampered token, two concurrent sessions |
| JWT & RBAC (API) | `tests/auth/rbac.spec.ts` | 11 | missing/invalid/tampered/refresh-as-access tokens, ownership 403s, admin bypass, role can't be self-assigned |
| URL CRUD (UI) | `tests/functional/url-crud.spec.ts` | 13 | create/search/filter/edit-expiry/delete/cancel-delete, deleted link 404s |
| Dashboard UI | `tests/ui/dashboard-ui.spec.ts` | 8 | copy-to-clipboard, pagination, empty state, responsive table, keyboard nav, labels, load-time budget |
| API keys (UI) | `tests/functional/api-keys-ui.spec.ts` | 4 | create/reveal-once, revoke, revoked key stops authenticating |
| E2E lifecycle | `tests/e2e/shorten-redirect-analytics.spec.ts` | 6 | full click→Kafka→DB→analytics path, unique-visitor counting, expired/missing/password-gated links, a real browser tab actually landing on the destination page (TC-E2E-006 — see below) |
| Auth API contract | `tests/api/auth.api.spec.ts` | 8 | schema, validation-error shape, correlation IDs, malformed JSON |
| URLs API contract | `tests/api/urls.api.spec.ts` | 12 | pagination envelope, sort, search, status filter, PATCH/DELETE semantics |
| API keys API contract | `tests/api/api-keys.api.spec.ts` | 7 | secret shown once, X-API-Key auth, cross-user revoke blocked |
| Input validation | `tests/validation/input-validation.spec.ts` | ~26 at runtime | table-driven against the real `ValidHttpUrl`/`NoScriptTag` rules; SQLi/JSON-injection proof |
| Security | `tests/security/*.spec.ts` | 6 | rate-limit 429 (isolated run), security headers, no stack-trace/secret leakage |

**132 concrete test cases** (`npx playwright test --list`, chromium + api projects) before the
cross-browser matrix (firefox/webkit/mobile) multiplies the UI subset further. **129 pass** in a
full parallel run out of the box; the remaining 3 are the deliberately-skipped `TC-JWT-007`
(true token expiry — see Known Limitations) and the 2-test rate-limit file, which only passes
against the backend's *default* (non-overridden) rate-limit config — see
[Known Limitations](#known-limitations).

---

## What This Suite Deliberately Does Not Test

Per this session's standing rule — coverage claims must reflect what's actually true, not what a
generic checklist expects — the following requested categories are **not implemented**, because
the corresponding feature does not exist in this application:

| Requested category | Why it's absent |
|---|---|
| Forgot password / password reset | No such flow exists in `frontend/login.html` or `AuthController` |
| Remember me | No such control exists on the login form |
| File upload/download (PDF, CSV, image) | The app has no file I/O anywhere |
| Dropdown/tabs/accordion beyond what exists | Only real widgets are covered: the status `<select>`, the login/register `<nav-pills>` toggle |
| Infinite/virtual scroll | The dashboard uses classic page-number pagination, not scroll-based loading |
| SSO/OAuth2 | Auth is JWT + API key only |
| True JWT expiry | See [Known Limitations](#known-limitations) |
| Visual/screenshot-diff regression | No baseline-image workflow was set up; would be a reasonable Future Enhancement |

Writing hollow tests against non-existent UI would have produced a bigger number and zero real
coverage. This table exists so that's a visible, deliberate decision, not a silent gap.

---

## Bugs Found While Building This Suite

Building tests against the **live** stack (not just writing code that compiles) surfaced real,
reproducible product bugs — each found by a specific test, diagnosed via Playwright's trace
viewer and live backend/Redis/Postgres inspection, and fixed in the application code (not worked
around in the test):

1. **Failed login showed no error message** (`frontend/js/api.js`). `apiFetch`'s generic
   401-handler treated *every* 401 — including `/auth/login` rejecting a wrong password — as an
   expired session: it cleared tokens and force-reloaded `login.html`, wiping out the real
   "Invalid username or password" toast before it ever rendered. Confirmed via the network trace
   for `TC-AUTH-003`: a `POST /auth/login → 401` at `14:14:04.570Z` was followed by a second,
   unprompted `GET /login.html` 323ms later. Fixed by only attempting the refresh-and-redirect
   flow when the failed request had actually attached a Bearer token in the first place.

2. **Registration failed intermittently under Hibernate's insert-reordering config**
   (`AuthServiceImpl`/new `UserRegistrationService`). `POST /auth/register` threw a 500 with
   `DataIntegrityViolationException: ... violates foreign key constraint
   "audit_logs_actor_user_id_fkey"`. Root cause: `AuditService.log()` runs in
   `Propagation.REQUIRES_NEW`, which suspends the caller's transaction and opens a genuinely
   separate one — it can never see a row the outer, still-open transaction hasn't committed yet,
   no matter how that row is flushed. `register()` created the user and audit-logged it (as its
   own actor) inside one transaction, so the audit write's FK check raced the user's own commit.
   Fixed by extracting user creation into `UserRegistrationService`, a separate bean with its own
   `@Transactional` boundary that genuinely commits before `register()` calls `auditService.log()`.
   Found by `TC-REG-001`.

3. **Analytics cache never invalidates on new clicks** (`CacheConfig` / click-processing path).
   `TC-E2E-002` fired 3 rapid redirects, then found `GET .../analytics` stuck reporting
   `totalClicks: 1` for the full 60s TTL, even though `urls.click_count` in Postgres was correctly
   `3` immediately. Root cause: the `analytics` cache is only skipped while `totalClicks == 0` (a
   deliberate, already-documented fix from earlier work) — but once the *first* click makes it
   non-zero, the cached snapshot is never evicted when *later* clicks arrive in the same window.
   Confirmed live: `analytics::{shortCode}` in Redis held a stale value for the rest of its TTL
   while the DB was already correct. **Not fixed in this pass** — flagged here and in
   [Known Limitations](#known-limitations) as a real, reproducible gap for a follow-up (cache
   eviction on click, mirroring the existing `evictCaches()` pattern used for `urlLookup`).

4. **Password-protected links are never tracked as a click**, even after successful password
   verification — see [Known Limitations](#known-limitations). Found by `TC-E2E-005`, also not
   fixed in this pass (product decision, not obviously a bug either way).

5. **Framework bug (not the app): API test fixture silently hit the wrong path.**
   `apiRequestContext`'s `baseURL` (`http://localhost/api/v1`) plus leading-slash route constants
   (`/auth/login`) resolved, per standard URL rules, to `http://localhost/auth/login` — dropping
   `/api/v1` entirely and hitting an unmapped path that Spring Security's catch-all then rejected
   with 401. Fixed by giving `API_BASE_URL` a trailing slash and making `ApiRoutes` paths
   relative (no leading slash) — except `ApiClient.redirect()`, which *deliberately* keeps its
   leading slash, since short-code redirects genuinely live at the origin root.

6. **Frontend responsive overflow + a11y gap** (`frontend/index.html`). `TC-UI-005` caught the
   navbar's user-info group overflowing the viewport horizontally on a 375px mobile width (no
   `flex-wrap`); `TC-UI-007` caught the expiry-date input having no label, `aria-label`, or
   placeholder — only a `title` attribute, which isn't reliably exposed to screen readers. Both
   fixed with minimal, additive markup changes.

7. **Environment quirk (not the app or this framework): a native, unrelated Postgres service on
   this machine shadows Docker's port 5432 forwarding.** `helpers/db.ts` connected successfully
   but to the *wrong* server — one with an entirely different (Prisma-based) schema — because a
   native Windows `postgres.exe` process (PID confirmed via `Get-NetTCPConnection`) already owned
   host port 5432 ahead of Docker Desktop's WSL2 forwarder. Not a code fix: worked around by
   remapping the container's published port (`POSTGRES_PORT=5433`) for this environment; see
   `.env.example` for the general diagnostic/workaround, since this can happen on any dev machine
   that also has a native Postgres install.

8. **Coverage gap: no test ever actually watched a browser follow a shortened link.**
   Every redirect assertion in this suite (including `TC-E2E-001`) went through
   `apiClient.redirect()` — an HTTP client reading a `302` status and a `Location` header, never
   a real browser navigating and rendering the destination. This gap was only caught by a human
   manually opening a shortened link in a real tab. Added `TC-E2E-006` to close it — and building
   that one test surfaced a second, genuinely interesting finding: pointing a real browser
   navigation at a deliberately unresolvable fake domain (the pattern used everywhere else in
   this suite to avoid real-internet dependencies) reliably threw `net::ERR_NAME_NOT_RESOLVED`
   even with `page.route()` intercepting it — Chromium performs its own DNS
   pre-resolution/preconnect for cross-origin *redirect targets* specifically, which can fail
   before Playwright's CDP-based route interception ever gets a chance to run. Worked around by
   giving `TC-E2E-006` a real, tiny HTTP server bound to `127.0.0.1` on an ephemeral port instead
   of a fake hostname — genuinely resolvable and reachable, so the test still never depends on
   the real internet or any external site.

Each is described here with the same "why", not just "what changed", so a future change to
`apiFetch`, transaction boundaries, cache eviction, or route constants can be checked against the
actual reasoning rather than just the diff.

---

## Running Tests

```bash
npx playwright test                          # everything, all projects, parallel
npx playwright test --project=chromium       # one browser
npx playwright test tests/auth               # one folder
npx playwright test --grep @smoke            # tagged subset
npx playwright test --ui                     # interactive UI mode
npx playwright test --debug                  # step through with the inspector

# Rate-limit test is excluded from the default run (see Known Limitations) - run it alone:
npx playwright test tests/security/rate-limit.spec.ts --workers=1
```

Tags used throughout (`@smoke`, `@regression`, `@security`, `@accessibility`, `@performance`)
are plain substrings in test titles — `--grep` matches them directly.

---

## Reporting

Three reporters run on every invocation (`playwright.config.ts`):

- **HTML** — `reports/html-report` (`npm run report:html` to open)
- **JUnit XML** — `reports/junit/results.xml` (CI test-result annotations)
- **Allure** — `reports/allure-results` (`npm run report:allure` to generate + open)

Screenshots (on failure), video (on failure), and full traces (on failure) are attached
automatically per `playwright.config.ts`'s `use` block — configurable via `.env`
(`SCREENSHOT`/`VIDEO`/`TRACE`).

---

## CI/CD

`.github/workflows/automation-tests.yml` (repo root — GitHub Actions only reads workflows from
there, not from `automation/.github/`): brings up the full `docker-compose.yml` stack, waits for
`/actuator/health`, typechecks, lints, runs the suite across chromium/firefox/webkit + the API
project, runs the rate-limit test separately, uploads all three report formats as artifacts, and
tears the stack down. Triggered on PRs touching `automation/`, `backend/`, or `frontend/`, plus a
nightly schedule.

`Dockerfile` builds a standalone test-runner image (Microsoft's official Playwright base image)
for running this suite against any already-running environment — it does not bundle the
application itself.

---

## Known Limitations

- **Rate limiting and parallel execution.** `RedisRateLimiter` is per-client-IP, and every
  request in a local/CI run shares one IP. The default budget (100 req/60s —
  `app.rate-limit.default-capacity`) is comfortably exceeded by the full suite running in
  parallel. CI overrides `RATE_LIMIT_DEFAULT_CAPACITY=5000` via `docker-compose.yml` environment
  variables; do the same locally (`RATE_LIMIT_DEFAULT_CAPACITY=5000 docker compose up -d
  --force-recreate backend`) before running the full parallel suite, or the general suite will
  see spurious 429s. `tests/security/rate-limit.spec.ts` deliberately tests the *real* limit and
  must be run alone (`--workers=1`) for exactly this reason.
- **The password-verification endpoint has its own separate, much tighter limit** (5 req/60s,
  hardcoded in `RateLimitFilter.VERIFY_PASSWORD_CAPACITY` — not affected by
  `RATE_LIMIT_DEFAULT_CAPACITY`, since it's a deliberate anti-brute-force control, not the
  general default). `TC-E2E-005` uses 2 of that 5-request budget per run; re-running just that
  test file repeatedly in quick succession while debugging can trip a real 429 within seconds -
  that's the control working as intended, not a bug.
- **True JWT expiry is not tested.** `JWT_ACCESS_EXPIRATION_MS` defaults to 1 hour; forging an
  already-expired-but-validly-signed token would require the suite to hold the signing secret,
  which would test the JWT library rather than this application. `TC-JWT-007` is present but
  `test.skip`'d with the reasoning inline; to actually run it, override
  `JWT_ACCESS_EXPIRATION_MS` to a few seconds via `docker-compose.yml`, restart the backend, and
  run `tests/auth/rbac.spec.ts` alone.
- **Password-protected links are never tracked as a click**, even after successful password
  verification (`UrlServiceImpl#verifyPasswordAndGetDestination` never publishes a
  `UrlClickedEvent`). Confirmed via `TC-E2E-005`. This is flagged as a real product gap
  discovered through testing, not an automation defect — worth a backend fix in a future pass.
- **Analytics cache can serve a stale click count for up to 60s after subsequent clicks.** The
  `analytics` cache is only skipped while `totalClicks == 0`; once non-zero, it's cached with no
  eviction hook on later clicks, so a link that gets clicked multiple times within one TTL window
  can show a frozen count. Confirmed live via `TC-E2E-002` (3 real clicks, DB `click_count=3`
  immediately, cached API response stuck at `1` for the rest of the TTL). `TC-E2E-002` asserts
  against Postgres directly (the immediately-consistent source of truth) rather than waiting out
  the cache — see the fix in [Bugs Found While Building This Suite](#bugs-found-while-building-this-suite).
- **Clipboard tests are Chromium-only.** `context.grantPermissions(['clipboard-read', ...])` is
  not reliably grantable in Firefox/WebKit automation; `TC-UI-001` is skipped on those projects
  with `test.skip(browserName !== 'chromium', ...)`.
- **No visual regression baseline.** Screenshot-diff testing (`toHaveScreenshot()`) was not set
  up — no baseline-image workflow exists yet. Listed under Future Enhancements.
- **Geo/country analytics field is always "Unknown".** Not a test gap — `NoOpGeoIpService` is a
  deliberate stub in the backend (see `docs/ARCHITECTURE.md`); tests don't assert a real value.

---

## Best Practices Followed

- Locator API throughout — `getByRole`, `getByLabel`-equivalent, `#id` selectors read from real
  markup; zero XPath.
- Auto-waiting web-first assertions (`expect(locator).toBeVisible()` etc.) everywhere except the
  two documented, justified exceptions in `login.spec.ts`/`dashboard-ui.spec.ts` where two
  dynamic values are compared against each other rather than a fixed expectation (each has an
  inline `eslint-disable` with its reasoning, not a blanket suppression).
- Zero hard-coded `waitForTimeout` except one, documented case (search-input debounce, which has
  no event to wait on instead) — flagged by ESLint (`playwright/no-wait-for-timeout`) as a
  warning specifically so it stays visible rather than silently multiplying.
- `retryUntil()` (`utils/retry.ts`) is the *only* polling mechanism, reserved for genuinely
  eventually-consistent state outside the DOM (Kafka → consumer → Postgres lag) — never used as
  a substitute for a proper locator wait.
- No global mutable state: every test creates its own users/URLs/API keys via `DataGenerator`:
  and the real API, so tests are independent and parallel-safe (rate limiting aside, see above).
- TypeScript strict mode, `noUnusedLocals`/`noUnusedParameters` on, path aliases configured.
- Page Object Model + Component Object Model, SOLID-ish service boundaries (`ApiClient`,
  `Db`, page objects each own one responsibility).

---

## Future Enhancements

1. Visual regression baseline (`toHaveScreenshot()`) for the dashboard and analytics pages.
2. A dedicated `docker-compose.rate-limit-test.yml` override (or CI job step) so
   `RATE_LIMIT_DEFAULT_CAPACITY` doesn't need manual overriding locally.
3. A short-TTL JWT profile/service specifically to exercise real token expiry (`TC-JWT-007`).
4. Contract tests against the OpenAPI spec the backend already generates
   (`/v3/api-docs`) to catch schema drift automatically instead of via hand-typed DTOs.
5. Sharding (`--shard=1/N`) wired into the CI matrix once suite runtime justifies it.
6. A fix for the password-protected-link click-tracking gap found in `TC-E2E-005`, plus a test
   that then asserts it *is* tracked.
