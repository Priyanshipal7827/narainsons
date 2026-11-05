function showMsg(el, text, ok) {
  if (!el) return;
  el.textContent = text;
  el.classList.remove("hidden", "error", "ok");
  el.classList.add(ok ? "ok" : "error");
}

document.addEventListener("DOMContentLoaded", () => {
  const page = document.body.getAttribute("data-page");
  const msg = document.getElementById("msg");

  if (page === "login") {
    document.getElementById("login-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        const data = await adminApi("/api/admin/auth/login", {
          method: "POST",
          body: JSON.stringify({
            email: document.getElementById("email").value.trim(),
            password: document.getElementById("password").value,
          }),
        });
        setAdminToken(data.access_token);
        window.location.href = "dashboard.html";
      } catch (err) {
        let text = err.message || "Login failed";
        if (text.includes("Invalid email") || text.includes("Unauthorized")) {
          text += " — check API URL: " + window.NS_ADMIN_API + "/api/admin/auth/login";
        }
        showMsg(msg, text, false);
      }
    });
  }

  if (page === "forgot") {
    let email = "";
    document.getElementById("btn-send-otp").addEventListener("click", async () => {
      email = document.getElementById("email").value.trim();
      try {
        const data = await adminApi("/api/admin/auth/password/forgot/send", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        document.getElementById("step-reset").classList.remove("hidden");
        let hint = data.message || "OTP has been sent";
        if (data.dev_otp) hint += " (dev OTP: " + data.dev_otp + ")";
        showMsg(msg, hint, true);
      } catch (err) {
        showMsg(msg, err.message, false);
      }
    });
    document.getElementById("btn-reset").addEventListener("click", async () => {
      try {
        await adminApi("/api/admin/auth/password/forgot/reset", {
          method: "POST",
          body: JSON.stringify({
            email,
            otp: document.getElementById("otp").value.trim(),
            new_password: document.getElementById("new-password").value,
          }),
        });
        showMsg(msg, "Password updated — please sign in", true);
        setTimeout(() => { window.location.href = "login.html"; }, 1500);
      } catch (err) {
        showMsg(msg, err.message, false);
      }
    });
  }

  if (page === "dashboard") {
    if (!getAdminToken()) {
      window.location.href = "login.html";
      return;
    }
    document.getElementById("btn-logout").addEventListener("click", () => {
      setAdminToken("");
      window.location.href = "login.html";
    });
    loadDashboard();
  }
});

async function loadDashboard() {
  const msg = document.getElementById("msg");
  try {
    const data = await adminApi("/api/admin/dashboard");
    if (data.admin) {
      document.getElementById("admin-name").textContent =
        " — " + (data.admin.full_name || data.admin.email);
    }
    renderLoans(data.pending_loans || []);
    renderEmi(data.pending_emi_payments || []);
  } catch (err) {
    showMsg(msg, err.message, false);
    if (err.message.includes("Unauthorized") || err.message.includes("session")) {
      setAdminToken("");
      window.location.href = "login.html";
    }
  }
}

function renderLoans(loans) {
  const el = document.getElementById("loans-table");
  if (!loans.length) {
    el.innerHTML = "<p class='muted'>No pending loan applications</p>";
    return;
  }
  el.innerHTML = "<table><tr><th>ID</th><th>Code</th><th>Amount</th><th>Action</th></tr>" +
    loans.map((l) => `<tr>
      <td>${l.id}</td><td>${l.application_code}</td><td>${l.requested_amount}</td>
      <td>
        <button data-loan-approve="${l.id}">Approve</button>
        <button data-loan-reject="${l.id}">Reject</button>
      </td></tr>`).join("") + "</table>";
  el.querySelectorAll("[data-loan-approve]").forEach((btn) => {
    btn.addEventListener("click", () => actLoan(btn.getAttribute("data-loan-approve"), "approve"));
  });
  el.querySelectorAll("[data-loan-reject]").forEach((btn) => {
    btn.addEventListener("click", () => actLoan(btn.getAttribute("data-loan-reject"), "reject"));
  });
}

function renderEmi(payments) {
  const el = document.getElementById("emi-table");
  if (!payments.length) {
    el.innerHTML = "<p class='muted'>No pending EMI payment proofs</p>";
    return;
  }
  el.innerHTML = "<table><tr><th>ID</th><th>EMI</th><th>Amount</th><th>Screenshot</th><th>Action</th></tr>" +
    payments.map((p) => `<tr>
      <td>${p.id}</td><td>${p.emi_schedule_id}</td><td>${p.amount_claimed}</td>
      <td><a href="${p.screenshot_url}" target="_blank" rel="noopener">View</a></td>
      <td>
        <button data-emi-approve="${p.id}">Approve</button>
        <button data-emi-reject="${p.id}">Reject</button>
      </td></tr>`).join("") + "</table>";
  el.querySelectorAll("[data-emi-approve]").forEach((btn) => {
    btn.addEventListener("click", () => actEmi(btn.getAttribute("data-emi-approve"), "approve"));
  });
  el.querySelectorAll("[data-emi-reject]").forEach((btn) => {
    btn.addEventListener("click", () => actEmi(btn.getAttribute("data-emi-reject"), "reject"));
  });
}

async function actLoan(id, action) {
  await adminApi(`/api/admin/loan/applications/${id}/${action}`, {
    method: "POST",
    body: JSON.stringify({ admin_note: action + " from dashboard" }),
  });
  loadDashboard();
}

async function actEmi(id, action) {
  await adminApi(`/api/admin/emi/payments/${id}/${action}`, {
    method: "POST",
    body: JSON.stringify({ admin_note: action + " from dashboard" }),
  });
  loadDashboard();
}
