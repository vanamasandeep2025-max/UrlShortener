import * as dotenv from 'dotenv';
import * as path from 'path';

dotenv.config({ path: path.resolve(__dirname, '../.env') });

function required(name: string, fallback?: string): string {
  const value = process.env[name] ?? fallback;
  if (value === undefined) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function bool(name: string, fallback: boolean): boolean {
  const raw = process.env[name];
  if (raw === undefined) return fallback;
  return raw.toLowerCase() === 'true';
}

function num(name: string, fallback: number): number {
  const raw = process.env[name];
  if (raw === undefined) return fallback;
  const parsed = Number(raw);
  return Number.isNaN(parsed) ? fallback : parsed;
}

/**
 * Single source of truth for all environment-derived config. Every other file
 * (playwright.config.ts, fixtures, page objects, api clients) reads from here
 * instead of touching process.env directly, so environments are swapped by
 * changing .env alone.
 */
export const env = {
  name: required('TEST_ENV', 'docker'),
  appBaseUrl: required('APP_BASE_URL', 'http://localhost'),
  // Trailing slash is load-bearing: ApiClient/ApiRoutes use relative (no leading-slash)
  // paths so they append onto this base per standard URL-resolution rules. A leading
  // slash in the path (e.g. "/urls") would instead reset to the origin root and silently
  // drop "/api/v1" - see automation/README.md "Known Limitations" for the real bug this
  // caused before the convention was fixed.
  apiBaseUrl: required('API_BASE_URL', 'http://localhost/api/v1/'),

  demoUsername: required('DEMO_USERNAME', 'demo'),
  demoPassword: required('DEMO_PASSWORD', 'Demo@12345'),
  adminUsername: required('ADMIN_USERNAME', 'admin'),
  adminPassword: required('ADMIN_PASSWORD', 'Admin@12345'),

  db: {
    host: required('DB_HOST', 'localhost'),
    port: num('DB_PORT', 5432),
    database: required('DB_NAME', 'urlshortener'),
    user: required('DB_USER', 'urlshortener'),
    password: required('DB_PASSWORD', 'urlshortener'),
  },

  headless: bool('HEADLESS', true),
  workers: num('WORKERS', 4),
  retries: num('RETRIES', 1),
  trace: required('TRACE', 'retain-on-failure'),
  video: required('VIDEO', 'retain-on-failure'),
  screenshot: required('SCREENSHOT', 'only-on-failure'),
} as const;
