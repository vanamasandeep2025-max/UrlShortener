import * as http from 'node:http';
import type { AddressInfo } from 'node:net';
import { test, expect } from '../../fixtures/base';
import { DataGenerator } from '../../utils/dataGenerator';
import { retryUntil } from '../../utils/retry';
import { Db } from '../../helpers/db';
import { AnalyticsPage } from '../../pages/AnalyticsPage';
import { ProtectedPage } from '../../pages/ProtectedPage';

/**
 * A real, tiny HTTP server bound to 127.0.0.1 on an ephemeral port, for tests that need
 * a genuinely resolvable, genuinely reachable redirect *destination* without depending on
 * the real internet (127.0.0.1 always resolves; a fake/unresolvable TLD does not - see
 * TC-E2E-006, which exists specifically because a manual test surfaced that Chromium's own
 * DNS pre-resolution for cross-origin redirect targets can fail a navigation before
 * Playwright's page.route() interception ever gets a chance to run).
 */
async function startMockDestinationServer(bodyText: string): Promise<{ url: string; close: () => Promise<void> }> {
  const server = http.createServer((_req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/html' });
    res.end(`<!doctype html><html><body>${bodyText}</body></html>`);
  });
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;
  return {
    url: `http://127.0.0.1:${port}/landing-page`,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  };
}

/**
 * The platform's actual hottest path end-to-end: create -> redirect -> Kafka ->
 * consumer -> Postgres -> analytics. This is deliberately the most heavily-asserted
 * file in the suite - it is the exact flow that shipped two real production bugs this
 * session (a dropped url_clicks insert via clearAutomatically=true, and a Redis cache
 * ClassCastException on the analytics read path). See docs/ARCHITECTURE.md "End-to-End
 * Request Lifecycle" and "Design Decisions" for the incidents these assertions guard.
 */
test.describe('E2E - Shorten, redirect, and analytics', () => {
  test.afterAll(async () => {
    await Db.close();
  });

  /**
   * Priority: P0 | Preconditions: none beyond a valid demo session
   * Test Data: a freshly generated long URL
   * Expected: 302 redirect to the original URL; click_count and url_clicks stay in
   * lockstep; analytics reflects the click within a few seconds (Kafka consumer lag)
   */
  test('TC-E2E-001: a click on a short link redirects, is persisted, and shows up in analytics @smoke @regression', async ({
    apiClient,
    demoTokens,
    page,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const originalUrl = DataGenerator.longUrl();
    const created = await client.createUrlOrThrow({ url: originalUrl });

    // --- Redirect: the response the real user actually waits for ---
    const redirectResponse = await client.redirect(created.shortCode, false);
    expect(redirectResponse.status()).toBe(302);
    expect(redirectResponse.headers()['location']).toBe(originalUrl);

    // --- DB: click_count and url_clicks must never drift apart (see UrlClickIngestionService) ---
    const urlRow = await retryUntil(
      () => Db.findUrlByShortCode(created.shortCode),
      (row) => row !== null && Number(row.click_count) >= 1,
      { label: 'click_count increments after redirect', attempts: 8, delayMs: 1000 }
    );
    expect(urlRow).not.toBeNull();
    const clickRowCount = await Db.countUrlClicks(urlRow!.id);
    expect(clickRowCount).toBe(Number(urlRow!.click_count));
    expect(clickRowCount).toBeGreaterThanOrEqual(1);

    const latestClick = await Db.latestUrlClick(urlRow!.id);
    expect(latestClick).not.toBeNull();
    expect(latestClick!.device_type).toBeTruthy();

    // --- Analytics: same click, visible through the real read path (cache write + read) ---
    const analytics = await retryUntil(
      () => client.getAnalyticsOrThrow(created.shortCode),
      (a) => a.totalClicks >= 1,
      { label: 'analytics reflects the click', attempts: 8, delayMs: 1000 }
    );
    expect(analytics.totalClicks).toBeGreaterThanOrEqual(1);
    expect(analytics.uniqueVisitors).toBeGreaterThanOrEqual(1);
    expect(analytics.dailyClicks.length).toBeGreaterThanOrEqual(1);

    // --- Analytics cache read path: a second call must succeed identically (regression
    // guard for the ClassCastException fixed in CacheConfig - see docs/ARCHITECTURE.md) ---
    const secondRead = await client.getAnalyticsOrThrow(created.shortCode);
    expect(secondRead).toEqual(analytics);

    // --- UI: the analytics page renders the same numbers a real owner would see ---
    const analyticsPage = new AnalyticsPage(page);
    await page.goto('/login.html');
    await page.evaluate(
      ({ access, refresh }) => {
        window.localStorage.setItem('usp_access_token', access);
        window.localStorage.setItem('usp_refresh_token', refresh);
      },
      { access: demoTokens.accessToken, refresh: demoTokens.refreshToken }
    );
    await analyticsPage.open(created.shortCode);
    await expect(analyticsPage.totalClicks).toHaveText(String(analytics.totalClicks));
  });

  test('TC-E2E-002: multiple clicks from the same client are counted as one unique visitor @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const created = await client.createUrlOrThrow({ url: DataGenerator.longUrl() });

    await client.redirect(created.shortCode, false);
    await client.redirect(created.shortCode, false);
    await client.redirect(created.shortCode, false);

    // Verified against the DB (source of truth for persistence), not the analytics API:
    // the "analytics" cache is only skipped while totalClicks==0 (see CacheConfig /
    // docs/ARCHITECTURE.md "Caching Architecture"). Once the FIRST click makes it non-zero,
    // it's cached for the full 60s TTL with no invalidation on subsequent clicks - confirmed
    // live (3 rapid clicks, DB click_count=3 immediately, but the analytics endpoint kept
    // returning totalClicks=1 for the remainder of that TTL window). That's a real,
    // documented cache-staleness gap (automation/README.md "Known Limitations"), not
    // something this test should wait 60s to route around.
    const urlRow = await retryUntil(
      () => Db.findUrlByShortCode(created.shortCode),
      (row) => row !== null && Number(row.click_count) >= 3,
      { label: '3 clicks persisted', attempts: 10, delayMs: 1000 }
    );
    expect(Number(urlRow!.click_count)).toBe(3);
    expect(await Db.countUrlClicks(urlRow!.id)).toBe(3);

    // ip_hash-based unique-visitor counting: same test runner IP -> exactly one unique
    // visitor. Queried directly since it's the same DB-vs-cache staleness concern above.
    const distinctVisitors = await Db.query<{ count: string }>(
      'SELECT count(DISTINCT ip_hash)::text AS count FROM url_clicks WHERE url_id = $1',
      [urlRow!.id]
    );
    expect(Number(distinctVisitors.rows[0].count)).toBe(1);
  });

  test('TC-E2E-003: an expired link returns 410 Gone and is never counted as a click @regression', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const created = await client.createUrlOrThrow({
      url: DataGenerator.longUrl(),
      expiryDate: DataGenerator.isoDateOffsetDays(-1),
    });

    const redirectResponse = await client.redirect(created.shortCode, false);
    expect(redirectResponse.status()).toBe(410);

    const urlRow = await Db.findUrlByShortCode(created.shortCode);
    expect(Number(urlRow!.click_count)).toBe(0);
  });

  test('TC-E2E-004: redirecting to a non-existent short code returns 404 @regression', async ({ apiClient }) => {
    const response = await apiClient.redirect(DataGenerator.randomShortCode(), false);
    expect(response.status()).toBe(404);
  });

  test('TC-E2E-005: a password-protected link is not tracked as a click until the correct password is verified @regression', async ({
    apiClient,
    demoTokens,
    page,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const originalUrl = DataGenerator.longUrl();
    const password = 'LinkSecret123!';
    const created = await client.createUrlOrThrow({ url: originalUrl, password });

    // Visiting the bare short link redirects to the password gate, not the destination -
    // and this must NOT count as a click (see RedirectController step 10 in ARCHITECTURE.md).
    const redirectResponse = await client.redirect(created.shortCode, false);
    expect(redirectResponse.status()).toBe(302);
    expect(redirectResponse.headers()['location']).toContain('/protected.html');

    const rowBeforeVerify = await Db.findUrlByShortCode(created.shortCode);
    expect(Number(rowBeforeVerify!.click_count)).toBe(0);

    const protectedPage = new ProtectedPage(page);
    await protectedPage.open(created.shortCode);
    await protectedPage.submitPassword('WrongPassword!');
    await expect(protectedPage.errorMessage).toBeVisible();

    // Asserting on the verify-password API response rather than the resulting browser
    // navigation: frontend/js/protected.js does `window.location.href = data.originalUrl`
    // on success, and DataGenerator.longUrl() targets a non-resolvable fake domain by
    // design (tests must never depend on reaching the real internet) - the response
    // contract is what this test is actually responsible for, not DNS resolution.
    //
    // Reading the body via page.waitForResponse().json() races Playwright's CDP body
    // retrieval against the page's own navigation lifecycle: even with the outbound
    // navigation itself aborted via page.route(originalUrl, ...), the frame can still enter
    // a provisional-navigation state early enough to invalidate CDP's response cache before
    // getResponseBody runs, intermittently throwing "No resource with given identifier
    // found". Man-in-the-middling the verify-password call instead sidesteps this
    // completely: the body is captured here, before the page's own JS (and therefore any
    // navigation it triggers) ever sees the response.
    let capturedBody: { originalUrl: string } | undefined;
    let capturedStatus: number | undefined;
    await page.route('**/verify-password', async (route) => {
      const response = await route.fetch();
      capturedStatus = response.status();
      capturedBody = await response.json();
      await route.fulfill({ response });
    });
    await page.route(originalUrl, (route) => route.abort());

    await protectedPage.submitPassword(password);
    await expect.poll(() => capturedBody !== undefined, { timeout: 8_000 }).toBe(true);
    expect(capturedStatus).toBe(200);
    expect(capturedBody!.originalUrl).toBe(originalUrl);

    // Known product gap, confirmed via UrlServiceImpl#verifyPasswordAndGetDestination: a
    // successful password verification never publishes a click event, so a real visit to
    // a protected link is invisible in analytics even after the correct password is
    // entered. Documented in automation/README.md "Known Limitations", not treated as an
    // automation defect.
    const rowAfterVerify = await Db.findUrlByShortCode(created.shortCode);
    expect(Number(rowAfterVerify!.click_count)).toBe(0);
  });

  /**
   * Priority: P0 | Maps to docs/test-cases/url-lifecycle-test-cases.md TC-RDR-023.
   * Test Data: a real HTTP server bound to 127.0.0.1 on an ephemeral port (see
   * startMockDestinationServer above)
   * Expected: a real browser tab that opens the short link actually finishes navigating
   * and lands on the destination - not just a 302/Location header inspected via API.
   *
   * Deliberately distinct from every other redirect assertion in this file (and from
   * TC-E2E-001), which all check the redirect via `apiClient.redirect()` - an HTTP
   * client reading a status code and a header, never a browser actually following it.
   * That gap was invisible until a human opened a real shortened link in a real tab and
   * watched it land on the wrong (or no) page - this test exists so that specific
   * failure mode has automated coverage going forward.
   *
   * Uses a real local server rather than page.route()-intercepting a fake/unresolvable
   * host: that was the first approach tried here, and it failed for a genuinely
   * interesting reason worth recording - Chromium performs its own DNS
   * pre-resolution/preconnect for cross-origin redirect *targets*, which can throw
   * net::ERR_NAME_NOT_RESOLVED before Playwright's page.route() interception (a CDP
   * Fetch-domain hook) ever gets a chance to handle the request. A real server on
   * 127.0.0.1 sidesteps the whole problem: genuinely resolvable, genuinely reachable,
   * still fully hermetic (no dependency on the real internet or any external site).
   */
  test('TC-E2E-006: opening a shortened link in a real browser tab actually lands on the destination page @smoke @regression', async ({
    apiClient,
    demoTokens,
    page,
  }) => {
    const destinationMarker = 'mock-destination-landed';
    const destination = await startMockDestinationServer(destinationMarker);

    try {
      const client = apiClient.withToken(demoTokens.accessToken);
      const created = await client.createUrlOrThrow({ url: destination.url });

      await page.goto(`http://localhost/${created.shortCode}`);

      // The real browser tab's own URL after following the redirect - not a header read
      // from an HTTP client - is the assertion that actually answers "did opening this
      // shortened link work".
      expect(page.url()).toBe(destination.url);
      await expect(page.locator('body')).toContainText(destinationMarker);
    } finally {
      await destination.close();
    }
  });
});
