import { Pool, QueryResult, QueryResultRow } from 'pg';
import { env } from '../config/environment';
import { createLogger } from '../utils/logger';

const logger = createLogger('db');

/**
 * Direct Postgres access for assertions the API/UI intentionally don't expose - e.g.
 * confirming a url_clicks row actually landed after the Kafka round trip, not just that
 * urls.click_count incremented (see UrlClickIngestionService.saveAndFlush - these two
 * were previously able to drift apart; this is the check that would have caught it).
 * Read-only by convention: every method here is a SELECT. Tests must never mutate
 * state through this pool - go through the real API so the flows under test stay real.
 */
let pool: Pool | undefined;

function getPool(): Pool {
  if (!pool) {
    pool = new Pool({
      host: env.db.host,
      port: env.db.port,
      database: env.db.database,
      user: env.db.user,
      password: env.db.password,
      max: 5,
      idleTimeoutMillis: 10_000,
    });
  }
  return pool;
}

export interface UrlClickRow {
  id: string;
  url_id: string;
  event_id: string;
  clicked_at: Date;
  browser: string | null;
  os: string | null;
  device_type: string | null;
  ip_hash: string;
}

export interface UrlRow {
  id: string;
  short_code: string;
  original_url: string;
  click_count: string;
  deleted_at: Date | null;
}

export const Db = {
  async query<T extends QueryResultRow>(sql: string, params: unknown[] = []): Promise<QueryResult<T>> {
    logger.debug('query', { sql, params });
    return getPool().query<T>(sql, params);
  },

  async findUrlByShortCode(shortCode: string): Promise<UrlRow | null> {
    const result = await this.query<UrlRow>(
      'SELECT id, short_code, original_url, click_count, deleted_at FROM urls WHERE short_code = $1',
      [shortCode]
    );
    return result.rows[0] ?? null;
  },

  async countUrlClicks(urlId: string): Promise<number> {
    const result = await this.query<{ count: string }>(
      'SELECT count(*)::text AS count FROM url_clicks WHERE url_id = $1',
      [urlId]
    );
    return Number(result.rows[0]?.count ?? 0);
  },

  async latestUrlClick(urlId: string): Promise<UrlClickRow | null> {
    const result = await this.query<UrlClickRow>(
      `SELECT id, url_id, event_id, clicked_at, browser, os, device_type, ip_hash
       FROM url_clicks WHERE url_id = $1 ORDER BY clicked_at DESC LIMIT 1`,
      [urlId]
    );
    return result.rows[0] ?? null;
  },

  async findUserByUsername(username: string): Promise<{ id: string; role: string; deleted_at: Date | null } | null> {
    const result = await this.query<{ id: string; role: string; deleted_at: Date | null }>(
      'SELECT id, role, deleted_at FROM users WHERE username = $1',
      [username]
    );
    return result.rows[0] ?? null;
  },

  async close(): Promise<void> {
    if (pool) {
      await pool.end();
      pool = undefined;
    }
  },
};
