import type { FullConfig } from '@playwright/test';
import { waitForAppReady } from './utils/healthCheck';
import { createLogger } from './utils/logger';

const logger = createLogger('global-setup');

/**
 * Runs once before the whole suite, in a separate process from any test worker.
 * Fails the entire run fast (with one clear message) instead of letting 200+ tests
 * each independently time out against a stack that never came up.
 */
export default async function globalSetup(_config: FullConfig): Promise<void> {
  logger.info('Waiting for the application to become ready...');
  await waitForAppReady();
}
