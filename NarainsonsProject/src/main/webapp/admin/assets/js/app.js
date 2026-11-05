(function () {
  const ctx = document.body.dataset.context || "";
  const base = window.NS_API_BASE || "";

  function api(path, options) {
    return fetch(base + path, {
      headers: { "Content-Type": "application/json", ...(options && options.headers) },
      ...options,
    }).then(async (res) => {
      const json = await res.json().catch(() => ({}));
      if (!res.ok || json.success === false) {
        throw new Error(json.message || json.error || "Request failed");
      }
      return json;
    });
  }

  function showMessage(el, text, type) {
    if (!el) return;
    el.textContent = text;
    var isNsAuth = document.body.classList.contains("ns-auth");
    if (isNsAuth) {
      el.className = "ns-toast " + (type === "success" ? "success" : type === "info" ? "info" : "error");
    } else {
      el.className = "message " + type;
    }
    el.classList.remove("hidden");
  }

  function pingBackendStatus() {
    var el = document.getElementById("backend-status");
    if (!el || !base) {
      if (el) {
        el.textContent =
          "API URL not set. Open this page via Live Server (not file://) or set ns_api_base in browser console.";
        el.className = "ns-auth-card__backend ns-auth-card__backend--warn";
      }
      return;
    }
    if (window.location.protocol === "file:") {
      el.textContent =
        "Page opened as file:// — browser may block API. Use Live Server on Narainsons-WebApp folder.";
      el.className = "ns-auth-card__backend ns-auth-card__backend--warn";
    }
    var url = base.replace(/\/$/, "") + "/api/health";
    fetch(url)
      .then(function (res) {
        return res.json().catch(function () {
          return {};
        }).then(function (json) {
          if (res.ok && (json.status === "ok" || json.success !== false)) {
            el.textContent = "Backend OK: " + url;
            el.className = "ns-auth-card__backend ns-auth-card__backend--ok";
          } else {
            el.textContent = "Backend responded but not healthy (" + res.status + "). Check Tomcat deploy.";
            el.className = "ns-auth-card__backend ns-auth-card__backend--warn";
          }
        });
      })
      .catch(function () {
        el.textContent =
          "Cannot reach backend at " +
          url +
          ". Tomcat log only means server started — open this URL in browser to confirm deploy.";
        el.className = "ns-auth-card__backend ns-auth-card__backend--error";
      });
  }

  function wirePasswordToggle(button) {
    const targetId = button.getAttribute("data-toggle-for") || "password";
    const input = document.getElementById(targetId);
    if (!input) return;
    button.addEventListener("click", () => {
      const show = input.type === "password";
      input.type = show ? "text" : "password";
      const showIcon = button.querySelector(".icon-eye--show, .ns-eye-icon--show");
      const hideIcon = button.querySelector(".icon-eye--hide, .ns-eye-icon--hide");
      if (showIcon) showIcon.classList.toggle("hidden", show);
      if (hideIcon) hideIcon.classList.toggle("hidden", !show);
      button.classList.toggle("is-revealed", show);
      button.setAttribute("aria-label", show ? "Hide password" : "Show password");
      button.setAttribute("aria-pressed", show ? "true" : "false");
    });
  }

  document.querySelectorAll(".input-wrap__toggle").forEach(wirePasswordToggle);

  if (document.body.classList.contains("ns-auth") && typeof wirePasswordToggles === "function") {
    wirePasswordToggles();
  } else if (document.body.classList.contains("ns-auth") && typeof initPasswordToggleDelegation === "function") {
    initPasswordToggleDelegation();
  }

  if (ctx === "login") {
    const msg = document.getElementById("auth-message");
    const form = document.getElementById("login-form");

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const email = document.getElementById("email").value.trim();
      const password = document.getElementById("password").value;
      if (!email || !password) {
        showMessage(msg, "Please enter both email and password.", "error");
        return;
      }
      try {
        const data = await adminApi("/api/admin/auth/login", {
          method: "POST",
          body: JSON.stringify({ email, password }),
        });
        if (data && data.access_token) {
          setAdminToken(data.access_token);
          if (data.admin && typeof normalizeAdminProfile === "function") {
            nsAdminProfileCache = normalizeAdminProfile(data.admin);
          }
          var afterLogin =
            typeof consumeAdminReturnUrl === "function" ? consumeAdminReturnUrl() : null;
          window.location.href = afterLogin || "admin-dashboard.html";
        } else {
          showMessage(msg, "Login succeeded but no access token was returned.", "error");
        }
      } catch (err) {
        showMessage(msg, err.message, "error");
      }
    });
  }

  if (ctx === "admin-forgot") {
    const msg = document.getElementById("auth-message");
    const resetSection = document.getElementById("admin-reset-section");
    let email = "";

    document.getElementById("btn-admin-send-otp").addEventListener("click", async () => {
      email = document.getElementById("email").value.trim();
      if (!email) {
        showMessage(msg, "Please enter your admin email.", "error");
        return;
      }
      try {
        const data = await adminApi("/api/admin/auth/password/forgot/send", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        resetSection.classList.remove("hidden");
        let hint = (data && data.message) || "OTP has been sent.";
        if (data && data.dev_otp) hint += " Dev OTP: " + data.dev_otp;
        showMessage(msg, hint, "info");
      } catch (err) {
        showMessage(msg, err.message, "error");
      }
    });

    document.getElementById("btn-admin-reset").addEventListener("click", async () => {
      try {
        await adminApi("/api/admin/auth/password/forgot/reset", {
          method: "POST",
          body: JSON.stringify({
            email: email || document.getElementById("email").value.trim(),
            otp: document.getElementById("otp").value.trim(),
            new_password: document.getElementById("new-password").value,
          }),
        });
        showMessage(msg, "Password updated. You can sign in now.", "success");
        setTimeout(function () {
          window.location.href = "login.html";
        }, 1200);
      } catch (err) {
        showMessage(msg, err.message, "error");
      }
    });
  }

  if (ctx === "forgot") {
    const msg = document.getElementById("auth-message");
    const stepLabel = document.getElementById("step-label");
    const otpSection = document.getElementById("otp-section");
    const pwdSection = document.getElementById("password-section");
    const btnSend = document.getElementById("btn-send-otp");
    const btnReset = document.getElementById("btn-reset");
    let otpSent = false;

    btnSend.addEventListener("click", async () => {
      const email = document.getElementById("email").value.trim();
      if (!email) {
        showMessage(msg, "Please enter your email.", "error");
        return;
      }
      try {
        const r = await api("/api/auth/password/forgot/send", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        otpSent = true;
        otpSection.classList.remove("hidden");
        pwdSection.classList.remove("hidden");
        btnReset.classList.remove("hidden");
        stepLabel.textContent = "Step 2 — OTP & new password";
        let hint = (r.data && r.data.message) || "OTP has been sent to your email.";
        if (r.data && r.data.dev_otp) hint += " Dev OTP: " + r.data.dev_otp;
        showMessage(msg, hint, "info");
      } catch (err) {
        showMessage(msg, err.message, "error");
      }
    });

    btnReset.addEventListener("click", async () => {
      const email = document.getElementById("email").value.trim();
      const otp = document.getElementById("otp").value.trim();
      const newPassword = document.getElementById("new-password").value;
      const confirm = document.getElementById("confirm-password").value;
      if (!otpSent) {
        showMessage(msg, "Please send an OTP first.", "error");
        return;
      }
      if (!otp || !newPassword) {
        showMessage(msg, "OTP and new password are required.", "error");
        return;
      }
      if (newPassword.length < 8) {
        showMessage(msg, "Password must be at least 8 characters.", "error");
        return;
      }
      if (newPassword !== confirm) {
        showMessage(msg, "Confirm password does not match.", "error");
        return;
      }
      try {
        await api("/api/auth/password/forgot/reset", {
          method: "POST",
          body: JSON.stringify({ email, otp, new_password: newPassword }),
        });
        showMessage(msg, "Password updated. Please sign in.", "success");
        setTimeout(() => { window.location.href = "login.html"; }, 1200);
      } catch (err) {
        showMessage(msg, err.message, "error");
      }
    });
  }

  if (ctx === "dashboard") {
    const token = sessionStorage.getItem("access_token");
    if (!token) {
      window.location.href = "login.html";
      return;
    }

    function fillProfile(name, mail) {
      const letter = (name[0] || "U").toUpperCase();
      document.getElementById("user-name").textContent = name;
      document.getElementById("avatar-letter").textContent = letter;
      const pa = document.getElementById("profile-avatar");
      if (pa) pa.textContent = letter;
      document.getElementById("profile-name").textContent = name;
      document.getElementById("profile-email").textContent = mail || "—";
    }

    api("/api/user/me", { headers: { Authorization: "Bearer " + token } })
      .then((r) => {
        const u = (r.data && r.data.user) || {};
        const name = u.name || u.first_name || u.email || "User";
        fillProfile(name, u.email);
        document.getElementById("meta-mobile").textContent = u.mobile_number || "—";
        document.getElementById("meta-step").textContent = String(r.data.onboarding_step ?? 0);
        document.getElementById("kpi-step").textContent = String(r.data.onboarding_step ?? 0);
        document.getElementById("kpi-complete").textContent = r.data.profile_complete ? "Yes" : "No";
      })
      .catch(() => {
        sessionStorage.clear();
        window.location.href = "login.html";
      });

    document.getElementById("btn-logout").addEventListener("click", () => {
      sessionStorage.clear();
      window.location.href = "login.html";
    });
  }
})();
