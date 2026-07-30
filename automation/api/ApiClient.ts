import { APIRequestContext, APIResponse } from '@playwright/test';
import { ApiRoutes } from '../constants/routes';
import {
  AnalyticsResponse,
  ApiKeyResponse,
  AuthResponse,
  CreateApiKeyRequest,
  CreateUrlRequest,
  LoginRequest,
  PageResponse,
  RefreshTokenRequest,
  RegisterRequest,
  UpdateExpiryRequest,
  UrlResponse,
  UrlStatusFilter,
  VerifyUrlPasswordRequest,
} from './types';

/**
 * Thin, typed wrapper around Playwright's native APIRequestContext (preferred over a
 * separate Axios instance for in-test assertions: it shares Playwright's tracing,
 * request/response logging, and reporter integration for free). One instance per test
 * worker via the `apiClient` fixture; construct a second instance with `withToken` when
 * a test needs two authenticated identities at once (e.g. IDOR/ownership checks).
 */
export class ApiClient {
  constructor(
    private readonly request: APIRequestContext,
    private token?: string,
    private apiKey?: string
  ) {}

  withToken(token: string): ApiClient {
    return new ApiClient(this.request, token, undefined);
  }

  /** Authenticates via the X-API-Key header instead of a JWT - see ApiKeyAuthenticationFilter. */
  withApiKey(apiKey: string): ApiClient {
    return new ApiClient(this.request, undefined, apiKey);
  }

  private authHeaders(): Record<string, string> {
    if (this.apiKey) return { 'X-API-Key': this.apiKey };
    if (this.token) return { Authorization: `Bearer ${this.token}` };
    return {};
  }

  // ---- Auth ----------------------------------------------------------------

  async register(payload: RegisterRequest): Promise<APIResponse> {
    return this.request.post(ApiRoutes.authRegister, { data: payload });
  }

  async login(payload: LoginRequest): Promise<APIResponse> {
    return this.request.post(ApiRoutes.authLogin, { data: payload });
  }

  async refresh(payload: RefreshTokenRequest): Promise<APIResponse> {
    return this.request.post(ApiRoutes.authRefresh, { data: payload });
  }

  async loginAndGetTokens(username: string, password: string): Promise<AuthResponse> {
    const response = await this.login({ username, password });
    if (!response.ok()) {
      throw new Error(`Login failed for "${username}": ${response.status()} ${await response.text()}`);
    }
    return response.json();
  }

  // ---- URLs ------------------------------------------------------------

  async createUrl(payload: CreateUrlRequest): Promise<APIResponse> {
    return this.request.post(ApiRoutes.urls, { data: payload, headers: this.authHeaders() });
  }

  async listUrls(params: {
    page?: number;
    size?: number;
    sort?: string;
    status?: UrlStatusFilter;
    search?: string;
  } = {}): Promise<APIResponse> {
    return this.request.get(ApiRoutes.urls, {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 10,
        sort: params.sort ?? 'createdAt,desc',
        status: params.status ?? 'ALL',
        ...(params.search ? { search: params.search } : {}),
      },
      headers: this.authHeaders(),
    });
  }

  async deleteUrl(id: string): Promise<APIResponse> {
    return this.request.delete(ApiRoutes.urlById(id), { headers: this.authHeaders() });
  }

  async updateExpiry(id: string, payload: UpdateExpiryRequest): Promise<APIResponse> {
    return this.request.patch(ApiRoutes.urlById(id), { data: payload, headers: this.authHeaders() });
  }

  async getAnalytics(shortCode: string): Promise<APIResponse> {
    return this.request.get(ApiRoutes.urlAnalytics(shortCode), { headers: this.authHeaders() });
  }

  async verifyPassword(shortCode: string, payload: VerifyUrlPasswordRequest): Promise<APIResponse> {
    return this.request.post(ApiRoutes.urlVerifyPassword(shortCode), { data: payload });
  }

  /** Convenience wrapper for tests that just need a valid URL to exist, not to assert on creation itself. */
  async createUrlOrThrow(payload: CreateUrlRequest): Promise<UrlResponse> {
    const response = await this.createUrl(payload);
    if (!response.ok()) {
      throw new Error(`createUrl failed: ${response.status()} ${await response.text()}`);
    }
    return response.json();
  }

  async listUrlsOrThrow(
    params: Parameters<ApiClient['listUrls']> extends [infer P] ? P : never
  ): Promise<PageResponse<UrlResponse>> {
    const response = await this.listUrls(params);
    if (!response.ok()) {
      throw new Error(`listUrls failed: ${response.status()} ${await response.text()}`);
    }
    return response.json();
  }

  async getAnalyticsOrThrow(shortCode: string): Promise<AnalyticsResponse> {
    const response = await this.getAnalytics(shortCode);
    if (!response.ok()) {
      throw new Error(`getAnalytics failed: ${response.status()} ${await response.text()}`);
    }
    return response.json();
  }

  // ---- API keys ----------------------------------------------------------

  async createApiKey(payload: CreateApiKeyRequest): Promise<APIResponse> {
    return this.request.post(ApiRoutes.apiKeys, { data: payload, headers: this.authHeaders() });
  }

  async listApiKeys(): Promise<APIResponse> {
    return this.request.get(ApiRoutes.apiKeys, { headers: this.authHeaders() });
  }

  async revokeApiKey(id: string): Promise<APIResponse> {
    return this.request.delete(ApiRoutes.apiKeyById(id), { headers: this.authHeaders() });
  }

  async createApiKeyOrThrow(payload: CreateApiKeyRequest): Promise<ApiKeyResponse> {
    const response = await this.createApiKey(payload);
    if (!response.ok()) {
      throw new Error(`createApiKey failed: ${response.status()} ${await response.text()}`);
    }
    return response.json();
  }

  // ---- Redirect (short-code resolution, not under /api/v1) ---------------

  /**
   * Calls the raw short-code path directly; caller decides whether to follow the 302.
   * Deliberately a LEADING-slash path (unlike every other method here): standard URL
   * resolution treats a leading slash as absolute-path-from-origin, which is exactly what
   * short-code redirects need (they live at the site root, not under /api/v1) - this is
   * the one place that behavior is wanted rather than the bug it was everywhere else.
   */
  async redirect(shortCode: string, followRedirects: boolean): Promise<APIResponse> {
    return this.request.get(`/${shortCode}`, { maxRedirects: followRedirects ? 5 : 0 });
  }
}
