document.addEventListener("DOMContentLoaded", () => {
  const params = new URLSearchParams(window.location.search);
  const shortCode = params.get("code");
  const form = document.getElementById("verifyForm");
  const errorMsg = document.getElementById("errorMsg");

  if (!shortCode) {
    form.classList.add("d-none");
    errorMsg.textContent = "No link code provided.";
    errorMsg.classList.remove("d-none");
    return;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    errorMsg.classList.add("d-none");
    const password = document.getElementById("linkPassword").value;

    try {
      const response = await fetch(`/api/v1/urls/${encodeURIComponent(shortCode)}/verify-password`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password }),
      });
      if (!response.ok) {
        throw new Error("Incorrect password");
      }
      const data = await response.json();
      window.location.href = data.originalUrl;
    } catch (err) {
      errorMsg.textContent = "Incorrect password. Try again.";
      errorMsg.classList.remove("d-none");
    }
  });
});
