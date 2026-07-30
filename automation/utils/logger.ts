/* eslint-disable no-console */
type LogLevel = 'INFO' | 'WARN' | 'ERROR' | 'DEBUG';

function line(level: LogLevel, scope: string, message: string, meta?: unknown): string {
  const timestamp = new Date().toISOString();
  const base = `[${timestamp}] [${level}] [${scope}] ${message}`;
  return meta === undefined ? base : `${base} ${JSON.stringify(meta)}`;
}

/**
 * Structured console logger, one instance per logical scope (test name, fixture, api
 * client). Deliberately not a file-writing logger — Playwright's own reporters already
 * capture stdout per test into the HTML/Allure report, so duplicating that to disk would
 * just be a second copy of the same data to keep in sync.
 */
export class Logger {
  constructor(private readonly scope: string) {}

  info(message: string, meta?: unknown): void {
    console.log(line('INFO', this.scope, message, meta));
  }

  warn(message: string, meta?: unknown): void {
    console.warn(line('WARN', this.scope, message, meta));
  }

  error(message: string, meta?: unknown): void {
    console.error(line('ERROR', this.scope, message, meta));
  }

  debug(message: string, meta?: unknown): void {
    if (process.env.DEBUG_LOGS === 'true') {
      console.log(line('DEBUG', this.scope, message, meta));
    }
  }

  testStart(testName: string): void {
    this.info(`START  ${testName}`);
  }

  testEnd(testName: string, status: string, durationMs: number): void {
    this.info(`END    ${testName} -> ${status} (${durationMs}ms)`);
  }

  retry(testName: string, attempt: number): void {
    this.warn(`RETRY  ${testName} -> attempt ${attempt}`);
  }
}

export function createLogger(scope: string): Logger {
  return new Logger(scope);
}
