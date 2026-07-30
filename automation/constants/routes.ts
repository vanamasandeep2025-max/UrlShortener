/** Real frontend page paths, as served by nginx from /frontend (see infra/nginx/nginx.conf). */
export const Routes = {
  login: '/login.html',
  dashboard: '/index.html',
  analytics: (shortCode: string): string => `/analytics.html?code=${encodeURIComponent(shortCode)}`,
  apiKeys: '/api-keys.html',
  protected: (shortCode: string): string => `/protected.html?code=${encodeURIComponent(shortCode)}`,
} as const;

/**
 * Real REST endpoints under API_BASE_URL, matching the controllers in backend/.../controller.
 * Deliberately relative (no leading slash): API_BASE_URL ends in a trailing slash
 * specifically so these append onto it. A leading slash here would resolve against the
 * origin instead and silently drop "/api/v1" - see config/environment.ts.
 */
export const ApiRoutes = {
  authRegister: 'auth/register',
  authLogin: 'auth/login',
  authRefresh: 'auth/refresh',

  urls: 'urls',
  urlById: (id: string): string => `urls/${id}`,
  urlAnalytics: (shortCode: string): string => `urls/${shortCode}/analytics`,
  urlVerifyPassword: (shortCode: string): string => `urls/${shortCode}/verify-password`,

  apiKeys: 'api-keys',
  apiKeyById: (id: string): string => `api-keys/${id}`,
} as const;
