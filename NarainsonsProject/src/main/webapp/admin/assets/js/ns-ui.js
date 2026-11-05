/* Shared admin UI: formatters, the data table (filters, paging, export), charts and staff alerts. */

var nsPageSizes = [25, 50, 100, 200, 500];

function nsEsc(v) {
  return String(v == null ? "" : v)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function nsMoney(v) {
  if (v == null || v === "") return "—";
  var n = Number(v);
  if (isNaN(n)) return nsEsc(v);
  return "₹" + n.toLocaleString("en-IN", { maximumFractionDigits: 2 });
}

function nsCount(v) {
  var n = Number(v);
  return isNaN(n) ? "—" : n.toLocaleString("en-IN");
}

function nsText(v) {
  return v == null || v === "" ? '<span class="text-muted">—</span>' : nsEsc(v);
}

function nsDay(offset) {
  var d = new Date();
  d.setDate(d.getDate() + (offset || 0));
  return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
}

var NS_BADGE_COLORS = {
  pending: "amber", submitted: "blue", awaiting_proof: "blue", approved: "teal", verified: "green", active: "green",
  disbursed: "indigo", paid: "green", closed: "slate", rejected: "red", failed: "red", cancelled: "slate", overdue: "red",
  inactive: "slate", blocked: "red", suspended: "red", super_admin: "purple", admin: "blue",
};

function nsBadge(status) {
  if (status == null || status === "") return '<span class="text-muted">—</span>';
  var key = String(status).toLowerCase();
  var label = key.replace(/_/g, " ");
  return '<span class="badge badge-' + (NS_BADGE_COLORS[key] || "slate") + '">' + nsEsc(label.charAt(0).toUpperCase() + label.slice(1)) + "</span>";
}

function nsYesNo(v) {
  return Number(v) === 1 || v === true ? nsBadge("active") : nsBadge("inactive");
}

function nsToast(text, type) {
  if (typeof showGlobalToast === "function") showGlobalToast(text, type || "success");
}

/** Runs an action and reloads the table; shows the server's message if it fails. */
async function nsAct(fn, okMessage, after) {
  try {
    await fn();
    if (okMessage) nsToast(okMessage, "success");
    if (after) after();
  } catch (err) {
    nsToast(err.message, "error");
  }
}

function nsPost(path, body) {
  return adminApi(path, { method: "POST", body: JSON.stringify(body || {}) });
}

/** t: isOn(row), save(row, on), confirm(row, on), lockedReason(row) returns why it cannot be changed. */
function nsSwitchHtml(t, row, ref) {
  var on = t.isOn(row);
  var locked = t.lockedReason ? t.lockedReason(row) : "";
  return (
    '<label class="switch"' + (locked ? ' title="' + nsEsc(locked) + '"' : "") + '><input type="checkbox" data-switch="' + ref + '"' +
    (on ? " checked" : "") + (locked ? " disabled" : "") + ' aria-label="Active"><span class="switch-track"></span>' +
    '<span class="switch-text">' + (on ? "Active" : "Inactive") + "</span></label>"
  );
}

/* ---------- proof / document viewer ---------- */

function nsOpenImage(url) {
  var safe = nsSafeUrl(url);
  if (!safe) return nsToast("This file link is not valid", "error");
  var modal = document.getElementById("ns-viewer");
  if (!modal) {
    document.body.insertAdjacentHTML(
      "beforeend",
      '<div class="viewer" id="ns-viewer" role="dialog" aria-modal="true" aria-label="File preview">' +
        '<button type="button" class="btn btn-sm btn-light viewer-close" id="ns-viewer-close">Close</button>' +
        '<img id="ns-viewer-img" alt="Uploaded file"></div>'
    );
    modal = document.getElementById("ns-viewer");
    var close = function () {
      modal.classList.remove("is-open");
    };
    document.getElementById("ns-viewer-close").addEventListener("click", close);
    modal.addEventListener("click", function (e) {
      if (e.target === modal) close();
    });
  }
  var img = document.getElementById("ns-viewer-img");
  var show = function (src) {
    img.src = src;
    modal.classList.add("is-open");
  };
  if (nsIsProtectedFile(safe)) {
    nsProtectedBlobUrl(safe).then(show).catch(function (err) {
      nsToast(err.message, "error");
    });
  } else {
    show(safe);
  }
}

/* ---------- data table ---------- */

var NS_RANGES = [
  { v: "", label: "Any date" },
  { v: "today", label: "Today" },
  { v: "yesterday", label: "Yesterday" },
  { v: "7", label: "Last 7 days" },
  { v: "30", label: "Last 30 days" },
  { v: "month", label: "This month" },
  { v: "lastmonth", label: "Last month" },
  { v: "custom", label: "Custom dates" },
];

function nsRangeDates(v) {
  var now = new Date();
  var fmt = function (d) {
    return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
  };
  if (v === "today") return [nsDay(0), nsDay(0)];
  if (v === "yesterday") return [nsDay(-1), nsDay(-1)];
  if (v === "7") return [nsDay(-6), nsDay(0)];
  if (v === "30") return [nsDay(-29), nsDay(0)];
  if (v === "month") return [fmt(new Date(now.getFullYear(), now.getMonth(), 1)), nsDay(0)];
  if (v === "lastmonth") return [fmt(new Date(now.getFullYear(), now.getMonth() - 1, 1)), fmt(new Date(now.getFullYear(), now.getMonth(), 0))];
  return ["", ""];
}

/**
 * cfg: el, dataset, columns [{key,label,render,sortable}] (omit for automatic columns),
 * filters [{key,label,options|number}], cards [{value,label,color}] on cfg.cardParam,
 * base {param: value} fixed filters, syncUrl, simple (no filter bar), dateLabel, searchHint.
 */
function NsTable(cfg) {
  this.cfg = cfg;
  this.root = typeof cfg.el === "string" ? document.getElementById(cfg.el) : cfg.el;
  this.id = "t" + NsTable.seq++;
  this.state = { search: "", range: "", date_from: "", date_to: "", limit: cfg.limit || 25, offset: 0, sort: "", dir: "", f: {} };
  if (cfg.syncUrl) this.readUrl();
  this.build();
  this.load();
}
NsTable.seq = 1;

NsTable.prototype.readUrl = function () {
  var q = new URLSearchParams(window.location.search);
  var s = this.state;
  s.search = q.get("search") || "";
  s.date_from = q.get("date_from") || "";
  s.date_to = q.get("date_to") || "";
  if (s.date_from || s.date_to) s.range = "custom";
  var size = Number(q.get("limit"));
  if (nsPageSizes.indexOf(size) !== -1) s.limit = size;
  var keys = this.filterKeys();
  keys.forEach(function (k) {
    if (q.get(k)) s.f[k] = q.get(k);
  });
};

NsTable.prototype.filterKeys = function () {
  var keys = [];
  (this.cfg.filters || []).forEach(function (f) {
    if (f.number) {
      keys.push(f.key + "_min", f.key + "_max");
    } else {
      keys.push(f.key);
    }
  });
  if (this.cfg.cardParam && keys.indexOf(this.cfg.cardParam) === -1) keys.push(this.cfg.cardParam);
  return keys;
};

NsTable.prototype.writeUrl = function () {
  if (!this.cfg.syncUrl) return;
  var q = new URLSearchParams(window.location.search);
  ["search", "date_from", "date_to", "limit"].concat(this.filterKeys()).forEach(function (k) {
    q.delete(k);
  });
  var s = this.state;
  if (s.search) q.set("search", s.search);
  if (s.date_from) q.set("date_from", s.date_from);
  if (s.date_to) q.set("date_to", s.date_to);
  if (s.limit !== 25) q.set("limit", s.limit);
  Object.keys(s.f).forEach(function (k) {
    if (s.f[k]) q.set(k, s.f[k]);
  });
  var text = q.toString();
  history.replaceState(null, "", window.location.pathname + (text ? "?" + text : ""));
};

NsTable.prototype.build = function () {
  var c = this.cfg;
  var id = this.id;
  var s = this.state;
  var html = "";
  if (c.cards) html += '<div class="stat-row stat-row--filter" id="' + id + '-cards"></div>';
  html += '<section class="panel">';
  if (c.title) html += '<div class="panel-head"><h2>' + nsEsc(c.title) + "</h2></div>";
  if (!c.simple) {
    html += '<form class="filters" id="' + id + '-form" autocomplete="off">';
    html += '<div class="field field-grow"><label for="' + id + '-search">Search</label><input class="form-control" id="' + id + '-search" type="search" placeholder="' + nsEsc(c.searchHint || "Search") + '" value="' + nsEsc(s.search) + '"></div>';
    (c.filters || []).forEach(function (f) {
      if (f.number) {
        html += '<div class="field"><label for="' + id + "-" + f.key + '_min">' + nsEsc(f.label) + " from</label>" + '<input class="form-control" type="number" min="0" id="' + id + "-" + f.key + '_min" value="' + nsEsc(s.f[f.key + "_min"] || "") + '"></div>';
        html += '<div class="field"><label for="' + id + "-" + f.key + '_max">' + nsEsc(f.label) + " to</label>" + '<input class="form-control" type="number" min="0" id="' + id + "-" + f.key + '_max" value="' + nsEsc(s.f[f.key + "_max"] || "") + '"></div>';
      } else {
        html += '<div class="field"><label for="' + id + "-" + f.key + '">' + nsEsc(f.label) + '</label><select class="form-control" id="' + id + "-" + f.key + '"><option value="">All</option>';
        f.options.forEach(function (o) {
          var val = Array.isArray(o) ? o[0] : o;
          var label = Array.isArray(o) ? o[1] : String(o).charAt(0).toUpperCase() + String(o).slice(1).replace(/_/g, " ");
          html += '<option value="' + nsEsc(val) + '"' + (String(s.f[f.key] || "") === String(val) ? " selected" : "") + ">" + nsEsc(label) + "</option>";
        });
        html += "</select></div>";
      }
    });
    if (c.date !== false) {
      html += '<div class="field"><label for="' + id + '-range">' + nsEsc(c.dateLabel || "Date") + '</label><select class="form-control" id="' + id + '-range">';
      NS_RANGES.forEach(function (r) {
        html += '<option value="' + r.v + '"' + (s.range === r.v ? " selected" : "") + ">" + r.label + "</option>";
      });
      html += "</select></div>";
      html += '<div class="field" id="' + id + '-from-wrap"><label for="' + id + '-from">From</label><input class="form-control" type="date" id="' + id + '-from" value="' + nsEsc(s.date_from) + '"></div>';
      html += '<div class="field" id="' + id + '-to-wrap"><label for="' + id + '-to">To</label><input class="form-control" type="date" id="' + id + '-to" value="' + nsEsc(s.date_to) + '"></div>';
    }
    html += '<div class="field field-actions"><button type="submit" class="btn btn-sm btn-primary">Apply</button><button type="button" class="btn btn-sm btn-light" id="' + id + '-reset">Reset</button></div>';
    html += "</form>";
  }
  html += '<div class="table-tools">';
  html += '<div class="tool-group"><label for="' + id + '-size">Show</label><select class="form-control form-control-inline" id="' + id + '-size">';
  nsPageSizes.forEach(function (n) {
    html += '<option value="' + n + '"' + (s.limit === n ? " selected" : "") + ">" + n + "</option>";
  });
  html += '</select><span class="text-muted" id="' + id + '-info"></span></div>';
  html += '<div class="tool-group"><button type="button" class="btn btn-sm btn-light" id="' + id + '-refresh">Refresh</button>';
  html += '<button type="button" class="btn btn-sm btn-green" id="' + id + '-csv">CSV</button>';
  html += '<button type="button" class="btn btn-sm btn-teal" id="' + id + '-xlsx">Excel</button></div>';
  html += "</div>";
  html += '<div class="table-responsive" id="' + id + '-body"></div>';
  html += '<nav class="pager" aria-label="Pages" id="' + id + '-pager"></nav>';
  html += "</section>";
  this.root.innerHTML = html;

  var self = this;
  var el = function (x) {
    return document.getElementById(id + "-" + x);
  };
  if (!c.simple) {
    el("form").addEventListener("submit", function (e) {
      e.preventDefault();
      self.readForm();
      self.state.offset = 0;
      self.load();
    });
    el("reset").addEventListener("click", function () {
      self.reset();
    });
    if (c.date !== false) {
      this.syncDateFields();
      el("range").addEventListener("change", function () {
        var dates = nsRangeDates(el("range").value);
        if (el("range").value !== "custom") {
          el("from").value = dates[0];
          el("to").value = dates[1];
        }
        self.syncDateFields();
        self.readForm();
        self.state.offset = 0;
        self.load();
      });
    }
    (c.filters || []).forEach(function (f) {
      if (!f.number) {
        el(f.key).addEventListener("change", function () {
          self.readForm();
          self.state.offset = 0;
          self.load();
        });
      }
    });
  }
  el("size").addEventListener("change", function () {
    self.state.limit = Number(el("size").value);
    self.state.offset = 0;
    self.load();
  });
  el("refresh").addEventListener("click", function () {
    self.load();
  });
  el("csv").addEventListener("click", function () {
    self.download("csv");
  });
  el("xlsx").addEventListener("click", function () {
    self.download("xlsx");
  });
};

NsTable.prototype.syncDateFields = function () {
  var custom = document.getElementById(this.id + "-range").value === "custom";
  document.getElementById(this.id + "-from-wrap").hidden = !custom;
  document.getElementById(this.id + "-to-wrap").hidden = !custom;
};

NsTable.prototype.readForm = function () {
  var c = this.cfg;
  var id = this.id;
  var s = this.state;
  var el = function (x) {
    return document.getElementById(id + "-" + x);
  };
  s.search = el("search").value.trim();
  (c.filters || []).forEach(function (f) {
    if (f.number) {
      s.f[f.key + "_min"] = el(f.key + "_min").value;
      s.f[f.key + "_max"] = el(f.key + "_max").value;
    } else {
      s.f[f.key] = el(f.key).value;
    }
  });
  if (c.date !== false) {
    s.range = el("range").value;
    s.date_from = el("from").value;
    s.date_to = el("to").value;
  }
};

NsTable.prototype.reset = function () {
  var size = this.state.limit;
  this.state = { search: "", range: "", date_from: "", date_to: "", limit: size, offset: 0, sort: "", dir: "", f: {} };
  this.build();
  this.load();
};

NsTable.prototype.params = function (withPaging) {
  var s = this.state;
  var q = new URLSearchParams();
  var base = this.cfg.base || {};
  Object.keys(base).forEach(function (k) {
    q.set(k, base[k]);
  });
  if (s.search) q.set("search", s.search);
  if (s.date_from) q.set("date_from", s.date_from);
  if (s.date_to) q.set("date_to", s.date_to);
  Object.keys(s.f).forEach(function (k) {
    if (s.f[k] !== "" && s.f[k] != null) q.set(k, s.f[k]);
  });
  if (s.sort) {
    q.set("sort", s.sort);
    q.set("dir", s.dir);
  }
  if (withPaging) {
    q.set("limit", s.limit);
    q.set("offset", s.offset);
  }
  return q.toString();
};

NsTable.prototype.load = async function () {
  var body = document.getElementById(this.id + "-body");
  if (!body) return;
  body.classList.add("is-loading");
  this.writeUrl();
  try {
    var data = await adminApi("/api/admin/list/" + this.cfg.dataset + "?" + this.params(true));
    this.data = data;
    this.renderRows(data);
    this.renderPager(data);
    this.renderCards(data);
  } catch (err) {
    if (err.status === 401) return redirectToAdminLogin();
    body.innerHTML = '<div class="empty"><strong>Could not load this list.</strong> ' + nsEsc(err.message) + "</div>";
    document.getElementById(this.id + "-pager").innerHTML = "";
  }
  body.classList.remove("is-loading");
};

NsTable.prototype.columns = function (rows) {
  if (this.cfg.columns) return this.cfg.columns;
  var keys = rows.length ? Object.keys(rows[0]) : [];
  return keys.map(function (k) {
    return { key: k, label: k.replace(/_/g, " ").replace(/\b\w/g, function (m) { return m.toUpperCase(); }) };
  });
};

NsTable.prototype.renderRows = function (data) {
  var self = this;
  var body = document.getElementById(this.id + "-body");
  var rows = data.rows || [];
  var cols = this.columns(rows);
  if (!rows.length) {
    body.innerHTML = '<div class="empty">' + nsEsc(this.cfg.emptyText || "No records match these filters.") + "</div>";
    return;
  }
  var s = this.state;
  var head = cols
    .map(function (c) {
      if (c.sortable === false) return "<th>" + nsEsc(c.label) + "</th>";
      var mark = s.sort === c.key ? (s.dir === "asc" ? " ▲" : " ▼") : "";
      return '<th><button type="button" class="th-sort" data-sort="' + nsEsc(c.key) + '">' + nsEsc(c.label) + mark + "</button></th>";
    })
    .join("");
  var trs = rows
    .map(function (r, rowIndex) {
      return (
        "<tr>" +
        cols
          .map(function (c, colIndex) {
            var cell = c.toggle ? nsSwitchHtml(c.toggle, r, colIndex + ":" + rowIndex) : c.render ? c.render(r) : nsText(r[c.key]);
            return "<td" + (c.num ? ' class="text-end"' : "") + ">" + cell + "</td>";
          })
          .join("") +
        "</tr>"
      );
    })
    .join("");
  body.innerHTML = '<table class="table table-striped table-bordered"><thead><tr>' + head + "</tr></thead><tbody>" + trs + "</tbody></table>";
  body.querySelectorAll("[data-sort]").forEach(function (b) {
    b.addEventListener("click", function () {
      var key = b.getAttribute("data-sort");
      if (self.state.sort === key) {
        self.state.dir = self.state.dir === "asc" ? "desc" : "asc";
      } else {
        self.state.sort = key;
        self.state.dir = "asc";
      }
      self.load();
    });
  });
  body.querySelectorAll("[data-switch]").forEach(function (input) {
    input.addEventListener("change", async function () {
      var at = input.getAttribute("data-switch").split(":");
      var t = cols[Number(at[0])].toggle;
      var row = rows[Number(at[1])];
      var on = input.checked;
      if (t.confirm && !window.confirm(t.confirm(row, on))) {
        input.checked = !on;
        return;
      }
      input.disabled = true;
      try {
        await t.save(row, on);
        nsToast(on ? "Turned on" : "Turned off", "success");
        self.load();
      } catch (err) {
        input.checked = !on;
        input.disabled = false;
        nsToast(err.message, "error");
      }
    });
  });
  if (this.cfg.onRender) this.cfg.onRender(body, this);
};

NsTable.prototype.renderPager = function (data) {
  var self = this;
  var total = Number(data.total) || 0;
  var limit = Number(data.limit) || 25;
  var offset = Number(data.offset) || 0;
  var pages = Math.max(1, Math.ceil(total / limit));
  var page = Math.floor(offset / limit) + 1;
  var from = total ? offset + 1 : 0;
  var to = Math.min(offset + limit, total);
  document.getElementById(this.id + "-info").textContent = "Showing " + nsCount(from) + "–" + nsCount(to) + " of " + nsCount(total);

  var nums = [];
  for (var p = Math.max(1, page - 2); p <= Math.min(pages, page + 2); p++) nums.push(p);
  var btn = function (label, target, disabled, active) {
    return (
      '<button type="button" class="page-btn' + (active ? " is-active" : "") + '" data-page="' + target + '"' + (disabled ? " disabled" : "") + (active ? ' aria-current="page"' : "") + ">" + label + "</button>"
    );
  };
  var html = btn("First", 1, page === 1) + btn("Prev", page - 1, page === 1);
  if (nums[0] > 1) html += '<span class="page-gap">…</span>';
  nums.forEach(function (n) {
    html += btn(n, n, false, n === page);
  });
  if (nums[nums.length - 1] < pages) html += '<span class="page-gap">…</span>';
  html += btn("Next", page + 1, page === pages) + btn("Last", pages, page === pages);
  var pager = document.getElementById(this.id + "-pager");
  pager.innerHTML = html;
  pager.querySelectorAll("[data-page]").forEach(function (b) {
    b.addEventListener("click", function () {
      self.state.offset = (Number(b.getAttribute("data-page")) - 1) * limit;
      self.load();
    });
  });
};

NsTable.prototype.renderCards = function (data) {
  var c = this.cfg;
  if (!c.cards) return;
  var self = this;
  var wrap = document.getElementById(this.id + "-cards");
  var counts = data.counts || {};
  var all = Object.keys(counts).reduce(function (sum, k) {
    return sum + Number(counts[k]);
  }, 0);
  var active = this.state.f[c.cardParam] || "";
  wrap.innerHTML = c.cards
    .map(function (card) {
      var n = card.value === "" ? all : Number(counts[card.value] || 0);
      return (
        '<button type="button" class="stat stat-' + card.color + (active === card.value ? " is-active" : "") + '" data-card="' + nsEsc(card.value) + '">' +
        '<span class="stat-value">' + nsCount(n) + '</span><span class="stat-label">' + nsEsc(card.label) + "</span></button>"
      );
    })
    .join("");
  wrap.querySelectorAll("[data-card]").forEach(function (b) {
    b.addEventListener("click", function () {
      var v = b.getAttribute("data-card");
      self.state.f[c.cardParam] = v;
      var sel = document.getElementById(self.id + "-" + c.cardParam);
      if (sel) sel.value = v;
      self.state.offset = 0;
      self.load();
    });
  });
};

NsTable.prototype.download = async function (format) {
  var btn = document.getElementById(this.id + "-" + format);
  btn.disabled = true;
  try {
    var url = (window.NS_API_BASE || "").replace(/\/$/, "") + "/api/admin/list/" + this.cfg.dataset + "/export?format=" + format + "&" + this.params(false);
    var res = await fetch(url, { headers: { Authorization: "Bearer " + getAdminToken() } });
    if (!res.ok) {
      var json = await res.json().catch(function () { return {}; });
      throw new Error(json.message || "Download failed");
    }
    var blob = await res.blob();
    var a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = "narainsons-" + this.cfg.dataset + "-" + nsDay(0) + "." + format;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 5000);
  } catch (err) {
    nsToast(err.message, "error");
  }
  btn.disabled = false;
};

/* ---------- bar chart: one series, one scale; hover any day for its value, click to open that day ---------- */

function nsBarChart(el, points, opts) {
  var W = 640;
  var H = 170;
  var L = 30;
  var R = 6;
  var T = 8;
  var B = 22;
  var max = points.reduce(function (m, p) { return Math.max(m, Number(p.count)); }, 0);
  var top = max <= 4 ? 4 : Math.ceil(max / 4) * 4;
  var plotW = W - L - R;
  var plotH = H - T - B;
  var step = plotW / Math.max(points.length, 1);
  var barW = Math.max(1, step - 2);
  var y = function (v) { return T + plotH - (v / top) * plotH; };
  var svg = '<svg viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="' + nsEsc(opts.label) + '">';
  [0, top / 2, top].forEach(function (v) {
    svg += '<line x1="' + L + '" x2="' + (W - R) + '" y1="' + y(v) + '" y2="' + y(v) + '" class="chart-grid"/>';
    svg += '<text x="' + (L - 6) + '" y="' + (y(v) + 3) + '" text-anchor="end" class="chart-axis">' + v + "</text>";
  });
  points.forEach(function (p, i) {
    var x = L + i * step + 1;
    var n = Number(p.count);
    if (n > 0) {
      var h = Math.max(2, y(0) - y(n));
      var r = Math.min(3, barW / 2);
      svg += '<path class="chart-bar" fill="' + opts.color + '" d="M' + x + " " + y(0) + "V" + (y(0) - h + r) + "q0 " + -r + " " + r + " " + -r + "h" + (barW - 2 * r) + "q" + r + " 0 " + r + " " + r + "V" + y(0) + 'z"/>';
    }
    svg += '<a href="' + nsEsc(opts.link(p.date)) + '"><rect class="chart-hit" data-i="' + i + '" x="' + (L + i * step) + '" y="' + T + '" width="' + step + '" height="' + plotH + '" fill="transparent"/></a>';
  });
  var tick = function (i, anchor) {
    if (!points[i]) return "";
    var x = L + i * step + step / 2;
    if (anchor === "start") x = L;
    if (anchor === "end") x = W - R;
    return '<text x="' + x + '" y="' + (H - 6) + '" text-anchor="' + anchor + '" class="chart-axis">' + nsEsc(nsShortDate(points[i].date)) + "</text>";
  };
  svg += tick(0, "start") + tick(Math.floor((points.length - 1) / 2), "middle") + tick(points.length - 1, "end") + "</svg>";
  el.innerHTML = '<div class="chart">' + svg + '<div class="chart-tip" hidden></div></div>';
  var tip = el.querySelector(".chart-tip");
  var chart = el.querySelector(".chart");
  el.querySelectorAll(".chart-hit").forEach(function (rect) {
    rect.addEventListener("mouseenter", function () {
      var p = points[Number(rect.getAttribute("data-i"))];
      tip.innerHTML = "<strong>" + nsEsc(p.count) + "</strong> " + nsEsc(opts.unit) + "<br>" + nsEsc(nsShortDate(p.date));
      tip.hidden = false;
      var box = rect.getBoundingClientRect();
      var parent = chart.getBoundingClientRect();
      var left = box.left - parent.left + box.width / 2;
      tip.style.left = Math.min(Math.max(left, 44), parent.width - 44) + "px";
    });
    rect.addEventListener("mouseleave", function () {
      tip.hidden = true;
    });
  });
}

function nsShortDate(iso) {
  var parts = String(iso).split("-");
  var months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  return parts.length === 3 ? Number(parts[2]) + " " + months[Number(parts[1]) - 1] : iso;
}

/* ---------- staff alerts: bell, toast and sound ---------- */

var nsAlerts = {
  SEEN_KEY: "ns_alert_seen_id",
  RUNG_KEY: "ns_alert_rung_id",
  SOUND_KEY: "ns_alert_sound",
  POLL_MS: 15000,
  ctx: null,
  plays: 0,
  latest: 0,

  soundOn: function () {
    try {
      return localStorage.getItem(this.SOUND_KEY) !== "off";
    } catch (e) {
      return true;
    }
  },

  audio: function () {
    if (!this.ctx) {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (AC) this.ctx = new AC();
    }
    return this.ctx;
  },

  /** A loud siren-like phrase, played twice: heavy low hits under a rising chord at full volume. */
  play: function () {
    var ctx = this.audio();
    if (!ctx) return false;
    if (ctx.state === "suspended") {
      ctx.resume();
      if (ctx.state === "suspended") {
        this.showSoundHint(true);
        return false;
      }
    }
    this.plays++;
    var master = ctx.createDynamicsCompressor();
    master.threshold.value = -6;
    master.ratio.value = 12;
    var gain = ctx.createGain();
    gain.gain.value = 1;
    master.connect(gain);
    gain.connect(ctx.destination);
    var hit = function (when, freq, type, dur, vol) {
      var o = ctx.createOscillator();
      var g = ctx.createGain();
      o.type = type;
      o.frequency.setValueAtTime(freq, when);
      g.gain.setValueAtTime(0.0001, when);
      g.gain.exponentialRampToValueAtTime(vol, when + 0.015);
      g.gain.exponentialRampToValueAtTime(0.0001, when + dur);
      o.connect(g);
      g.connect(master);
      o.start(when);
      o.stop(when + dur + 0.05);
    };
    var chords = [
      [523.25, 659.25, 783.99],
      [587.33, 739.99, 880],
      [659.25, 830.61, 987.77],
      [783.99, 987.77, 1174.66],
    ];
    var t = ctx.currentTime + 0.05;
    for (var round = 0; round < 2; round++) {
      chords.forEach(function (chord, i) {
        var at = t + i * 0.26;
        hit(at, 82.41, "sawtooth", 0.42, 0.9);
        hit(at, 164.81, "square", 0.3, 0.5);
        chord.forEach(function (f) {
          hit(at, f, "sawtooth", 0.28, 0.45);
          hit(at, f * 2, "square", 0.16, 0.14);
        });
      });
      t += 1.25;
    }
    return true;
  },

  showSoundHint: function (show) {
    var btn = document.getElementById("ns-sound-btn");
    if (btn) btn.classList.toggle("is-waiting", !!show);
  },

  start: function () {
    if (!document.body.classList.contains("ns-admin") || !getAdminToken()) return;
    var self = this;
    if (typeof ensureAdminTopbarBell === "function") ensureAdminTopbarBell();
    this.mountControls();
    // Browsers keep audio off until the page has been clicked once.
    var unlock = function () {
      var ctx = self.audio();
      if (ctx && ctx.state === "suspended") ctx.resume();
      self.showSoundHint(false);
    };
    document.addEventListener("click", unlock);
    document.addEventListener("keydown", unlock);
    this.poll();
    setInterval(function () { self.poll(); }, this.POLL_MS);
  },

  mountControls: function () {
    var self = this;
    var actions = document.querySelector(".ns-topbar__actions");
    if (!actions || document.getElementById("ns-sound-btn")) return;
    actions.insertAdjacentHTML(
      "afterbegin",
      '<button type="button" class="topbar-pill" id="ns-sound-btn" title="Turn the alert sound on or off"></button>'
    );
    var sound = document.getElementById("ns-sound-btn");
    var paint = function () {
      sound.textContent = self.soundOn() ? "Sound on" : "Sound off";
      sound.classList.toggle("is-off", !self.soundOn());
    };
    paint();
    sound.addEventListener("click", function () {
      try {
        localStorage.setItem(self.SOUND_KEY, self.soundOn() ? "off" : "on");
      } catch (e) {}
      paint();
      if (self.soundOn()) {
        var ctx = self.audio();
        if (ctx && ctx.state === "suspended") ctx.resume().then(function () { self.play(); });
        else self.play();
      }
      if (window.Notification && Notification.permission === "default") Notification.requestPermission();
    });
    var bell = document.querySelector(".ns-topbar__bell");
    if (bell) {
      bell.insertAdjacentHTML("afterend", '<div class="alert-menu" id="ns-alert-menu" hidden></div>');
      bell.addEventListener("click", function (e) {
        e.stopPropagation();
        var menu = document.getElementById("ns-alert-menu");
        menu.hidden = !menu.hidden;
      });
      document.addEventListener("click", function (e) {
        var menu = document.getElementById("ns-alert-menu");
        if (menu && !menu.hidden && !menu.contains(e.target)) menu.hidden = true;
      });
    }
  },

  poll: async function () {
    var seen = 0;
    try {
      seen = Number(localStorage.getItem(this.SEEN_KEY)) || 0;
    } catch (e) {}
    var data;
    try {
      data = await adminApi("/api/admin/alerts?after_id=" + seen);
    } catch (err) {
      return;
    }
    this.latest = Number(data.latest_id) || 0;
    var badge = document.getElementById("notif-badge");
    if (badge) {
      badge.textContent = data.unread_count > 99 ? "99+" : data.unread_count;
      badge.classList.toggle("is-zero", !Number(data.unread_count));
    }
    this.renderMenu(data);
    try {
      localStorage.setItem(this.SEEN_KEY, String(this.latest));
    } catch (e) {}
    var items = data.new_items || [];
    if (items.length) this.announce(items);
  },

  announce: function (items) {
    var rung = 0;
    try {
      rung = Number(localStorage.getItem(this.RUNG_KEY)) || 0;
    } catch (e) {}
    // With several tabs open only the first one to see the alert rings.
    if (this.latest > rung) {
      try {
        localStorage.setItem(this.RUNG_KEY, String(this.latest));
      } catch (e) {}
      if (this.soundOn()) this.play();
      if (window.Notification && Notification.permission === "granted") {
        items.slice(-3).forEach(function (a) {
          new Notification(a.title, { body: a.body, tag: "ns-alert-" + a.id });
        });
      }
    }
    var stack = document.getElementById("ns-alert-stack");
    if (!stack) {
      document.body.insertAdjacentHTML("beforeend", '<div class="alert-stack" id="ns-alert-stack" role="status" aria-live="assertive"></div>');
      stack = document.getElementById("ns-alert-stack");
    }
    items.slice(-4).forEach(function (a) {
      var item = document.createElement("a");
      item.className = "alert-toast alert-" + nsEsc(a.type);
      item.href = nsSafeUrl(a.link) || "admin-notifications.html";
      item.innerHTML = "<strong>" + nsEsc(a.title) + "</strong><span>" + nsEsc(a.body) + "</span>";
      stack.appendChild(item);
      setTimeout(function () { item.remove(); }, 15000);
    });
  },

  renderMenu: function (data) {
    var self = this;
    var menu = document.getElementById("ns-alert-menu");
    if (!menu) return;
    var rows = (data.recent || [])
      .map(function (a) {
        return (
          '<a class="alert-row" href="' + nsEsc(nsSafeUrl(a.link) || "admin-notifications.html") + '"><strong>' + nsEsc(a.title) + "</strong><span>" + nsEsc(a.body) + "</span><small>" + nsEsc(a.created_at) + "</small></a>"
        );
      })
      .join("");
    menu.innerHTML =
      '<div class="alert-menu-head"><strong>Alerts</strong><button type="button" class="btn btn-xs btn-light" id="ns-alert-read">Mark all read</button></div>' +
      (rows || '<div class="empty">No alerts yet.</div>') +
      '<a class="alert-all" href="admin-notifications.html">View all alerts</a>';
    document.getElementById("ns-alert-read").addEventListener("click", async function (e) {
      e.stopPropagation();
      await nsAct(function () { return nsPost("/api/admin/alerts/read"); }, null, function () { self.poll(); });
    });
  },
};

document.addEventListener("DOMContentLoaded", function () {
  setTimeout(function () { nsAlerts.start(); }, 0);
});
