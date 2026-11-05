/** Backend API — NarainsonsProject (Tomcat). Login par override kar sakte ho. */
(function () {
  var stored = localStorage.getItem("ns_api_base");
  if (stored) {
    window.NS_API_BASE = stored;
    return;
  }
  var path = window.location.pathname || "";
  var adminIdx = path.indexOf("/admin/");
  if (adminIdx > 0) {
    window.NS_API_BASE = window.location.origin + path.substring(0, adminIdx);
    return;
  }
  window.NS_API_BASE = "http://localhost:8082/NarainsonsProject";
})();

/** Returns the URL if it is http(s) or relative; "" for javascript:, data: and the like. */
function nsSafeUrl(url) {
  var u = String(url == null ? "" : url).trim();
  if (!u) return "";
  if (/^https?:\/\//i.test(u)) return u;
  if (/^[a-z][a-z0-9+.-]*:/i.test(u) || u.indexOf("//") === 0) return "";
  return u;
}

window.NS_ADMIN_TOKEN_KEY = "ns_admin_access_token";
window.NS_ADMIN_RETURN_KEY = "ns_admin_return_after_login";

/** Login ke baad wapas isi admin HTML page par bhejne ke liye (relative path). */
function saveAdminReturnUrl() {
  try {
    var path = window.location.pathname || "";
    var idx = path.indexOf("/admin/");
    var rel = idx >= 0 ? path.substring(idx + "/admin/".length) : path.replace(/^\//, "");
    if (!rel || rel.indexOf("login.html") === 0) return;
    rel += window.location.search || "";
    sessionStorage.setItem(window.NS_ADMIN_RETURN_KEY, rel);
  } catch (e) {}
}

function consumeAdminReturnUrl() {
  try {
    var rel = sessionStorage.getItem(window.NS_ADMIN_RETURN_KEY);
    sessionStorage.removeItem(window.NS_ADMIN_RETURN_KEY);
    if (!rel || rel.indexOf("login.html") !== -1) return null;
    return rel;
  } catch (e) {
    return null;
  }
}

function redirectToAdminLogin() {
  saveAdminReturnUrl();
  setAdminToken("");
  window.location.href = "login.html";
}

function getAdminToken() {
  return localStorage.getItem(window.NS_ADMIN_TOKEN_KEY) || "";
}

function setAdminToken(token) {
  if (token) localStorage.setItem(window.NS_ADMIN_TOKEN_KEY, token);
  else localStorage.removeItem(window.NS_ADMIN_TOKEN_KEY);
}

async function adminApi(path, options) {
  const opts = options || {};
  const headers = Object.assign({ "Content-Type": "application/json" }, opts.headers || {});
  const token = getAdminToken();
  if (token) headers.Authorization = "Bearer " + token;
  const url = (window.NS_API_BASE || "").replace(/\/$/, "") + path;
  let res;
  try {
    res = await fetch(url, Object.assign({}, opts, { headers }));
  } catch (netErr) {
    throw new Error("Could not reach the server. Please try again in a moment.");
  }
  const json = await res.json().catch(function () {
    return {};
  });
  if (!res.ok || json.success === false) {
    var msg = json.message || res.statusText || "Request failed";
    if (res.status === 401) {
      msg = "Your session has expired. Please sign in again.";
    } else if (res.status === 404 && !json.message) {
      msg = "This feature is not available.";
    }
    var failure = new Error(msg);
    failure.status = res.status;
    throw failure;
  }
  return json.data;
}

/** Uploaded user files need the admin token, so they are fetched and shown as blobs. */
function nsIsProtectedFile(url) {
  return String(url || "").indexOf("/api/v1/files/") !== -1;
}

async function nsProtectedBlobUrl(url) {
  var res = await fetch(url, { headers: { Authorization: "Bearer " + getAdminToken() } });
  if (!res.ok) throw new Error("Could not open this file");
  return URL.createObjectURL(await res.blob());
}

function nsHydrateProtectedImages(root) {
  root.querySelectorAll("img[data-protected-src]").forEach(function (img) {
    nsProtectedBlobUrl(img.getAttribute("data-protected-src"))
      .then(function (blobUrl) {
        img.src = blobUrl;
      })
      .catch(function () {});
  });
}

/** Any element with data-protected-file opens that file in a new tab. */
document.addEventListener("click", function (e) {
  var el = e.target.closest ? e.target.closest("[data-protected-file]") : null;
  if (!el) return;
  e.preventDefault();
  nsProtectedBlobUrl(el.getAttribute("data-protected-file"))
    .then(function (blobUrl) {
      window.open(blobUrl, "_blank", "noopener");
    })
    .catch(function (err) {
      if (typeof showGlobalToast === "function") showGlobalToast(err.message, "error");
    });
});

var nsAdminPhotoObjectUrl = null;

async function adminUploadBannerImage(file) {
  var fd = new FormData();
  fd.append("image", file);
  var token = getAdminToken();
  var res = await fetch((window.NS_API_BASE || "").replace(/\/$/, "") + "/api/admin/banners/upload-image", {
    method: "POST",
    headers: token ? { Authorization: "Bearer " + token } : {},
    body: fd,
  });
  var json = await res.json().catch(function () {
    return {};
  });
  if (!res.ok || json.success === false) {
    throw new Error(json.message || "Banner image upload failed");
  }
  return json.data;
}

async function adminUploadProfilePhoto(file) {
  var fd = new FormData();
  fd.append("photo", file);
  var token = getAdminToken();
  var res = await fetch(window.NS_API_BASE + "/api/admin/auth/profile/photo", {
    method: "POST",
    headers: token ? { Authorization: "Bearer " + token } : {},
    body: fd,
  });
  var json = await res.json().catch(function () {
    return {};
  });
  if (!res.ok || json.success === false) {
    throw new Error(json.message || "Photo upload failed");
  }
  return json.data;
}

async function refreshAdminProfilePhotoElements(admin) {
  var targets = document.querySelectorAll("img[data-admin-photo]");
  if (!targets.length) return;
  var usePhoto = admin && admin.has_profile_photo && getAdminToken();
  if (!usePhoto) {
    targets.forEach(function (img) {
      var fallback = img.getAttribute("data-default-src") || "assets/img/logo.jpg";
      img.src = fallback;
    });
    return;
  }
  try {
    var res = await fetch(window.NS_API_BASE + "/api/admin/auth/profile/photo", {
      headers: { Authorization: "Bearer " + getAdminToken() },
    });
    if (!res.ok) return;
    var blob = await res.blob();
    if (nsAdminPhotoObjectUrl) URL.revokeObjectURL(nsAdminPhotoObjectUrl);
    nsAdminPhotoObjectUrl = URL.createObjectURL(blob);
    targets.forEach(function (img) {
      img.src = nsAdminPhotoObjectUrl;
    });
    var preview = document.getElementById("profile-photo-preview-img");
    var empty = document.getElementById("profile-photo-preview-empty");
    if (preview) {
      preview.src = nsAdminPhotoObjectUrl;
      preview.classList.remove("hidden");
    }
    if (empty) empty.classList.add("hidden");
  } catch (e) {
    /* keep preview / default */
  }
}

function saveApiBase() {
  const el = document.getElementById("api-base-input");
  if (el && el.value.trim()) {
    localStorage.setItem("ns_api_base", el.value.trim().replace(/\/$/, ""));
    window.NS_API_BASE = localStorage.getItem("ns_api_base");
  }
}
