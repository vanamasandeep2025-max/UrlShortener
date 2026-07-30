import axios from 'axios';
import { env } from '../config/environment';
import { createLogger } from './logger';

const logger = createLogger('healthCheck');

/**
 * Standalone Axios ping used only in global-setup, before any Playwright browser/
 * request-context exists. In-test HTTP assertions use ApiClient (Playwright's own
 * APIRequestContext) instead, since that integrates with tracing and the HTML/Allure
 * reports; Axios's only job here is a fast fail with a clear message if the stack
 * (docker compose up) isn't actually running, rather than 200+ tests all timing out
 * independently against a dead server.
 */
export async function waitForAppReady(timeoutMs = 60_000, intervalMs = 2_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  const healthUrl = `${env.appBaseUrl}/actuator/health`;

  while (Date.now() < deadline) {
    try {
      const response = await axios.get(healthUrl, { timeout: 3_000, validateStatus: () => true });
      if (response.status === 200 && response.data?.status === 'UP') {
        logger.info(`Backend is UP at ${healthUrl}`);
        return;
      }
      logger.debug('Backend not ready yet', { status: response.status, body: response.data });
    } catch (err) {
      logger.debug('Backend not reachable yet', { error: (err as Error).message });
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }

  throw new Error(
    `Backend at ${healthUrl} did not become ready within ${timeoutMs}ms. ` +
      `Is "docker compose up --build" running for this stack?`
  );
}
