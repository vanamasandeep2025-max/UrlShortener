let currentPage = 0;
const PAGE_SIZE = 10;
let expiryModal;

function decodeJwtSubject(token) {
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return payload.sub || null;
  } catch (e) {
    return null;
  }
}

function toLocalInputValue(isoString) {
  if (!isoString) {
    return "";
  }
  const d = new Date(isoString);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

async function loadUrls() {
  const search = document.getElementById("searchInput").value.trim();
  const status = document.getElementById("statusFilter").value;
  const params = new URLSearchParams({
    page: currentPage,
    size: PAGE_SIZE,
    sort: "createdAt,desc",
    status,
  });
  if (search) {
    params.set("search", search);
  }

  const tbody = document.getElementById("urlsTableBody");
  try {
    const page = await apiJson(`/urls?${params.toString()}`);
    renderRows(page.content);
    renderPagination(page);
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center text-danger py-4">${escapeHtml(err.message)}</td></tr>`;
  }
}

function renderRows(urls) {
  const tbody = document.getElementById("urlsTableBody");
  if (!urls.length) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">No links yet - shorten one above.</td></tr>`;
    return;
  }
  tbody.innerHTML = urls
    .map((url) => {
      const expired = url.expiresAt && new Date(url.expiresAt) < new Date();
      return `
      <tr>
        <td>
          <a class="short-url-chip text-decoration-none" href="${escapeHtml(url.shortUrl)}" target="_blank" rel="noopener">${escapeHtml(url.shortUrl)}</a>
          ${url.passwordProtected ? '<span class="badge bg-warning text-dark status-pill ms-1">protected</span>' : ""}
          <button class="btn btn-sm btn-link p-0 ms-1" title="Copy" onclick="copyShortUrl('${escapeHtml(url.shortUrl)}')">Copy</button>
        </td>
        <td class="text-truncate" style="max-width: 260px;" title="${escapeHtml(url.originalUrl)}">${escapeHtml(url.originalUrl)}</td>
        <td>${url.clickCount}</td>
        <td class="text-muted-small">${formatDate(url.createdAt)}</td>
        <td>
          <span class="text-muted-small">${formatDate(url.expiresAt)}</span>
          ${expired ? '<span class="badge bg-danger status-pill ms-1">expired</span>' : ""}
        </td>
        <td class="text-end text-nowrap">
          <a class="btn btn-sm btn-outline-secondary" href="analytics.html?code=${encodeURIComponent(url.shortCode)}">Analytics</a>
          <button class="btn btn-sm btn-outline-secondary" onclick="openExpiryModal('${url.id}', '${url.expiresAt || ""}')">Edit expiry</button>
          <button class="btn btn-sm btn-outline-danger" onclick="deleteUrl('${url.id}')">Delete</button>
        </td>
      </tr>`;
    })
    .join("");
}

function renderPagination(page) {
  const pagination = document.getElementById("pagination");
  const items = [];
  for (let i = 0; i < page.totalPages; i++) {
    items.push(`<li class="page-item ${i === page.page ? "active" : ""}">
      <button class="page-link" onclick="goToPage(${i})">${i + 1}</button>
    </li>`);
  }
  pagination.innerHTML = items.join("");
}

function goToPage(page) {
  currentPage = page;
  loadUrls();
}

async function copyShortUrl(shortUrl) {
  await navigator.clipboard.writeText(shortUrl);
  showToast("Copied to clipboard", "success");
}

async function deleteUrl(id) {
  if (!confirm("Delete this link? This cannot be undone.")) {
    return;
  }
  try {
    await apiFetch(`/urls/${id}`, { method: "DELETE" });
    showToast("Link deleted", "success");
    loadUrls();
  } catch (err) {
    showToast(err.message || "Delete failed", "danger");
  }
}

function openExpiryModal(id, expiresAt) {
  document.getElementById("expiryModalUrlId").value = id;
  document.getElementById("expiryModalInput").value = toLocalInputValue(expiresAt);
  expiryModal.show();
}

document.addEventListener("DOMContentLoaded", () => {
  requireLogin();

  const token = getAccessToken();
  const username = token ? decodeJwtSubject(token) : null;
  document.getElementById("currentUserLabel").textContent = username ? `Signed in as ${username}` : "";

  expiryModal = new bootstrap.Modal(document.getElementById("expiryModal"));

  document.getElementById("logoutBtn").addEventListener("click", () => {
    clearTokens();
    window.location.href = "login.html";
  });

  document.getElementById("createForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const url = document.getElementById("urlInput").value.trim();
    const expiryInput = document.getElementById("expiryInput").value;
    const password = document.getElementById("passwordInput").value;
    const payload = { url };
    if (expiryInput) {
      payload.expiryDate = new Date(expiryInput).toISOString();
    }
    if (password) {
      payload.password = password;
    }
    try {
      await apiJson("/urls", { method: "POST", body: JSON.stringify(payload) });
      showToast("Link created", "success");
      document.getElementById("createForm").reset();
      currentPage = 0;
      loadUrls();
    } catch (err) {
      showToast(err.message || "Could not create link", "danger");
    }
  });

  document.getElementById("saveExpiryBtn").addEventListener("click", async () => {
    const id = document.getElementById("expiryModalUrlId").value;
    const value = document.getElementById("expiryModalInput").value;
    const payload = { expiresAt: value ? new Date(value).toISOString() : null };
    try {
      await apiJson(`/urls/${id}`, { method: "PATCH", body: JSON.stringify(payload) });
      expiryModal.hide();
      showToast("Expiry updated", "success");
      loadUrls();
    } catch (err) {
      showToast(err.message || "Update failed", "danger");
    }
  });

  document.getElementById("searchInput").addEventListener("input", debounce(() => {
    currentPage = 0;
    loadUrls();
  }, 350));
  document.getElementById("statusFilter").addEventListener("change", () => {
    currentPage = 0;
    loadUrls();
  });

  loadUrls();
});

function debounce(fn, delayMs) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delayMs);
  };
}
