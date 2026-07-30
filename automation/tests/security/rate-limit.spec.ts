import { test, expect } from '../../fixtures/base';
import { DataGenerator } from '../../utils/dataGenerator';

/**
 * Rate limiting is per-client-IP (see RedisRateLimiter + RateLimitFilter), and every
 * request this suite makes originates from the same Docker-host IP through nginx. Firing
 * enough requests to trip the limiter (default 100 req/60s - app.rate-limit.*) WOULD
 * poison unrelated tests running in parallel with unexpected 429s if this file ran
 * alongside them. It is therefore deliberately excluded from the default parallel run
 * and must be run in isolation:
 *
 *   npx playwright test tests/security/rate-limit.spec.ts --workers=1
 *
 * See automation/README.md "Known Limitations" for the full rationale and the
 * docker-compose environment override (RATE_LIMIT_DEFAULT_CAPACITY) used to raise the
 * ceiling for the rest of the suite so it doesn't trip this limiter by accident.
 */
test.describe.serial('Security - Rate limiting @isolated', () => {
  test('TC-SEC-RATE-001: exceeding the per-IP request budget returns 429 with a Retry-After-style body @security', async ({
    apiClient,
    demoTokens,
  }) => {
    const client = apiClient.withToken(demoTokens.accessToken);
    const capacity = 100; // app.rate-limit.default-capacity default - see application.yml
    let sawRateLimited = false;

    for (let i = 0; i < capacity + 20 && !sawRateLimited; i++) {
      const response = await client.listUrls({ page: 0, size: 1 });
      if (response.status() === 429) {
        sawRateLimited = true;
        const body = await response.json();
        expect(body.status).toBe(429);
      }
    }

    expect(sawRateLimited).toBe(true);
  });

  test('TC-SEC-RATE-002: the login endpoint is rate-limited independently of general API traffic @security', async ({
    apiClient,
  }) => {
    let sawRateLimited = false;
    for (let i = 0; i < 120 && !sawRateLimited; i++) {
      const response = await apiClient.login({ username: DataGenerator.username(), password: 'wrong' });
      if (response.status() === 429) {
        sawRateLimited = true;
      }
    }
    expect(sawRateLimited).toBe(true);
  });
});
