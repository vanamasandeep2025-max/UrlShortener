document.addEventListener("DOMContentLoaded", () => {
  if (isLoggedIn()) {
    window.location.href = "index.html";
    return;
  }

  const loginForm = document.getElementById("loginForm");
  const registerForm = document.getElementById("registerForm");
  const tabs = document.querySelectorAll("#authTabs .nav-link");

  tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      tabs.forEach((t) => t.classList.remove("active"));
      tab.classList.add("active");
      const isLogin = tab.dataset.tab === "login";
      loginForm.classList.toggle("d-none", !isLogin);
      registerForm.classList.toggle("d-none", isLogin);
    });
  });

  loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const username = document.getElementById("loginUsername").value.trim();
    const password = document.getElementById("loginPassword").value;
    try {
      const data = await apiJson("/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      setTokens(data.accessToken, data.refreshToken);
      window.location.href = "index.html";
    } catch (err) {
      showToast(err.message || "Login failed", "danger");
    }
  });

  registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const username = document.getElementById("registerUsername").value.trim();
    const email = document.getElementById("registerEmail").value.trim();
    const password = document.getElementById("registerPassword").value;
    try {
      const data = await apiJson("/auth/register", {
        method: "POST",
        body: JSON.stringify({ username, email, password }),
      });
      setTokens(data.accessToken, data.refreshToken);
      window.location.href = "index.html";
    } catch (err) {
      showToast(err.message || "Registration failed", "danger");
    }
  });
});
