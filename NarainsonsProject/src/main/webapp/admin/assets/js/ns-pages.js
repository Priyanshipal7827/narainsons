/* The admin pages. Each one is a function below, chosen by <body data-page>. */

var nsAdmin = null;

function nsParam(name) {
  return new URLSearchParams(window.location.search).get(name);
}

function nsMeta(items) {
  return (
    '<div class="table-responsive"><table class="table table-striped table-bordered table-kv"><tbody>' +
    items
      .map(function (i) {
        return "<tr><th>" + nsEsc(i[0]) + "</th><td>" + (i[2] ? i[1] : nsText(i[1])) + "</td></tr>";
      })
      .join("") +
    "</tbody></table></div>"
  );
}

function nsLink(href, label) {
  return '<a href="' + nsEsc(href) + '">' + nsEsc(label == null || label === "" ? "—" : label) + "</a>";
}

function nsCustomerCell(r) {
  return nsLink("admin-user-view.html?id=" + encodeURIComponent(r.user_id), r.customer || "User #" + r.user_id) + '<br><small class="text-muted">' + nsEsc(r.mobile_number || "") + "</small>";
}

var NS_LOAN_STATUSES = ["pending", "approved", "disbursed", "closed", "rejected", "cancelled"];
var NS_PURPOSES = ["personal", "business", "medical", "education", "home_repair", "other"];

/* ---------- loans ---------- */

function nsLoanColumns() {
  return [
    { key: "id", label: "ID", render: function (r) { return nsLink("admin-loan-view.html?id=" + r.id, "#" + r.id); } },
    { key: "application_code", label: "Code", render: function (r) { return nsLink("admin-loan-view.html?id=" + r.id, r.application_code); } },
    { key: "customer", label: "Customer", render: nsCustomerCell },
    { key: "requested_amount", label: "Amount", num: true, render: function (r) { return nsMoney(r.requested_amount); } },
    { key: "tenure_months", label: "Months", num: true },
    { key: "emi_amount", label: "EMI", num: true, render: function (r) { return nsMoney(r.emi_amount); } },
    { key: "purpose", label: "Purpose", render: function (r) { return nsText(String(r.purpose || "").replace(/_/g, " ")); } },
    { key: "kyc_status", label: "KYC", render: function (r) { return nsBadge(r.kyc_status); } },
    { key: "status", label: "Status", render: function (r) { return nsBadge(r.status); } },
    { key: "created_at", label: "Applied" },
    { key: "actions", label: "Actions", sortable: false, render: nsLoanActions },
  ];
}

function nsLoanActions(r) {
  var id = nsEsc(r.id);
  var html = '<div class="btn-row"><a class="btn btn-xs btn-light" href="admin-loan-view.html?id=' + id + '">View</a>';
  if (r.status === "pending") {
    html += '<button type="button" class="btn btn-xs btn-green" data-loan="approve" data-id="' + id + '">Approve</button>';
    html += '<button type="button" class="btn btn-xs btn-red" data-loan="reject" data-id="' + id + '">Reject</button>';
  }
  if (r.status === "approved") {
    html += '<button type="button" class="btn btn-xs btn-primary" data-loan="disburse" data-id="' + id + '">Disburse</button>';
  }
  return html + "</div>";
}

function nsLoanDecision(id, action, reload) {
  var path = "/api/admin/loan/applications/" + encodeURIComponent(id) + "/" + action;
  if (action === "approve") {
    if (!window.confirm("Approve loan #" + id + "?")) return;
    nsAct(function () { return nsPost(path, { admin_note: "" }); }, "Loan approved", reload);
  } else if (action === "reject") {
    var note = window.prompt("Reason for rejecting loan #" + id + ":");
    if (note === null) return;
    nsAct(function () { return nsPost(path, { admin_note: note }); }, "Loan rejected", reload);
  } else {
    var ref = window.prompt("Bank / UTR reference for the money sent on loan #" + id + ":");
    if (ref === null) return;
    nsAct(function () { return nsPost(path, { disbursement_reference: ref }); }, "Loan disbursed", reload);
  }
}

function nsWireLoanButtons(body, table) {
  body.querySelectorAll("[data-loan]").forEach(function (b) {
    b.addEventListener("click", function () {
      nsLoanDecision(b.getAttribute("data-id"), b.getAttribute("data-loan"), function () { table.load(); });
    });
  });
}

/* ---------- EMI payments ---------- */

function nsPaymentColumns() {
  return [
    { key: "id", label: "ID" },
    { key: "application_code", label: "Loan", render: function (r) { return nsLink("admin-loan-view.html?id=" + r.loan_application_id, r.application_code); } },
    { key: "customer", label: "Customer", render: nsCustomerCell },
    { key: "installment_number", label: "EMI no.", num: true },
    { key: "due_date", label: "Due" },
    { key: "amount_due", label: "EMI amount", num: true, render: function (r) { return nsMoney(r.amount_due); } },
    { key: "amount_claimed", label: "Paid", num: true, render: function (r) { return nsMoney(r.amount_claimed); } },
    { key: "utr_reference", label: "UTR" },
    { key: "screenshot_url", label: "Proof", sortable: false, render: function (r) {
      return r.screenshot_url ? '<button type="button" class="btn btn-xs btn-light" data-proof="' + nsEsc(r.screenshot_url) + '">View</button>' : nsText("");
    } },
    { key: "status", label: "Status", render: function (r) { return nsBadge(r.status); } },
    { key: "submitted_at", label: "Submitted" },
    { key: "actions", label: "Actions", sortable: false, render: function (r) {
      if (r.status !== "pending") return nsText(r.admin_note);
      return '<div class="btn-row"><button type="button" class="btn btn-xs btn-green" data-pay="approve" data-id="' + nsEsc(r.id) + '">Approve</button><button type="button" class="btn btn-xs btn-red" data-pay="reject" data-id="' + nsEsc(r.id) + '">Reject</button></div>';
    } },
  ];
}

function nsWirePaymentButtons(body, table) {
  body.querySelectorAll("[data-proof]").forEach(function (b) {
    b.addEventListener("click", function () { nsOpenImage(b.getAttribute("data-proof")); });
  });
  body.querySelectorAll("[data-pay]").forEach(function (b) {
    b.addEventListener("click", function () {
      var id = b.getAttribute("data-id");
      var action = b.getAttribute("data-pay");
      var note = "";
      if (action === "approve") {
        if (!window.confirm("Approve payment #" + id + "? Check your bank first.")) return;
      } else {
        note = window.prompt("Reason for rejecting payment #" + id + ":");
        if (note === null) return;
      }
      nsAct(function () { return nsPost("/api/admin/emi/payments/" + encodeURIComponent(id) + "/" + action, { admin_note: note }); },
        "Payment " + (action === "approve" ? "approved" : "rejected"), function () { table.load(); });
    });
  });
}

function nsScheduleColumns() {
  return [
    { key: "id", label: "ID" },
    { key: "application_code", label: "Loan", render: function (r) { return nsLink("admin-loan-view.html?id=" + r.loan_application_id, r.application_code); } },
    { key: "customer", label: "Customer", render: nsCustomerCell },
    { key: "installment_number", label: "EMI no.", num: true },
    { key: "due_date", label: "Due date" },
    { key: "amount_due", label: "EMI amount", num: true, render: function (r) { return nsMoney(r.amount_due); } },
    { key: "amount_paid", label: "Paid", num: true, render: function (r) { return nsMoney(r.amount_paid); } },
    { key: "status", label: "Status", render: function (r) { return nsBadge(r.status); } },
    { key: "loan_status", label: "Loan", render: function (r) { return nsBadge(r.loan_status); } },
    { key: "last_reminder_at", label: "Last reminder" },
    { key: "paid_at", label: "Paid on" },
  ];
}

/* ---------- pages ---------- */

var NS_PAGES = {
  dashboard: function (root) {
    var range = { from: nsParam("date_from") || nsDay(-29), to: nsParam("date_to") || nsDay(0) };
    root.innerHTML =
      '<form class="filters filters-bar" id="dash-form">' +
      '<div class="field"><label for="dash-range">Period</label><select class="form-control" id="dash-range"><option value="7">Last 7 days</option><option value="30" selected>Last 30 days</option><option value="month">This month</option><option value="lastmonth">Last month</option><option value="custom">Custom dates</option></select></div>' +
      '<div class="field"><label for="dash-from">From</label><input class="form-control" type="date" id="dash-from"></div>' +
      '<div class="field"><label for="dash-to">To</label><input class="form-control" type="date" id="dash-to"></div>' +
      '<div class="field field-actions"><button type="submit" class="btn btn-sm btn-primary">Apply</button></div></form>' +
      '<div id="dash-body"><div class="empty">Loading…</div></div>';
    var from = document.getElementById("dash-from");
    var to = document.getElementById("dash-to");
    from.value = range.from;
    to.value = range.to;
    document.getElementById("dash-range").addEventListener("change", function (e) {
      if (e.target.value === "custom") return;
      var d = nsRangeDates(e.target.value);
      from.value = d[0];
      to.value = d[1];
      load();
    });
    document.getElementById("dash-form").addEventListener("submit", function (e) {
      e.preventDefault();
      load();
    });

    function card(color, value, label, href) {
      return '<a class="stat stat-' + color + '" href="' + nsEsc(href) + '"><span class="stat-value">' + value + '</span><span class="stat-label">' + nsEsc(label) + "</span></a>";
    }

    async function load() {
      var body = document.getElementById("dash-body");
      var d;
      try {
        d = await adminApi("/api/admin/dashboard/summary?date_from=" + encodeURIComponent(from.value) + "&date_to=" + encodeURIComponent(to.value));
      } catch (err) {
        if (err.status === 401) return redirectToAdminLogin();
        body.innerHTML = '<div class="empty"><strong>Could not load the dashboard.</strong> ' + nsEsc(err.message) + "</div>";
        return;
      }
      var dates = "date_from=" + d.date_from + "&date_to=" + d.date_to;
      var week = "date_from=" + nsDay(0) + "&date_to=" + nsDay(7);
      body.innerHTML =
        '<h2 class="section-title">Needs action</h2><div class="stat-row">' +
        card("amber", nsCount(d.pending_loans), "Loans to review", "admin-loans.html?status=pending") +
        card("blue", nsCount(d.kyc_to_review), "KYC to review", "admin-users.html?kyc_status=submitted") +
        card("indigo", nsCount(d.approved_to_disburse), "Approved, send money", "admin-loans.html?status=approved") +
        card("teal", nsCount(d.emi_proofs_pending), "EMI proofs to verify", "admin-emi-payments.html?status=pending") +
        card("red", nsCount(d.overdue_emis), "Overdue EMIs · " + nsMoney(d.overdue_amount), "admin-emi-schedules.html?status=overdue") +
        "</div>" +
        '<h2 class="section-title">Loan book today</h2><div class="stat-row">' +
        card("navy", nsCount(d.total_users), "Total customers", "admin-users.html") +
        card("green", nsCount(d.active_loans), "Active loans", "admin-loans.html?status=disbursed") +
        card("purple", nsMoney(d.outstanding_amount), "Outstanding EMIs", "admin-emi-schedules.html?loan_status=disbursed") +
        card("plum", nsCount(d.due_next_7_days), "EMIs due in 7 days", "admin-emi-schedules.html?status=pending&" + week) +
        card("slate", nsCount(d.closed_loans), "Closed loans", "admin-loans.html?status=closed") +
        card("wine", nsCount(d.rejected_loans), "Rejected loans", "admin-loans.html?status=rejected") +
        "</div>" +
        '<h2 class="section-title">' + nsEsc(nsShortDate(d.date_from)) + " to " + nsEsc(nsShortDate(d.date_to)) + '</h2><div class="stat-row">' +
        card("navy", nsCount(d.new_users), "New customers", "admin-users.html?" + dates) +
        card("blue", nsCount(d.applications), "Loan applications", "admin-loans.html?" + dates) +
        card("indigo", nsMoney(d.amount_requested), "Amount requested", "admin-loans.html?" + dates) +
        card("teal", nsMoney(d.amount_disbursed), "Amount disbursed", "admin-loans.html?status=disbursed") +
        card("green", nsMoney(d.amount_collected), "EMI collected", "admin-emi-payments.html?status=approved") +
        "</div>" +
        '<div class="grid-2"><section class="panel"><div class="panel-head"><h2>Loan applications per day</h2></div><div id="chart-loans" class="panel-pad"></div></section>' +
        '<section class="panel"><div class="panel-head"><h2>New customers per day</h2></div><div id="chart-users" class="panel-pad"></div></section></div>' +
        '<div id="dash-loans"></div><div id="dash-pay"></div>';
      nsBarChart(document.getElementById("chart-loans"), d.series_applications, {
        label: "Loan applications per day", color: "#0b2f5e", unit: "applications",
        link: function (day) { return "admin-loans.html?date_from=" + day + "&date_to=" + day; },
      });
      nsBarChart(document.getElementById("chart-users"), d.series_users, {
        label: "New customers per day", color: "#0b5563", unit: "customers",
        link: function (day) { return "admin-users.html?date_from=" + day + "&date_to=" + day; },
      });
      new NsTable({ el: "dash-loans", dataset: "loans", simple: true, title: "Loans waiting for review", base: { status: "pending" }, columns: nsLoanColumns(), onRender: nsWireLoanButtons, emptyText: "No loans are waiting for review." });
      new NsTable({ el: "dash-pay", dataset: "emi_payments", simple: true, title: "EMI proofs waiting for review", base: { status: "pending" }, columns: nsPaymentColumns(), onRender: nsWirePaymentButtons, emptyText: "No EMI proofs are waiting for review." });
    }
    load();
  },

  users: function (root) {
    new NsTable({
      el: root, dataset: "users", syncUrl: true, searchHint: "ID, name, mobile, email, city", dateLabel: "Joined",
      cardParam: "kyc_status",
      cards: [
        { value: "", label: "All customers", color: "navy" },
        { value: "pending", label: "KYC not started", color: "slate" },
        { value: "submitted", label: "KYC to review", color: "blue" },
        { value: "verified", label: "KYC verified", color: "green" },
        { value: "rejected", label: "KYC rejected", color: "red" },
      ],
      filters: [
        { key: "kyc_status", label: "KYC", options: ["pending", "submitted", "verified", "rejected"] },
        { key: "account_status", label: "Account", options: ["active", "inactive", "blocked", "suspended"] },
        { key: "onboarding_step", label: "Onboarding", options: [["0", "Not started"], ["1", "Step 1 done"], ["2", "Step 2 done"], ["3", "Completed"]] },
        { key: "employment_type", label: "Work", options: ["Salaried", "Self Employed", "Business", "Professional", "Unemployed", "Other"] },
        { key: "income", label: "Income", number: true },
      ],
      columns: [
        { key: "id", label: "ID", render: function (r) { return nsLink("admin-user-view.html?id=" + r.id, "#" + r.id); } },
        { key: "name", label: "Name", render: function (r) { return nsLink("admin-user-view.html?id=" + r.id, r.name); } },
        { key: "mobile_number", label: "Mobile" },
        { key: "email", label: "Email" },
        { key: "current_city", label: "City" },
        { key: "employment_type", label: "Work" },
        { key: "monthly_income", label: "Income", num: true, render: function (r) { return nsMoney(r.monthly_income); } },
        { key: "kyc_status", label: "KYC", render: function (r) { return nsBadge(r.kyc_status); } },
        { key: "account_status", label: "Active", toggle: {
          isOn: function (r) { return r.account_status === "active"; },
          lockedReason: function () { return nsAdmin && nsAdmin.role === "super_admin" ? "" : "Only a super admin can change this"; },
          confirm: function (r, on) { return on ? "Turn on " + (r.name || "this customer") + "?" : "Turn off " + (r.name || "this customer") + "? They will not be able to use the app."; },
          save: function (r, on) { return nsPost("/api/admin/users/" + r.id + "/status", { active: on }); },
        } },
        { key: "onboarding_step", label: "Step", num: true },
        { key: "created_at", label: "Joined" },
        { key: "last_login", label: "Last login" },
        { key: "actions", label: "Actions", sortable: false, render: function (r) { return '<a class="btn btn-xs btn-light" href="admin-user-view.html?id=' + nsEsc(r.id) + '">View</a>'; } },
      ],
    });
  },

  loans: function (root) {
    new NsTable({
      el: root, dataset: "loans", syncUrl: true, searchHint: "ID, code, customer, mobile, UTR", dateLabel: "Applied",
      cardParam: "status",
      cards: [
        { value: "", label: "All loans", color: "navy" },
        { value: "pending", label: "Pending", color: "amber" },
        { value: "approved", label: "Approved", color: "teal" },
        { value: "disbursed", label: "Disbursed", color: "indigo" },
        { value: "closed", label: "Closed", color: "green" },
        { value: "rejected", label: "Rejected", color: "red" },
        { value: "cancelled", label: "Cancelled", color: "slate" },
      ],
      filters: [
        { key: "status", label: "Status", options: NS_LOAN_STATUSES },
        { key: "purpose", label: "Purpose", options: NS_PURPOSES },
        { key: "kyc_status", label: "KYC", options: ["pending", "submitted", "verified", "rejected"] },
        { key: "amount", label: "Amount", number: true },
        { key: "tenure", label: "Months", number: true },
      ],
      columns: nsLoanColumns(),
      onRender: nsWireLoanButtons,
    });
  },

  "loan-view": async function (root) {
    var id = nsParam("id");
    if (!id) {
      root.innerHTML = '<div class="empty">No loan selected. <a href="admin-loans.html">Open the loan list</a>.</div>';
      return;
    }
    var a;
    try {
      a = (await adminApi("/api/admin/loan/applications/" + encodeURIComponent(id))).application;
    } catch (err) {
      if (err.status === 401) return redirectToAdminLogin();
      root.innerHTML = '<div class="empty"><strong>Could not load this loan.</strong> ' + nsEsc(err.message) + ' <a href="admin-loans.html">Back to loans</a></div>';
      return;
    }
    var user = {};
    try {
      user = (await adminApi("/api/admin/users/" + encodeURIComponent(a.user_id))).user || {};
    } catch (e) {}
    var buttons = "";
    if (a.status === "pending") {
      buttons = '<button type="button" class="btn btn-sm btn-green" data-loan="approve">Approve</button><button type="button" class="btn btn-sm btn-red" data-loan="reject">Reject</button>';
    } else if (a.status === "approved") {
      buttons = '<button type="button" class="btn btn-sm btn-primary" data-loan="disburse">Disburse</button>';
    }
    var guarantors = (a.guarantors || []).map(function (g) {
      return "<tr><td>" + nsEsc(g.index) + "</td><td>" + nsText(g.full_name) + "</td><td>" + nsText(g.mobile_number) + "</td><td>" + nsText(g.relation) + "</td><td>" + nsText(g.address) + "</td><td>" + nsText(g.aadhaar_last4) + "</td><td>" + nsText(g.pan_last4) + "</td></tr>";
    }).join("");
    root.innerHTML =
      '<div class="page-actions"><a class="btn btn-sm btn-light" href="admin-loans.html">Back to loans</a>' + buttons + "</div>" +
      '<div class="stat-row">' +
      '<div class="stat stat-navy"><span class="stat-value">' + nsMoney(a.requested_amount) + '</span><span class="stat-label">Loan amount</span></div>' +
      '<div class="stat stat-indigo"><span class="stat-value">' + nsMoney(a.emi_amount) + '</span><span class="stat-label">EMI, ' + nsEsc(a.tenure_months) + " months</span></div>" +
      '<div class="stat stat-teal"><span class="stat-value">' + nsMoney(a.total_payable) + '</span><span class="stat-label">Total repayable</span></div>' +
      '<div class="stat stat-purple"><span class="stat-value">' + nsEsc(a.interest_rate_annual == null ? "—" : a.interest_rate_annual + "%") + '</span><span class="stat-label">Yearly interest</span></div>' +
      '<div class="stat stat-slate"><span class="stat-value">' + nsMoney(a.processing_fee) + '</span><span class="stat-label">Processing fee</span></div>' +
      "</div>" +
      '<div class="grid-2"><section class="panel"><div class="panel-head"><h2>Application ' + nsEsc(a.application_code) + "</h2>" + nsBadge(a.status) + "</div>" +
      nsMeta([
        ["Purpose", a.purpose], ["Details", a.purpose_detail], ["Applied", a.created_at], ["Reviewed", a.reviewed_at],
        ["Staff note", a.admin_note], ["Disbursed", a.disbursed_at], ["Bank / UTR reference", a.disbursement_reference], ["Closed", a.closed_at],
      ]) + "</section>" +
      '<section class="panel"><div class="panel-head"><h2>Customer</h2><a class="btn btn-xs btn-light" href="admin-user-view.html?id=' + nsEsc(a.user_id) + '">Open profile</a></div>' +
      nsMeta([
        ["Name", user.name], ["Mobile", user.mobile_number], ["KYC", nsBadge(user.kyc_status), true], ["Work", a.employment_type], ["Company", a.company_name],
        ["Monthly income", nsMoney(a.monthly_income), true], ["Bank", a.bank_name], ["IFSC / account", (a.ifsc_code || "—") + " / ••••" + (a.bank_account_last4 || "")],
      ]) + "</section></div>" +
      '<section class="panel"><div class="panel-head"><h2>Guarantors</h2></div><div class="table-responsive"><table class="table table-striped table-bordered"><thead><tr><th>No.</th><th>Name</th><th>Mobile</th><th>Relation</th><th>Address</th><th>Aadhaar</th><th>PAN</th></tr></thead><tbody>' +
      (guarantors || '<tr><td colspan="7">No guarantors recorded.</td></tr>') + "</tbody></table></div></section>" +
      '<div id="loan-emis"></div><div id="loan-pays"></div>';
    root.querySelectorAll("[data-loan]").forEach(function (b) {
      b.addEventListener("click", function () {
        nsLoanDecision(a.id, b.getAttribute("data-loan"), function () { window.location.reload(); });
      });
    });
    new NsTable({ el: "loan-emis", dataset: "emi_schedules", simple: true, title: "EMI schedule", base: { loan_id: a.id }, columns: nsScheduleColumns(), emptyText: "The EMI schedule is created when the loan is approved." });
    new NsTable({ el: "loan-pays", dataset: "emi_payments", simple: true, title: "EMI payments", base: { loan_id: a.id }, columns: nsPaymentColumns(), onRender: nsWirePaymentButtons, emptyText: "No payments submitted yet." });
  },

  emi: function (root) {
    new NsTable({
      el: root, dataset: "emi_payments", syncUrl: true, searchHint: "ID, loan code, UTR, customer, mobile", dateLabel: "Submitted",
      cardParam: "status",
      cards: [
        { value: "", label: "All payments", color: "navy" },
        { value: "pending", label: "To verify", color: "amber" },
        { value: "approved", label: "Approved", color: "green" },
        { value: "rejected", label: "Rejected", color: "red" },
      ],
      filters: [
        { key: "status", label: "Status", options: ["pending", "approved", "rejected"] },
        { key: "amount", label: "Amount", number: true },
      ],
      columns: nsPaymentColumns(),
      onRender: nsWirePaymentButtons,
    });
  },

  schedules: function (root) {
    new NsTable({
      el: root, dataset: "emi_schedules", syncUrl: true, searchHint: "Loan code, customer, mobile", dateLabel: "Due date",
      cardParam: "status",
      cards: [
        { value: "", label: "All EMIs", color: "navy" },
        { value: "pending", label: "Upcoming", color: "blue" },
        { value: "awaiting_proof", label: "Proof to verify", color: "amber" },
        { value: "overdue", label: "Overdue", color: "red" },
        { value: "paid", label: "Paid", color: "green" },
      ],
      filters: [
        { key: "status", label: "EMI", options: ["pending", "awaiting_proof", "overdue", "paid"] },
        { key: "loan_status", label: "Loan", options: ["approved", "disbursed", "closed", "rejected"] },
        { key: "amount", label: "Amount", number: true },
      ],
      columns: nsScheduleColumns(),
    });
  },

  guarantors: function (root) {
    new NsTable({
      el: root, dataset: "guarantors", syncUrl: true, searchHint: "Guarantor, mobile, loan code, customer", dateLabel: "Added",
      filters: [{ key: "relation", label: "Relation", options: ["father", "mother", "spouse", "brother", "sister", "friend", "colleague", "other"] }],
      columns: [
        { key: "id", label: "ID" },
        { key: "application_code", label: "Loan", render: function (r) { return nsLink("admin-loan-view.html?id=" + r.loan_application_id, r.application_code); } },
        { key: "customer", label: "Customer" },
        { key: "guarantor_index", label: "No.", num: true },
        { key: "full_name", label: "Guarantor" },
        { key: "mobile_number", label: "Mobile" },
        { key: "relation", label: "Relation" },
        { key: "address", label: "Address" },
        { key: "aadhaar_last4", label: "Aadhaar" },
        { key: "pan_last4", label: "PAN" },
        { key: "created_at", label: "Added" },
      ],
    });
  },

  notifications: function (root) {
    root.innerHTML =
      '<div class="tabs" role="tablist"><button type="button" class="tab is-active" data-tab="alerts">Staff alerts</button><button type="button" class="tab" data-tab="customers">Sent to customers</button><button type="button" class="tab" data-tab="send">Send a message</button></div>' +
      '<div id="tab-alerts"></div><div id="tab-customers" hidden></div><div id="tab-send" hidden></div>';
    var built = {};
    function show(name) {
      ["alerts", "customers", "send"].forEach(function (t) {
        document.getElementById("tab-" + t).hidden = t !== name;
      });
      root.querySelectorAll(".tab").forEach(function (b) {
        b.classList.toggle("is-active", b.getAttribute("data-tab") === name);
      });
      if (built[name]) return;
      built[name] = true;
      if (name === "alerts") {
        new NsTable({
          el: "tab-alerts", dataset: "admin_alerts", searchHint: "Title or message",
          cardParam: "type",
          cards: [
            { value: "", label: "All alerts", color: "navy" },
            { value: "new_user", label: "New customers", color: "teal" },
            { value: "new_loan", label: "New loans", color: "indigo" },
            { value: "kyc_submitted", label: "KYC submitted", color: "blue" },
            { value: "emi_proof", label: "EMI proofs", color: "green" },
          ],
          filters: [{ key: "type", label: "Type", options: [["new_user", "New customer"], ["new_loan", "New loan"], ["kyc_submitted", "KYC submitted"], ["emi_proof", "EMI proof"]] }],
          columns: [
            { key: "id", label: "ID" },
            { key: "type", label: "Type", render: function (r) { return nsBadge(r.type); } },
            { key: "title", label: "Title" },
            { key: "body", label: "Message" },
            { key: "created_at", label: "Time" },
            { key: "link", label: "Open", sortable: false, render: function (r) { var u = nsSafeUrl(r.link); return u ? '<a class="btn btn-xs btn-light" href="' + nsEsc(u) + '">Open</a>' : nsText(""); } },
          ],
        });
      } else if (name === "customers") {
        new NsTable({
          el: "tab-customers", dataset: "user_notifications", searchHint: "Title, message, customer, mobile",
          filters: [{ key: "category", label: "Category", options: ["loan_status", "emi_reminder", "emi_payment", "kyc", "general"] }],
          columns: [
            { key: "id", label: "ID" },
            { key: "customer", label: "Customer", render: function (r) { return nsLink("admin-user-view.html?id=" + r.user_id, r.customer); } },
            { key: "category", label: "Category", render: function (r) { return nsBadge(r.category); } },
            { key: "title", label: "Title" },
            { key: "body", label: "Message" },
            { key: "read_at", label: "Read" },
            { key: "created_at", label: "Sent" },
          ],
        });
      } else {
        var isSuper = nsAdmin && nsAdmin.role === "super_admin";
        document.getElementById("tab-send").innerHTML =
          '<section class="panel panel-narrow"><div class="panel-head"><h2>Send a message to customers</h2></div><form class="form-grid panel-pad" id="send-form">' +
          '<div class="field field-full"><label for="send-to">Send to</label><select class="form-control" id="send-to"><option value="one">One customer</option>' + (isSuper ? '<option value="all">All active customers</option>' : "") + "</select></div>" +
          '<div class="field field-full" id="send-id-wrap"><label for="send-user">Customer ID</label><input class="form-control" id="send-user" type="number" min="1"></div>' +
          '<div class="field field-full"><label for="send-title">Title</label><input class="form-control" id="send-title" maxlength="200" required></div>' +
          '<div class="field field-full"><label for="send-body">Message</label><textarea class="form-control" id="send-body" rows="3" maxlength="1000" required></textarea></div>' +
          '<div class="field field-full"><button type="submit" class="btn btn-sm btn-primary">Send message</button></div></form></section>';
        document.getElementById("send-to").addEventListener("change", function (e) {
          document.getElementById("send-id-wrap").hidden = e.target.value === "all";
        });
        document.getElementById("send-form").addEventListener("submit", function (e) {
          e.preventDefault();
          var all = document.getElementById("send-to").value === "all";
          if (all && !window.confirm("Send this message to all active customers?")) return;
          var body = { title: document.getElementById("send-title").value, body: document.getElementById("send-body").value };
          if (all) body.all = true;
          else body.user_id = document.getElementById("send-user").value;
          nsAct(function () { return nsPost("/api/admin/notifications/send", body); }, "Message sent", function () {
            document.getElementById("send-form").reset();
          });
        });
      }
    }
    root.querySelectorAll(".tab").forEach(function (b) {
      b.addEventListener("click", function () { show(b.getAttribute("data-tab")); });
    });
    show("alerts");
  },

  settings: function (root) {
    var canEdit = nsAdmin && nsAdmin.role === "super_admin";
    root.innerHTML =
      '<section class="panel"><div class="panel-head"><h2>EMI payment details shown in the app</h2></div><form class="form-grid panel-pad" id="emi-form">' +
      [["payee_name", "Payee name"], ["upi_id", "UPI ID"], ["qr_image_url", "QR image URL"], ["bank_name", "Bank"], ["bank_account_name", "Account name"], ["bank_account_number", "Account number"], ["ifsc_code", "IFSC"]]
        .map(function (f) {
          return '<div class="field"><label for="emi-' + f[0] + '">' + f[1] + '</label><input class="form-control" id="emi-' + f[0] + '"' + (canEdit ? "" : " disabled") + "></div>";
        })
        .join("") +
      '<div class="field field-full"><label for="emi-payment_note">Note for customers</label><textarea class="form-control" id="emi-payment_note" rows="2"' + (canEdit ? "" : " disabled") + "></textarea></div>" +
      (canEdit ? '<div class="field field-full"><button type="submit" class="btn btn-sm btn-primary">Save payment details</button></div>' : '<p class="text-muted field-full">Only a super admin can change these.</p>') +
      "</form></section>" +
      '<section class="panel"><div class="panel-head"><h2>Loan guide videos</h2><button type="button" class="btn btn-sm btn-primary" id="video-new">Add video</button></div>' +
      '<form class="form-grid panel-pad" id="video-form" hidden><input type="hidden" id="video-id">' +
      '<div class="field"><label for="video-title">Title</label><input class="form-control" id="video-title" required maxlength="200"></div>' +
      '<div class="field"><label for="video-url">Video URL</label><input class="form-control" id="video-url" required maxlength="1000"></div>' +
      '<div class="field"><label for="video-thumb">Thumbnail URL</label><input class="form-control" id="video-thumb" maxlength="1000"></div>' +
      '<div class="field"><label for="video-type">Type</label><select class="form-control" id="video-type"><option>mp4</option><option>youtube</option><option>vimeo</option><option>hls</option></select></div>' +
      '<div class="field"><label for="video-duration">Seconds</label><input class="form-control" id="video-duration" type="number" min="0"></div>' +
      '<div class="field"><label for="video-sort">Order</label><input class="form-control" id="video-sort" type="number" value="0"></div>' +
      '<div class="field"><label for="video-active">Status</label><select class="form-control" id="video-active"><option value="1">Active</option><option value="0">Inactive</option></select></div>' +
      '<div class="field field-full"><label for="video-desc">Description</label><textarea class="form-control" id="video-desc" rows="2" maxlength="500"></textarea></div>' +
      '<div class="field field-full btn-row"><button type="submit" class="btn btn-sm btn-primary">Save video</button><button type="button" class="btn btn-sm btn-light" id="video-cancel">Cancel</button></div></form>' +
      '<div id="video-table"></div></section>';

    adminApi("/api/admin/settings/emi-collection").then(function (d) {
      var s = d.settings || {};
      ["payee_name", "upi_id", "qr_image_url", "bank_name", "bank_account_name", "bank_account_number", "ifsc_code", "payment_note"].forEach(function (k) {
        document.getElementById("emi-" + k).value = s[k] || "";
      });
    }).catch(function (err) {
      nsToast(err.message, "error");
    });
    document.getElementById("emi-form").addEventListener("submit", function (e) {
      e.preventDefault();
      var body = {};
      ["payee_name", "upi_id", "qr_image_url", "bank_name", "bank_account_name", "bank_account_number", "ifsc_code", "payment_note"].forEach(function (k) {
        body[k] = document.getElementById("emi-" + k).value.trim() || null;
      });
      nsAct(function () { return nsPost("/api/admin/settings/emi-collection", body); }, "Payment details saved");
    });

    var form = document.getElementById("video-form");
    var rowsById = {};
    var table = new NsTable({
      el: "video-table", dataset: "videos", simple: true, emptyText: "No guide videos yet.",
      columns: [
        { key: "id", label: "ID" },
        { key: "title", label: "Title" },
        { key: "video_type", label: "Type" },
        { key: "video_url", label: "Video", sortable: false, render: function (r) { var u = nsSafeUrl(r.video_url); return u ? '<a href="' + nsEsc(u) + '" target="_blank" rel="noopener noreferrer">Open</a>' : nsText(""); } },
        { key: "duration_seconds", label: "Seconds", num: true },
        { key: "sort_order", label: "Order", num: true },
        { key: "is_active", label: "Active", toggle: {
          isOn: function (r) { return Number(r.is_active) === 1; },
          save: function (r, on) { return nsPost("/api/admin/videos/" + r.id + "/update", Object.assign({}, r, { is_active: on })); },
        } },
        { key: "created_at", label: "Added" },
        { key: "actions", label: "Actions", sortable: false, render: function (r) {
          rowsById[r.id] = r;
          return '<div class="btn-row"><button type="button" class="btn btn-xs btn-light" data-video-edit="' + nsEsc(r.id) + '">Edit</button><button type="button" class="btn btn-xs btn-red" data-video-del="' + nsEsc(r.id) + '">Delete</button></div>';
        } },
      ],
      onRender: function (body, t) {
        body.querySelectorAll("[data-video-edit]").forEach(function (b) {
          b.addEventListener("click", function () { openForm(rowsById[b.getAttribute("data-video-edit")]); });
        });
        body.querySelectorAll("[data-video-del]").forEach(function (b) {
          b.addEventListener("click", function () {
            var id = b.getAttribute("data-video-del");
            if (!window.confirm("Delete video #" + id + "?")) return;
            nsAct(function () { return nsPost("/api/admin/videos/" + id + "/delete"); }, "Video deleted", function () { t.load(); });
          });
        });
      },
    });
    function openForm(r) {
      r = r || {};
      form.hidden = false;
      document.getElementById("video-id").value = r.id || "";
      document.getElementById("video-title").value = r.title || "";
      document.getElementById("video-url").value = r.video_url || "";
      document.getElementById("video-thumb").value = r.thumbnail_url || "";
      document.getElementById("video-type").value = r.video_type || "mp4";
      document.getElementById("video-duration").value = r.duration_seconds == null ? "" : r.duration_seconds;
      document.getElementById("video-sort").value = r.sort_order == null ? 0 : r.sort_order;
      document.getElementById("video-active").value = r.is_active === 0 ? "0" : "1";
      document.getElementById("video-desc").value = r.description || "";
      document.getElementById("video-title").focus();
    }
    document.getElementById("video-new").addEventListener("click", function () { openForm(); });
    document.getElementById("video-cancel").addEventListener("click", function () { form.hidden = true; });
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var id = document.getElementById("video-id").value;
      var body = {
        title: document.getElementById("video-title").value,
        video_url: document.getElementById("video-url").value,
        thumbnail_url: document.getElementById("video-thumb").value,
        video_type: document.getElementById("video-type").value,
        duration_seconds: document.getElementById("video-duration").value,
        sort_order: document.getElementById("video-sort").value,
        is_active: document.getElementById("video-active").value === "1",
        description: document.getElementById("video-desc").value,
      };
      nsAct(function () { return nsPost(id ? "/api/admin/videos/" + id + "/update" : "/api/admin/videos", body); }, "Video saved", function () {
        form.hidden = true;
        table.load();
      });
    });
  },

  admins: function (root) {
    if (!nsAdmin || nsAdmin.role !== "super_admin") {
      root.innerHTML = '<div class="empty">Only a super admin can manage staff accounts.</div>';
      return;
    }
    root.innerHTML =
      '<section class="panel panel-narrow"><div class="panel-head"><h2>Add a staff account</h2></div><form class="form-grid panel-pad" id="admin-form" autocomplete="off">' +
      '<div class="field"><label for="new-name">Full name</label><input class="form-control" id="new-name" required maxlength="150"></div>' +
      '<div class="field"><label for="new-email">Email</label><input class="form-control" id="new-email" type="email" required></div>' +
      '<div class="field"><label for="new-mobile">Mobile</label><input class="form-control" id="new-mobile"></div>' +
      '<div class="field"><label for="new-password">Password (8+ characters)</label><input class="form-control" id="new-password" type="password" required minlength="8" autocomplete="new-password"></div>' +
      '<div class="field field-full"><button type="submit" class="btn btn-sm btn-primary">Add staff account</button></div></form></section><div id="admin-table"></div>';
    var table = new NsTable({
      el: "admin-table", dataset: "admins", searchHint: "Name, email, mobile", dateLabel: "Added",
      filters: [
        { key: "role", label: "Level", options: [["super_admin", "Super admin"], ["admin", "Admin"]] },
        { key: "account_status", label: "Status", options: ["active", "inactive"] },
      ],
      columns: [
        { key: "id", label: "ID" },
        { key: "full_name", label: "Name" },
        { key: "email", label: "Email" },
        { key: "mobile_number", label: "Mobile" },
        { key: "role", label: "Level", render: function (r) { return nsBadge(r.role); } },
        { key: "account_status", label: "Active", toggle: {
          isOn: function (r) { return r.account_status === "active"; },
          lockedReason: function (r) { return Number(r.id) === Number(nsAdmin.id) ? "You cannot turn off your own account" : ""; },
          confirm: function (r, on) { return (on ? "Turn on " : "Turn off ") + (r.full_name || "this staff account") + "?"; },
          save: function (r, on) { return nsPost("/api/admin/admins/" + r.id + "/status", { active: on }); },
        } },
        { key: "last_login", label: "Last login" },
        { key: "created_at", label: "Added" },
      ],
    });
    document.getElementById("admin-form").addEventListener("submit", function (e) {
      e.preventDefault();
      var body = {
        full_name: document.getElementById("new-name").value,
        email: document.getElementById("new-email").value,
        mobile_number: document.getElementById("new-mobile").value,
        password: document.getElementById("new-password").value,
      };
      nsAct(function () { return nsPost("/api/admin/auth/register", body); }, "Staff account added", function () {
        document.getElementById("admin-form").reset();
        table.load();
      });
    });
  },

  database: function (root) {
    var tables = [
      ["users", "Customers"], ["loans", "Loans"], ["emi_payments", "EMI payments"], ["emi_schedules", "EMI schedule"], ["guarantors", "Guarantors"],
      ["user_notifications", "Customer notifications"], ["admin_alerts", "Staff alerts"], ["banners", "Banners"], ["videos", "Guide videos"], ["emi_settings", "Payment details"],
    ];
    if (nsAdmin && nsAdmin.role === "super_admin") tables.push(["admins", "Staff"]);
    var current = nsParam("t");
    if (!tables.some(function (t) { return t[0] === current; })) current = "users";
    root.innerHTML =
      '<div class="tabs">' + tables.map(function (t) {
        return '<a class="tab' + (t[0] === current ? " is-active" : "") + '" href="admin-database.html?t=' + t[0] + '">' + nsEsc(t[1]) + "</a>";
      }).join("") + '</div><div id="db-table"></div>';
    new NsTable({ el: "db-table", dataset: current });
  },
};

/** Loan and payment tables on the customer page. */
function nsUserViewExtras(userId) {
  var host = document.getElementById("user-view-root");
  if (!host) return;
  host.insertAdjacentHTML("beforeend", '<div id="uv-loans"></div><div id="uv-pays"></div>');
  new NsTable({ el: "uv-loans", dataset: "loans", simple: true, title: "Loans", base: { user_id: userId }, columns: nsLoanColumns(), onRender: nsWireLoanButtons, emptyText: "This customer has no loans." });
  new NsTable({ el: "uv-pays", dataset: "emi_payments", simple: true, title: "EMI payments", base: { user_id: userId }, columns: nsPaymentColumns(), onRender: nsWirePaymentButtons, emptyText: "This customer has no EMI payments." });
}

document.addEventListener("DOMContentLoaded", function () {
  var page = document.body.getAttribute("data-page");
  var render = NS_PAGES[page];
  if (!render || !document.getElementById("page-root")) return;
  initAdminShell({
    onReady: function (admin) {
      nsAdmin = admin || {};
      render(document.getElementById("page-root"));
    },
  });
});
