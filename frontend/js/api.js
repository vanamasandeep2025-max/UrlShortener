// Shared fetch wrapper: attaches the JWT, transparently refreshes an expired access
// token once, and redirects to login on unrecoverable auth failure. Every other page's
// script relies on this instead of calling fetch() directly.
const API_BASE = "/api/v1";
const ACCESS_TOKEN_KEY = "usp_access_token";
const REFRESH_TOKEN_KEY = "usp_refresh_token";

function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

function setTokens(accessToken, refreshToken) {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
}

function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

function isLoggedIn() {
  return !!getAccessToken();
}

function requireLogin() {
  if (!isLoggedIn()) {
    window.location.href = "login.html";
  }
}

async function refreshAccessToken() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return false;
  }
  const response = await fetch(`${API_BASE}/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  if (!response.ok) {
    return false;
  }
  const data = await response.json();
  setTokens(data.accessToken, data.refreshToken);
  return true;
}

async function apiFetch(path, options = {}, retry = true) {
  const headers = Object.assign({}, options.headers || {});
  if (options.body && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }
  const token = getAccessToken();
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, Object.assign({}, options, { headers }));

  // Only an already-authenticated request (one that actually attached a Bearer token) can
  // have a "session" to expire. A 401 from an anonymous call - most importantly /auth/login
  // itself on a wrong password - is just that endpoint's normal rejection and must flow
  // through to the caller's own error handling instead of being reinterpreted as an expired
  // session, which used to clear tokens and force-reload login.html, silently wiping out the
  // real "Invalid username or password" toast before it ever rendered.
  if (response.status === 401 && retry && token) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      return apiFetch(path, options, false);
    }
    clearTokens();
    window.location.href = "login.html";
    return Promise.reject(new Error("Session expired"));
  }

  return response;
}

async function apiJson(path, options = {}) {
  const response = await apiFetch(path, options);
  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json") ? await response.json() : null;
  if (!response.ok) {
    const message = (body && (body.message || body.error)) || `Request failed (${response.status})`;
    const error = new Error(message);
    error.status = response.status;
    error.body = body;
    throw error;
  }
  return body;
}

function showToast(message, variant = "primary") {
  let host = document.getElementById("toastHost");
  if (!host) {
    host = document.createElement("div");
    host.id = "toastHost";
    document.body.appendChild(host);
  }
  const toastEl = document.createElement("div");
  toastEl.className = `toast align-items-center text-bg-${variant} border-0 mb-2`;
  toastEl.setAttribute("role", "alert");
  toastEl.innerHTML = `
    <div class="d-flex">
      <div class="toast-body">${escapeHtml(message)}</div>
      <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
    </div>`;
  host.appendChild(toastEl);
  const toast = new bootstrap.Toast(toastEl, { delay: 4000 });
  toast.show();
  toastEl.addEventListener("hidden.bs.toast", () => toastEl.remove());
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value == null ? "" : String(value);
  return div.innerHTML;
}

function formatDate(isoString) {
  if (!isoString) {
    return "-";
  }
  return new Date(isoString).toLocaleString();
}
