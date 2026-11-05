var nsViewUserId = null;
var nsViewUser = null;
var nsViewLoans = [];

document.addEventListener("DOMContentLoaded", function () {
  if (document.body.getAttribute("data-page") !== "user-view") return;
  initAdminChromeEarly();
  if (!getAdminToken()) {
    window.location.href = "login.html";
    return;
  }
  nsViewUserId = parseUserIdFromQuery();
  if (!nsViewUserId) {
    showViewError("No customer selected. Open this page from Customers → View.");
    return;
  }
  bindUserViewPage();
  loadUserViewPage();
  initAdminShell({});
});

function parseUserIdFromQuery() {
  var params = new URLSearchParams(window.location.search);
  var id = params.get("id");
  if (!id || !/^\d+$/.test(id)) return null;
  return id;
}

function bindUserViewPage() {
  document.getElementById("btn-view-delete").addEventListener("click", deleteViewUser);
  document.getElementById("view-active-toggle").addEventListener("change", function (e) {
    var input = e.target;
    setViewUserActive(input.checked, input);
  });
}

async function loadUserViewPage() {
  try {
    var data = await adminApi("/api/admin/users/" + nsViewUserId);
    nsViewUser = data.user;
    nsViewLoans = data.loan_applications || [];
    if (!nsViewUser) throw new Error("User not found");
    document.title = (nsViewUser.name || "Applicant") + " — Narainsons Admin";
    renderUserViewPage(nsViewUser, nsViewLoans);
    syncViewActiveToggle(nsViewUser);
  } catch (err) {
    showViewError(err.message || "Could not load user");
    showGlobalToast(err.message, "error");
  }
}

function showViewError(msg) {
  var root = document.getElementById("user-view-root");
  if (!root) return;
  root.innerHTML =
    '<div class="ns-empty ns-empty--error"><p><strong>Profile unavailable</strong></p><p>' +
    escapeHtml(msg) +
    '</p><a class="ns-btn ns-btn--primary ns-btn--sm" href="admin-users.html">Back to list</a></div>';
}

function syncViewActiveToggle(user) {
  var input = document.getElementById("view-active-toggle");
  if (!input) return;
  input.checked = (user.account_status || "active").toLowerCase() === "active";
}

function renderUserViewPage(user, loans) {
  var root = document.getElementById("user-view-root");
  var name = user.name || [user.first_name, user.last_name].filter(Boolean).join(" ") || "Applicant";
  var initial = (name.trim()[0] || "A").toUpperCase();

  var html =
    '<header class="ns-user-view-hero">' +
    '<div class="ns-user-view-hero__brand ns-no-print-screen">' +
    '<img src="assets/img/logo.jpg" alt="" width="40" height="40">' +
    "<span>Narainsons · Applicant record</span></div>" +
    '<div class="ns-user-view-hero__main">' +
    '<div class="ns-user-view-hero__avatar" aria-hidden="true">' +
    escapeHtml(initial) +
    "</div>" +
    '<div class="ns-user-view-hero__text">' +
    "<h1>" +
    escapeHtml(name) +
    "</h1>" +
    '<p class="ns-user-view-hero__meta">User ID <strong>#' +
    escapeHtml(String(user.id)) +
    "</strong>" +
    (user.customer_code ? " · Code <strong>" + escapeHtml(user.customer_code) + "</strong>" : "") +
    "</p>" +
    '<p class="ns-user-view-hero__contact">' +
    escapeHtml(user.mobile_number || "—") +
    " · " +
    escapeHtml(user.email || "—") +
    "</p>" +
    '<div class="ns-user-view-hero__badges">' +
    accountBadge(user.account_status) +
    kycBadge(user.kyc_status) +
    onboardingBadge(user.onboarding_step) +
    "</div></div></div>" +
    '<dl class="ns-user-view-kpis">' +
    kpi("Joined", formatAdminDate(user.created_at)) +
    kpi("Last login", formatAdminDate(user.last_login)) +
    kpi("Onboarding", user.onboarding_step != null ? "Step " + user.onboarding_step : "—") +
    kpi("KYC verified", formatAdminDate(user.kyc_verified_at)) +
    "</dl></header>";

  html += '<div class="ns-user-view-grid">';
  html += sectionCard("Personal", [
    ["Full name", name],
    ["First / last", [user.first_name, user.last_name].filter(Boolean).join(" ") || "—"],
    ["Gender", user.gender],
    ["Date of birth", formatDob(user.date_of_birth)],
    ["Age", user.age],
    ["Marital status", user.marital_status],
    ["Nationality", user.nationality],
    ["Alt. mobile", user.alternate_mobile_number],
  ]);
  html += sectionCard("KYC & identity", [
    ["KYC status", user.kyc_status],
    ["Aadhaar last 4", user.aadhaar_last4],
    ["PAN last 4", user.pan_last4],
    ["KYC verified at", formatAdminDate(user.kyc_verified_at)],
    ["Rejection reason", user.kyc_rejection_reason],
    ["Aadhaar doc", user.aadhaar_document],
    ["PAN doc", user.pan_document],
  ]);
  html += sectionCard("Current address", [
    ["Address", user.current_address],
    ["City", user.current_city],
    ["State", user.current_state],
    ["PIN", user.current_pincode],
    ["Country", user.current_country],
  ]);
  html += sectionCard("Permanent address", [
    ["Same as current", yn(user.same_as_current_address)],
    ["Address", user.permanent_address],
    ["City", user.permanent_city],
    ["State", user.permanent_state],
    ["PIN", user.permanent_pincode],
  ]);
  html += sectionCard("Employment & income", [
    ["Employment type", user.employment_type],
    ["Company", user.company_name],
    ["Designation", user.designation],
    ["Experience (months)", user.work_experience_months],
    ["Monthly income", money(user.monthly_income)],
    ["Annual income", money(user.annual_income)],
    ["Income source", user.income_source],
  ]);
  html += sectionCard("Bank details", [
    ["Bank name", user.bank_name],
    ["IFSC", user.ifsc_code],
    ["Account last 4", user.bank_account_last4],
    ["Verification", user.bank_verification_status],
    ["Verified at", formatAdminDate(user.bank_verified_at)],
  ]);
  html += sectionCard("Verification & security", [
    ["Mobile verified", formatAdminDate(user.mobile_verified_at)],
    ["Email verified", formatAdminDate(user.email_verified_at)],
    ["OTP verified", formatAdminDate(user.otp_verified_at)],
    ["Login attempts", user.login_attempts],
    ["Locked until", formatAdminDate(user.locked_until)],
    ["Last logout", formatAdminDate(user.last_logout_at)],
  ]);
  html += sectionCard("Device & app", [
    ["Device type", user.device_type],
    ["Device name", user.device_name],
    ["Model", user.device_model],
    ["OS", user.os_version],
    ["App version", user.app_version],
    ["Time zone", user.time_zone],
    ["Logged in now", yn(user.is_login)],
    ["App installed", yn(user.is_app_installed)],
    ["Install time", formatAdminDate(user.application_install_time)],
  ]);
  html += sectionCard("Consents", [
    ["Privacy consent", yn(user.privacy_consent)],
    ["Privacy at", formatAdminDate(user.privacy_consent_at)],
    ["Terms accepted", yn(user.terms_accepted)],
    ["Terms at", formatAdminDate(user.terms_accepted_at)],
    ["KYC consent", yn(user.kyc_consent)],
    ["KYC consent at", formatAdminDate(user.kyc_consent_at)],
    ["Notifications", yn(user.notification_enable)],
  ]);
  html += sectionCard("Record meta", [
    ["Created", formatAdminDate(user.created_at)],
    ["Updated", formatAdminDate(user.updated_at)],
    ["Account status", user.account_status],
  ]);
  html += "</div>";


  html += kycReviewCard(user);
  root.innerHTML = html;
  wireKycReview(root);
  nsUserViewExtras(user.id);
}

function kycDocLink(label, url) {
  var safe = nsSafeUrl(url);
  if (!safe) return "";
  return (
    '<a class="ns-btn ns-btn--outline ns-btn--sm" href="' +
    escapeHtml(safe) +
    '"' +
    (nsIsProtectedFile(safe) ? ' data-protected-file="' + escapeHtml(safe) + '"' : "") +
    ' target="_blank" rel="noopener noreferrer">' +
    escapeHtml(label) +
    "</a> "
  );
}

function kycReviewCard(user) {
  var submitted = (user.kyc_status || "").toLowerCase() === "submitted";
  return (
    '<section class="ns-user-view-card ns-no-print"><h3>KYC review</h3><p>' +
    kycDocLink("View Aadhaar", user.aadhaar_document) +
    kycDocLink("View PAN", user.pan_document) +
    "</p>" +
    (submitted
      ? '<p><button type="button" class="ns-btn ns-btn--primary ns-btn--sm" id="btn-kyc-verify">Verify KYC</button> ' +
        '<button type="button" class="ns-btn ns-btn--danger ns-btn--sm" id="btn-kyc-reject">Reject KYC</button></p>'
      : '<p class="ns-muted">KYC status: ' + escapeHtml(user.kyc_status || "pending") + "</p>") +
    "</section>"
  );
}

function wireKycReview(root) {
  var verify = root.querySelector("#btn-kyc-verify");
  var reject = root.querySelector("#btn-kyc-reject");
  if (verify) {
    verify.addEventListener("click", function () {
      submitKycReview({ action: "verify" });
    });
  }
  if (reject) {
    reject.addEventListener("click", function () {
      var reason = window.prompt("Reason for rejecting KYC:");
      if (reason) submitKycReview({ action: "reject", reason: reason });
    });
  }
}

async function submitKycReview(body) {
  try {
    await adminApi("/api/admin/users/" + nsViewUserId + "/kyc", { method: "POST", body: JSON.stringify(body) });
    showGlobalToast("KYC updated", "success");
    window.location.reload();
  } catch (err) {
    showGlobalToast(err.message, "error");
  }
}

function sectionCard(title, rows) {
  return (
    '<section class="ns-user-view-card"><h3>' +
    escapeHtml(title) +
    '</h3><dl class="ns-detail-grid ns-detail-grid--view">' +
    rows.map(function (r) {
      return detailRow(r[0], r[1]);
    }).join("") +
    "</dl></section>"
  );
}

function kpi(label, value) {
  return "<div><dt>" + escapeHtml(label) + "</dt><dd>" + escapeHtml(value || "—") + "</dd></div>";
}

function detailRow(label, value) {
  if (value === true) value = "Yes";
  if (value === false) value = "No";
  var v = value === null || value === undefined || value === "" ? "—" : String(value);
  if (typeof value === "object" && value !== null && typeof value.year === "number") {
    v = formatAdminDate(value);
  }
  return '<div class="ns-detail-row"><dt>' + escapeHtml(label) + "</dt><dd>" + escapeHtml(v) + "</dd></div>";
}

function yn(v) {
  if (v === true) return "Yes";
  if (v === false) return "No";
  return "—";
}

function money(n) {
  if (n === null || n === undefined || n === "") return "—";
  var num = Number(n);
  if (isNaN(num)) return String(n);
  return "₹ " + num.toLocaleString("en-IN", { maximumFractionDigits: 2 });
}

function formatDob(v) {
  if (!v) return "—";
  if (typeof v === "object" && v.year) {
    return v.year + "-" + String(v.month || 1).padStart(2, "0") + "-" + String(v.day || 1).padStart(2, "0");
  }
  return String(v);
}

function accountBadge(status) {
  var s = (status || "active").toLowerCase();
  var on = s === "active";
  return '<span class="ns-pill ns-pill--status ' + (on ? "is-on" : "is-off") + '">' + (on ? "Active" : "Inactive") + "</span>";
}

function kycBadge(status) {
  var s = (status || "pending").toLowerCase();
  var cls =
    s === "verified" ? "verified" : s === "rejected" ? "rejected" : s === "submitted" ? "submitted" : "pending";
  return '<span class="ns-pill ns-pill--kyc ns-pill--kyc-' + cls + '">' + s.charAt(0).toUpperCase() + s.slice(1) + "</span>";
}

function onboardingBadge(step) {
  if (step == null) return "";
  return '<span class="ns-pill ns-pill--onboarding">Step ' + escapeHtml(String(step)) + "</span>";
}

async function setViewUserActive(active, inputEl) {
  try {
    await adminApi("/api/admin/users/" + nsViewUserId + "/status", {
      method: "POST",
      body: JSON.stringify({ active: active }),
    });
    showGlobalToast(active ? "User activated" : "User deactivated", "success");
    if (nsViewUser) {
      nsViewUser.account_status = active ? "active" : "inactive";
      var badges = document.querySelector(".ns-user-view-hero__badges");
      if (badges) {
        badges.innerHTML =
          accountBadge(nsViewUser.account_status) +
          kycBadge(nsViewUser.kyc_status) +
          onboardingBadge(nsViewUser.onboarding_step);
      }
    }
    syncViewActiveToggle(nsViewUser);
  } catch (err) {
    if (inputEl) inputEl.checked = !active;
    showGlobalToast(err.message, "error");
  }
}

async function deleteViewUser() {
  if (!nsViewUser) return;
  var label = nsViewUser.name || nsViewUser.mobile_number || "ID " + nsViewUserId;
  if (!window.confirm('Delete "' + label + '"? (soft delete)')) return;
  try {
    await adminApi("/api/admin/users/" + nsViewUserId + "/delete", { method: "POST", body: JSON.stringify({}) });
    showGlobalToast("User deleted", "success");
    window.location.href = "admin-users.html";
  } catch (err) {
    showGlobalToast(err.message, "error");
  }
}

function escapeHtml(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
