#!/usr/bin/env python3
"""Regression test for the admin list, export, dashboard and alert APIs.

WRITES TEST DATA: use a throwaway server and database with app.dev.expose_otp=true.
Usage: e2e_admin.py <base-url> <admin-email> <admin-password>
Needs the openpyxl package to check the Excel download.
"""
import csv
import io
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")
BASE, EMAIL, PASSWORD = sys.argv[1:4]
failures = []


def raw(method, path, body=None, tok=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"} if data else {}
    if tok:
        headers["Authorization"] = "Bearer " + tok
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        res = urllib.request.urlopen(req, timeout=60)
        return res.status, res.read(), res.headers
    except urllib.error.HTTPError as e:
        return e.code, e.read(), e.headers


def call(method, path, body=None, tok=None):
    status, content, _ = raw(method, path, body, tok)
    return status, json.loads(content.decode("utf-8"))


def check(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else f"   -> {detail}"))
    if not ok:
        failures.append(name)


s, j = call("POST", "/api/admin/auth/register", {"email": EMAIL, "password": PASSWORD, "full_name": "Test Super"})
s, j = call("POST", "/api/admin/auth/login", {"email": EMAIL, "password": PASSWORD})
A = j["data"]["access_token"]
ME = j["data"]["admin"]["id"]


def lst(dataset, **params):
    return call("GET", f"/api/admin/list/{dataset}?" + urllib.parse.urlencode(params), tok=A)


print("=== every list loads and pages")
for ds in ["users", "loans", "emi_payments", "emi_schedules", "guarantors", "user_notifications", "banners", "videos", "emi_settings", "admin_alerts", "admins"]:
    st, res = lst(ds, limit=25, offset=0)
    check(f"list {ds}", st == 200 and "total" in res["data"] and "rows" in res["data"], res)

st, res = lst("users", limit=25)
total = res["data"]["total"]
check("page size 25 is respected", len(res["data"]["rows"]) == min(25, total))
for size in [50, 100, 200, 500]:
    check(f"page size {size}", lst("users", limit=size)[1]["data"]["limit"] == size)
check("other page sizes fall back to 25", lst("users", limit=7)[1]["data"]["limit"] == 25)
if total > 25:
    a = lst("users", limit=25, offset=0)[1]["data"]["rows"][0]["id"]
    b = lst("users", limit=25, offset=25)[1]["data"]["rows"][0]["id"]
    check("second page differs", a != b)
check("unknown list is 404", lst("nope")[0] == 404)

print("=== filters")
rows = lst("loans", status="pending", limit=500)[1]["data"]["rows"]
check("status filter", all(r["status"] == "pending" for r in rows))
rows = lst("loans", amount_min=20000, amount_max=30000, limit=500)[1]["data"]["rows"]
check("amount range", all(20000 <= float(r["requested_amount"]) <= 30000 for r in rows))
res = lst("loans", date_from="2020-01-01", date_to="2020-01-31")[1]["data"]
check("date range excludes everything", res["total"] == 0 and res["rows"] == [], res["total"])
check("bad date is a clean 400", lst("loans", date_from="05-11-2026")[0] == 400)
all_counts = lst("loans", limit=25)[1]["data"]["counts"]
filtered = lst("loans", status="pending")[1]["data"]
check("cards still count every status while one is selected", filtered["counts"] == all_counts, filtered["counts"])
st, res = lst("loans", sort="requested_amount", dir="asc", limit=500)
amounts = [float(r["requested_amount"]) for r in res["data"]["rows"]]
check("sort ascending", amounts == sorted(amounts))
st, res = lst("loans", sort="password_hash; DROP TABLE users", limit=25)
check("unknown sort column is ignored", st == 200)
st, res = lst("users", search="' OR '1'='1")
check("search text is not SQL", st == 200 and res["data"]["total"] == 0, res)

print("=== nothing secret comes back")
row = lst("users", limit=25)[1]["data"]["rows"][0]
bad = [k for k in row if k in ("password_hash", "otp_hash", "session_token", "fcm_token", "contacts_data", "gallery_data", "aadhaar_encrypted", "pan_encrypted")]
check("user list has no secret columns", not bad, bad)
row = lst("admins", limit=25)[1]["data"]["rows"][0]
check("admin list has no secret columns", "password_hash" not in row and "otp_hash" not in row)

print("=== downloads")
st, content, headers = raw("GET", "/api/admin/list/loans/export?format=csv", tok=A)
check("csv download", st == 200 and "text/csv" in headers.get("Content-Type", ""), st)
text = content.decode("utf-8-sig")
reader = list(csv.reader(io.StringIO(text)))
check("csv has header and all rows", reader[0][0] == "Id" and len(reader) - 1 == lst("loans")[1]["data"]["total"], len(reader))
st, content, _ = raw("GET", "/api/admin/list/loans/export?format=csv&status=pending", tok=A)
rows = list(csv.DictReader(io.StringIO(content.decode("utf-8-sig"))))
check("csv follows the filter", all(r["Status"] == "pending" for r in rows) and len(rows) == lst("loans", status="pending")[1]["data"]["total"])
st, content, headers = raw("GET", "/api/admin/list/users/export?format=xlsx", tok=A)
check("excel download", st == 200 and "spreadsheetml" in headers.get("Content-Type", ""), st)
import openpyxl
wb = openpyxl.load_workbook(io.BytesIO(content))
sheet = wb.active
check("excel opens with all rows", sheet.max_row - 1 == lst("users")[1]["data"]["total"], sheet.max_row)
check("excel header bold", sheet.cell(1, 1).font.bold is True)
check("excel numbers are numbers", isinstance(sheet.cell(2, 1).value, (int, float)), type(sheet.cell(2, 1).value))
check("download needs login", raw("GET", "/api/admin/list/users/export?format=csv")[0] == 401)

print("=== formula injection")
s, j = call("POST", "/api/auth/otp/send", {"mobile_number": "9555500001"})
otp = j["data"]["dev_otp"]
u = call("POST", "/api/auth/otp/verify", {"mobile_number": "9555500001", "otp": otp})[1]["data"]["access_token"]
call("POST", "/api/user/onboarding", {"step": 1, "name": "=HYPERLINK(\"http://evil\",\"x\")", "first_name": "A", "last_name": "B", "email": "inject@test.local", "gender": "Male", "date_of_birth": "1990-01-01"}, u)
st, content, _ = raw("GET", "/api/admin/list/users/export?format=csv&search=inject", tok=A)
rows = list(csv.DictReader(io.StringIO(content.decode("utf-8-sig"))))
check("csv neutralises a formula in a name", rows and rows[0]["Name"].startswith("'="), rows[:1])
st, content, _ = raw("GET", "/api/admin/list/users/export?format=xlsx&search=inject", tok=A)
sheet = openpyxl.load_workbook(io.BytesIO(content)).active
names = [c.value for c in sheet[2]]
check("excel neutralises a formula in a name", any(isinstance(v, str) and v.startswith("'=") for v in names), names[:4])

print("=== alerts")
base = call("GET", "/api/admin/alerts?after_id=0", tok=A)[1]["data"]
check("first poll announces nothing old", base["new_items"] == [])
check("new customer raised an alert", any(a["type"] == "new_user" and "9555500001" in a["body"] for a in base["recent"]), base["recent"][:2])
latest = base["latest_id"]
s, j = call("POST", "/api/auth/otp/send", {"mobile_number": "9555500002"})
check("OTP request alone raises no alert", call("GET", f"/api/admin/alerts?after_id={latest}", tok=A)[1]["data"]["new_items"] == [])
u2 = call("POST", "/api/auth/otp/verify", {"mobile_number": "9555500002", "otp": j["data"]["dev_otp"]})[1]["data"]["access_token"]
items = call("GET", f"/api/admin/alerts?after_id={latest}", tok=A)[1]["data"]["new_items"]
check("verified sign-up raises one alert", len(items) == 1 and items[0]["type"] == "new_user", items)
latest = items[0]["id"]
s, j = call("POST", "/api/auth/otp/send", {"mobile_number": "9555500002"})
call("POST", "/api/auth/otp/verify", {"mobile_number": "9555500002", "otp": j["data"]["dev_otp"]})
check("second login raises no alert", call("GET", f"/api/admin/alerts?after_id={latest}", tok=A)[1]["data"]["new_items"] == [])
call("POST", "/api/user/onboarding", {"step": 1, "name": "Alert User", "first_name": "A", "last_name": "U", "email": "alert.user@test.local", "gender": "Male", "date_of_birth": "1990-01-01"}, u2)
call("POST", "/api/user/onboarding", {"step": 2, "current_address": "A", "current_city": "C", "current_state": "S", "current_pincode": "110001", "employment_type": "Salaried", "monthly_income": 30000}, u2)
st3 = {"step": 3, "aadhaar_last4": "1234", "pan_last4": "123F", "bank_name": "B", "ifsc_code": "HDFC0001234", "bank_account_last4": "1234", "privacy_consent": True, "terms_accepted": True, "kyc_consent": True}
call("POST", "/api/user/onboarding", st3, u2)
s, j = call("POST", "/api/v1/loan/apply", {"requested_amount": 10000, "tenure_months": 3, "purpose": "personal", "guarantors": [{"full_name": "G", "mobile_number": "9000000199", "relation": "friend"}]}, u2)
check("loan applied", s == 201, j)
loan = j["data"]["loan_application"]["id"]
items = call("GET", f"/api/admin/alerts?after_id={latest}", tok=A)[1]["data"]["new_items"]
types = [i["type"] for i in items]
check("KYC and new loan raise alerts", types == ["kyc_submitted", "new_loan"], types)
check("alert links to the loan", items[-1]["link"] == f"admin-loan-view.html?id={loan}", items[-1])
before = call("GET", "/api/admin/alerts", tok=A)[1]["data"]["unread_count"]
check("unread counted", before > 0)
call("POST", "/api/admin/alerts/read", {}, A)
check("mark all read", call("GET", "/api/admin/alerts", tok=A)[1]["data"]["unread_count"] == 0)

print("=== dashboard and loan page")
st, res = call("GET", "/api/admin/dashboard/summary", tok=A)
d = res["data"]
check("dashboard loads", st == 200 and len(d["series_applications"]) == 30, st)
check("dashboard pending count matches list", float(d["pending_loans"]) == lst("loans", status="pending")[1]["data"]["total"])
check("dashboard customers match list", float(d["total_users"]) == lst("users")[1]["data"]["total"])
check("series sums to applications", sum(p["count"] for p in d["series_applications"]) == float(d["applications"]))
check("dashboard bad date is 400", call("GET", "/api/admin/dashboard/summary?date_from=nope", tok=A)[0] == 400)
st, res = call("GET", f"/api/admin/loan/applications/{loan}", tok=A)
check("loan detail with guarantors", st == 200 and len(res["data"]["application"]["guarantors"]) == 1, res)
check("missing loan is 404", call("GET", "/api/admin/loan/applications/999999", tok=A)[0] == 404)

print("=== staff roles")
s, j = call("POST", "/api/admin/auth/register", {"email": "low.e2e@test.local", "password": PASSWORD, "full_name": "Low"}, A)
check("add staff", s == 201, j)
low_id = j["data"]["admin"]["id"]
LOW = call("POST", "/api/admin/auth/login", {"email": "low.e2e@test.local", "password": PASSWORD})[1]["data"]["access_token"]
check("staff cannot list admins", call("GET", "/api/admin/list/admins", tok=LOW)[0] == 403)
check("staff cannot export admins", raw("GET", "/api/admin/list/admins/export?format=csv", tok=LOW)[0] == 403)
check("staff can list loans", call("GET", "/api/admin/list/loans", tok=LOW)[0] == 200)
check("staff cannot change staff", call("POST", f"/api/admin/admins/{ME}/status", {"active": False}, LOW)[0] == 403)
check("staff cannot message everyone", call("POST", "/api/admin/notifications/send", {"all": True, "title": "T", "body": "B"}, LOW)[0] == 403)
check("cannot deactivate yourself", call("POST", f"/api/admin/admins/{ME}/status", {"active": False}, A)[0] == 400)
check("deactivate staff", call("POST", f"/api/admin/admins/{low_id}/status", {"active": False}, A)[0] == 200)
check("deactivated staff token stops working", call("GET", "/api/admin/list/loans", tok=LOW)[0] == 401)

print("=== settings, videos, messages")
s, j = call("POST", "/api/admin/videos", {"title": "V", "video_url": "https://x.example/v.mp4", "video_type": "mp4", "sort_order": 5, "is_active": True}, A)
check("add video", s == 200, j)
rows = lst("videos", search="V", limit=500)[1]["data"]["rows"]
vid = max(r["id"] for r in rows)
check("bad video url refused", call("POST", "/api/admin/videos", {"title": "V", "video_url": "javascript:alert(1)", "video_type": "mp4"}, A)[0] == 400)
check("edit video", call("POST", f"/api/admin/videos/{vid}/update", {"title": "V2", "video_url": "https://x.example/v2.mp4", "video_type": "youtube"}, A)[0] == 200)
check("delete video", call("POST", f"/api/admin/videos/{vid}/delete", {}, A)[0] == 200)
check("delete missing video is 404", call("POST", f"/api/admin/videos/{vid}/delete", {}, A)[0] == 404)
uid = lst("users", search="alert.user")[1]["data"]["rows"][0]["id"]
s, j = call("POST", "/api/admin/notifications/send", {"user_id": uid, "title": "Hello", "body": "Message"}, A)
check("message one customer", s == 200 and j["data"]["sent"] == 1, j)
check("message unknown customer is 404", call("POST", "/api/admin/notifications/send", {"user_id": 999999, "title": "H", "body": "M"}, A)[0] == 404)
s, j = call("POST", "/api/admin/notifications/send", {"all": True, "title": "All", "body": "M"}, A)
check("message everyone", s == 200 and j["data"]["sent"] >= 1, j)

print(f"\n{'ALL PASSED' if not failures else str(len(failures)) + ' FAILED: ' + ', '.join(failures)}")
sys.exit(1 if failures else 0)
