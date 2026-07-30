import { createLogger } from './logger';

const logger = createLogger('retry');

export interface RetryOptions {
  attempts?: number;
  delayMs?: number;
  backoffFactor?: number;
  label?: string;
}

/**
 * Bounded retry for genuinely eventually-consistent operations (Kafka -> consumer ->
 * Postgres lag on the analytics/click-count path is a few seconds by design - see
 * docs/ARCHITECTURE.md "End-to-End Request Lifecycle", step 18). NOT a substitute for
 * Playwright's own auto-waiting locators/assertions - this exists only for the handful
 * of places a test polls a value that lives outside the DOM (a REST response, a DB row).
 */
export async function retryUntil<T>(
  fn: () => Promise<T>,
  predicate: (result: T) => boolean,
  options: RetryOptions = {}
): Promise<T> {
  const { attempts = 5, delayMs = 1000, backoffFactor = 1.5, label = 'operation' } = options;

  let lastResult: T | undefined;
  let currentDelay = delayMs;

  for (let attempt = 1; attempt <= attempts; attempt++) {
    lastResult = await fn();
    if (predicate(lastResult)) {
      return lastResult;
    }
    if (attempt < attempts) {
      logger.debug(`"${label}" not yet satisfied, retrying`, { attempt, nextDelayMs: currentDelay });
      await sleep(currentDelay);
      currentDelay = Math.round(currentDelay * backoffFactor);
    }
  }

  throw new Error(`retryUntil: "${label}" did not satisfy predicate after ${attempts} attempts`);
}

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
