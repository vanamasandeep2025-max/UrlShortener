/**
 * Request/response shapes mirrored 1:1 from backend/src/main/java/com/urlshortener/dto.
 * Kept intentionally hand-written (not generated) so a field rename in the backend
 * fails typecheck here rather than failing silently at runtime.
 */

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInMs: number;
}

export interface CreateUrlRequest {
  url: string;
  expiryDate?: string;
  password?: string;
}

export interface UpdateExpiryRequest {
  expiresAt: string | null;
}

export interface VerifyUrlPasswordRequest {
  password: string;
}

export interface UrlResponse {
  id: string;
  shortCode: string;
  shortUrl: string;
  originalUrl: string;
  clickCount: number;
  passwordProtected: boolean;
  createdAt: string;
  updatedAt: string;
  expiresAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface DailyClickCount {
  date: string;
  count: number;
}

export interface AnalyticsResponse {
  shortCode: string;
  totalClicks: number;
  uniqueVisitors: number;
  browsers: Record<string, number>;
  operatingSystems: Record<string, number>;
  deviceTypes: Record<string, number>;
  countries: Record<string, number>;
  referrers: Record<string, number>;
  dailyClicks: DailyClickCount[];
}

export interface CreateApiKeyRequest {
  name: string;
  expiresAt?: string;
}

export interface ApiKeyResponse {
  id: string;
  name: string;
  keyPrefix: string;
  plaintextKey?: string;
  scopes: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  createdAt: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  correlationId: string;
  validationErrors?: Record<string, string>;
}

export type UrlStatusFilter = 'ALL' | 'ACTIVE' | 'EXPIRED';
