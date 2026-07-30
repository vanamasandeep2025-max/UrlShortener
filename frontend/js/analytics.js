function renderBarChart(containerId, dataObject) {
  const container = document.getElementById(containerId);
  const entries = Object.entries(dataObject || {}).sort((a, b) => b[1] - a[1]);
  if (!entries.length) {
    container.innerHTML = '<p class="text-muted-small">No data yet</p>';
    return;
  }
  const max = Math.max(...entries.map(([, count]) => count));
  container.innerHTML = entries
    .map(([label, count]) => {
      const pct = max > 0 ? Math.round((count / max) * 100) : 0;
      return `
      <div class="bar-row">
        <div class="bar-label" title="${escapeHtml(label)}">${escapeHtml(label)}</div>
        <div class="bar-track"><div class="bar-fill" style="width:${pct}%"></div></div>
        <div class="bar-count">${count}</div>
      </div>`;
    })
    .join("");
}

function renderDailyClicks(containerId, dailyClicks) {
  const container = document.getElementById(containerId);
  if (!dailyClicks || !dailyClicks.length) {
    container.innerHTML = '<p class="text-muted-small">No data yet</p>';
    return;
  }
  const asMap = {};
  dailyClicks.forEach((d) => (asMap[d.date] = d.count));
  renderBarChart(containerId, asMap);
}

document.addEventListener("DOMContentLoaded", async () => {
  requireLogin();

  const params = new URLSearchParams(window.location.search);
  const shortCode = params.get("code");
  document.getElementById("shortCodeLabel").textContent = shortCode || "unknown";

  if (!shortCode) {
    return;
  }

  try {
    const data = await apiJson(`/urls/${encodeURIComponent(shortCode)}/analytics`);
    document.getElementById("totalClicks").textContent = data.totalClicks;
    document.getElementById("uniqueVisitors").textContent = data.uniqueVisitors;
    renderDailyClicks("dailyClicksChart", data.dailyClicks);
    renderBarChart("browsersChart", data.browsers);
    renderBarChart("osChart", data.operatingSystems);
    renderBarChart("deviceChart", data.deviceTypes);
    renderBarChart("countryChart", data.countries);
    renderBarChart("referrerChart", data.referrers);
  } catch (err) {
    showToast(err.message || "Could not load analytics", "danger");
  }
});
