# URL Shortener Platform — Manual Test Case Repository

**Scope:** Full URL lifecycle — Authentication, Shorten URL, Redirect, Password-Protected
Links (secure sharing), Analytics, List URLs, Soft Delete, Update Expiry, API Keys, Rate
Limiting, RBAC/Authorization, Frontend UI, cross-cutting Security, Performance,
Compatibility, Accessibility, Error Handling/Resilience, and Production Validation.

**System under test:** `url-shortener-platform` (Spring Boot 3 / Java 21)

**Total test cases:** 256

Written against the platform's actual implemented behavior (real endpoints, real status
codes, real field constraints) rather than generic template scenarios. An interactive,
filterable version of this same data is also published as a web artifact.

---

## Test Case Matrix

| Test Case ID | Module | Scenario | Preconditions | Test Steps | Test Data | Expected Result | Priority | Severity | Test Type |
|---|---|---|---|---|---|---|---|---|---|
| TC-AUTH-001 | Auth | Register with fully valid data | No account exists for the username/email | 1) POST /api/v1/auth/register with valid username, email, password 2) Inspect response | username: qa_new1, email: qa_new1@test.com, password: Passw0rd! | 201 Created; body has accessToken, refreshToken, tokenType=Bearer, expiresInMs=3600000 | P0 | Critical | Functional |
| TC-AUTH-002 | Auth | Register with username already taken (case-insensitive) | User 'demo' already exists | 1) POST register with username 'DEMO' (different case) 2) Inspect response | username: DEMO, email: new@test.com, password: Passw0rd! | 409 Conflict; message 'Username is already taken' | P1 | High | Negative |
| TC-AUTH-003 | Auth | Register with email already registered | demo@example.com already registered | 1) POST register with a new username but existing email | username: qa_new2, email: demo@example.com, password: Passw0rd! | 409 Conflict; message 'Email is already registered' | P1 | High | Negative |
| TC-AUTH-004 | Auth | Register with missing username field | None | 1) POST register JSON without 'username' key | {email, password only} | 400 Bad Request; validationErrors.username present | P1 | High | Negative |
| TC-AUTH-005 | Auth | Register with username below minimum length (2 chars) | None | 1) POST register with username='ab' | username: ab | 400; validationErrors.username size violation (min 3) | P2 | Medium | Boundary |
| TC-AUTH-006 | Auth | Register with username at minimum boundary (3 chars) | None | 1) POST register with username exactly 3 chars | username: abc | 201 Created (boundary accepted) | P2 | Medium | Boundary |
| TC-AUTH-007 | Auth | Register with username above maximum length (51 chars) | None | 1) POST register with 51-char username | username: 51 chars of 'a' | 400; size violation (max 50) | P2 | Medium | Boundary |
| TC-AUTH-008 | Auth | Register with username containing disallowed characters | None | 1) POST register with spaces/@ in username | username: 'qa user@1' | 400; pattern violation (only letters/digits/_/./- allowed) | P2 | Medium | Negative |
| TC-AUTH-009 | Auth | Register with invalid email format | None | 1) POST register with malformed email | email: 'not-an-email' | 400; validationErrors.email | P1 | High | Negative |
| TC-AUTH-010 | Auth | Register with password below 8 chars | None | 1) POST register with 7-char password | password: 'Abc123!' | 400; size violation (min 8) | P1 | High | Boundary |
| TC-AUTH-011 | Auth | Register with password missing complexity (no uppercase) | None | 1) POST register with all-lowercase+digit password | password: 'passw0rd' | 400; pattern violation (needs upper/lower/digit) | P1 | High | Negative |
| TC-AUTH-012 | Auth | Register with password exactly at 8-char complexity boundary | None | 1) POST register with password meeting min length+complexity exactly | password: 'Passw0r1' | 201 Created | P2 | Medium | Boundary |
| TC-AUTH-013 | Auth | Register with SQL injection payload in username | None | 1) POST register with username = "admin' OR '1'='1" | username: admin' OR '1'='1 | 400 (pattern rejects non-alphanumeric); no DB error, no injection effect | P0 | Critical | Security |
| TC-AUTH-014 | Auth | Register with script tag in email local-part | None | 1) POST register with email containing <script> | email: '<script>alert(1)</script>@test.com' | 400 (fails email format validation) | P1 | High | Security |
| TC-AUTH-015 | Auth | Login with valid credentials | Account 'demo' exists with password Demo@12345 | 1) POST /api/v1/auth/login with correct credentials | username: demo, password: Demo@12345 | 200 OK; accessToken + refreshToken returned | P0 | Critical | Functional |
| TC-AUTH-016 | Auth | Login with unknown username | None | 1) POST login with a username that does not exist | username: nosuchuser, password: whatever | 401 Unauthorized; generic 'Invalid username or password' (no user-enumeration hint) | P0 | Critical | Security |
| TC-AUTH-017 | Auth | Login with wrong password for existing user | Account 'demo' exists | 1) POST login with correct username, wrong password | username: demo, password: WrongPass1 | 401; same generic message as unknown-username case | P0 | Critical | Security |
| TC-AUTH-018 | Auth | Login username is case-insensitive | Account 'demo' exists | 1) POST login with username 'DEMO' | username: DEMO, password: Demo@12345 | 200 OK; login succeeds | P2 | Medium | Functional |
| TC-AUTH-019 | Auth | Refresh access token using a valid refresh token | Logged in, refreshToken in hand | 1) POST /api/v1/auth/refresh with refreshToken | refreshToken: <valid> | 200; new accessToken and refreshToken issued (rotation) | P1 | High | Functional |
| TC-AUTH-020 | Auth | Refresh using an access token instead of a refresh token | Have a valid accessToken | 1) POST /api/v1/auth/refresh passing the accessToken value | refreshToken: <accessToken value> | 401; 'Provided token is not a refresh token' | P1 | High | Negative |
| TC-AUTH-021 | Auth | Refresh with an expired refresh token | Refresh token older than 24h (JWT_REFRESH_EXPIRATION_MS) | 1) POST refresh with expired token | refreshToken: <expired> | 401 Invalid or expired token | P1 | High | Boundary |
| TC-AUTH-022 | Auth | Refresh with a tampered JWT signature | Valid token, one character altered in the signature segment | 1) POST refresh with mutated token string | refreshToken: <tampered> | 401; signature validation fails | P0 | Critical | Security |
| TC-AUTH-023 | Auth | Call protected endpoint with expired access token | Access token past 1h expiry | 1) GET /api/v1/urls with expired Bearer token | Authorization: Bearer <expired> | 401 Unauthorized | P0 | Critical | Security |
| TC-AUTH-024 | Auth | Call protected endpoint with no Authorization header | None | 1) GET /api/v1/urls with no auth header | (none) | 401 Unauthorized, JSON error body from RestAuthenticationEntryPoint | P0 | Critical | Security |
| TC-AUTH-025 | Auth | Call protected endpoint with malformed Bearer header | None | 1) GET /api/v1/urls with header 'Authorization: Token abc' | Authorization: Token abc | Treated as unauthenticated; 401 on protected route | P2 | Medium | Negative |
| TC-AUTH-026 | Auth | Newly registered user always receives role USER | None | 1) Register a new account 2) Inspect JWT role claim | username: qa_new3 | role claim = USER; cannot self-assign ADMIN via API | P0 | Critical | Security |
| TC-CRT-001 | Shorten URL | Create short URL with valid https URL, no expiry, no password | Authenticated as a USER | 1) POST /api/v1/urls {url} 2) Inspect response | url: https://www.example.com/very/long/url | 201 Created; id, shortCode (7 chars), shortUrl, createdAt returned; expiresAt null; passwordProtected false | P0 | Critical | Functional |
| TC-CRT-002 | Shorten URL | Create short URL with valid http (non-https) URL | Authenticated | 1) POST with http:// url | url: http://example.com/page | 201 Created (http explicitly allowed) | P1 | High | Functional |
| TC-CRT-003 | Shorten URL | Create with url field missing | Authenticated | 1) POST body without 'url' key | {}  | 400; validationErrors.url 'must not be blank' | P1 | High | Negative |
| TC-CRT-004 | Shorten URL | Create with empty string url | Authenticated | 1) POST {url:''} | url: '' | 400; NotBlank violation | P1 | High | Negative |
| TC-CRT-005 | Shorten URL | Create with whitespace-only url | Authenticated | 1) POST {url:'   '} | url: '   ' | 400; NotBlank treats blank/whitespace as invalid | P2 | Medium | Negative |
| TC-CRT-006 | Shorten URL | Create with url containing leading/trailing spaces | Authenticated | 1) POST {url:' https://example.com '} | url: ' https://example.com ' | 400; malformed URL (validator does not trim before parsing) | P2 | Medium | Boundary |
| TC-CRT-007 | Shorten URL | Create with javascript: scheme | Authenticated | 1) POST {url:'javascript:alert(1)'} | url: javascript:alert(1) | 400; @ValidHttpUrl rejects non-http(s) schemes | P0 | Critical | Security |
| TC-CRT-008 | Shorten URL | Create with ftp:// scheme | Authenticated | 1) POST {url:'ftp://files.example.com/x'} | url: ftp://files.example.com/x | 400; scheme not allowed | P2 | Medium | Negative |
| TC-CRT-009 | Shorten URL | Create with data: URI (XSS-in-redirect attempt) | Authenticated | 1) POST {url:'data:text/html,<script>1</script>'} | url: data:text/html,... | 400; rejected by scheme allowlist | P0 | Critical | Security |
| TC-CRT-010 | Shorten URL | Create with a <script> tag embedded in the URL | Authenticated | 1) POST {url:'https://example.com/?x=<script>alert(1)</script>'} | url with inline <script> | 400; @NoScriptTag rejects it before persistence | P0 | Critical | Security |
| TC-CRT-011 | Shorten URL | Create with an onerror= handler embedded in URL | Authenticated | 1) POST {url:'https://example.com/<img src=x onerror=alert(1)>'} | url with onerror= | 400; @NoScriptTag pattern match on event handlers | P0 | Critical | Security |
| TC-CRT-012 | Shorten URL | Create with url exceeding 8192 characters | Authenticated | 1) POST with a 8193-char URL string | url length 8193 | 400; exceeds max length | P2 | Medium | Boundary |
| TC-CRT-013 | Shorten URL | Create with url at exactly 8192 characters (boundary) | Authenticated | 1) POST with url length exactly 8192 | url length 8192 | 201 Created (accepted at boundary) | P2 | Medium | Boundary |
| TC-CRT-014 | Shorten URL | Create with url missing a host (e.g. 'https://') | Authenticated | 1) POST {url:'https://'} | url: https:// | 400; host empty, fails validator | P2 | Medium | Negative |
| TC-CRT-015 | Shorten URL | Create the same original URL twice | Authenticated, no prior link to this URL | 1) POST same url twice in separate requests | url: https://example.com/repeat | Both succeed with 201 and two distinct shortCodes (no dedup enforced) — confirm this is the intended business rule | P2 | Medium | Functional |
| TC-CRT-016 | Shorten URL | Create with expiryDate in the past | Authenticated | 1) POST {url, expiryDate: '2000-01-01T00:00:00Z'} | expiryDate: past instant | 201 Created — link is immediately expired on first redirect (no forward-only validation); flag as a business-rule gap | P2 | Medium | Negative |
| TC-CRT-017 | Shorten URL | Create with expiryDate far in the future | Authenticated | 1) POST with expiryDate year 2999 | expiryDate: 2999-01-01T00:00:00Z | 201 Created; expiresAt reflected exactly | P3 | Low | Boundary |
| TC-CRT-018 | Shorten URL | Create with malformed expiryDate string | Authenticated | 1) POST {expiryDate:'not-a-date'} | expiryDate: not-a-date | 400; JSON deserialization/binding error | P2 | Medium | Negative |
| TC-CRT-019 | Shorten URL | Create with password exactly 4 characters (min boundary) | Authenticated | 1) POST {url, password:'abcd'} | password: abcd (4 chars) | 201 Created; passwordProtected true | P2 | Medium | Boundary |
| TC-CRT-020 | Shorten URL | Create with password of 3 characters (below min) | Authenticated | 1) POST {password:'abc'} | password: abc (3 chars) | 400; size violation (min 4) | P2 | Medium | Boundary |
| TC-CRT-021 | Shorten URL | Create with password at 72-character max boundary | Authenticated | 1) POST with 72-char password | password length 72 | 201 Created | P3 | Low | Boundary |
| TC-CRT-022 | Shorten URL | Create with password at 73 characters (above max) | Authenticated | 1) POST with 73-char password | password length 73 | 400; size violation (max 72) | P2 | Medium | Boundary |
| TC-CRT-023 | Shorten URL | Create without Authorization header | Not authenticated | 1) POST /api/v1/urls with no auth | (none) | 401 Unauthorized | P0 | Critical | Security |
| TC-CRT-024 | Shorten URL | Create using a valid API key instead of JWT | Active API key exists for user | 1) POST with X-API-Key header, no Bearer token | X-API-Key: usk_... | 201 Created; ownership attributed to the key's user | P1 | High | Functional |
| TC-CRT-025 | Shorten URL | Create using a revoked API key | API key previously revoked | 1) POST with revoked X-API-Key | X-API-Key: <revoked> | 401; request treated as unauthenticated | P1 | High | Security |
| TC-CRT-026 | Shorten URL | Two concurrent create requests by the same user | Authenticated | 1) Fire 2 simultaneous POST /api/v1/urls calls | Two different urls | Both succeed with 201 and distinct, non-colliding shortCodes | P1 | High | Functional |
| TC-CRT-027 | Shorten URL | shortUrl in response reflects configured APP_BASE_URL | Authenticated, deployed behind Nginx at http://localhost | 1) POST create 2) Inspect shortUrl field | url: https://example.com | shortUrl = 'http://localhost/{shortCode}' matching the reverse-proxy origin | P1 | High | Functional |
| TC-CRT-028 | Shorten URL | Create with Unicode/IDN domain in URL | Authenticated | 1) POST {url:'https://xn--exmple-cua.com/pfad'} | IDN-encoded domain | 201 Created (valid host per URL parser) | P3 | Low | Functional |
| TC-CRT-029 | Shorten URL | Create with emoji in URL query string | Authenticated | 1) POST {url:'https://example.com/?q=😀'} | url with emoji character | 201 Created; emoji preserved in originalUrl on read-back | P3 | Low | Boundary |
| TC-CRT-030 | Shorten URL | Response never includes password_hash or other internal fields | Authenticated, password-protected create | 1) POST with password 2) Inspect full JSON response | password: Secret1 | Response has only passwordProtected boolean, never the hash/plaintext | P0 | Critical | Security |
| TC-CRT-031 | Shorten URL | Create with JSON body containing an unexpected extra field | Authenticated | 1) POST {url, extraField:'x'} | extraField: 'x' | 201 Created; unknown field silently ignored, no 400 | P3 | Low | API |
| TC-RDR-001 | Redirect | Redirect a valid, active short code | Link exists, active, public | 1) GET /{shortCode} with redirects disabled 2) Inspect status+Location | shortCode of an active link | 302 Found; Location header = stored originalUrl | P0 | Critical | Functional |
| TC-RDR-002 | Redirect | Redirect an unknown short code | No link with this code exists | 1) GET /doesNotExist | shortCode: doesNotExist | 404 Not Found | P0 | Critical | Negative |
| TC-RDR-003 | Redirect | Redirect a soft-deleted short code | Link was deleted via DELETE /api/v1/urls/{id} | 1) GET /{shortCode} of the deleted link | deleted shortCode | 404 Not Found (deletedAt filtered out of lookup) | P1 | High | Functional |
| TC-RDR-004 | Redirect | Redirect an expired short code | Link expiresAt is in the past | 1) GET /{shortCode} of expired link | expired shortCode | 410 Gone; body explains the expiry date | P1 | High | Functional |
| TC-RDR-005 | Redirect | Redirect at the exact expiry instant boundary | expiresAt set to 'now' | 1) GET the link at/just after its expiresAt instant | expiresAt = now() | 410 Gone once expiresAt has passed isBefore(now) check | P2 | Medium | Boundary |
| TC-RDR-006 | Redirect | Redirect a password-protected link (no password supplied) | Link created with a password | 1) GET /{shortCode} directly in browser | protected shortCode | 302 to /protected.html?code={shortCode}; destination not leaked; no click recorded yet | P0 | Critical | Security |
| TC-RDR-007 | Redirect | Short code is case-sensitive | Active link with mixed-case Base62 code, e.g. 'aZ3kP9x' | 1) GET the same code with case flipped | e.g. 'Az3Kp9X' | 404 (different code, not matched) | P2 | Medium | Functional |
| TC-RDR-008 | Redirect | Attempt path traversal via short-code segment | None | 1) GET /..%2f..%2fetc%2fpasswd | %2e%2e path payload | 404/blocked at routing layer; no filesystem access, no 500 | P1 | High | Security |
| TC-RDR-009 | Redirect | Click count reflected in analytics only after async processing | Active link, zero prior clicks | 1) GET redirect 2) Immediately GET analytics 3) Poll analytics again after ~5s | - | Immediately after: may still show 0 (async Kafka pipeline); within a few seconds: totalClicks=1 | P1 | High | Functional |
| TC-RDR-010 | Redirect | Burst of clicks under the default rate limit | Active link | 1) Issue 50 GET requests in <60s from one IP | 50 rapid requests | All 302 (under 100/60s default cap) | P2 | Medium | Performance |
| TC-RDR-011 | Redirect | Requests exceeding the rate limit | Active link | 1) Issue 101 GET requests within 60s from one IP | 101 rapid requests | Requests 101+ return 429 with Retry-After header | P1 | High | Security |
| TC-RDR-012 | Redirect | Redirect response has empty body | Active link | 1) GET redirect 2) Inspect Content-Length | - | Content-Length: 0; no body leaking destination in-band | P3 | Low | Functional |
| TC-RDR-013 | Redirect | Distinct browsers produce distinct browser breakdown entries | Active link | 1) Click via Chrome UA 2) Click via Firefox UA 3) GET analytics | User-Agent: Chrome.../ Firefox... | analytics.browsers contains both 'Chrome' and 'Firefox' with counts 1 each | P1 | High | Functional |
| TC-RDR-014 | Redirect | Bot/crawler user agent is classified as BOT device type | Active link | 1) GET redirect with User-Agent containing 'bot' 2) Check analytics.deviceTypes | User-Agent: Mozilla/5.0 (compatible; Googlebot/2.1) | deviceTypes.BOT incremented; click still counted (no bot filtering) | P2 | Medium | Functional |
| TC-RDR-015 | Redirect | Referrer header captured in analytics | Active link | 1) GET redirect with Referer header set 2) Check analytics.referrers | Referer: https://socialsite.example | referrers map contains the raw referrer value with count 1 | P2 | Medium | Functional |
| TC-RDR-016 | Redirect | No Referer header groups under Direct | Active link | 1) GET redirect with no Referer header 2) Check analytics.referrers | (no Referer) | referrers map contains 'Direct' entry | P2 | Medium | Functional |
| TC-RDR-017 | Redirect | First hit populates the Redis lookup cache (cold path) | Fresh link, no prior redirect | 1) GET redirect 2) Inspect Redis key urlLookup:{shortCode} | - | 302 returned; Redis key created with configured TTL | P2 | Medium | Functional |
| TC-RDR-018 | Redirect | Subsequent hit served from warm cache | Cache already populated from TC-RDR-017 | 1) GET the same short code again within TTL | - | 302 returned without a fresh Postgres SELECT (cache hit) | P2 | Medium | Performance |
| TC-RDR-019 | Redirect | Owner soft-deletes link; cached redirect evicted immediately | Link cached from a prior redirect | 1) GET (warms cache) 2) DELETE the link 3) GET again immediately | - | Second GET returns 404 — explicit delete evicts cache immediately (unlike passive TTL expiry) | P1 | High | Functional |
| TC-RDR-020 | Redirect | Redirect target itself is unreachable/broken | originalUrl points to a domain that 404s | 1) GET redirect | url: https://example.com/does-not-exist-upstream | 302 still issued to the stored URL regardless of destination liveness (app does not validate) | P3 | Low | Functional |
| TC-RDR-021 | Redirect | Redirect without following, verifying raw headers via curl --head | Active link | 1) curl -I http://host/{shortCode} | - | HTTP/1.1 302; Location header present and correct; no body fetch needed | P2 | Medium | API |
| TC-RDR-022 | Redirect | Query parameters on the short link are not merged into destination | Active link created from a bare URL | 1) GET /{shortCode}?utm_source=test | ?utm_source=test appended to short link | 302 Location is exactly the stored originalUrl; extra query params dropped, not merged — confirm this matches intended UX | P2 | Medium | Functional |
| TC-PWD-001 | Secure Sharing | Create a password-protected link | Authenticated | 1) POST /api/v1/urls {url, password} 2) Inspect response | password: Secret1 | 201; passwordProtected=true; password never echoed back | P0 | Critical | Security |
| TC-PWD-002 | Secure Sharing | Verify with the correct password | Protected link exists with password 'Secret1' | 1) POST /api/v1/urls/{shortCode}/verify-password {password:'Secret1'} | password: Secret1 | 200 OK; body {originalUrl: <destination>} | P0 | Critical | Functional |
| TC-PWD-003 | Secure Sharing | Verify with an incorrect password | Protected link exists | 1) POST verify-password with wrong password | password: WrongPass | 401 Unauthorized; 'Incorrect password' | P0 | Critical | Security |
| TC-PWD-004 | Secure Sharing | Verify with an empty password field | Protected link exists | 1) POST verify-password {password:''} | password: '' | 400; NotBlank violation | P2 | Medium | Negative |
| TC-PWD-005 | Secure Sharing | Verify against a non-existent short code | None | 1) POST verify-password for an unknown code | shortCode: doesNotExist | 404 Not Found | P2 | Medium | Negative |
| TC-PWD-006 | Secure Sharing | Verify a public (non-protected) link with any password value | Public link exists | 1) POST verify-password for a public link with arbitrary password | password: anything | 200 OK; originalUrl returned regardless — confirm this permissive behavior is intended for public links | P2 | Medium | Functional |
| TC-PWD-007 | Secure Sharing | Brute-force protection on verify-password endpoint | Protected link exists | 1) Send 6 wrong-password attempts within 60s from same IP | 6 rapid attempts | 1st-5th: 401 each; 6th: 429 Too Many Requests (5/60s limit, stricter than default) | P0 | Critical | Security |
| TC-PWD-008 | Secure Sharing | Verify endpoint requires no authentication | Protected link exists | 1) POST verify-password with no Authorization header | (no auth header) | Request processed normally (public endpoint by design) | P1 | High | Functional |
| TC-PWD-009 | Secure Sharing | Password comparison is case-sensitive | Password set to 'Secret1' | 1) POST verify-password with 'secret1' (lowercase s) | password: secret1 | 401 (bcrypt exact match fails) | P2 | Medium | Functional |
| TC-PWD-010 | Secure Sharing | Password containing Unicode characters | None | 1) Create link with password containing accented/non-Latin characters 2) Verify with same value | password: 'Sëcrét密码1' | 201 on create; 200 on verify with exact same value | P3 | Low | Boundary |
| TC-PWD-011 | Secure Sharing | Very long password near bcrypt's 72-byte input limit | None | 1) Create with a password whose UTF-8 byte length is >72 2) Verify with the same string | password: 80-byte multi-byte string | Behavior should be documented: bcrypt truncates beyond 72 bytes — verify create/verify remain consistent with each other | P3 | Low | Boundary |
| TC-PWD-012 | Secure Sharing | Rate limit key isolates two different client IPs | Protected link exists | 1) 5 failed attempts from IP A 2) 1 attempt from IP B | 2 distinct source IPs | IP B's first attempt is not blocked by IP A's exhausted budget | P2 | Medium | Security |
| TC-PWD-013 | Secure Sharing | Redirect endpoint never leaks destination for protected links pre-verification | Protected link exists | 1) GET /{shortCode} directly 2) Inspect full response | - | Response is a 302 to /protected.html only — no originalUrl anywhere in headers/body | P0 | Critical | Security |
| TC-PWD-014 | Secure Sharing | Successful verification does not itself record a click | Protected link exists, zero prior clicks | 1) POST verify-password successfully 2) GET analytics | - | totalClicks remains 0 after verify alone — the frontend must separately navigate to the destination for a click to count | P2 | Medium | Functional |
| TC-PWD-015 | Secure Sharing | SQL/NoSQL injection payload in password field | Protected link exists | 1) POST verify-password with a SQLi payload as password | password: "' OR '1'='1" | 401 (parameterized bcrypt comparison; no injection effect) | P0 | Critical | Security |
| TC-ANL-001 | Analytics | Analytics for a link with zero clicks | Freshly created link, owner authenticated | 1) GET /api/v1/urls/{shortCode}/analytics immediately after create | - | 200; totalClicks=0, uniqueVisitors=0, all breakdown maps empty; result NOT cached (zero-click snapshot excluded from cache) | P1 | High | Functional |
| TC-ANL-002 | Analytics | Analytics reflects accumulated clicks accurately | Link has 5 recorded clicks from 3 distinct IPs | 1) GET analytics 2) Verify counts | - | totalClicks=5, uniqueVisitors=3 | P0 | Critical | Functional |
| TC-ANL-003 | Analytics | Unique visitors counts distinct IP hashes, not raw click count | Same IP clicks twice | 1) Click twice from same IP 2) GET analytics | - | totalClicks=2, uniqueVisitors=1 | P1 | High | Functional |
| TC-ANL-004 | Analytics | Daily clicks grouped by calendar date | Clicks recorded on two different dates | 1) GET analytics.dailyClicks | - | Two entries, one per date, each with correct count | P2 | Medium | Functional |
| TC-ANL-005 | Analytics | Browser breakdown reflects parsed User-Agent values | Clicks from Chrome and Safari UAs | 1) GET analytics.browsers | - | Map contains 'Chrome' and 'Safari' keys with correct counts | P1 | High | Functional |
| TC-ANL-006 | Analytics | Unparseable/blank User-Agent handled gracefully | Click sent with empty User-Agent header | 1) GET redirect with no UA 2) GET analytics.browsers/deviceTypes | User-Agent: (empty) | No exception; browser entry null/absent, deviceType OTHER | P2 | Medium | Negative |
| TC-ANL-007 | Analytics | Device type breakdown across desktop/mobile/tablet | Clicks from 3 different UA classes | 1) GET analytics.deviceTypes | - | DESKTOP, MOBILE, TABLET buckets populated per the classification heuristic | P2 | Medium | Functional |
| TC-ANL-008 | Analytics | Country breakdown is always unresolved (documented limitation) | Any click | 1) GET analytics.countries | - | No real geo-IP provider configured (NoOpGeoIpService) — country always null/absent; not a defect, a documented gap | P3 | Low | Functional |
| TC-ANL-009 | Analytics | Referrer breakdown groups by raw referrer string, not normalized domain | Clicks from 'https://a.example/page1' and 'https://a.example/page2' | 1) GET analytics.referrers | - | Two separate entries (not merged to domain 'a.example') — documented simplification | P3 | Low | Functional |
| TC-ANL-010 | Analytics | Non-owner, non-admin cannot view another user's analytics | Link owned by User A; requester is User B | 1) User B GETs analytics for User A's shortCode | - | 403 Forbidden | P0 | Critical | Security |
| TC-ANL-011 | Analytics | Admin can view any user's analytics | Link owned by a regular USER; requester is ADMIN | 1) Admin GETs analytics for that shortCode | - | 200 OK | P1 | High | Functional |
| TC-ANL-012 | Analytics | Analytics for a non-existent short code | None | 1) GET analytics for an unknown code | - | 404 Not Found | P2 | Medium | Negative |
| TC-ANL-013 | Analytics | Analytics call without authentication | None | 1) GET analytics with no Authorization header | - | 401 Unauthorized | P0 | Critical | Security |
| TC-ANL-014 | Analytics | Cached (non-zero) analytics response is stable within TTL | Link has clicks; analytics already fetched once | 1) GET analytics 2) Record a new click 3) GET analytics again within 60s TTL | - | Second call may still show pre-click counts until TTL/eviction (documented eventual consistency) | P2 | Medium | Functional |
| TC-ANL-015 | Analytics | End-to-end async pipeline latency is bounded | Fresh link | 1) Click 2) Poll analytics every 2s up to 20s | - | totalClicks reaches 1 well within 20s under normal load | P1 | High | Performance |
| TC-ANL-016 | Analytics | Kafka processing failure routes to dead-letter without corrupting analytics | Simulated consumer failure (ops-level test) | 1) Force consumer exception on a click event 2) Inspect dead-letter topic and analytics | - | Event lands on 'dead-letter' topic after retries exhausted; analytics for other clicks remain correct and unaffected | P2 | Medium | Error Handling |
| TC-LST-001 | List URLs | List returns only the requesting user's own links | User has 3 links; another user has 2 | 1) GET /api/v1/urls as the first user | - | 200; content contains exactly the 3 own links, none of the other user's | P0 | Critical | Security |
| TC-LST-002 | List URLs | Admin sees all users' links | Multiple users each own links; requester is ADMIN | 1) GET /api/v1/urls as ADMIN | - | 200; content includes links across all owners | P1 | High | Functional |
| TC-LST-003 | List URLs | Default pagination applied when page/size omitted | User has 25 links | 1) GET /api/v1/urls with no query params | - | 200; page=0, size=20, totalElements=25, totalPages=2 | P1 | High | Functional |
| TC-LST-004 | List URLs | Custom page and size honored | User has 25 links | 1) GET /api/v1/urls?page=1&size=10 | page=1, size=10 | 200; content has 10 items (items 11-20), page=1 | P2 | Medium | Functional |
| TC-LST-005 | List URLs | Search matches substring in original URL (case-insensitive) | Link with originalUrl containing 'ExampleDomain' | 1) GET /api/v1/urls?search=exampledomain | search=exampledomain | 200; matching link returned despite case difference | P1 | High | Functional |
| TC-LST-006 | List URLs | Search matches substring in short code | Link with known shortCode | 1) GET /api/v1/urls?search={partial shortCode} | - | 200; link returned | P2 | Medium | Functional |
| TC-LST-007 | List URLs | Search with no matches returns empty result set | - | 1) GET /api/v1/urls?search=zzzznomatch | search=zzzznomatch | 200; content=[], totalElements=0 (not 404) | P2 | Medium | Negative |
| TC-LST-008 | List URLs | status=ACTIVE excludes expired links | User has 1 active + 1 expired link | 1) GET /api/v1/urls?status=ACTIVE | status=ACTIVE | 200; only the active link returned | P1 | High | Functional |
| TC-LST-009 | List URLs | status=EXPIRED returns only expired links | Same setup as above | 1) GET /api/v1/urls?status=EXPIRED | status=EXPIRED | 200; only the expired link returned | P1 | High | Functional |
| TC-LST-010 | List URLs | status=ALL (or omitted) returns both active and expired | Same setup as above | 1) GET /api/v1/urls?status=ALL | status=ALL | 200; both links returned | P2 | Medium | Functional |
| TC-LST-011 | List URLs | Soft-deleted links never appear in any status filter | One link soft-deleted | 1) GET /api/v1/urls?status=ALL | status=ALL | Deleted link absent regardless of filter | P0 | Critical | Functional |
| TC-LST-012 | List URLs | Default sort is createdAt descending | User has links created at different times | 1) GET /api/v1/urls with no sort param | - | Most recently created link appears first | P2 | Medium | Functional |
| TC-LST-013 | List URLs | Explicit sort parameter is honored | - | 1) GET /api/v1/urls?sort=createdAt,asc | sort=createdAt,asc | Oldest link appears first | P3 | Low | Functional |
| TC-LST-014 | List URLs | Pagination beyond the last page returns an empty page | User has 5 links, size=20 | 1) GET /api/v1/urls?page=5&size=20 | page=5 | 200; content=[], last=true | P2 | Medium | Boundary |
| TC-LST-015 | List URLs | Negative or zero size parameter handled without server error | - | 1) GET /api/v1/urls?size=0 | size=0 | No 500; either a validation error or a sane default is applied (verify actual behavior) | P2 | Medium | Negative |
| TC-LST-016 | List URLs | Very large size parameter does not exhaust resources | User has 50 links | 1) GET /api/v1/urls?size=100000 | size=100000 | 200; bounded response, no timeout/OOM | P3 | Low | Performance |
| TC-LST-017 | List URLs | List call without authentication | None | 1) GET /api/v1/urls with no auth | - | 401 Unauthorized | P0 | Critical | Security |
| TC-LST-018 | List URLs | Crafted search string cannot surface another user's links | User A crafts search matching User B's URL content | 1) User A searches for text known only in User B's link | search=<B's unique text> | 200; empty result — ownership scoping applied before search filter | P0 | Critical | Security |
| TC-LST-019 | List URLs | Response never includes password_hash for protected links in the list | User has one protected link | 1) GET /api/v1/urls 2) Inspect item for that link | - | Item shows passwordProtected=true only, no hash/plaintext anywhere | P0 | Critical | Security |
| TC-LST-020 | List URLs | List remains consistent while a new link is created concurrently | - | 1) Start a paginated GET 2) Concurrently POST a new link 3) Complete original GET | - | No duplicate or skipped rows within the in-flight request's result set | P3 | Low | Functional |
| TC-DEL-001 | Delete | Owner deletes their own link | Link owned by requester | 1) DELETE /api/v1/urls/{id} | - | 204 No Content; link excluded from list; GET redirect now 404s | P0 | Critical | Functional |
| TC-DEL-002 | Delete | Non-owner, non-admin attempts to delete another user's link | Link owned by User A; requester is User B | 1) User B sends DELETE for User A's link id | - | 403 Forbidden; link remains active | P0 | Critical | Security |
| TC-DEL-003 | Delete | Admin deletes any user's link | Link owned by a USER; requester is ADMIN | 1) DELETE as ADMIN | - | 204 No Content | P1 | High | Functional |
| TC-DEL-004 | Delete | Delete a non-existent id | None | 1) DELETE /api/v1/urls/{random UUID} | - | 404 Not Found | P2 | Medium | Negative |
| TC-DEL-005 | Delete | Delete an already-deleted link (idempotency) | Link already soft-deleted | 1) DELETE the same id a second time | - | 404 Not Found (deletedAt filter excludes it from lookup) | P2 | Medium | Negative |
| TC-DEL-006 | Delete | Delete without authentication | None | 1) DELETE with no Authorization header | - | 401 Unauthorized | P0 | Critical | Security |
| TC-DEL-007 | Delete | Deleted short code becomes available for reuse | Link with code 'ABC1234' deleted | 1) Create enough new links to (statistically) or force a code collision retry against 'ABC1234' | - | New link can successfully reuse the freed code (unique index only applies to live rows) | P2 | Medium | Functional |
| TC-DEL-008 | Delete | Historical click/analytics data preserved after delete | Link with recorded clicks is deleted | 1) DELETE the link 2) Query url_clicks table directly (DB-level check) | - | url_clicks rows remain intact, still referencing the deleted url_id (audit trail preserved) | P1 | High | Database |
| TC-DEL-009 | Delete | Redis cache evicted immediately on delete | Link previously cached via a redirect | 1) Warm cache via GET redirect 2) DELETE the link 3) GET redirect again | - | Immediate 404 — no stale-cache window for explicit deletes | P1 | High | Functional |
| TC-DEL-010 | Delete | Delete with a malformed UUID path parameter | None | 1) DELETE /api/v1/urls/not-a-uuid | id: not-a-uuid | 400 Bad Request | P2 | Medium | Negative |
| TC-DEL-011 | Delete | Audit log entry created for URL_DELETED | Link deleted successfully | 1) DELETE link 2) Inspect audit_logs table | - | Row with action=URL_DELETED, correct actor_user_id and entity_id | P2 | Medium | Audit |
| TC-EXP-001 | Update Expiry | Owner sets a new future expiry date | Link owned by requester, non-expiring | 1) PATCH /api/v1/urls/{id} {expiresAt: future date} | expiresAt: 2030-01-01T00:00:00Z | 200 OK; expiresAt reflects the new value | P0 | Critical | Functional |
| TC-EXP-002 | Update Expiry | Owner clears expiry by sending null | Link currently has an expiry date | 1) PATCH {expiresAt: null} | expiresAt: null | 200 OK; expiresAt now null, link is non-expiring | P1 | High | Functional |
| TC-EXP-003 | Update Expiry | Non-owner attempts to update expiry | Link owned by User A; requester is User B | 1) PATCH as User B | - | 403 Forbidden | P0 | Critical | Security |
| TC-EXP-004 | Update Expiry | Admin updates any user's link expiry | Link owned by a USER; requester ADMIN | 1) PATCH as ADMIN | - | 200 OK | P1 | High | Functional |
| TC-EXP-005 | Update Expiry | Update expiry for a non-existent link id | None | 1) PATCH /api/v1/urls/{random UUID} | - | 404 Not Found | P2 | Medium | Negative |
| TC-EXP-006 | Update Expiry | Update with a malformed date string | Link exists | 1) PATCH {expiresAt:'31-02-2025'} | expiresAt: 31-02-2025 (invalid) | 400 Bad Request | P2 | Medium | Negative |
| TC-EXP-007 | Update Expiry | Set expiry to a past timestamp | Link exists, active | 1) PATCH {expiresAt: yesterday} | expiresAt: past date | 200 OK accepted; link becomes immediately expired on next redirect — confirm this matches intended behavior | P2 | Medium | Negative |
| TC-EXP-008 | Update Expiry | Cache evicted immediately after expiry update | Link cached via a prior redirect | 1) Warm cache 2) PATCH to set past expiry 3) GET redirect immediately | - | Redirect immediately returns 410, no stale-cache serving of the old (non-expired) state | P1 | High | Functional |
| TC-EXP-009 | Update Expiry | Update expiry without authentication | None | 1) PATCH with no Authorization header | - | 401 Unauthorized | P0 | Critical | Security |
| TC-EXP-010 | Update Expiry | Update expiry on a soft-deleted link | Link previously deleted | 1) PATCH the deleted link's id | - | 404 Not Found (loadOwned filters deletedAt) | P2 | Medium | Negative |
| TC-EXP-011 | Update Expiry | Audit log entry created for URL_EXPIRY_UPDATED | Expiry updated successfully | 1) PATCH expiry 2) Inspect audit_logs | - | Row with action=URL_EXPIRY_UPDATED, correct entity_id and new value in details | P2 | Medium | Audit |
| TC-KEY-001 | API Keys | Create a new API key | Authenticated | 1) POST /api/v1/api-keys {name} | name: 'CI pipeline' | 201 Created; plaintextKey present in this response only | P0 | Critical | Functional |
| TC-KEY-002 | API Keys | List API keys never returns the plaintext key | One or more keys exist | 1) GET /api/v1/api-keys | - | 200; each item has keyPrefix only, plaintextKey field absent/null | P0 | Critical | Security |
| TC-KEY-003 | API Keys | Revoke own API key | Key exists and is active | 1) DELETE /api/v1/api-keys/{id} 2) Attempt to use the revoked key | - | 204 on revoke; subsequent use of that key is treated as unauthenticated (401 on protected routes) | P0 | Critical | Security |
| TC-KEY-004 | API Keys | Attempt to revoke another user's API key | Key belongs to User A; requester is User B | 1) User B sends DELETE for User A's key id | - | 403 Forbidden | P0 | Critical | Security |
| TC-KEY-005 | API Keys | Use an expired API key | Key has expiresAt in the past | 1) Call an endpoint using X-API-Key of expired key | - | 401; treated as inactive/expired | P1 | High | Security |
| TC-KEY-006 | API Keys | API key grants the same role permissions as its owner | Key belongs to a USER (not ADMIN) | 1) Use the key to attempt an admin-only action (e.g. list all users' links) | - | Scoped to USER permissions, same as if that user logged in via JWT | P1 | High | Security |
| TC-KEY-007 | API Keys | Missing X-API-Key header falls back to anonymous, not an error | No Bearer token, no X-API-Key | 1) Call a public endpoint (e.g. redirect) with neither header | - | Request processed as anonymous; no exception | P3 | Low | Functional |
| TC-KEY-008 | API Keys | Garbage/malformed API key value | None | 1) Call with X-API-Key: garbage-value | X-API-Key: garbage-value | 401 on protected routes (unknown key hash, treated as unauthenticated) | P2 | Medium | Negative |
| TC-KEY-009 | API Keys | Key prefix shown in list matches the issued plaintext key's prefix | Key created, plaintext noted | 1) Compare keyPrefix from list response to the start of the original plaintextKey | - | Prefix characters match exactly | P3 | Low | Functional |
| TC-KEY-010 | API Keys | lastUsedAt updates on use but is throttled | Key used repeatedly within 60s | 1) Use key twice within a few seconds 2) Inspect lastUsedAt via DB | - | lastUsedAt updates on first use; not rewritten on every single request within the throttle window (~1 min) | P3 | Low | Performance |
| TC-KEY-011 | API Keys | Create API key without authentication | None | 1) POST /api/v1/api-keys with no auth | - | 401 Unauthorized | P0 | Critical | Security |
| TC-KEY-012 | API Keys | API key hash, not plaintext, is what's persisted | Key created | 1) Inspect api_keys table directly | - | key_hash column holds a SHA-256 hex digest; no plaintext key anywhere in the DB | P0 | Critical | Security |
| TC-RTL-001 | Rate Limiting | Default API limit blocks the 101st request in a 60s window | Authenticated client | 1) Issue 101 requests to any authenticated endpoint within 60s from one client | 101 requests | Requests 1-100 succeed; request 101 returns 429 with Retry-After header | P0 | Critical | Security |
| TC-RTL-002 | Rate Limiting | Rate limit window resets after 60 seconds | Client at limit | 1) Exhaust the limit 2) Wait 61s 3) Retry | - | Request after the wait succeeds (200/expected code, not 429) | P1 | High | Functional |
| TC-RTL-003 | Rate Limiting | Rate limit key differs for API key vs bare IP | Two different API keys used from the same source IP | 1) Exhaust limit using key A 2) Immediately call using key B from the same IP | - | Key B is not blocked by key A's exhausted budget (independent counters) | P2 | Medium | Security |
| TC-RTL-004 | Rate Limiting | verify-password has an independent, tighter limit | Protected link exists | 1) Exhaust the general 100/60s limit on other endpoints 2) Call verify-password | - | verify-password still enforces its own 5/60s budget, unaffected by the general counter | P1 | High | Security |
| TC-RTL-005 | Rate Limiting | Rate limiter fails open when Redis is unreachable | Redis stopped/unreachable | 1) Stop Redis 2) Issue requests past what would normally be limited | - | Requests still succeed (fail-open) rather than the API going fully down | P1 | High | Error Handling |
| TC-RTL-006 | Rate Limiting | 429 response body matches the standard error schema | Client at limit | 1) Trigger a 429 2) Inspect JSON body | - | Body has timestamp, status=429, error, message, correlationId (same shape as other errors) | P2 | Medium | API |
| TC-RBAC-001 | RBAC | Self-registration always yields role USER | - | 1) Register via public endpoint 2) Inspect issued JWT role claim | - | role=USER; no client-supplied field can request ADMIN | P0 | Critical | Security |
| TC-RBAC-002 | RBAC | USER cannot act on another USER's resources across all mutating endpoints | Two USER accounts, each with their own links | 1) Attempt PATCH/DELETE/analytics on the other user's resource for each endpoint | - | 403 Forbidden consistently across create/list/delete/expiry/analytics | P0 | Critical | Security |
| TC-RBAC-003 | RBAC | ADMIN can act on any user's resources | ADMIN account, USER-owned resources | 1) Perform list/delete/expiry/analytics as ADMIN on a USER's link | - | All succeed (200/204 as appropriate) | P1 | High | Functional |
| TC-RBAC-004 | RBAC | Tampered JWT role claim is rejected | Valid JWT with role=USER, payload edited client-side to role=ADMIN without re-signing | 1) Send request with the tampered token | - | 401 — signature validation fails before the role claim is ever trusted | P0 | Critical | Security |
| TC-RBAC-005 | RBAC | Role enforcement is consistent between JWT and API-key auth paths | Same user authenticates once via JWT, once via API key | 1) Attempt an admin-only action via both auth methods | - | Identical authorization outcome regardless of auth mechanism used | P1 | High | Security |
| TC-UI-001 | Frontend | Login page tab switch toggles Sign-in/Register forms | Load login.html | 1) Click 'Register' tab 2) Click 'Sign in' tab | - | Register form shows/hides correctly; only one form visible at a time; active tab styled | P2 | Medium | Usability |
| TC-UI-002 | Frontend | Shorten form blocks submission of an empty URL | On index.html, logged in | 1) Leave URL field blank 2) Click Shorten | - | Native browser 'required' validation prevents submit; no network call fires | P2 | Medium | Usability |
| TC-UI-003 | Frontend | Successful shorten shows a confirmation toast and resets the form | Logged in | 1) Submit a valid URL | url: https://example.com | Green/success toast 'Link created'; form fields cleared; table refreshes with the new row | P1 | High | Usability |
| TC-UI-004 | Frontend | Failed shorten shows a red error toast with the server's message | Logged in | 1) Submit a URL that fails validation (e.g. javascript: scheme) | url: javascript:alert(1) | Danger-styled toast displaying the API's error message, form retains entered values | P1 | High | Usability |
| TC-UI-005 | Frontend | URL list shows a loading state before data arrives | Page just loaded | 1) Observe table immediately on load | - | 'Loading...' row shown until the API response resolves | P2 | Medium | Usability |
| TC-UI-006 | Frontend | URL list shows an empty state with zero links | New user, no links created yet | 1) Load index.html | - | 'No links yet - shorten one above.' message in place of a table row | P2 | Medium | Usability |
| TC-UI-007 | Frontend | Copy button copies the short URL and confirms via toast | At least one link in the list | 1) Click 'Copy' next to a link | - | Short URL placed on clipboard; 'Copied to clipboard' success toast shown | P2 | Medium | Usability |
| TC-UI-008 | Frontend | Pagination controls reflect and change the active page | More than one page of links | 1) Click page 2 2) Observe active state and table contents | - | Page 2 marked active; table shows the corresponding subset of links | P2 | Medium | Usability |
| TC-UI-009 | Frontend | Search input is debounced, not fired per keystroke | Several links exist | 1) Type a multi-character search term quickly | - | Only one (or few) API calls fire after typing pauses, not one per keystroke | P3 | Low | Performance |
| TC-UI-010 | Frontend | Status filter change triggers an immediate reload | Mixed active/expired links exist | 1) Change the status dropdown | status=EXPIRED | Table updates to reflect only matching links without a manual refresh | P2 | Medium | Functional |
| TC-UI-011 | Frontend | Edit-expiry modal pre-fills the link's current expiry | Link has an existing expiry date | 1) Click 'Edit expiry' on that link | - | Modal's datetime-local input shows the existing value converted to local time | P2 | Medium | Usability |
| TC-UI-012 | Frontend | Delete requires confirmation before calling the API | Link exists | 1) Click 'Delete' 2) Dismiss the confirm dialog 3) Click 'Delete' again and accept | - | Dismissing cancels with no API call; accepting proceeds with the DELETE request | P1 | High | Usability |
| TC-UI-013 | Frontend | Analytics bar charts scale proportionally for skewed data | One browser dominates click share (e.g. 95% Chrome) | 1) Load analytics.html for that link | - | Chrome bar renders near-full width; minority browsers render proportionally smaller, not zero-width or clipped | P3 | Low | Usability |
| TC-UI-014 | Frontend | Analytics shows 'No data yet' for empty breakdown categories | Link has clicks but, e.g., no referrer data recorded | 1) Load analytics.html | - | Referrers panel shows 'No data yet' instead of a blank/broken chart | P2 | Medium | Usability |
| TC-UI-015 | Frontend | New API key is shown once with a copy reminder, then never again | API key just created | 1) Create a key 2) Refresh the page | - | Plaintext key visible in an alert immediately after creation; gone after refresh (list only shows prefix) | P1 | High | Security |
| TC-UI-016 | Frontend | Protected-link page shows inline error without a full page reload | Wrong password submitted on protected.html | 1) Enter wrong password 2) Submit | password: wrong | Inline error message appears below the form; page does not navigate away | P2 | Medium | Usability |
| TC-UI-017 | Frontend | Expired access token triggers a silent refresh, not an abrupt logout | Access token expired but refresh token still valid | 1) Perform any authenticated action after token expiry | - | App silently exchanges the refresh token and retries the original request without user-visible interruption | P1 | High | Functional |
| TC-UI-018 | Frontend | Failed silent refresh redirects to login | Both access and refresh tokens expired/invalid | 1) Perform an authenticated action | - | App clears stored tokens and redirects to login.html | P1 | High | Functional |
| TC-UI-019 | Frontend | Page title and rendered text use correct UTF-8 characters (no mojibake) | Any page load | 1) Load each frontend page 2) Inspect title bar and body text | - | All text renders as intended plain characters; no garbled byte-sequence artifacts (regression check) | P1 | High | Regression |
| TC-UI-020 | Frontend | Browser back/forward between pages preserves usable state | Navigate index -> analytics -> back | 1) Click into analytics from a link 2) Click browser Back | - | Returns to index.html with the list intact, no broken/blank state | P3 | Low | Usability |
| TC-SEC-001 | Security | SQL injection attempt via the list search parameter | Authenticated | 1) GET /api/v1/urls?search=' OR '1'='1 | search: ' OR '1'='1 | 200; treated as a literal search string (parameterized query), no data leak, no error | P0 | Critical | Security |
| TC-SEC-002 | Security | Stored XSS payload rejected at input, not sanitized-on-output | Authenticated | 1) POST create with a script-tag payload in url | url with <script> | 400 at input validation — payload never reaches storage in the first place | P0 | Critical | Security |
| TC-SEC-003 | Security | Error responses JSON-encode any reflected user input | Authenticated | 1) Trigger a validation error with special characters in a field | input containing <, >, " | Error message is valid JSON; special characters properly escaped, not raw-HTML-renderable | P1 | High | Security |
| TC-SEC-004 | Security | IDOR on update/delete endpoints | User A owns a link; User B knows/guesses its id | 1) User B calls PATCH and DELETE on User A's link id | - | 403 Forbidden on both | P0 | Critical | Security |
| TC-SEC-005 | Security | IDOR on analytics endpoint | Same setup as above, via shortCode | 1) User B requests analytics for User A's shortCode | - | 403 Forbidden | P0 | Critical | Security |
| TC-SEC-006 | Security | Redirect cannot be manipulated into an open redirect beyond the stored URL | Active link | 1) Attempt to inject additional redirect targets via headers/params on the GET | Various header/param injection attempts | 302 Location always equals exactly the stored originalUrl | P1 | High | Security |
| TC-SEC-007 | Security | CORS blocks requests from a disallowed origin | Origin not in CORS_ALLOWED_ORIGINS | 1) Send a cross-origin XHR from a disallowed origin | Origin: https://evil.example | No Access-Control-Allow-Origin for that origin; browser blocks the response from being read | P1 | High | Security |
| TC-SEC-008 | Security | CORS allows a configured origin correctly | Origin matches CORS_ALLOWED_ORIGINS | 1) Send cross-origin request from the allowed origin | Origin: http://localhost | Response includes correct ACAO header; request succeeds | P2 | Medium | Security |
| TC-SEC-009 | Security | Clickjacking protection header present | Any response | 1) Inspect response headers | - | X-Frame-Options: DENY present on all responses | P2 | Medium | Security |
| TC-SEC-010 | Security | No session cookie is ever set (validates the CSRF-exempt design) | Any authenticated flow | 1) Login 2) Inspect Set-Cookie headers across all responses | - | No session cookie set at any point; auth is bearer-token/header only, confirming CSRF genuinely does not apply here | P1 | High | Security |
| TC-SEC-011 | Security | JWT with alg=none is rejected | - | 1) Craft a JWT with 'none' algorithm and no signature 2) Send as Bearer token | Forged unsigned JWT | 401 — token parsing/verification rejects it | P0 | Critical | Security |
| TC-SEC-012 | Security | Plaintext passwords never appear in application logs | Register and login performed | 1) Grep structured JSON logs for the plaintext password used | - | No occurrence of the raw password anywhere in logs | P0 | Critical | Security |
| TC-SEC-013 | Security | API key plaintext never persisted, only its hash | Key created | 1) Inspect api_keys table | - | Only key_hash (SHA-256) and key_prefix stored; full plaintext absent from DB | P0 | Critical | Security |
| TC-SEC-014 | Security | Authorization/X-API-Key header values are not echoed in error responses or logs verbatim | Invalid token supplied | 1) Call with an invalid Bearer token 2) Inspect error response and logs | - | Header value itself is not reflected back to the client or written to logs in full | P1 | High | Security |
| TC-SEC-015 | Security | No dedicated login-attempt lockout beyond general rate limiting | Repeated wrong-password attempts | 1) Attempt many wrong-password logins for the same username | - | Governed only by the general 100/60s API rate limit, no account-specific lockout — flag as a gap vs typical banking-grade lockout policy | P2 | Medium | Security |
| TC-SEC-016 | Security | Directory-traversal-style short codes don't expose the filesystem via Nginx | - | 1) Request paths like /..%2f..%2f or /../../etc/passwd through the public entry point | - | Nginx/backend return 404/400, no file content served | P1 | High | Security |
| TC-SEC-017 | Security | TLS termination is a deployment responsibility, not enforced by the app itself | Documentation review | 1) Confirm HTTPS is expected to be terminated at Nginx/load balancer in production | - | Documented in deployment checklist; flagged as a pre-production requirement, not a runtime app behavior | P2 | Medium | Security |
| TC-PERF-001 | Performance | Redirect p95 latency under load | Stack running with representative data | 1) Run the provided k6 script (perf/redirect-load-test.js) against a seeded short code | 1000 req/s ramp profile | p95 latency < 100ms per the stated NFR | P1 | High | Performance |
| TC-PERF-002 | Performance | Redirect sustains 1000 requests/second | Same as above | 1) Run k6 steady-state stage at 1000 req/s for 2 minutes | - | Error rate < 1%, no unbounded latency growth | P1 | High | Performance |
| TC-PERF-003 | Performance | Cache hit ratio stays high under repeated-code traffic | Popular short code hit repeatedly | 1) Drive repeated GETs against the same code 2) Check Grafana cache-hit-ratio panel | - | Hit ratio trends toward >90% after cache warm-up | P2 | Medium | Performance |
| TC-PERF-004 | Performance | List endpoint remains responsive with a large per-user dataset | User seeded with 10,000+ links | 1) GET /api/v1/urls with pagination | page=0,size=20 | Response time remains low; no full-table scan cost (pagination + indexes doing their job) | P2 | Medium | Performance |
| TC-PERF-005 | Performance | Analytics aggregation on a high-click-volume link | Link seeded with 100,000+ click rows | 1) GET analytics for that link | - | Query completes without timeout; response time within an acceptable bound | P2 | Medium | Performance |
| TC-PERF-006 | Performance | Kafka consumer lag stays bounded under a click burst | Burst of clicks fired in a short window | 1) Fire 5,000 clicks rapidly 2) Monitor consumer lag until drained | - | Lag drains within a reasonable time, no permanent unbounded backlog | P2 | Medium | Performance |
| TC-PERF-007 | Performance | HikariCP connection pool does not exhaust under concurrent load | High concurrent authenticated request volume | 1) Drive concurrent load exceeding the 20-connection pool size 2) Monitor Hikari metrics | - | Requests queue briefly rather than failing outright; no connection-leak growth over time | P2 | Medium | Performance |
| TC-PERF-008 | Performance | Redis outage degrades gracefully rather than failing hard | Redis stopped mid-run | 1) Stop Redis 2) Continue issuing redirect and rate-limited requests | - | Higher latency (DB fallback) but requests still succeed; no cascading 5xx storm | P1 | High | Performance |
| TC-COMPAT-001 | Compatibility | Frontend functions correctly on latest Chrome | Desktop Chrome | 1) Complete the shorten -> list -> analytics flow | - | No layout breakage, all interactions function as on other browsers | P2 | Medium | Compatibility |
| TC-COMPAT-002 | Compatibility | Frontend functions correctly on latest Firefox | Desktop Firefox | 1) Complete the same flow | - | Consistent behavior with Chrome baseline | P2 | Medium | Compatibility |
| TC-COMPAT-003 | Compatibility | Frontend functions correctly on latest Edge | Desktop Edge | 1) Complete the same flow | - | Consistent behavior with Chrome baseline | P3 | Low | Compatibility |
| TC-COMPAT-004 | Compatibility | Frontend functions correctly on Safari (macOS/iOS) | Safari desktop and iOS | 1) Complete the same flow, paying attention to datetime-local input support | - | Consistent behavior; note any Safari-specific input quirks | P2 | Medium | Compatibility |
| TC-COMPAT-005 | Compatibility | Responsive layout on a mobile viewport (<576px) | Any modern mobile browser or emulator | 1) Load index.html at a narrow viewport width | - | Bootstrap grid collapses to single-column; all controls remain reachable and usable | P2 | Medium | Compatibility |
| TC-COMPAT-006 | Compatibility | Responsive layout on a tablet viewport (768-1024px) | Tablet emulator | 1) Load index.html and analytics.html | - | Layout adapts without horizontal scrolling or overlap | P3 | Low | Compatibility |
| TC-COMPAT-007 | Compatibility | API behaves identically regardless of client tool | - | 1) Issue the same request via curl, Postman, and the frontend fetch() | - | Identical status codes and response bodies across all three | P3 | Low | Compatibility |
| TC-COMPAT-008 | Compatibility | Frontend under OS-level dark mode | OS dark mode enabled, browser respecting it | 1) Load any frontend page | - | App has no dedicated dark theme; verify text remains legible against the light-only design rather than becoming unreadable — flag as a known gap if contrast suffers | P3 | Low | Compatibility |
| TC-A11Y-001 | Accessibility | Full keyboard-only navigation of the shorten form and list actions | Keyboard only, no mouse | 1) Tab through index.html from the top | - | Logical tab order reaches every control; all actions (submit, edit, delete) operable via keyboard | P1 | High | Accessibility |
| TC-A11Y-002 | Accessibility | Visible focus indicator on every interactive element | Keyboard navigation | 1) Tab through all pages | - | A clear focus outline is visible on inputs, buttons, and links at every stop | P1 | High | Accessibility |
| TC-A11Y-003 | Accessibility | Form inputs have programmatically associated labels | Any form page | 1) Inspect with a screen reader or accessibility tree | - | Each input's accessible name matches its visible label (e.g. 'Password', 'Username') | P2 | Medium | Accessibility |
| TC-A11Y-004 | Accessibility | Text/background color contrast meets WCAG AA | All pages, both themes if applicable | 1) Run an automated contrast checker against body and muted text colors | - | Contrast ratio >= 4.5:1 for normal body text | P2 | Medium | Accessibility |
| TC-A11Y-005 | Accessibility | Success/error states are not conveyed by color alone | Toasts and inline errors | 1) Inspect toast and error-message content | - | Icon/text ('Error', 'Success', explicit message) accompanies the color, not color alone | P2 | Medium | Accessibility |
| TC-A11Y-006 | Accessibility | Edit-expiry modal traps and restores focus correctly | Modal opened via keyboard | 1) Open modal with Enter/click 2) Tab through it 3) Close it | - | Focus stays within the modal while open; returns to the triggering button on close | P2 | Medium | Accessibility |
| TC-A11Y-007 | Accessibility | Page remains usable at 200% browser zoom | Any page | 1) Zoom to 200% 2) Attempt the core flows | - | No content clipped or overlapping; all controls remain reachable | P3 | Low | Accessibility |
| TC-A11Y-008 | Accessibility | Icon-only or ambiguous controls have accessible names | - | 1) Inspect any icon-only buttons for aria-label/title | - | Every control has a screen-reader-announceable name describing its action | P3 | Low | Accessibility |
| TC-ERR-001 | Resilience | Postgres unavailable at backend startup | Postgres stopped before backend starts | 1) Start backend with Postgres down | - | Backend fails to become ready; container health check fails and restarts per policy; clear error in logs | P1 | High | Error Handling |
| TC-ERR-002 | Resilience | Postgres becomes unavailable mid-runtime | App running normally, then Postgres is stopped | 1) Stop Postgres 2) Issue a request requiring DB access | - | Request fails with 500; /actuator/health reflects DOWN for the db component | P1 | High | Error Handling |
| TC-ERR-003 | Resilience | Redis unavailable degrades but does not break core flows | Redis stopped | 1) Stop Redis 2) Perform redirect and create operations | - | Redirect falls back to direct DB reads; rate limiter fails open; app stays functional | P1 | High | Error Handling |
| TC-ERR-004 | Resilience | Kafka unavailable does not block the redirect response | Kafka stopped | 1) Stop Kafka 2) GET a redirect | - | 302 still returned promptly; click-publish failure is logged, not surfaced to the client | P1 | High | Error Handling |
| TC-ERR-005 | Resilience | Repeated consumer failures route the message to the dead-letter topic | Simulated processing exception | 1) Force the analytics consumer to throw on a message 2) Observe retries then DLQ | - | Message retried per the configured backoff, then published to 'dead-letter' after attempts are exhausted | P2 | Medium | Error Handling |
| TC-ERR-006 | Resilience | Malformed Kafka message does not crash the listener container | A non-deserializable message is produced to url-clicked | 1) Inject a malformed message | - | Consumer error-handles it without stopping consumption of subsequent valid messages | P2 | Medium | Error Handling |
| TC-ERR-007 | Resilience | Backend restart mid-request is recoverable by the client | Backend container restarted | 1) Fire a request as the backend restarts 2) Retry after it's back | - | First call may fail with a connection error; retry against the restarted instance succeeds | P3 | Low | Error Handling |
| TC-ERR-008 | Resilience | Unhandled exception never leaks a stack trace to the client | Force an unexpected exception in a controller | 1) Trigger the condition 2) Inspect the client response | - | 500 with a generic message; full stack trace only in server logs, not the response body | P1 | High | Security |
| TC-ERR-009 | Resilience | Missing critical env var falls back to a documented dev default, not a silent insecure state | JWT_SECRET unset | 1) Start the app with JWT_SECRET unset | - | App starts using the documented demo-only default secret; deployment docs flag this must be overridden for real use — verify this tradeoff is acceptable for the target environment | P2 | Medium | Error Handling |
| TC-ERR-010 | Resilience | Correlation ID present even on error responses | Any failing request | 1) Trigger any 4xx/5xx 2) Inspect response body and X-Request-Id header | - | correlationId in the JSON body matches the X-Request-Id response header, aiding log correlation | P2 | Medium | Error Handling |
| TC-PROD-001 | Production | Overall health endpoint reports UP | Stack deployed | 1) GET /actuator/health | - | 200; status=UP with db, redis, kafka, diskSpace components all UP | P0 | Critical | Smoke |
| TC-PROD-002 | Production | Liveness probe responds correctly | Stack deployed | 1) GET /actuator/health/liveness | - | 200; status=UP | P0 | Critical | Smoke |
| TC-PROD-003 | Production | Readiness probe responds correctly | Stack deployed | 1) GET /actuator/health/readiness | - | 200; status=UP | P0 | Critical | Smoke |
| TC-PROD-004 | Production | Prometheus successfully scrapes backend metrics | Prometheus + backend running | 1) Open Prometheus targets page 2) Check url-shortener-backend target | - | Target shown as UP with a recent successful scrape | P1 | High | Smoke |
| TC-PROD-005 | Production | Grafana dashboard renders with live data | Some traffic generated post-deploy | 1) Open the provisioned Grafana dashboard | - | Panels (request rate, latency, cache hit ratio, JVM, Hikari) show non-empty, sensible data | P2 | Medium | Smoke |
| TC-PROD-006 | Production | Swagger UI is reachable and lists all endpoints | Stack deployed | 1) Open /swagger-ui.html | - | Page loads; auth, urls, api-keys endpoint groups all present and documented | P2 | Medium | Smoke |
| TC-PROD-007 | Production | Full happy-path smoke chain succeeds end to end | Freshly deployed environment | 1) Register 2) Login 3) Create short URL 4) Redirect 5) Check analytics | - | Every step succeeds with expected status codes in sequence | P0 | Critical | Smoke |
| TC-PROD-008 | Production | Flyway migration history is fully applied with no pending/failed entries | Post-deployment | 1) Query flyway_schema_history table | - | All 5 migrations present with success=true, no gaps | P1 | High | Smoke |
| TC-PROD-009 | Production | Logs are structured JSON on the docker/prod profile | Post-deployment | 1) Inspect backend container stdout | - | Each log line is valid JSON with timestamp, level, message, correlation fields — parseable by log aggregators | P2 | Medium | Smoke |
| TC-PROD-010 | Production | Correlation ID present on every response | Post-deployment | 1) Issue any request 2) Inspect X-Request-Id response header | - | Header present on every response, unique per request unless propagated from an inbound value | P2 | Medium | Smoke |

---

## 1. Requirement Coverage Matrix

| Capability | Module tag | Cases | Deepest test types applied |
|---|---|---|---|
| Register / Login / Refresh (Auth) | Auth | 26 | Security, Boundary, Functional |
| Shorten URL (create) | Shorten URL | 31 | Security, Boundary, API, Functional |
| Redirect + click tracking | Redirect | 22 | Security, Performance, Functional |
| Password-protected links (secure sharing) | Secure Sharing | 15 | Security, Functional |
| Click analytics | Analytics | 16 | Functional, Security |
| List / search / filter / sort | List URLs | 20 | Security, Boundary, Functional |
| Soft delete | Delete | 11 | Functional, Database, Audit |
| Update / clear expiry | Update Expiry | 11 | Functional, Audit |
| API keys (create/list/revoke) | API Keys | 12 | Security, Functional |
| Distributed rate limiting | Rate Limiting | 6 | Security, Error Handling |
| Role-based access control | RBAC | 5 | Security |
| Frontend UI behavior | Frontend | 20 | Usability, Regression |
| Cross-cutting security controls | Security | 17 | Security |
| NFR: latency / throughput | Performance | 8 | Performance |
| Browser / device / OS compatibility | Compatibility | 8 | Compatibility |
| Accessibility (WCAG-aligned) | Accessibility | 8 | Accessibility |
| Dependency-outage resilience | Resilience | 10 | Error Handling |
| Post-deployment validation | Production | 10 | Smoke |

---

## 2. Missing Requirements

- No specification for custom/vanity short codes (only system-generated codes are defined).
- No password-reset or email-verification flow for user accounts.
- No stated retention/archival policy for soft-deleted links or click history.
- No specified maximum links-per-user quota.
- No documented bulk-operations requirement (bulk delete, bulk export of links or analytics).

## 3. Ambiguous Requirements

- Whether expiryDate may be set in the past at creation/update time (currently accepted, immediately expires the link).
- Whether verify-password should succeed for a non-protected link when a password is supplied (currently always succeeds).
- Whether query parameters appended to a short link should be forwarded to the destination (currently dropped).
- Whether duplicate original URLs from the same user should be deduplicated or always produce a new short code (currently always new).
- Country analytics: acceptable as permanently unresolved (NoOpGeoIpService) for this release, or a blocking gap?

## 4. Risk Areas

- No dedicated account lockout after repeated failed logins — relies solely on the general rate limiter.
- Rate limiting is per-IP/per-key; a distributed brute force across many IPs is not mitigated at the app layer.
- Cache-based eventual consistency on passive TTL expiry (as opposed to explicit delete/update) creates brief stale-read windows.
- Dead-letter Kafka messages require manual/ops-level inspection — no automated alerting is defined in this build.
- bcrypt's 72-byte input truncation for very long multi-byte passwords is a subtle correctness edge with no explicit user-facing warning.

## 5. Assumptions

- USER and ADMIN are the only roles in scope; no read-only/manager roles exist in the current build.
- Test environment is the Docker Compose stack (Postgres, Redis, Kafka, backend, Nginx) per docs/DEPLOYMENT.md.
- Demo accounts (demo/Demo@12345, admin/Admin@12345) are available and not treated as secrets in test environments.
- "Enterprise banking experience" rigor is applied to security/authorization depth; PCI/SOX-specific controls are out of scope unless separately requested.

---

## 6. Test Execution Priority

- Wave 1 (P0, pre-release gate): Auth, RBAC, IDOR/security, core create/redirect happy+negative paths, password-protected link security.
- Wave 2 (P1): Full CRUD lifecycle (list/delete/expiry), API keys, rate limiting, analytics correctness, resilience/error handling.
- Wave 3 (P2/P3): UI polish, accessibility, compatibility matrix, exploratory and corner-case sweeps.

## 7. Test Data Requirements

- Seeded demo/admin accounts plus at least 2 fresh USER accounts for ownership/IDOR testing.
- A pre-populated link set per status: active, expired, soft-deleted, password-protected.
- A link with a high click volume (1,000+ rows) for analytics performance checks.
- A set of representative User-Agent strings (Chrome/Firefox/Safari desktop, Android/iOS mobile, a known bot UA).
- Boundary-value strings for every validated field (username, password, url, expiryDate) at min/min-1/max/max+1.

## 8. Automation Candidates

- All Auth, Shorten URL, Redirect, and RBAC functional/negative cases — stable contracts, high regression value (already partially covered by the project's own JUnit/Testcontainers suite).
- Rate-limiting boundary cases — deterministic given a controlled clock/counter reset.
- The full happy-path smoke chain (register->login->create->redirect->analytics) as a synthetic post-deploy check.
- NOT recommended for automation: modal focus-trap and visual chart-proportion checks — better suited to manual/exploratory review.

## 9. Regression Suite Recommendation

Run this set after every release, no exceptions:

- TC-AUTH-001, 015-017, 023-026
- TC-CRT-001, 007, 010, 023, 030
- TC-RDR-001-004, 006, 011, 019
- TC-PWD-001-003, 007, 013
- TC-ANL-001, 002, 010
- TC-LST-001, 008-011, 017-019
- TC-DEL-001, 002, 009
- TC-EXP-001, 002, 008
- TC-KEY-001-004
- TC-RTL-001, 004, 005
- TC-RBAC-001-004
- TC-SEC-001, 002, 004, 005, 011-013
- TC-PROD-001, 003, 007

---

## 10. Production Smoke Checklist

Run in order immediately after every deployment. All items must pass before the deploy is considered good.

1. GET /actuator/health returns 200 with status UP and all components (db, redis, kafka, diskSpace) UP.
2. GET /actuator/health/liveness and /actuator/health/readiness both return 200.
3. Login with the demo account succeeds and returns a valid token pair.
4. Create a short URL, then GET its redirect and confirm a 302 to the correct destination.
5. Analytics for that link reaches totalClicks >= 1 within 20 seconds of the click.
6. Swagger UI loads at /swagger-ui.html with all endpoint groups visible.
7. Prometheus target url-shortener-backend shows state UP on the /targets page.
8. Grafana's provisioned dashboard loads and at least the request-rate panel shows data.
9. flyway_schema_history shows all 5 migrations applied with no failed entries.
10. Backend log output is valid structured JSON (not plaintext) under the docker/prod profile.
