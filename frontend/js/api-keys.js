async function loadKeys() {
  const tbody = document.getElementById("keysTableBody");
  try {
    const keys = await apiJson("/api-keys");
    if (!keys.length) {
      tbody.innerHTML = `<tr><td colspan="6" class="text-center text-muted py-4">No API keys yet.</td></tr>`;
      return;
    }
    tbody.innerHTML = keys
      .map(
        (key) => `
      <tr>
        <td>${escapeHtml(key.name)}</td>
        <td><code>${escapeHtml(key.keyPrefix)}…</code></td>
        <td class="text-muted-small">${escapeHtml(key.scopes)}</td>
        <td class="text-muted-small">${formatDate(key.lastUsedAt)}</td>
        <td class="text-muted-small">${formatDate(key.createdAt)}</td>
        <td class="text-end">
          <button class="btn btn-sm btn-outline-danger" onclick="revokeKey('${key.id}')">Revoke</button>
        </td>
      </tr>`
      )
      .join("");
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center text-danger py-4">${escapeHtml(err.message)}</td></tr>`;
  }
}

async function revokeKey(id) {
  if (!confirm("Revoke this API key? Any client using it will stop working immediately.")) {
    return;
  }
  try {
    await apiFetch(`/api-keys/${id}`, { method: "DELETE" });
    showToast("API key revoked", "success");
    loadKeys();
  } catch (err) {
    showToast(err.message || "Revoke failed", "danger");
  }
}

document.addEventListener("DOMContentLoaded", () => {
  requireLogin();

  document.getElementById("createKeyForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const name = document.getElementById("keyName").value.trim();
    const alertBox = document.getElementById("newKeyAlert");
    try {
      const created = await apiJson("/api-keys", { method: "POST", body: JSON.stringify({ name }) });
      alertBox.classList.remove("d-none");
      alertBox.innerHTML = `Key created - copy it now, it will not be shown again:<br><code>${escapeHtml(created.plaintextKey)}</code>`;
      document.getElementById("createKeyForm").reset();
      loadKeys();
    } catch (err) {
      showToast(err.message || "Could not create key", "danger");
    }
  });

  loadKeys();
});
