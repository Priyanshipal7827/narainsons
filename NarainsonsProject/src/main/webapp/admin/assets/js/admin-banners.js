var nsBannersList = [];
var nsBannersCrudEnabled = false;
var nsBannersSearchQuery = "";
var nsBannerPendingFile = null;
var nsBannerObjectUrl = null;

document.addEventListener("DOMContentLoaded", function () {
  if (document.body.getAttribute("data-page") !== "banners") return;
  initAdminChromeEarly();
  if (!getAdminToken()) {
    window.location.href = "login.html";
    return;
  }
  document.getElementById("btn-banners-refresh").addEventListener("click", loadBanners);
  bindBannerCrudUi();
  bindBannerSearch();
  loadBanners();
  initAdminShell({
    onReady: function () {
      initSidebarMenuFilter();
    },
  });
});

function bindBannerCrudUi() {
  var createBtn = document.getElementById("btn-banner-create");
  if (createBtn) {
    createBtn.addEventListener("click", function () {
      if (!nsBannersCrudEnabled) {
        showGlobalToast("Editing is unavailable until the server is updated.", "error");
        return;
      }
      openBannerModal(null);
    });
  }
  var form = document.getElementById("banner-form");
  if (form) {
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      saveBannerForm();
    });
  }
  document.querySelectorAll("[data-banner-modal-close]").forEach(function (el) {
    el.addEventListener("click", closeBannerModal);
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") closeBannerModal();
  });
  var imgInput = document.getElementById("banner-image-url");
  if (imgInput) {
    imgInput.addEventListener("input", function () {
      if (imgInput.value.trim()) {
        clearBannerFileSelection(false);
      }
      updateBannerImagePreview();
    });
    imgInput.addEventListener("blur", function () {
      var normalized = normalizeExternalImageUrl(imgInput.value.trim());
      if (normalized && normalized !== imgInput.value.trim()) {
        imgInput.value = normalized;
        updateBannerImagePreview();
        showGlobalToast("Converted Google link to a direct image URL", "success");
      }
    });
  }
  var fileInput = document.getElementById("banner-image-file");
  if (fileInput) {
    fileInput.addEventListener("change", function () {
      var file = fileInput.files && fileInput.files[0];
      if (file) onBannerFilePicked(file);
    });
  }
  var zone = document.getElementById("banner-upload-zone");
  if (zone && fileInput) {
    zone.addEventListener("dragover", function (e) {
      e.preventDefault();
      zone.classList.add("is-dragover");
    });
    zone.addEventListener("dragleave", function () {
      zone.classList.remove("is-dragover");
    });
    zone.addEventListener("drop", function (e) {
      e.preventDefault();
      zone.classList.remove("is-dragover");
      var file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
      if (file) onBannerFilePicked(file);
    });
  }
}

function clearBannerFileSelection(clearPreview) {
  nsBannerPendingFile = null;
  if (nsBannerObjectUrl) {
    URL.revokeObjectURL(nsBannerObjectUrl);
    nsBannerObjectUrl = null;
  }
  var fileInput = document.getElementById("banner-image-file");
  if (fileInput) fileInput.value = "";
  var nameEl = document.getElementById("banner-file-name");
  if (nameEl) nameEl.textContent = "";
  if (clearPreview !== false) {
    updateBannerImagePreview();
  }
}

function onBannerFilePicked(file) {
  if (!file || !file.type || file.type.indexOf("image/") !== 0) {
    showGlobalToast("Please choose an image file (JPG, PNG, WebP, or GIF)", "error");
    return;
  }
  clearBannerFileSelection(false);
  nsBannerPendingFile = file;
  nsBannerObjectUrl = URL.createObjectURL(file);
  var nameEl = document.getElementById("banner-file-name");
  if (nameEl) nameEl.textContent = file.name;
  document.getElementById("banner-image-url").value = "";
  showBannerPreviewSrc(nsBannerObjectUrl);
}

/**
 * Google Images page / redirect links ko direct image URL mein badalne ki koshish.
 * Hotlink ke liye img par referrerpolicy="no-referrer" zaroori hai.
 */
function normalizeExternalImageUrl(raw) {
  var s = (raw || "").trim();
  if (!s) return "";
  if (!/^https?:\/\//i.test(s) && !/^data:/i.test(s)) {
    if (/^[\w.-]+\.[a-z]{2,}/i.test(s)) {
      s = "https://" + s;
    }
  }
  try {
    var u = new URL(s);
    var host = u.hostname.toLowerCase();
    if (host.indexOf("google.") !== -1 || host === "goo.gl") {
      var imgurl = u.searchParams.get("imgurl") || u.searchParams.get("url");
      if (imgurl) {
        var decoded = imgurl;
        try {
          decoded = decodeURIComponent(imgurl);
        } catch (e) {}
        if (/^https?:\/\//i.test(decoded)) {
          return decoded.trim();
        }
      }
      if (host.indexOf("images.google.") !== -1 || u.pathname.indexOf("/imgres") !== -1) {
        return s;
      }
    }
  } catch (e) {}
  return s;
}

function resolveBannerImageUrl(url) {
  if (!url) return "";
  var u = normalizeExternalImageUrl(String(url).trim());
  if (/^https?:\/\//i.test(u) || u.indexOf("data:") === 0) return u;
  if (u.indexOf("/") === 0) return (window.location.origin || "") + u;
  return u;
}

function isLikelyDirectImageUrl(url) {
  if (!url) return false;
  var u = url.toLowerCase();
  if (/\.(jpe?g|png|webp|gif|avif)(\?|$)/i.test(u)) return true;
  if (u.indexOf("gstatic.com") !== -1 || u.indexOf("googleusercontent.com") !== -1) return true;
  if (u.indexOf("/api/media/banners/") !== -1) return true;
  return false;
}

var nsBannerUrlHintDefaultHtml = "";

function setBannerUrlHint(kind, message) {
  var el = document.getElementById("banner-image-url-hint");
  if (!el) return;
  if (!nsBannerUrlHintDefaultHtml) nsBannerUrlHintDefaultHtml = el.innerHTML;
  el.classList.remove("is-warn", "is-ok");
  if (kind === "warn") el.classList.add("is-warn");
  if (kind === "ok") el.classList.add("is-ok");
  if (message) {
    el.textContent = message;
  } else if (!kind) {
    el.innerHTML = nsBannerUrlHintDefaultHtml;
  }
}

function showBannerPreviewSrc(src) {
  var wrap = document.getElementById("banner-image-preview");
  var img = document.getElementById("banner-image-preview-img");
  if (!wrap || !img) return;
  if (!src) {
    wrap.classList.remove("is-loaded");
    img.removeAttribute("src");
    setBannerUrlHint("", null);
    return;
  }
  img.referrerPolicy = "no-referrer";
  img.onload = function () {
    wrap.classList.add("is-loaded");
    setBannerUrlHint("ok", "Image preview OK — save kar sakte ho.");
  };
  img.onerror = function () {
    wrap.classList.remove("is-loaded");
    setBannerUrlHint(
      "warn",
      "This URL does not open as an image (page link or blocked hotlink). Use Copy image address or upload a file."
    );
  };
  img.src = src;
  if (!isLikelyDirectImageUrl(src) && src.indexOf("data:") !== 0) {
    setBannerUrlHint(
      "warn",
      "This may not be a direct image URL — preview might fail. Upload is recommended."
    );
  }
}

function bindBannerSearch() {
  var input = document.getElementById("banner-search");
  if (!input) return;
  input.addEventListener("input", function () {
    nsBannersSearchQuery = input.value.trim().toLowerCase();
    renderBannersGrid(filterBanners(nsBannersList));
  });
}

function filterBanners(rows) {
  if (!nsBannersSearchQuery) return rows;
  return rows.filter(function (b) {
    var hay =
      String(b.id || "") +
      " " +
      (b.title || "") +
      " " +
      (b.subtitle || "") +
      " " +
      (b.action_value || "");
    return hay.toLowerCase().indexOf(nsBannersSearchQuery) !== -1;
  });
}

function updateBannerImagePreview() {
  if (nsBannerObjectUrl) {
    showBannerPreviewSrc(nsBannerObjectUrl);
    return;
  }
  var url = resolveBannerImageUrl((document.getElementById("banner-image-url").value || "").trim());
  showBannerPreviewSrc(url || "");
}

async function loadBanners() {
  var grid = document.getElementById("banners-grid");
  var meta = document.getElementById("banners-meta");
  grid.innerHTML = '<div class="ns-empty ns-empty--loading"><span class="ns-spinner"></span> Fetching banners…</div>';
  if (meta) meta.textContent = "Loading…";
  try {
    var loaded = await fetchBannersPayload();
    var data = loaded.data;
    var via = loaded.via;
    nsBannersCrudEnabled = !!loaded.crud;
    updateCrudHint();
    nsBannersList = sortBannerRows(Array.isArray(data.banners) ? data.banners : []);
    var stats = data.stats || statsFromBannerRows(nsBannersList);
    setText("stat-banners-total", stats.total != null ? stats.total : nsBannersList.length);
    setText("stat-banners-active", stats.active != null ? stats.active : "0");
    setText("stat-banners-inactive", stats.inactive != null ? stats.inactive : "0");
    if (meta) {
      meta.textContent = nsBannersList.length + " banner(s)";
    }
    renderBannersGrid(filterBanners(nsBannersList));
  } catch (err) {
    if (isAdminUnauthorized(err)) {
      showGlobalToast("Your session has expired. Please sign in again.", "error");
      window.location.href = "login.html";
      return;
    }
    grid.innerHTML =
      '<div class="ns-empty ns-empty--error"><p><strong>Could not load banners</strong></p><p>' +
      escapeHtml(err.message) +
      '</p><button type="button" class="ns-btn ns-btn--primary ns-btn--sm" id="btn-banners-retry">Retry</button></div>';
    document.getElementById("btn-banners-retry").addEventListener("click", loadBanners);
    showGlobalToast(err.message, "error");
  }
}

function updateCrudHint() {
  var hint = document.getElementById("banners-crud-hint");
  var createBtn = document.getElementById("btn-banner-create");
  if (createBtn) createBtn.disabled = !nsBannersCrudEnabled;
  if (!hint) return;
  if (nsBannersCrudEnabled) {
    hint.classList.add("hidden");
    hint.textContent = "";
    return;
  }
  hint.classList.remove("hidden");
  hint.textContent = "View-only mode — editing and uploads will be enabled later.";
}

function isAdminUnauthorized(err) {
  var msg = (err && err.message) || "";
  return msg.indexOf("401") !== -1 || msg.toLowerCase().indexOf("unauthorized") !== -1;
}

async function fetchBannersPayload() {
  try {
    var data = await adminApi("/api/admin/banners?limit=100");
    return { data: data, via: "banners", crud: true };
  } catch (bannerErr) {
    if (isAdminUnauthorized(bannerErr)) throw bannerErr;
  }
  var table = await adminApi("/api/admin/database/dashboard_banners?limit=100");
  var rows = Array.isArray(table.rows) ? table.rows : [];
  return {
    data: { banners: rows, stats: statsFromBannerRows(rows, table.count) },
    via: "database",
    crud: false,
  };
}

function sortBannerRows(rows) {
  return rows.slice().sort(function (a, b) {
    var sa = a.sort_order != null ? Number(a.sort_order) : 0;
    var sb = b.sort_order != null ? Number(b.sort_order) : 0;
    if (sa !== sb) return sa - sb;
    return Number(a.id || 0) - Number(b.id || 0);
  });
}

function statsFromBannerRows(rows, totalCount) {
  var total = totalCount != null ? Number(totalCount) : rows.length;
  var active = 0;
  rows.forEach(function (r) {
    if (r.is_active === true || r.is_active === 1 || r.is_active === "1") active++;
  });
  return { total: total, active: active, inactive: Math.max(0, total - active) };
}

function setText(id, v) {
  var el = document.getElementById(id);
  if (el) el.textContent = String(v);
}

function renderBannersGrid(banners) {
  var grid = document.getElementById("banners-grid");
  if (!banners.length) {
    var msg = nsBannersSearchQuery
      ? "No banners match this search."
      : nsBannersCrudEnabled
        ? 'No banners yet. Use <strong>New banner</strong> to add the first one.'
        : "No banners have been configured yet.";
    grid.innerHTML = '<div class="ns-empty">' + msg + "</div>";
    return;
  }
  grid.innerHTML = banners
    .map(function (b) {
      return bannerCardHtml(b);
    })
    .join("");
  grid.querySelectorAll("[data-banner-edit]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      openBannerModal(btn.getAttribute("data-banner-edit"));
    });
  });
  grid.querySelectorAll("[data-banner-delete]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      deleteBanner(btn.getAttribute("data-banner-delete"));
    });
  });
}

function bannerCardHtml(b) {
  var active = b.is_active === true || b.is_active === 1 || b.is_active === "1";
  var img = resolveBannerImageUrl(b.image_url || "");
  var actionLabel = formatAction(b.action_type, b.action_value);
  var crud =
    nsBannersCrudEnabled
      ? '<div class="ns-banner-card__actions ns-no-print">' +
        '<button type="button" class="ns-btn ns-btn--outline ns-btn--sm" data-banner-edit="' +
        escapeAttr(String(b.id)) +
        '">Edit</button>' +
        '<button type="button" class="ns-btn ns-btn--danger ns-btn--sm" data-banner-delete="' +
        escapeAttr(String(b.id)) +
        '">Delete</button>' +
        "</div>"
      : "";
  return (
    '<article class="ns-banner-card' +
    (active ? "" : " ns-banner-card--inactive") +
    '">' +
    '<div class="ns-banner-card__media">' +
    (img
      ? '<div class="ns-banner-card__media-inner">' +
        '<img src="' +
        escapeAttr(img) +
        '" alt="" loading="lazy" referrerpolicy="no-referrer" onerror="this.classList.add(\'is-broken\')">' +
        "</div>" +
        '<div class="ns-banner-card__media-fallback">Image preview unavailable</div>' +
        '<div class="ns-banner-card__media-overlay" aria-hidden="true">' +
        '<p class="ns-banner-card__overlay-title">' +
        escapeHtml(b.title || "Banner") +
        "</p>" +
        (formatTapLink(b) !== "—"
          ? '<span class="ns-banner-card__overlay-link">Tap → link set</span>'
          : "") +
        "</div>"
      : '<div class="ns-banner-card__media-empty">No image URL</div>') +
    '<div class="ns-banner-card__badges">' +
    '<span class="ns-banner-card__order">#' +
    escapeHtml(String(b.sort_order != null ? b.sort_order : 0)) +
    "</span>" +
    (active
      ? '<span class="ns-banner-card__live">Live</span>'
      : '<span class="ns-banner-card__live ns-banner-card__live--off">Off</span>') +
    "</div></div>" +
    '<div class="ns-banner-card__body">' +
    '<p class="ns-banner-card__id">ID ' +
    escapeHtml(String(b.id)) +
    "</p>" +
    "<h2>" +
    escapeHtml(b.title || "Untitled") +
    "</h2>" +
    '<p class="ns-banner-card__sub">' +
    escapeHtml(b.subtitle || "—") +
    "</p>" +
    '<dl class="ns-banner-card__details">' +
    detailRow("Tap link", formatTapLink(b)) +
    detailRow("Action", actionLabel) +
    detailRow("Starts", formatAdminDate(b.starts_at)) +
    detailRow("Ends", formatAdminDate(b.ends_at)) +
    detailRow("Created", formatAdminDate(b.created_at)) +
    detailRow("Updated", formatAdminDate(b.updated_at)) +
    detailRow("Image URL", truncateUrl(b.image_url)) +
    "</dl>" +
    crud +
    "</div></article>"
  );
}

function openBannerModal(id) {
  if (!nsBannersCrudEnabled) return;
  var modal = document.getElementById("banner-form-modal");
  var titleEl = document.getElementById("banner-form-title");
  var errEl = document.getElementById("banner-form-error");
  if (errEl) {
    errEl.classList.add("hidden");
    errEl.textContent = "";
  }
  if (id) {
    titleEl.textContent = "Edit banner #" + id;
    var row = nsBannersList.find(function (b) {
      return String(b.id) === String(id);
    });
    if (row) {
      fillBannerForm(row);
    } else {
      fillBannerForm({});
      document.getElementById("banner-form-id").value = String(id);
      loadBannerIntoForm(id);
    }
  } else {
    titleEl.textContent = "New banner";
    fillBannerForm({});
  }
  modal.classList.remove("hidden");
  updateBannerImagePreview();
}

function closeBannerModal() {
  var modal = document.getElementById("banner-form-modal");
  if (modal) modal.classList.add("hidden");
  clearBannerFileSelection();
}

async function loadBannerIntoForm(id) {
  try {
    var data = await adminApi("/api/admin/banners/" + encodeURIComponent(String(id)));
    if (data && data.banner) fillBannerForm(data.banner);
  } catch (err) {
    showGlobalToast(err.message, "error");
  }
}

function fillBannerForm(b) {
  clearBannerFileSelection(false);
  document.getElementById("banner-form-id").value = b.id != null ? String(b.id) : "";
  document.getElementById("banner-title").value = b.title || "";
  document.getElementById("banner-subtitle").value = b.subtitle || "";
  document.getElementById("banner-image-url").value = b.image_url || "";
  var actionType = (b.action_type || "none").toLowerCase();
  var tapLink =
    actionType === "url" && b.action_value ? String(b.action_value) : "";
  document.getElementById("banner-tap-link").value = tapLink;
  document.getElementById("banner-action-type").value = actionType;
  document.getElementById("banner-action-value").value = b.action_value || "";
  document.getElementById("banner-sort-order").value =
    b.sort_order != null ? String(b.sort_order) : "0";
  document.getElementById("banner-is-active").checked =
    b.id == null || b.is_active === true || b.is_active === 1 || b.is_active === "1";
  document.getElementById("banner-starts-at").value = toDatetimeLocalValue(b.starts_at);
  document.getElementById("banner-ends-at").value = toDatetimeLocalValue(b.ends_at);
  updateBannerImagePreview();
}

function toDatetimeLocalValue(value) {
  if (!value) return "";
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
      String(value.minute || 0).padStart(2, "0");
  }
  var s = String(value).trim().replace(" ", "T");
  if (s.length >= 16) return s.slice(0, 16);
  return s;
}

function readBannerFormPayload(imageUrl) {
  var tapLink = document.getElementById("banner-tap-link").value.trim();
  var actionType = tapLink ? "url" : "none";
  return {
    title: document.getElementById("banner-title").value.trim(),
    subtitle: document.getElementById("banner-subtitle").value.trim() || null,
    image_url: imageUrl,
    action_type: actionType,
    action_value: tapLink || null,
    sort_order: parseInt(document.getElementById("banner-sort-order").value, 10) || 0,
    is_active: document.getElementById("banner-is-active").checked,
    starts_at: datetimeLocalToApi(document.getElementById("banner-starts-at").value),
    ends_at: datetimeLocalToApi(document.getElementById("banner-ends-at").value),
  };
}

function datetimeLocalToApi(v) {
  if (!v) return null;
  return v.length === 16 ? v + ":00" : v;
}

async function saveBannerForm() {
  if (!nsBannersCrudEnabled) return;
  var errEl = document.getElementById("banner-form-error");
  var submit = document.getElementById("banner-form-submit");
  var id = document.getElementById("banner-form-id").value.trim();
  submit.disabled = true;
  if (errEl) {
    errEl.classList.add("hidden");
    errEl.textContent = "";
  }
  try {
    var imageUrl = normalizeExternalImageUrl(document.getElementById("banner-image-url").value.trim());
    if (nsBannerPendingFile) {
      var uploaded = await adminUploadBannerImage(nsBannerPendingFile);
      imageUrl = (uploaded && uploaded.image_url) || imageUrl;
    }
    if (!imageUrl) {
      throw new Error("A banner image is required — upload a file or paste an image link.");
    }
    if (!/^https?:\/\//i.test(imageUrl) && imageUrl.indexOf("data:") !== 0) {
      throw new Error("Image link must start with https:// (direct image URL).");
    }
    var payload = readBannerFormPayload(imageUrl);
    if (id) {
      await adminApi("/api/admin/banners/" + encodeURIComponent(id) + "/update", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      showGlobalToast("Banner updated", "success");
    } else {
      await adminApi("/api/admin/banners", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      showGlobalToast("Banner created", "success");
    }
    closeBannerModal();
    await loadBanners();
  } catch (err) {
    if (errEl) {
      errEl.textContent = err.message || "Save failed";
      errEl.classList.remove("hidden");
    }
    showGlobalToast(err.message, "error");
  } finally {
    submit.disabled = false;
  }
}

async function deleteBanner(id) {
  if (!nsBannersCrudEnabled || !id) return;
  var row = nsBannersList.find(function (b) {
    return String(b.id) === String(id);
  });
  var label = row && row.title ? row.title : "ID " + id;
  if (!window.confirm('Delete banner "' + label + '"? It will be removed from the app.')) return;
  try {
    await adminApi("/api/admin/banners/" + encodeURIComponent(String(id)) + "/delete", {
      method: "POST",
      body: JSON.stringify({}),
    });
    showGlobalToast("Banner deleted", "success");
    await loadBanners();
  } catch (err) {
    showGlobalToast(err.message, "error");
  }
}

function detailRow(label, value) {
  return (
    "<div><dt>" +
    escapeHtml(label) +
    "</dt><dd>" +
    escapeHtml(value || "—") +
    "</dd></div>"
  );
}

function formatAction(type, value) {
  type = (type || "none").toLowerCase();
  if (type === "none" || !value) return type;
  return type + " → " + value;
}

function formatTapLink(b) {
  var type = (b.action_type || "none").toLowerCase();
  if (type === "url" && b.action_value) return truncateUrl(b.action_value);
  return "—";
}

function truncateUrl(url) {
  if (!url) return "—";
  if (url.length <= 48) return url;
  return url.slice(0, 45) + "…";
}

function escapeHtml(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function escapeAttr(s) {
  return escapeHtml(s).replace(/'/g, "&#39;");
}

/* The list is the shared table; the create and edit form above stays as it was. */
var nsBannerTable = null;

async function loadBanners() {
  nsBannersCrudEnabled = true;
  if (nsBannerTable) {
    nsBannerTable.load();
    return;
  }
  var rows = {};
  nsBannerTable = new NsTable({
    el: "banners-grid", dataset: "banners", syncUrl: true, searchHint: "Title or subtitle", dateLabel: "Added",
    filters: [
      { key: "is_active", label: "Status", options: [["1", "Active"], ["0", "Inactive"]] },
      { key: "action_type", label: "On tap", options: ["none", "url", "screen"] },
    ],
    columns: [
      { key: "id", label: "ID" },
      { key: "image_url", label: "Image", sortable: false, render: function (r) {
        var u = nsSafeUrl(r.image_url);
        return u ? '<img class="thumb" src="' + nsEsc(u) + '" alt="" loading="lazy">' : nsText("");
      } },
      { key: "title", label: "Title" },
      { key: "subtitle", label: "Subtitle" },
      { key: "action_type", label: "On tap" },
      { key: "action_value", label: "Opens" },
      { key: "sort_order", label: "Order", num: true },
      { key: "is_active", label: "Active", toggle: {
        isOn: function (r) { return Number(r.is_active) === 1; },
        save: function (r, on) { return nsPost("/api/admin/banners/" + r.id + "/update", { is_active: on }); },
      } },
      { key: "starts_at", label: "Starts" },
      { key: "ends_at", label: "Ends" },
      { key: "actions", label: "Actions", sortable: false, render: function (r) {
        rows[r.id] = r;
        return '<div class="btn-row"><button type="button" class="btn btn-xs btn-light" data-banner-edit="' + nsEsc(r.id) + '">Edit</button><button type="button" class="btn btn-xs btn-red" data-banner-delete="' + nsEsc(r.id) + '">Delete</button></div>';
      } },
    ],
    onRender: function (body) {
      body.querySelectorAll("[data-banner-edit]").forEach(function (btn) {
        btn.addEventListener("click", function () { openBannerModal(btn.getAttribute("data-banner-edit")); });
      });
      body.querySelectorAll("[data-banner-delete]").forEach(function (btn) {
        btn.addEventListener("click", function () {
          var id = btn.getAttribute("data-banner-delete");
          if (!window.confirm("Delete banner #" + id + "?")) return;
          nsAct(function () { return nsPost("/api/admin/banners/" + id + "/delete"); }, "Banner deleted", function () { nsBannerTable.load(); });
        });
      });
    },
  });
}
