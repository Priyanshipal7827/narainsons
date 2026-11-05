/**
 * A browser can keep an old copy of a page from before an update. This script is always re-checked, so it
 * can spot that: an admin page without the current stylesheet is out of date, and reloads once to get the new one.
 */
(function () {
  if (!document.body || !document.body.classList.contains("ns-admin")) return;
  var key = "ns_stale_reload:" + window.location.pathname;
  var current = !!document.querySelector('link[href*="admin-ui.css"]');
  try {
    if (current) {
      sessionStorage.removeItem(key);
    } else if (!sessionStorage.getItem(key)) {
      sessionStorage.setItem(key, "1");
      window.location.reload();
    }
  } catch (e) {}
})();

/** Shared Narainsons admin shell (TejLite-style). */
var nsAdminProfileCache = null;

function requireAdminAuth() {
  if (!getAdminToken()) {
    window.location.href = "login.html";
    return false;
  }
  return true;
}

function adminInitial(name) {
  var n = (name || "A").trim();
  return (n[0] || "A").toUpperCase();
}

function formatAdminDate(value) {
  if (!value) return "—";
  try {
    if (typeof value === "object" && value !== null && typeof value.year === "number") {
      value =
        value.year +
        "-" +
        String(value.month).padStart(2, "0") +
        "-" +
        String(value.day).padStart(2, "0") +
        "T" +
        String(value.hour || 0).padStart(2, "0") +
        ":" +
        String(value.minute || 0).padStart(2, "0") +
        ":00";
    }
    var d = new Date(value);
    if (isNaN(d.getTime())) return String(value);
    return d.toLocaleString("en-IN", {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch (e) {
    return String(value);
  }
}

function formatRole(role) {
  if (!role) return "Administrator";
  if (role === "super_admin") return "Administrator";
  if (role === "admin") return "Administrator";
  return role.replace(/_/g, " ").replace(/\b\w/g, function (c) {
    return c.toUpperCase();
  });
}

function normalizeAdminProfile(admin) {
  if (!admin || typeof admin !== "object") return admin;
  return Object.assign({}, admin);
}

function showGlobalToast(text, type) {
  var existing = document.getElementById("ns-global-toast");
  if (existing) existing.remove();
  var el = document.createElement("div");
  el.id = "ns-global-toast";
  el.className = "ns-global-toast " + (type || "success");
  el.textContent = text;
  document.body.appendChild(el);
  setTimeout(function () {
    el.remove();
  }, 4000);
}

function bindAdminLogout() {
  document.querySelectorAll("[data-admin-logout]").forEach(function (btn) {
    if (btn.dataset.logoutWired === "1") return;
    btn.dataset.logoutWired = "1";
    btn.addEventListener("click", function () {
      closeProfileDropdown();
      setAdminToken("");
      window.location.href = "login.html";
    });
  });
}

function applyAdminProfileToShell(admin) {
  if (!admin) return;
  admin = normalizeAdminProfile(admin);
  var name = admin.full_name || admin.email || "Admin";
  var email = admin.email || "";
  var letter = adminInitial(name);
  var shortName = name.split(" ")[0] || name;
  var roleLabel = formatRole(admin.role);
  document.querySelectorAll("[data-admin-initial]").forEach(function (el) {
    el.textContent = letter;
  });
  document.querySelectorAll("[data-admin-name]").forEach(function (el) {
    el.textContent = shortName;
  });
  document.querySelectorAll("[data-admin-full-name]").forEach(function (el) {
    el.textContent = name;
  });
  document.querySelectorAll("[data-admin-email]").forEach(function (el) {
    el.textContent = email || "—";
    if (email) el.setAttribute("title", email);
    else el.removeAttribute("title");
  });
  document.querySelectorAll("[data-admin-role-label]").forEach(function (el) {
    el.textContent = roleLabel;
  });
  document.querySelectorAll("[data-admin-sidebar-name]").forEach(function (el) {
    el.textContent = name;
    el.setAttribute("title", name);
  });
  var onProfilePage =
    document.body.getAttribute("data-page") === "profile" ||
    /admin-profile\.html/i.test(window.location.pathname || "");
  document.querySelectorAll("[data-admin-sidebar-meta]").forEach(function (el) {
    var meta = onProfilePage ? "Admin · Profile" : email || roleLabel;
    el.textContent = meta;
    el.setAttribute("title", email || roleLabel || meta);
  });
  document.querySelectorAll("[data-admin-dropdown-name]").forEach(function (el) {
    el.textContent = name;
    el.setAttribute("title", name);
  });
  document.querySelectorAll("[data-admin-dropdown-email]").forEach(function (el) {
    el.textContent = email || "—";
    if (email) el.setAttribute("title", email);
    else el.removeAttribute("title");
  });
  document.querySelectorAll("[data-admin-dropdown-role]").forEach(function (el) {
    el.textContent = roleLabel;
  });
  if (typeof refreshAdminProfilePhotoElements === "function") {
    refreshAdminProfilePhotoElements(admin);
  }
}

function unwrapAdminPayload(data) {
  if (!data || typeof data !== "object") return null;
  if (data.admin && typeof data.admin === "object") return data.admin;
  if (data.email || data.full_name || data.role) return data;
  return null;
}

async function fetchAdminMe(force) {
  if (!force && nsAdminProfileCache) return nsAdminProfileCache;
  var data = await adminApi("/api/admin/auth/me");
  nsAdminProfileCache = normalizeAdminProfile(unwrapAdminPayload(data));
  return nsAdminProfileCache;
}

var NS_SIDEBAR_SVG = {
  dashboard:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>',
  profile:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>',
  users:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
  loans:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/></svg>',
  emi:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="1" y="4" width="22" height="16" rx="2"/><path d="M1 10h22"/></svg>',
  support:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>',
  banners:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18"/><path d="M9 21V9"/></svg>',
  bell:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>',
  settings:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/></svg>',
  database:
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/><path d="M3 12c0 1.66 4 3 9 3s9-1.34 9-3"/></svg>',
};

var NS_LOAN_SUPPORT_SUBLINKS = [
  { href: "admin-loan-support.html?t=emi_collection_settings", label: "EMI collection settings" },
  { href: "admin-loan-support.html?t=user_notifications", label: "User notifications" },
  { href: "admin-loan-support.html?t=loan_guarantors", label: "Loan guarantors" },
  { href: "admin-loan-support.html?t=loan_emi_schedules", label: "Loan EMI schedules" },
  { href: "admin-loan-support.html?t=loan_apply_guide_video", label: "Loan apply guide video" },
];

function shellEscapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function shellSidebarLink(href, label, menuLabel, iconKey, extraClass) {
  var icon = NS_SIDEBAR_SVG[iconKey] || "";
  return (
    '<a class="ns-sidebar__link' +
    (extraClass ? " " + extraClass : "") +
    '" href="' +
    shellEscapeHtml(href) +
    '" data-menu-label="' +
    shellEscapeHtml(menuLabel || label) +
    '">' +
    icon +
    '<span class="nav-label">' +
    shellEscapeHtml(label) +
    "</span></a>"
  );
}

/** Same sidebar on every admin page (dashboard, loans, EMI, support tables, CMS, DB). */
function buildAdminSidebarNav() {
  if (!document.body.classList.contains("ns-admin")) return;
  var nav = document.querySelector("#admin-sidebar .ns-sidebar__nav, aside.ns-sidebar .ns-sidebar__nav");
  if (!nav || nav.getAttribute("data-ns-nav-static") === "true") return;

  var parts = [];
  var section = function (name) {
    parts.push('<div class="ns-sidebar__section">' + name + "</div>");
  };
  section("MAIN");
  parts.push(shellSidebarLink("admin-dashboard.html", "Dashboard", "Dashboard", "dashboard"));
  parts.push(shellSidebarLink("admin-notifications.html", "Notifications", "Notifications", "bell"));
  section("CUSTOMERS");
  parts.push(shellSidebarLink("admin-users.html", "Customers", "Customers", "users"));
  section("LOANS");
  parts.push(shellSidebarLink("admin-loans.html", "Loan applications", "Loan Applications", "loans"));
  parts.push(shellSidebarLink("admin-guarantors.html", "Guarantors", "Guarantors", "support"));
  section("EMI");
  parts.push(shellSidebarLink("admin-emi-payments.html", "EMI payments", "EMI Payments", "emi"));
  parts.push(shellSidebarLink("admin-emi-schedules.html", "EMI schedule", "EMI Schedule", "support"));
  section("APP");
  parts.push(shellSidebarLink("admin-banners.html", "Home banners", "Home Banners", "banners"));
  parts.push(shellSidebarLink("admin-settings.html", "Payment details & videos", "Settings", "settings"));
  section("ADMIN");
  parts.push(shellSidebarLink("admin-admins.html", "Staff accounts", "Staff Accounts", "users"));
  parts.push(shellSidebarLink("admin-database.html", "All data", "All Data", "database"));
  parts.push(shellSidebarLink("admin-profile.html", "My profile", "My Profile", "profile"));

  nav.innerHTML = parts.join("");
}

var NS_TOPBAR_SEARCH_SVG =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>';

var NS_PROFILE_DROPDOWN_HTML =
  '<div class="ns-topbar__dropdown" id="profile-dropdown" role="menu">' +
  '<div class="ns-dropdown__head">' +
  '<div class="ns-dropdown__avatar" data-admin-initial aria-hidden="true">A</div>' +
  '<div class="ns-dropdown__meta">' +
  '<p class="ns-dropdown__name" data-admin-dropdown-name>Admin</p>' +
  '<p class="ns-dropdown__email" data-admin-dropdown-email>—</p>' +
  '<span class="ns-dropdown__badge" data-admin-dropdown-role>Administrator</span>' +
  "</div></div>" +
  '<div class="ns-dropdown__divider" role="separator"></div>' +
  '<div class="ns-dropdown__section-label">Account</div>' +
  '<a href="admin-dashboard.html" class="ns-dropdown__item" data-nav="dashboard" role="menuitem">' +
  '<span class="ns-dropdown__icon" aria-hidden="true">▦</span>Dashboard</a>' +
  '<a href="admin-profile.html" class="ns-dropdown__item" data-nav="profile" role="menuitem">' +
  '<span class="ns-dropdown__icon" aria-hidden="true">👤</span>My profile</a>' +
  '<div class="ns-dropdown__divider" role="separator"></div>' +
  '<button type="button" class="ns-dropdown__item ns-dropdown__item--logout" data-admin-logout role="menuitem">' +
  '<span class="ns-dropdown__icon" aria-hidden="true">⎋</span>Logout</button></div>';

var NS_TOPBAR_BELL_HTML =
  '<button type="button" class="ns-topbar__bell" aria-label="Notifications">' +
  '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">' +
  '<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>' +
  '<span class="ns-topbar__bell-badge" id="notif-badge">0</span></button>';

function ensureAdminTopbarBell() {
  var actions = document.querySelector(".ns-topbar__actions");
  if (!actions || actions.querySelector(".ns-topbar__bell")) return;
  actions.insertAdjacentHTML("afterbegin", NS_TOPBAR_BELL_HTML);
}

/** Fix pages with bare search input / missing profile dropdown (loan-support, EMI, etc.). */
function normalizeAdminTopbar() {
  if (!document.body.classList.contains("ns-admin")) return;
  ensureAdminTopbarBell();
  var searchCenter = document.querySelector(".ns-topbar__search-center");
  if (searchCenter) {
    var inp = document.getElementById("sidebar-menu-search");
    if (inp && !searchCenter.querySelector(".ns-topbar__search")) {
      var wrap = document.createElement("div");
      wrap.className = "ns-topbar__search";
      wrap.innerHTML = NS_TOPBAR_SEARCH_SVG;
      searchCenter.innerHTML = "";
      wrap.appendChild(inp);
      if (!inp.getAttribute("placeholder")) {
        inp.setAttribute("placeholder", "Search sidebar menu...");
      }
      searchCenter.appendChild(wrap);
    }
  }
  var userMenu = document.querySelector(".ns-topbar__user-menu");
  var profileBtn = document.getElementById("profile-menu-btn");
  if (profileBtn && !document.getElementById("profile-dropdown")) {
    if (!userMenu) {
      userMenu = document.createElement("div");
      userMenu.className = "ns-topbar__user-menu";
      profileBtn.parentNode.insertBefore(userMenu, profileBtn);
      userMenu.appendChild(profileBtn);
    }
    userMenu.insertAdjacentHTML("beforeend", NS_PROFILE_DROPDOWN_HTML);
    if (!profileBtn.querySelector(".ns-topbar__user-caret")) {
      profileBtn.insertAdjacentHTML(
        "beforeend",
        '<svg class="ns-topbar__user-caret" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><polyline points="6 9 12 15 18 9"/></svg>'
      );
    }
    if (!profileBtn.getAttribute("aria-haspopup")) {
      profileBtn.setAttribute("aria-haspopup", "true");
      profileBtn.setAttribute("aria-expanded", "false");
      profileBtn.setAttribute("aria-controls", "profile-dropdown");
    }
  }
}

function upgradeAdminSidebarChrome() {
  var brand = document.querySelector(".ns-sidebar__brand");
  if (brand && !brand.querySelector(".ns-sidebar__brand-name")) {
    brand.insertAdjacentHTML(
      "beforeend",
      '<p class="ns-sidebar__brand-name ns-sidebar__brand-name--sr">Narainsons</p>'
    );
  }
  var footer = document.querySelector(".ns-sidebar__footer");
  if (footer && !footer.querySelector(".ns-sidebar__footer-info")) {
    var nameEl = footer.querySelector(".ns-sidebar__footer-name");
    if (nameEl && nameEl.parentNode === footer) {
      var info = document.createElement("div");
      info.className = "ns-sidebar__footer-info";
      footer.insertBefore(info, nameEl);
      info.appendChild(nameEl);
      if (!footer.querySelector("[data-admin-sidebar-meta]")) {
        var meta = document.createElement("div");
        meta.className = "ns-sidebar__footer-role";
        meta.setAttribute("data-admin-sidebar-meta", "");
        meta.textContent = "Admin";
        info.appendChild(meta);
      }
    }
  }
}

async function initAdminShell(options) {
  options = options || {};
  upgradeAdminSidebarChrome();
  normalizeAdminTopbar();
  buildAdminSidebarNav();
  if (!requireAdminAuth()) return null;
  initAdminChromeEarly();
  initSidebarToggle();
  initSidebarMenuFilter();
  initSidebarLinkTitles();
  markSidebarActivePage();
  initMobileSidebarDefault();
  markProfileDropdownActivePage();

  try {
    var admin = await fetchAdminMe(false);
    applyAdminProfileToShell(admin);
    if (typeof options.onReady === "function") {
      options.onReady(admin);
    }
    return admin;
  } catch (err) {
    var onUsersPage = document.body.getAttribute("data-page") === "users";
    if (onUsersPage) {
      showGlobalToast(err.message || "Could not load admin profile — please sign in again.", "error");
      if (typeof options.onReady === "function") {
        options.onReady(null);
      }
      return null;
    }
    showGlobalToast(err.message || "Session expired", "error");
    if (typeof redirectToAdminLogin === "function") {
      redirectToAdminLogin();
    } else {
      setAdminToken("");
      window.location.href = "login.html";
    }
    return null;
  }
}

/** Tooltip for each sidebar link, useful when the sidebar is collapsed. */
function initSidebarLinkTitles() {
  document.querySelectorAll(".ns-sidebar__link").forEach(function (link) {
    var label = link.getAttribute("data-menu-label") || link.textContent.trim();
    if (label && !link.getAttribute("title")) link.setAttribute("title", label);
  });
}

/** Highlight sidebar link for the current admin HTML page (not hash panels). */
function markSidebarActivePage() {
  var page = document.body.getAttribute("data-page") || "";
  var fileByPage = {
    dashboard: "admin-dashboard.html",
    users: "admin-users.html",
    "user-view": "admin-users.html",
    banners: "admin-banners.html",
    loans: "admin-loans.html",
    emi: "admin-emi-payments.html",
    "loan-view": "admin-loans.html",
    notifications: "admin-notifications.html",
    guarantors: "admin-guarantors.html",
    schedules: "admin-emi-schedules.html",
    settings: "admin-settings.html",
    admins: "admin-admins.html",
    database: "admin-database.html",
    profile: "admin-profile.html",
  };
  var activeFile = fileByPage[page];
  var locPath = window.location.pathname || "";
  var locSearch = window.location.search || "";
  document.querySelectorAll(".ns-sidebar__link[href]").forEach(function (link) {
    var raw = (link.getAttribute("href") || "").trim();
    var href = raw.split("#")[0].trim();
    var isActive = false;
    if (link.getAttribute("data-ns-support-sub") === "1") {
      try {
        var u = new URL(raw, window.location.href);
        isActive =
          page === "loan-support" &&
          locPath.indexOf("admin-loan-support") !== -1 &&
          u.search === locSearch;
      } catch (e) {
        isActive = false;
      }
    } else if (activeFile && href === activeFile) {
      isActive =
        page !== "loan-support" ||
        raw.indexOf("?") === -1 ||
        !locSearch;
    } else if (page === "dashboard" && href.indexOf("admin-dashboard.html") === 0 && raw.indexOf("#") === -1) {
      isActive = true;
    } else if (page === "loan-support" && href === "admin-loan-support.html" && !locSearch) {
      isActive = true;
    }
    link.classList.toggle("is-active", isActive);
  });
  if (page === "loan-support") {
    var group = document.querySelector("[data-ns-support-group]");
    if (group) group.open = true;
  }
}

function initSidebarMenuFilter() {
  var input = document.getElementById("sidebar-menu-search");
  if (!input) return;
  input.setAttribute("placeholder", "Search menu…");
  input.setAttribute("aria-label", "Search the sidebar menu");
  // Enter opens the first match, Escape clears the search.
  input.addEventListener("keydown", function (e) {
    if (e.key === "Enter") {
      e.preventDefault();
      var first = Array.prototype.find.call(document.querySelectorAll(".ns-sidebar__link[data-menu-label]"), function (link) {
        return link.style.display !== "none";
      });
      if (first && input.value.trim()) window.location.href = first.getAttribute("href");
    } else if (e.key === "Escape") {
      input.value = "";
      input.dispatchEvent(new Event("input"));
    }
  });
  input.addEventListener("input", function () {
    var q = input.value.trim().toLowerCase();
    document.querySelectorAll(".ns-sidebar__link[data-menu-label]").forEach(function (link) {
      var label = (link.getAttribute("data-menu-label") || link.textContent).toLowerCase();
      var show = !q || label.indexOf(q) !== -1;
      link.style.display = show ? "" : "none";
    });
    document.querySelectorAll(".ns-sidebar__section").forEach(function (sec) {
      var next = sec.nextElementSibling;
      var anyVisible = false;
      while (next && !next.classList.contains("ns-sidebar__section")) {
        if (next.classList.contains("ns-sidebar__link") && next.style.display !== "none") {
          anyVisible = true;
          break;
        }
        if (next.classList.contains("ns-sidebar__details")) {
          var subVisible = false;
          next.querySelectorAll(".ns-sidebar__sublink").forEach(function (sub) {
            if (sub.style.display !== "none") subVisible = true;
          });
          if (subVisible) {
            anyVisible = true;
            next.open = true;
          }
          next.style.display = !q || subVisible ? "" : "none";
        }
        next = next.nextElementSibling;
      }
      sec.style.display = anyVisible || !q ? "" : "none";
    });
    var visible = Array.prototype.some.call(document.querySelectorAll(".ns-sidebar__link[data-menu-label]"), function (link) {
      return link.style.display !== "none";
    });
    var nav = document.querySelector(".ns-sidebar__nav");
    var none = document.getElementById("ns-menu-none");
    if (!none && nav) {
      nav.insertAdjacentHTML("beforeend", '<p class="nav-none" id="ns-menu-none" hidden>No menu item matches.</p>');
      none = document.getElementById("ns-menu-none");
    }
    if (none) none.hidden = visible;
  });
}

function passwordEyeToggleMarkup() {
  var show =
    '<svg class="ns-eye-icon ns-eye-icon--show icon-eye icon-eye--show" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.25" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"/>' +
    '<circle cx="12" cy="12" r="3.25"/></svg>';
  var hide =
    '<svg class="ns-eye-icon ns-eye-icon--hide icon-eye icon-eye--hide hidden" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.25" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/>' +
    '<path d="M10.73 5.08A10.07 10.07 0 0 1 12 5c7 0 10 7 10 7a14.2 14.2 0 0 1-2.08 3.15"/>' +
    '<path d="M6.61 6.61A14.2 14.2 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61"/>' +
    '<line x1="2" y1="2" x2="22" y2="22"/></svg>';
  return show + hide;
}

function upgradePasswordToggleButtons() {
  document.querySelectorAll(".ns-input-wrap__toggle").forEach(function (button) {
    if (button.dataset.eyeUpgraded === "1") return;
    if (button.querySelector(".ns-eye-icon, .icon-eye")) {
      button.dataset.eyeUpgraded = "1";
      return;
    }
    button.dataset.eyeUpgraded = "1";
    button.innerHTML = passwordEyeToggleMarkup();
  });
}

function syncPasswordToggleUi(button, input) {
  var revealed = input.type === "text";
  var showIcon = button.querySelector(".ns-eye-icon--show, .icon-eye--show");
  var hideIcon = button.querySelector(".ns-eye-icon--hide, .icon-eye--hide");
  if (showIcon) showIcon.classList.toggle("hidden", revealed);
  if (hideIcon) hideIcon.classList.toggle("hidden", !revealed);
  button.classList.toggle("is-revealed", revealed);
  button.setAttribute("aria-label", revealed ? "Hide password" : "Show password");
  button.setAttribute("title", revealed ? "Hide password" : "Show password");
  button.setAttribute("aria-pressed", revealed ? "true" : "false");
}

function initPasswordToggleDelegation() {
  if (window.__nsPasswordToggleDelegation) return;
  window.__nsPasswordToggleDelegation = true;
  document.addEventListener(
    "click",
    function (e) {
      var button = e.target.closest(".ns-input-wrap__toggle");
      if (!button) return;
      e.preventDefault();
      var targetId = button.getAttribute("data-toggle-for") || "password";
      var input = document.getElementById(targetId);
      if (!input) return;
      var reveal = input.type === "password";
      input.type = reveal ? "text" : "password";
      syncPasswordToggleUi(button, input);
    },
    false
  );
}

function wirePasswordToggles() {
  upgradePasswordToggleButtons();
  initPasswordToggleDelegation();
  document.querySelectorAll(".ns-input-wrap__toggle").forEach(function (button) {
    var targetId = button.getAttribute("data-toggle-for") || "password";
    var input = document.getElementById(targetId);
    if (input) syncPasswordToggleUi(button, input);
  });
}

/** Topbar user menu + logout on admin pages (before async /me). */
function initAdminChromeEarly() {
  if (!document.body.classList.contains("ns-admin")) return;
  if (!getAdminToken()) return;
  bindAdminLogout();
  initProfileDropdown();
  wirePasswordToggles();
}

function initMobileSidebarDefault() {
  if (window.matchMedia("(max-width: 768px)").matches) {
    document.body.classList.add("ns-admin--sidebar-collapsed");
    document.body.classList.remove("ns-admin--sidebar-mini");
  }
}

function initSidebarToggle() {
  var toggle = document.getElementById("sidebar-toggle");
  var overlay = document.getElementById("sidebar-overlay");
  if (!toggle) return;

  // Remember icons-only mode between pages.
  try {
    if (localStorage.getItem("ns_sidebar_mini") === "1" && !window.matchMedia("(max-width: 991px)").matches) {
      document.body.classList.add("ns-admin--sidebar-mini");
    }
  } catch (err) {}

  toggle.addEventListener("click", function (e) {
    e.stopPropagation();
    var mobile = window.matchMedia("(max-width: 768px)").matches;
    if (!mobile) {
      try {
        localStorage.setItem("ns_sidebar_mini", document.body.classList.contains("ns-admin--sidebar-mini") ? "0" : "1");
      } catch (err) {}
    }
    if (mobile) {
      var open = document.body.classList.toggle("ns-admin--sidebar-open");
      document.body.classList.toggle("ns-admin--sidebar-collapsed", !open);
      if (overlay) overlay.setAttribute("aria-hidden", open ? "false" : "true");
    } else {
      document.body.classList.toggle("ns-admin--sidebar-mini");
      document.body.classList.remove("ns-admin--sidebar-open");
      document.body.classList.remove("ns-admin--sidebar-collapsed");
      if (overlay) overlay.setAttribute("aria-hidden", "true");
      toggle.setAttribute(
        "aria-label",
        document.body.classList.contains("ns-admin--sidebar-mini")
          ? "Expand sidebar"
          : "Collapse sidebar to icons"
      );
    }
  });

  if (overlay) {
    overlay.addEventListener("click", function () {
      document.body.classList.add("ns-admin--sidebar-collapsed");
      document.body.classList.remove("ns-admin--sidebar-open");
      overlay.setAttribute("aria-hidden", "true");
    });
  }
}

function closeProfileDropdown() {
  var btn = document.getElementById("profile-menu-btn");
  var menu = document.getElementById("profile-dropdown");
  if (!btn || !menu) return;
  menu.classList.remove("is-open");
  btn.setAttribute("aria-expanded", "false");
  btn.classList.remove("is-open");
}

function markProfileDropdownActivePage() {
  var page = document.body.getAttribute("data-page") || "";
  document.querySelectorAll(".ns-topbar__dropdown a.ns-dropdown__item[data-nav]").forEach(function (link) {
    link.classList.toggle("is-active", link.getAttribute("data-nav") === page);
  });
}

function initProfileDropdown() {
  var btn = document.getElementById("profile-menu-btn");
  var menu = document.getElementById("profile-dropdown");
  if (!btn || !menu) return;
  if (menu.dataset.dropdownWired === "1") return;
  menu.dataset.dropdownWired = "1";
  menu.classList.remove("hidden");
  markProfileDropdownActivePage();

  btn.addEventListener("click", function (e) {
    e.preventDefault();
    e.stopPropagation();
    var willOpen = !menu.classList.contains("is-open");
    if (willOpen) {
      menu.classList.add("is-open");
      btn.setAttribute("aria-expanded", "true");
      btn.classList.add("is-open");
    } else {
      closeProfileDropdown();
    }
  });

  menu.addEventListener("click", function (e) {
    e.stopPropagation();
  });

  document.addEventListener("click", function (e) {
    if (!menu.classList.contains("is-open")) return;
    if (btn.contains(e.target) || menu.contains(e.target)) return;
    closeProfileDropdown();
  });

  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") closeProfileDropdown();
  });

  menu.querySelectorAll("a.ns-dropdown__item").forEach(function (link) {
    link.addEventListener("click", function () {
      closeProfileDropdown();
    });
  });
}

document.addEventListener("DOMContentLoaded", function () {
  if (document.body.classList.contains("ns-auth")) {
    wirePasswordToggles();
  }
});
