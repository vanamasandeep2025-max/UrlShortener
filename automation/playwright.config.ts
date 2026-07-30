import { defineConfig, devices } from '@playwright/test';
import { env } from './config/environment';

/**
 * Single config for the whole framework. Project list intentionally separates the
 * "real browser matrix" (chromium/firefox/webkit + two mobile emulations) from the
 * "api" project: API-only tests don't need a browser context at all, so giving them
 * their own project keeps `npx playwright test --project=api` fast in CI.
 */
export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  timeout: 30_000,
  expect: {
    timeout: 8_000,
  },

  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? env.retries : 0,
  workers: process.env.CI ? env.workers : undefined,

  globalSetup: require.resolve('./global-setup'),

  reporter: [
    ['list'],
    ['html', { outputFolder: 'reports/html-report', open: 'never' }],
    ['junit', { outputFile: 'reports/junit/results.xml' }],
    ['allure-playwright', { resultsDir: 'reports/allure-results' }],
  ],

  use: {
    baseURL: env.appBaseUrl,
    headless: env.headless,
    trace: env.trace as 'on' | 'off' | 'retain-on-failure' | 'on-first-retry',
    video: env.video as 'on' | 'off' | 'retain-on-failure' | 'on-first-retry',
    screenshot: env.screenshot as 'on' | 'off' | 'only-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 15_000,
    ignoreHTTPSErrors: true,
    // SLOW_MO_MS delays every Playwright action by that many ms - for a test engineer
    // watching a headed run manually, not for normal execution (0 by default, unset).
    launchOptions: {
      slowMo: Number(process.env.SLOW_MO_MS ?? 0),
    },
  },

  projects: [
    {
      name: 'api',
      testDir: './tests/api',
      use: {
        baseURL: env.apiBaseUrl,
      },
    },
    {
      name: 'chromium',
      testDir: './tests',
      testIgnore: ['**/api/**'],
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      testDir: './tests',
      testIgnore: ['**/api/**'],
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      testDir: './tests',
      testIgnore: ['**/api/**'],
      use: { ...devices['Desktop Safari'] },
    },
    {
      name: 'mobile-chrome',
      testDir: './tests',
      testIgnore: ['**/api/**'],
      use: { ...devices['Pixel 7'] },
    },
    {
      name: 'mobile-safari',
      testDir: './tests',
      testIgnore: ['**/api/**'],
      use: { ...devices['iPhone 14'] },
    },
  ],
});
