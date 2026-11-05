#!/usr/bin/env python3
"""Builds the full API reference by calling every API on a running server.

Each call must return the expected status, and every response field must have a description in
api_field_docs.py, or the build stops. So the docs can only show APIs and fields that really exist.

WRITES TEST DATA: use a throwaway server with an EMPTY database and app.dev.expose_otp=true.
Set loan.interest.annual_percent=12 and loan.processing_fee.percent=2 so the samples show interest.

Usage: build_api_docs.py <base-url> <output-dir> <cron-secret>
Writes: API-REFERENCE.md, API-REFERENCE.html, Narainsons-API.postman_collection.json
"""
import html
import json
import os
import re
import sys
import uuid
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from api_field_docs import FIELDS, TYPES, DYNAMIC, OPAQUE  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
BASE, OUT, CRON = sys.argv[1:4]
V = "/api/v1"
ctx = {}
endpoints = []
missing = {}

GROUPS = [
    ("system", "app", "System", "Check the server is up."),
    ("auth", "app", "Sign-in and tokens", "Mobile OTP, email OTP or password. All return the same token block."),
    ("user", "app", "Profile and onboarding", "Three onboarding steps in order, then the profile is complete."),
    ("files", "app", "File upload", "Upload first, then send the returned `url` in the API that needs it."),
    ("devices", "app", "Devices and push", "Register the FCM token so this phone gets push notifications."),
    ("dashboard", "app", "Home screen", "Everything the home screen needs."),
    ("loan", "app", "Loans", "Quote, apply, track, cancel."),
    ("emi", "app", "EMI", "Schedule and paying an EMI."),
    ("notifications", "app", "Notifications", "The in-app list. Every push is also saved here."),
    ("media", "app", "Public media", "Banner images, no sign-in needed."),
    ("a_auth", "staff", "Staff sign-in and profile", "Admin panel login. Staff tokens do not work on app APIs, and app tokens do not work here."),
    ("a_list", "staff", "Staff lists", "One API for every table: search, filters, dates, sorting, paging. See the list parameters below."),
    ("a_dash", "staff", "Staff dashboard and alerts", "Dashboard figures and the alert bell."),
    ("a_user", "staff", "Staff: customers", "Customer details, KYC review, on/off and delete."),
    ("a_loan", "staff", "Staff: loans", "Approve, reject and disburse."),
    ("a_emi", "staff", "Staff: EMI payments", "Verify payment proof."),
    ("a_cms", "staff", "Staff: banners, videos and settings", "What customers see in the app."),
    ("a_misc", "staff", "Staff: messages and accounts", "Messages to customers and staff accounts."),
    ("a_older", "staff", "Staff: older list APIs", "Still work. New work should use the list API."),
    ("internal", "staff", "Internal", "For scheduled jobs on the server."),
]


def http(method, path, body=None, token=None, multipart=None, headers=None, raw=False):
    hdr = dict(headers or {})
    data = None
    if multipart:
        bd = uuid.uuid4().hex
        parts = b""
        for name, value in multipart.get("fields", {}).items():
            parts += f'--{bd}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
        fname, ctype, content = multipart["file"]
        field = multipart.get("field", "file")
        parts += f'--{bd}\r\nContent-Disposition: form-data; name="{field}"; filename="{fname}"\r\nContent-Type: {ctype}\r\n\r\n'.encode() + content + f"\r\n--{bd}--\r\n".encode()
        data, hdr["Content-Type"] = parts, f"multipart/form-data; boundary={bd}"
    elif body is not None:
        data, hdr["Content-Type"] = json.dumps(body).encode(), "application/json"
    if token:
        hdr["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=hdr)
    try:
        res = urllib.request.urlopen(req, timeout=60)
        status, text = res.status, res.read()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read()
    if raw:
        return status, text
    return status, json.loads(text.decode("utf-8"))


def mask(value):
    """Examples must not look like usable credentials; long lists are cut to two items."""
    if isinstance(value, dict):
        out = {}
        for k, v in value.items():
            if k == "access_token":
                out[k] = "eyJhbGciOiJIUzI1NiJ9.<payload>.<signature>"
            elif k == "refresh_token":
                out[k] = "<opaque refresh token>"
            elif k == "dev_otp":
                out[k] = "123456"
            else:
                out[k] = mask(v)
        return out
    if isinstance(value, list):
        return [mask(v) for v in value[:2]]
    return value


def type_of(v):
    if isinstance(v, bool):
        return "boolean"
    if isinstance(v, (int, float)):
        return "number"
    if isinstance(v, str):
        return "string"
    if isinstance(v, list):
        return "array"
    if isinstance(v, dict):
        return "object"
    return None


def describe(path, name, parent):
    for key in (parent + "." + name, name):
        if key in FIELDS:
            return FIELDS[key]
    missing[(parent + "." + name)] = path
    return ""


def flatten(value, prefix, parent, rows, seen):
    if isinstance(value, dict):
        for k, v in value.items():
            if parent in DYNAMIC:
                path = f"{prefix}.<{DYNAMIC[parent]}>"
                if path not in seen:
                    seen[path] = len(rows)
                    rows.append({"field": path, "type": type_of(v) or "string", "desc": FIELDS.get(parent + ".*", "")})
                    if parent + ".*" not in FIELDS:
                        missing[parent + ".*"] = path
                continue
            path = f"{prefix}.{k}" if prefix else k
            t = type_of(v)
            known = TYPES.get(parent + "." + k) or TYPES.get(k)
            if path in seen:
                if t and rows[seen[path]]["type"] == "null":
                    rows[seen[path]]["type"] = t
            else:
                seen[path] = len(rows)
                rows.append({"field": path, "type": t or known or "null", "desc": describe(path, k, parent)})
            if k in OPAQUE:
                continue
            if isinstance(v, dict):
                flatten(v, path, k, rows, seen)
            elif isinstance(v, list):
                for item in v:
                    if isinstance(item, (dict, list)):
                        flatten(item, path + "[]", k, rows, seen)
    elif isinstance(value, list):
        for item in value:
            flatten(item, prefix + "[]", parent, rows, seen)


def doc(group, title, method, path, auth="user", body=None, multipart=None, query=None, params=None, req=None, notes=None,
        errors=None, expect=200, shown_path=None, headers=None, binary=None, doc_body=None, doc_headers=None, alias=None):
    """Runs the call for real. A wrong status stops the build, so the docs can only show working APIs."""
    real_path = path + ("?" + query if query else "")
    token = ctx.get(auth) if auth in ("user", "admin") else None
    if binary:
        status, content = http(method, real_path, body, token, multipart, headers, raw=True)
        res = None
    else:
        status, res = http(method, real_path, body, token, multipart, headers)
    if status != expect:
        sys.exit(f"{method} {path}: expected {expect}, got {status}: {res}")
    print(f"OK {status} {method} {path}")
    rows = []
    if res is not None and res.get("data") is not None:
        flatten(res["data"], "", "data", rows, {})
    endpoints.append({
        "group": group, "title": title, "method": method, "path": shown_path or path, "query": query, "auth": auth,
        "body": doc_body if doc_body is not None else body,
        "multipart": ({"fields": multipart.get("fields", {}), "field": multipart.get("field", "file")} if multipart else None),
        "params": params or [], "req": req or [], "notes": notes, "errors": errors or [], "status": status,
        "response": mask(res) if res is not None else None, "binary": binary, "rows": rows,
        "headers": doc_headers or [], "alias": alias,
    })
    return res.get("data") if res else None


ADMIN = "/api/admin"
AUTH_ERR = [("401", "Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`)")]
STAFF_ERR = [("401", "Not signed in as staff")]
SUPER_ERR = STAFF_ERR + [("403", "Signed in, but not a super admin")]
LIST_PARAMS = [
    ("limit", "number", "25, 50, 100, 200 or 500. Any other value becomes 25. Default 25."),
    ("offset", "number", "Rows to skip. Default 0."),
    ("search", "string", "Text search across the main columns."),
    ("date_from", "date", "From this day, `yyyy-MM-dd`."),
    ("date_to", "date", "To this day inclusive, `yyyy-MM-dd`."),
    ("sort", "string", "Any column in the response. Unknown columns are ignored."),
    ("dir", "string", "`asc` or `desc`. Default `desc` when `sort` is given."),
]
PNG = b"\x89PNG\r\n\x1a\n" + b"\0" * 32
PDF = b"%PDF-1.4\n" + b"\0" * 32

# ---------- staff bootstrap ----------
d = doc("a_auth", "Create the first admin", "POST", ADMIN + "/auth/register", auth=None, expect=201,
        body={"email": "first.admin@test.local", "password": "FirstAdmin123", "full_name": "First Admin", "mobile_number": "9000000900"},
        req=[("email", "string", "Yes", "Valid email, not already registered."), ("password", "string", "Yes", "At least 8 characters."),
             ("full_name", "string", "Yes", "Up to 150 characters."), ("mobile_number", "string", "No", "Up to 20 characters.")],
        notes="Without a token this works only while no admin exists, and creates a super admin. A signed-in super admin can call it any time to add a normal admin.",
        errors=[("400", "Bad field, email taken, or an admin already exists and no token was sent")])
d = doc("a_auth", "Staff login", "POST", ADMIN + "/auth/login", auth=None, body={"email": "first.admin@test.local", "password": "FirstAdmin123"},
        req=[("email", "string", "Yes", ""), ("password", "string", "Yes", "")],
        notes="Staff tokens last 12 hours and have no refresh token. 5 wrong passwords lock the account for 15 minutes. Limit: 30 logins per IP per 15 minutes.",
        errors=[("400", "Wrong email or password"), ("403", "Account is not active"), ("429", "Locked or rate limited")])
ctx["admin"] = d["access_token"]
doc("a_auth", "My staff profile", "GET", ADMIN + "/auth/me", auth="admin", errors=STAFF_ERR)
doc("a_auth", "Update my staff profile", "POST", ADMIN + "/auth/profile", auth="admin", body={"full_name": "First Admin", "mobile_number": "9000000901"},
    req=[("full_name", "string", "Yes", "The update is skipped if this is blank."), ("mobile_number", "string", "No", "")], errors=STAFF_ERR)
doc("a_auth", "Upload my staff photo", "POST", ADMIN + "/auth/profile/photo", auth="admin", multipart={"field": "photo", "file": ("me.png", "image/png", PNG)},
    notes="Multipart form with `photo`. JPG, PNG, WebP or GIF, up to 3 MB.", errors=STAFF_ERR + [("400", "Wrong file type or too large")])
doc("a_auth", "Get my staff photo", "GET", ADMIN + "/auth/profile/photo", auth="admin", binary="The image file", errors=STAFF_ERR + [("404", "No photo uploaded yet")])
d = doc("a_auth", "Change my staff password", "POST", ADMIN + "/auth/password/change", auth="admin",
        body={"current_password": "FirstAdmin123", "new_password": "FirstAdmin456"},
        req=[("current_password", "string", "Yes", ""), ("new_password", "string", "Yes", "At least 8 characters.")],
        notes="Ends every staff session for this account and returns a new token. Replace the stored one.", errors=STAFF_ERR + [("400", "Current password wrong, or new one too short")])
ctx["admin"] = d["access_token"]
d = doc("a_auth", "Staff forgot password: send OTP", "POST", ADMIN + "/auth/password/forgot/send", auth=None, body={"email": "first.admin@test.local"},
        req=[("email", "string", "Yes", "")], notes="Same response whether or not the email exists. Limit: 10 per IP per hour.", errors=[("400", "Bad email"), ("429", "Rate limited")])
doc("a_auth", "Staff forgot password: reset", "POST", ADMIN + "/auth/password/forgot/reset", auth=None,
    body={"email": "first.admin@test.local", "otp": d["dev_otp"], "new_password": "FirstAdmin789"},
    doc_body={"email": "first.admin@test.local", "otp": "123456", "new_password": "FirstAdmin789"},
    req=[("email", "string", "Yes", ""), ("otp", "string", "Yes", "Valid 10 minutes; 5 wrong tries cancel it."), ("new_password", "string", "Yes", "At least 8 characters.")],
    notes="Ends every staff session for this account.", errors=[("400", "Wrong or expired OTP, or weak password"), ("429", "Rate limited")])
ctx["admin"] = http("POST", ADMIN + "/auth/login", {"email": "first.admin@test.local", "password": "FirstAdmin789"})[1]["data"]["access_token"]

# ---------- app: sign-in ----------
doc("system", "Health check", "GET", V + "/health", auth=None, notes="200 when the app and database are fine, otherwise 503 with a reason. Also at `/api/health`.", errors=[("503", "Database down, schema out of date, or `jwt.secret` missing")])
AUTH_DESC = "Returns the token block."
MOBILE = "9000000001"
d = doc("auth", "Send mobile OTP", "POST", V + "/auth/otp/send", auth=None, body={"mobile_number": MOBILE},
        req=[("mobile_number", "string", "Yes", "9876543210, +91 98765 43210 or 09876543210 are all accepted.")],
        notes="Creates the account on first use. Limit: 5 per number per 15 minutes, 20 per IP per hour. `dev_otp` and `sms_provider_response` appear only when the server has `app.dev.expose_otp=true`, never in production.",
        errors=[("400", "Bad mobile number"), ("403", "This account was closed"), ("429", "Rate limited"), ("503", "The SMS could not be sent")])
d = doc("auth", "Verify mobile OTP", "POST", V + "/auth/otp/verify", auth=None, body={"mobile_number": MOBILE, "otp": d["dev_otp"]},
        doc_body={"mobile_number": MOBILE, "otp": "123456"},
        req=[("mobile_number", "string", "Yes", ""), ("otp", "string", "Yes", "6 digits, valid 10 minutes, works once. 5 wrong tries cancel it.")],
        notes=AUTH_DESC, errors=[("400", "Wrong or expired OTP"), ("403", "Account not active")])
ctx["user"], ctx["refresh"] = d["access_token"], d["refresh_token"]

# ---------- app: profile ----------
USER_ERR = AUTH_ERR + [("403", "Account not active")]
doc("user", "Get my profile", "GET", V + "/user/me", notes="Call on every app open to refresh status. Also accepts the token as an `X-Access-Token` header.", errors=USER_ERR)
doc("user", "Onboarding step 1: basic details", "POST", V + "/user/onboarding", body={
    "step": 1, "name": "Asha Verma", "first_name": "Asha", "last_name": "Verma", "email": "asha.verma@test.local", "gender": "Female",
    "date_of_birth": "1995-04-12", "age": 31, "marital_status": "Single", "nationality": "Indian", "alternate_mobile_number": "9000000002"},
    req=[("step", "number", "Yes", "1"), ("name", "string", "Yes", "Up to 150 characters."), ("first_name", "string", "Yes", "Up to 80."), ("last_name", "string", "Yes", "Up to 80."),
         ("email", "string", "Yes", "Valid email, not used by another customer. Changing it clears email verification."), ("gender", "string", "Yes", "`Male`, `Female` or `Other`. `m`, `f` and `o` also work."),
         ("date_of_birth", "date", "Yes", "`yyyy-MM-dd`, not in the future."), ("age", "number", "No", ""), ("marital_status", "string", "No", "`Single`, `Married`, `Divorced` or `Widowed`."),
         ("nationality", "string", "No", "Up to 80. Default `Indian`."), ("profile_image", "string", "No", "`url` from the upload API."), ("alternate_mobile_number", "string", "No", "10-digit mobile.")],
    notes="Steps must be done in order. An earlier step can be sent again to edit it.",
    errors=USER_ERR + [("400", "Missing or invalid field, or an earlier step is not done"), ("409", "Email already in use")])
doc("user", "Onboarding step 2: address and work", "POST", V + "/user/onboarding", body={
    "step": 2, "current_address": "12 MG Road", "current_city": "Jaipur", "current_state": "Rajasthan", "current_pincode": "302001", "current_country": "India",
    "permanent_address": "12 MG Road", "permanent_city": "Jaipur", "permanent_state": "Rajasthan", "permanent_pincode": "302001", "permanent_country": "India", "same_as_current_address": True,
    "employment_type": "Salaried", "company_name": "Acme Pvt Ltd", "designation": "Accountant", "work_experience_months": 48, "monthly_income": 35000, "annual_income": 420000, "income_source": "Salary"},
    req=[("step", "number", "Yes", "2"), ("current_address", "string", "Yes", ""), ("current_city", "string", "Yes", "Up to 100."), ("current_state", "string", "Yes", "Up to 100."), ("current_pincode", "string", "Yes", "6 digits."),
         ("current_country", "string", "No", "Default `India`."), ("permanent_address", "string", "No", ""), ("permanent_city", "string", "No", ""), ("permanent_state", "string", "No", ""), ("permanent_pincode", "string", "No", ""), ("permanent_country", "string", "No", ""),
         ("same_as_current_address", "boolean", "No", ""), ("employment_type", "string", "Yes", "`Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`."), ("company_name", "string", "No", "Up to 200."), ("designation", "string", "No", "Up to 150."),
         ("work_experience_months", "number", "No", ""), ("monthly_income", "number", "Yes", "0 or more."), ("annual_income", "number", "No", ""), ("income_source", "string", "No", "Up to 150.")],
    errors=USER_ERR + [("400", "Missing or invalid field, or step 1 is not done")])
FILE_REQ = [("file", "file", "Yes", "Up to 5 MB. The server checks the real file type."), ("purpose", "string", "Yes", "`payment_proof` or `profile_image` (JPG, PNG, WebP); `aadhaar` or `pan` (also PDF).")]
aad = doc("files", "Upload a file", "POST", V + "/files", multipart={"fields": {"purpose": "aadhaar"}, "file": ("aadhaar.pdf", "application/pdf", PDF)}, expect=201, req=FILE_REQ,
          notes="Multipart form. Limit: 30 per hour per customer.", errors=AUTH_ERR + [("400", "Wrong type, bad purpose or no file"), ("429", "Rate limited")])
pan = http("POST", V + "/files", None, ctx["user"], {"fields": {"purpose": "pan"}, "file": ("pan.png", "image/png", PNG)})[1]["data"]
doc("files", "Download a file", "GET", V + "/files/" + aad["file_id"], shown_path=V + "/files/{file_id}", binary="The file itself",
    notes="Only the owner and staff can read it; anyone else gets 404. Image widgets must send the Authorization header.", errors=AUTH_ERR + [("404", "Not found, or not yours")])
doc("user", "Onboarding step 3: KYC, bank, consent", "POST", V + "/user/onboarding", body={
    "step": 3, "aadhaar_last4": "1234", "pan_last4": "567F", "aadhaar_document": aad["url"], "pan_document": pan["url"], "bank_name": "HDFC Bank", "ifsc_code": "HDFC0001234", "bank_account_last4": "6789",
    "privacy_consent": True, "terms_accepted": True, "kyc_consent": True},
    req=[("step", "number", "Yes", "3"), ("aadhaar_last4", "string", "Yes", "4 digits."), ("pan_last4", "string", "Yes", "4 letters or digits."), ("aadhaar_encrypted", "string", "No", "Encrypted full number, if the app has one. Never returned."), ("pan_encrypted", "string", "No", "Same, for PAN."),
         ("aadhaar_document", "string", "No", "`url` from the upload API."), ("pan_document", "string", "No", "`url` from the upload API."), ("bank_name", "string", "Yes", "Up to 150."), ("ifsc_code", "string", "Yes", "Like `HDFC0001234`."),
         ("bank_account_encrypted", "string", "No", "Encrypted full number. Never returned."), ("bank_account_last4", "string", "Yes", "4 digits."), ("privacy_consent", "boolean", "Yes", ""), ("terms_accepted", "boolean", "Yes", ""), ("kyc_consent", "boolean", "Yes", "")],
    notes="Sets `kyc_status` to `submitted` and bank verification to `pending`. Staff then verify or reject. If rejected, the reason is in `kyc_rejection_reason`; send this step again to resubmit.",
    errors=USER_ERR + [("400", "Missing or invalid field, or step 2 is not done")])
doc("user", "Sync device details", "POST", V + "/user/sync", body={"device_id": "android-abc123", "device_type": "android", "device_name": "Asha's phone", "device_model": "Pixel 8", "os_version": "14", "app_version": "1.0.0", "time_zone": "Asia/Kolkata", "latitude": 26.9124, "longitude": 75.7873},
    req=[(n, t, "No", r) for n, t, r in [("device_id", "string", ""), ("device_type", "string", "`android`, `ios` or `web`."), ("device_name", "string", ""), ("device_model", "string", ""), ("os_version", "string", ""), ("app_version", "string", ""), ("time_zone", "string", ""),
         ("latitude", "number", ""), ("longitude", "number", ""), ("fcm_token", "string", "Also registers the phone for push."), ("notification_enable", "boolean", ""), ("notification_disable_at", "datetime", "`yyyy-MM-ddTHH:mm:ss`"), ("is_app_installed", "boolean", ""), ("application_install_time", "datetime", ""), ("application_uninstall_time", "datetime", ""), ("is_login", "boolean", ""),
         ("contacts", "array", "Ignored unless the server enables it."), ("gallery", "array", "Ignored unless the server enables it.")]],
    notes="All fields are optional. Contacts and gallery are not stored while `app.sync.contacts_gallery.enabled=false`, so do not ask users for those permissions.", errors=USER_ERR + [("400", "Invalid field value")])
d = doc("user", "Set or change password", "POST", V + "/user/password", body={"new_password": "AshaPass123"},
        req=[("current_password", "string", "If one exists", "Not needed the first time."), ("new_password", "string", "Yes", "At least 8 characters.")],
        notes="Ends all other logins and returns new tokens. Replace the stored ones.", errors=USER_ERR + [("400", "Current password wrong, or new one too short")])
ctx["user"], ctx["refresh"] = d["access_token"], d["refresh_token"]
d = doc("auth", "Password login", "POST", V + "/auth/login", auth=None, body={"email": "asha.verma@test.local", "password": "AshaPass123"},
        req=[("email", "string", "Yes", ""), ("password", "string", "Yes", "")], notes="5 wrong passwords lock the account for 15 minutes. Limit: 60 sign-in calls per IP per 15 minutes.", errors=[("400", "Wrong email or password"), ("403", "Account not active"), ("429", "Locked or rate limited")])
ctx["user"], ctx["refresh"] = d["access_token"], d["refresh_token"]
d = doc("auth", "Refresh tokens", "POST", V + "/auth/token/refresh", auth=None, body={"refresh_token": ctx["refresh"]}, doc_body={"refresh_token": "<refresh token>"},
        req=[("refresh_token", "string", "Yes", "")], notes="Call when an API returns `TOKEN_EXPIRED`. Each refresh token works once: always save the new one. Reusing an old one ends that login. Do not run two refreshes at once.",
        errors=[("400", "Field missing"), ("401", "`INVALID_REFRESH_TOKEN`: send the user to sign-in"), ("403", "Account not active")])
ctx["user"], ctx["refresh"] = d["access_token"], d["refresh_token"]
d = doc("auth", "Forgot password: send OTP", "POST", V + "/auth/password/forgot/send", auth=None, body={"email": "asha.verma@test.local"}, req=[("email", "string", "Yes", "")],
        notes="Same response whether or not the email exists.", errors=[("400", "Bad email"), ("429", "Rate limited")])
doc("auth", "Forgot password: reset", "POST", V + "/auth/password/forgot/reset", auth=None, body={"email": "asha.verma@test.local", "otp": d["dev_otp"], "new_password": "AshaPass456"},
    doc_body={"email": "asha.verma@test.local", "otp": "123456", "new_password": "AshaPass456"},
    req=[("email", "string", "Yes", ""), ("otp", "string", "Yes", ""), ("new_password", "string", "Yes", "At least 8 characters.")], notes="Ends all logins, so send the user to sign-in.", errors=[("400", "Wrong OTP or weak password")])
d = http("POST", V + "/auth/login", {"email": "asha.verma@test.local", "password": "AshaPass456"})[1]["data"]
ctx["user"], ctx["refresh"] = d["access_token"], d["refresh_token"]
d = doc("auth", "Send email OTP", "POST", V + "/auth/email/send", auth=None, body={"email": "second.user@test.local"}, req=[("email", "string", "Yes", "")],
        notes="Same as mobile OTP, using email. Creates the account on first use.", errors=[("400", "Bad email"), ("403", "Account closed"), ("429", "Rate limited"), ("503", "The email could not be sent")])
second = doc("auth", "Verify email OTP", "POST", V + "/auth/email/verify", auth=None, body={"email": "second.user@test.local", "otp": d["dev_otp"]}, doc_body={"email": "second.user@test.local", "otp": "123456"},
             req=[("email", "string", "Yes", ""), ("otp", "string", "Yes", "")], notes=AUTH_DESC, errors=[("400", "Wrong or expired OTP"), ("403", "Account not active")])

# ---------- app: devices, home ----------
FCM = "<token from Firebase SDK>"
doc("devices", "Register this phone for push", "POST", V + "/user/devices", body={"fcm_token": FCM, "platform": "android", "device_id": "android-abc123", "app_version": "1.0.0"},
    req=[("fcm_token", "string", "Yes", ""), ("platform", "string", "No", "`android` (default), `ios` or `web`."), ("device_id", "string", "No", ""), ("app_version", "string", "No", "")],
    notes="Call after every login and whenever Firebase gives a new token. Dead tokens are removed automatically.", errors=USER_ERR + [("400", "Token missing or bad platform")])
doc("devices", "Unregister this phone", "POST", V + "/user/devices/remove", body={"fcm_token": FCM}, req=[("fcm_token", "string", "Yes", "")], errors=USER_ERR + [("400", "Token missing")])
doc("devices", "Turn push on or off", "POST", V + "/user/notification-settings", body={"enabled": True}, req=[("enabled", "boolean", "Yes", "")], notes="Off stops push only; the in-app list still fills up.", errors=USER_ERR + [("400", "Field missing")])

# ---------- staff: KYC before loans ----------
uid = http("GET", V + "/user/me", None, ctx["user"])[1]["data"]["user"]["id"]
second_id = http("GET", V + "/user/me", None, second["access_token"])[1]["data"]["user"]["id"]

doc("dashboard", "Banners", "GET", V + "/dashboard/banners", errors=USER_ERR)
doc("dashboard", "Loan rules", "GET", V + "/dashboard/loan-config", notes="Build the apply form from this: amounts, tenure, purposes, relations, guarantor count.", errors=USER_ERR)
doc("dashboard", "How-to-apply video", "GET", V + "/dashboard/loan-apply-video", errors=USER_ERR)

QUOTE = {"requested_amount": 20000, "tenure_months": 6}
doc("loan", "Get a quote", "POST", V + "/loan/quote", body=QUOTE, req=[("requested_amount", "number", "Yes", "10,000 to 50,000 in steps of 5,000."), ("tenure_months", "number", "Yes", "3 to 24.")],
    notes="What the loan will cost, before applying. Rates come from server settings. Samples here use 12% and 2%.", errors=USER_ERR + [("400", "Amount or months not allowed")])
GUARANTORS = [{"full_name": "Ravi Verma", "mobile_number": "9000000101", "relation": "brother", "address": "Jaipur", "aadhaar_last4": "1111", "pan_last4": "222A"},
              {"full_name": "Neha Sharma", "mobile_number": "9000000102", "relation": "friend"}]
APPLY = dict(QUOTE, purpose="personal", purpose_detail="Home repair", monthly_income=35000, employment_type="Salaried", company_name="Acme Pvt Ltd",
             bank_name="HDFC Bank", ifsc_code="HDFC0001234", bank_account_last4="6789", guarantors=GUARANTORS)
d = doc("loan", "Apply for a loan", "POST", V + "/loan/apply", body=APPLY, expect=201,
        req=[("requested_amount", "number", "Yes", "10,000 to 50,000 in steps of 5,000."), ("tenure_months", "number", "Yes", "3 to 24."), ("purpose", "string", "Yes", "Use `purpose_options` from loan rules."), ("purpose_detail", "string", "No", ""),
             ("monthly_income", "number", "No", "Defaults to the profile."), ("employment_type", "string", "No", "Defaults to the profile."), ("company_name", "string", "No", "Defaults to the profile."),
             ("bank_name", "string", "No", "Defaults to the profile."), ("ifsc_code", "string", "No", "Defaults to the profile."), ("bank_account_last4", "string", "No", "Defaults to the profile."),
             ("guarantors", "array", "Yes", "Exactly 1 per ₹10,000, minimum 1. See `guarantors_required_by_amount`."), ("guarantors[].full_name", "string", "Yes", ""), ("guarantors[].mobile_number", "string", "Yes", "10 digits, different for each."), ("guarantors[].relation", "string", "Yes", "Use `relation_options`."),
             ("guarantors[].address", "string", "No", ""), ("guarantors[].aadhaar_last4", "string", "No", ""), ("guarantors[].pan_last4", "string", "No", "")],
        notes="Needs a complete profile. Refused while another loan is pending or has unpaid EMIs.",
        errors=USER_ERR + [("400", "Bad field, profile incomplete, or a loan is already open"), ("409", "`KYC_REJECTED`: send step 3 again first")])
L1 = d["loan_application"]["id"]
doc("loan", "Cancel an application", "POST", f"{V}/loan/applications/{L1}/cancel", shown_path=V + "/loan/applications/{id}/cancel", errors=USER_ERR + [("404", "Not found, or not yours"), ("409", "`LOAN_NOT_PENDING`")], notes="Only while pending.")
L2 = http("POST", V + "/loan/apply", APPLY, ctx["user"])[1]["data"]["loan_application"]["id"]
ERR_NF = STAFF_ERR + [("404", "Not found")]
doc("a_loan", "Reject a loan", "POST", f"{ADMIN}/loan/applications/{L2}/reject", auth="admin", body={"admin_note": "Income proof missing"}, shown_path=ADMIN + "/loan/applications/{id}/reject",
    req=[("admin_note", "string", "No", "Reason, saved on the loan.")], notes="Only while pending. The customer gets a push.", errors=ERR_NF + [("400", "Not pending")])
L3 = http("POST", V + "/loan/apply", APPLY, ctx["user"])[1]["data"]["loan_application"]["id"]
doc("loan", "My applications", "GET", V + "/loan/applications", query="limit=20&offset=0", params=[("limit", "number", "1 to 50. Default 20."), ("offset", "number", "Default 0.")], notes="Newest first.", errors=USER_ERR)
doc("loan", "One application", "GET", f"{V}/loan/applications/{L3}", shown_path=V + "/loan/applications/{id}", errors=USER_ERR + [("404", "Not found, or not yours")])
doc("a_user", "Verify or reject KYC", "POST", f"{ADMIN}/users/{uid}/kyc", auth="admin", body={"action": "verify"}, shown_path=ADMIN + "/users/{id}/kyc",
    req=[("action", "string", "Yes", "`verify` or `reject`."), ("reason", "string", "To reject", "Shown to the customer.")], notes="Only for submitted KYC. Verifying also marks the bank details verified. The customer gets a push.", errors=ERR_NF + [("400", "KYC not submitted, or reason missing")])
doc("a_loan", "Approve a loan", "POST", f"{ADMIN}/loan/applications/{L3}/approve", auth="admin", body={"admin_note": "Checked"}, shown_path=ADMIN + "/loan/applications/{id}/approve",
    req=[("admin_note", "string", "No", "")], notes="Only while pending, and KYC must be verified. Creates the EMI schedule. The customer gets a push.", errors=ERR_NF + [("400", "Not pending"), ("409", "`KYC_NOT_VERIFIED`")])
doc("emi", "EMI summary", "GET", V + "/emi/summary", notes="`can_pay` stays false until the money is sent. When there is no approved loan, only `has_active_loan: false` is returned.", errors=USER_ERR)
doc("a_loan", "Disburse a loan", "POST", f"{ADMIN}/loan/applications/{L3}/disburse", auth="admin", body={"disbursement_reference": "UTR123456"}, shown_path=ADMIN + "/loan/applications/{id}/disburse",
    req=[("disbursement_reference", "string", "Yes", "Bank or UTR reference, up to 100 characters.")], notes="Record this after sending the money. EMIs then run from today: the first is due in a month. The customer gets a push.", errors=ERR_NF + [("400", "Not approved, or reference missing")])
doc("dashboard", "Home screen", "GET", V + "/dashboard/home", notes="Everything for the home screen in one call.", errors=USER_ERR)
sched = doc("emi", "EMI schedule", "GET", V + "/emi/schedule", params=[("loan_id", "number", "Optional. Any of the customer's own loans, including closed ones. Default: the current loan.")], errors=USER_ERR + [("404", "No approved loan, or not yours")])
doc("emi", "How to pay", "GET", V + "/emi/payment-instructions", notes="UPI, QR and bank details set by staff.", errors=USER_ERR)
proof = http("POST", V + "/files", None, ctx["user"], {"fields": {"purpose": "payment_proof"}, "file": ("proof.png", "image/png", PNG)})[1]["data"]
PAY_REQ = [("emi_schedule_id", "number", "Yes", "`id` from the EMI schedule."), ("screenshot_url", "string", "Yes", "`url` from the upload API."), ("amount_claimed", "number", "No", "Defaults to the EMI amount. Less than the EMI is refused."), ("utr_reference", "string", "No", "Up to 100 characters.")]
PAY_ERR = USER_ERR + [("400", "Bad field, already paid, or proof already pending"), ("404", "EMI not found, or not yours"), ("409", "`LOAN_NOT_DISBURSED`")]
first = sched["installments"][0]["id"]
pay = doc("emi", "Submit an EMI payment", "POST", V + "/emi/payments/submit", expect=201, body={"emi_schedule_id": first, "screenshot_url": proof["url"], "utr_reference": "UPI998877"}, req=PAY_REQ, notes="The EMI becomes `awaiting_proof` until staff decide.", errors=PAY_ERR)
doc("a_emi", "Reject an EMI payment", "POST", f"{ADMIN}/emi/payments/{pay['payment_submission_id']}/reject", auth="admin", body={"admin_note": "Screenshot is blurred"}, shown_path=ADMIN + "/emi/payments/{id}/reject",
    req=[("admin_note", "string", "No", "Shown to the customer.")], notes="Only while pending. The EMI returns to pending, or overdue if past due.", errors=ERR_NF + [("400", "Already decided")])
pay = http("POST", V + "/emi/payments/submit", {"emi_schedule_id": first, "screenshot_url": proof["url"], "utr_reference": "UPI998878"}, ctx["user"])[1]["data"]
doc("a_emi", "Approve an EMI payment", "POST", f"{ADMIN}/emi/payments/{pay['payment_submission_id']}/approve", auth="admin", body={"admin_note": "Received in bank"}, shown_path=ADMIN + "/emi/payments/{id}/approve",
    req=[("admin_note", "string", "No", "")], notes="Check your bank first. Approving the last EMI closes the loan. The customer gets a push.", errors=ERR_NF + [("400", "Already decided, or the claim is below the EMI")])
doc("emi", "My payments", "GET", V + "/emi/payments", query="limit=20&offset=0", params=[("limit", "number", "1 to 50. Default 20."), ("offset", "number", "Default 0.")], errors=USER_ERR)
doc("emi", "One payment", "GET", f"{V}/emi/payments/{pay['payment_submission_id']}", shown_path=V + "/emi/payments/{id}", errors=USER_ERR + [("404", "Not found, or not yours")])
doc("a_misc", "Send a message to customers", "POST", ADMIN + "/notifications/send", auth="admin", body={"user_id": uid, "title": "Office closed", "body": "Our office is closed on Monday."},
    req=[("user_id", "number", "Unless `all`", ""), ("all", "boolean", "No", "`true` messages every active customer. Super admin only."), ("title", "string", "Yes", ""), ("body", "string", "Yes", "")], notes="Saved in the customer's list and sent as push.", errors=SUPER_ERR + [("400", "Field missing"), ("404", "No such customer")])
notes = doc("notifications", "List notifications", "GET", V + "/notifications", query="limit=50&offset=0", params=[("limit", "number", "1 to 100. Default 50."), ("offset", "number", "Default 0.")], notes="Includes `unread_count` for the bell badge. `payload` depends on the category.", errors=USER_ERR)
nid = notes["notifications"][0]["id"]
doc("notifications", "Mark one read", "POST", f"{V}/notifications/read/{nid}", shown_path=V + "/notifications/read/{id}", body={}, errors=USER_ERR)
doc("notifications", "Mark one read (by body)", "POST", V + "/notifications/read", body={"notification_id": nid}, req=[("notification_id", "number", "Yes", "")], errors=USER_ERR)
doc("notifications", "Mark all read", "POST", V + "/notifications/read-all", body={}, errors=USER_ERR)

# ---------- staff lists ----------
LIST_FILTERS = {
    "users": ("Customers", "`kyc_status`, `account_status`, `onboarding_step`, `employment_type`, `gender`, `income_min`, `income_max`"),
    "loans": ("Loans", "`status`, `purpose`, `kyc_status`, `user_id`, `amount_min`, `amount_max`, `tenure_min`, `tenure_max`"),
    "emi_payments": ("EMI payments", "`status`, `user_id`, `loan_id`, `amount_min`, `amount_max`"),
    "emi_schedules": ("EMI schedule", "`status`, `loan_status`, `loan_id`, `user_id`, `amount_min`, `amount_max`. Dates filter the due date."),
    "guarantors": ("Guarantors", "`relation`, `loan_id`"),
    "user_notifications": ("Customer notifications", "`category`, `user_id`"),
    "banners": ("Banners", "`is_active`, `action_type`"),
    "videos": ("Guide videos", "`is_active`, `video_type`"),
    "emi_settings": ("Payment details history", "`is_active`"),
    "admin_alerts": ("Staff alerts", "`type`"),
    "admins": ("Staff accounts", "`role`, `account_status`. Super admin only."),
}
doc("a_list", "How lists work: example", "GET", ADMIN + "/list/loans", auth="admin", query="status=disbursed&limit=25&offset=0&sort=created_at&dir=desc", shown_path=ADMIN + "/list/{name}", params=LIST_PARAMS,
    notes="`counts` gives per-status totals for the cards, and appears on lists that have a main status. Replace `{name}` with a list name below.", errors=ERR_NF + [("400", "Bad date")])
doc("a_list", "Download a list", "GET", ADMIN + "/list/loans/export", auth="admin", query="format=xlsx&status=disbursed", shown_path=ADMIN + "/list/{name}/export", binary="A CSV or Excel file",
    params=[("format", "string", "`csv` or `xlsx`.")] + LIST_PARAMS[2:], notes="Takes the same filters as the list, without paging. Up to 100,000 rows. Text that a spreadsheet would run as a formula is neutralised.", errors=ERR_NF + [("400", "Bad date")])
for name, (label, filters) in LIST_FILTERS.items():
    doc("a_list", f"List: {label}", "GET", f"{ADMIN}/list/{name}", auth="admin", query="limit=25", shown_path=f"{ADMIN}/list/{name}", params=[("Filters", "string", filters)], errors=SUPER_ERR if name == "admins" else STAFF_ERR)

# ---------- staff dashboard ----------
doc("a_dash", "Dashboard figures", "GET", ADMIN + "/dashboard/summary", auth="admin", params=[("date_from", "date", "Default: 29 days before `date_to`."), ("date_to", "date", "Default: today. At most 366 days apart.")], errors=STAFF_ERR + [("400", "Bad date")])
doc("a_dash", "Alerts for the bell", "GET", ADMIN + "/alerts", auth="admin", query="after_id=0", params=[("after_id", "number", "Last id this browser has seen. `new_items` holds anything newer. 0 returns none.")], notes="The panel polls this every 15 seconds and plays the alert sound for `new_items`.", errors=STAFF_ERR)
doc("a_dash", "Mark all alerts read", "POST", ADMIN + "/alerts/read", auth="admin", body={}, notes="Per staff account.", errors=STAFF_ERR)

# ---------- staff customers ----------
doc("a_user", "One customer, all details", "GET", f"{ADMIN}/users/{uid}", auth="admin", shown_path=ADMIN + "/users/{id}", errors=ERR_NF)
doc("a_user", "Turn a customer on or off", "POST", f"{ADMIN}/users/{second_id}/status", auth="admin", body={"active": False}, shown_path=ADMIN + "/users/{id}/status",
    req=[("active", "boolean", "Yes", "`false` stops them using the app."), ("account_status", "string", "No", "Older form: `active` or anything else.")], errors=SUPER_ERR + [("404", "Not found")])
http("POST", f"{ADMIN}/users/{second_id}/status", {"active": True}, ctx["admin"])
otp3 = http("POST", V + "/auth/otp/send", {"mobile_number": "9000000003"})[1]["data"]["dev_otp"]
http("POST", V + "/auth/otp/verify", {"mobile_number": "9000000003", "otp": otp3})
third_id = [r["id"] for r in http("GET", ADMIN + "/list/users?search=9000000003", None, ctx["admin"])[1]["data"]["rows"]][0]
doc("a_user", "Delete a customer", "POST", f"{ADMIN}/users/{third_id}/delete", auth="admin", body={}, shown_path=ADMIN + "/users/{id}/delete",
    notes="Super admin only. Ends their logins. The record stays for history, and the mobile and email cannot be registered again.", errors=SUPER_ERR + [("404", "Not found, or already deleted")])

# ---------- banners, videos, settings ----------
BANNER_REQ = [("title", "string", "To create", "Up to 200."), ("subtitle", "string", "No", "Up to 500."), ("image_url", "string", "To create", "http(s) URL or the upload result."), ("action_type", "string", "No", "`none`, `url` or `screen`."),
              ("action_value", "string", "With url or screen", "URL or screen name, up to 500."), ("sort_order", "number", "No", ""), ("is_active", "boolean", "No", ""), ("starts_at", "datetime", "No", "`yyyy-MM-ddTHH:mm:ss`"), ("ends_at", "datetime", "No", "After `starts_at`.")]
up = doc("a_cms", "Upload a banner image", "POST", ADMIN + "/banners/upload-image", auth="admin", multipart={"field": "image", "file": ("banner.png", "image/png", PNG)}, notes="Multipart form with `image`. JPG, PNG, WebP or GIF, up to 5 MB. Returns a public URL.", errors=STAFF_ERR + [("400", "Wrong type or too large")])
doc("media", "Get a banner image", "GET", ADMIN.replace("/admin", "") + "/media/banners/" + up["file_name"], auth=None, shown_path="/api/media/banners/{file_name}", binary="The image file", notes="Public. Banner `image_url` values already point here.", errors=[("404", "Not found")])
b = doc("a_cms", "Create a banner", "POST", ADMIN + "/banners", auth="admin", expect=201, body={"title": "Festival offer", "subtitle": "Apply today", "image_url": up["image_url"], "action_type": "screen", "action_value": "loan_apply", "sort_order": 5, "is_active": True}, doc_body={"title": "Festival offer", "subtitle": "Apply today", "image_url": "https://example.com/banner.png", "action_type": "screen", "action_value": "loan_apply", "sort_order": 5, "is_active": True}, req=BANNER_REQ, errors=STAFF_ERR + [("400", "Bad field")])
bid = b["banner"]["id"]
doc("a_cms", "One banner", "GET", f"{ADMIN}/banners/{bid}", auth="admin", shown_path=ADMIN + "/banners/{id}", errors=ERR_NF)
doc("a_cms", "Update a banner", "POST", f"{ADMIN}/banners/{bid}/update", auth="admin", body={"is_active": False}, shown_path=ADMIN + "/banners/{id}/update", req=BANNER_REQ, notes="Send only what changes.", errors=ERR_NF + [("400", "Bad field")])
doc("a_cms", "Delete a banner", "POST", f"{ADMIN}/banners/{bid}/delete", auth="admin", body={}, shown_path=ADMIN + "/banners/{id}/delete", errors=ERR_NF)
VIDEO_REQ = [("title", "string", "Yes", "Up to 200."), ("video_url", "string", "Yes", "http(s) URL."), ("video_type", "string", "Yes", "`mp4`, `youtube`, `vimeo` or `hls`."), ("description", "string", "No", "Up to 500."), ("thumbnail_url", "string", "No", "http(s) URL."), ("duration_seconds", "number", "No", ""), ("sort_order", "number", "No", ""), ("is_active", "boolean", "No", "Default true.")]
VID = {"title": "How to apply", "video_url": "https://example.com/apply.mp4", "video_type": "mp4", "duration_seconds": 120, "sort_order": 9, "is_active": True}
doc("a_cms", "Add a guide video", "POST", ADMIN + "/videos", auth="admin", body=VID, req=VIDEO_REQ, errors=STAFF_ERR + [("400", "Bad field")])
vid = max(r["id"] for r in http("GET", ADMIN + "/list/videos?limit=100", None, ctx["admin"])[1]["data"]["rows"])
doc("a_cms", "Update a guide video", "POST", f"{ADMIN}/videos/{vid}/update", auth="admin", body=dict(VID, is_active=False), shown_path=ADMIN + "/videos/{id}/update", req=VIDEO_REQ, notes="Send the whole video, not only what changes.", errors=ERR_NF + [("400", "Bad field")])
doc("a_cms", "Delete a guide video", "POST", f"{ADMIN}/videos/{vid}/delete", auth="admin", body={}, shown_path=ADMIN + "/videos/{id}/delete", errors=ERR_NF)
doc("a_cms", "Payment details: read", "GET", ADMIN + "/settings/emi-collection", auth="admin", errors=STAFF_ERR)
doc("a_cms", "Payment details: save", "POST", ADMIN + "/settings/emi-collection", auth="admin",
    body={"payee_name": "Narainsons Finance", "upi_id": "narainsons@upi", "qr_image_url": "https://example.com/qr.png", "bank_name": "HDFC Bank", "bank_account_name": "Narainsons Finance Pvt Ltd", "bank_account_number": "XXXX1234567890", "ifsc_code": "HDFC0001234", "payment_note": "Write the loan code in the payment note."},
    req=[("payee_name", "string", "Yes", ""), ("upi_id", "string", "No", ""), ("qr_image_url", "string", "No", "http(s) URL."), ("bank_name", "string", "No", ""), ("bank_account_name", "string", "No", ""), ("bank_account_number", "string", "No", ""), ("ifsc_code", "string", "No", ""), ("payment_note", "string", "No", "")],
    notes="This is what the app shows under How to pay.", errors=SUPER_ERR + [("400", "Bad field")])

# ---------- staff accounts ----------
low = doc("a_misc", "Add a staff account", "POST", ADMIN + "/auth/register", auth="admin", expect=201, body={"email": "staff.two@test.local", "password": "StaffTwo123", "full_name": "Staff Two"},
          req=[("email", "string", "Yes", ""), ("password", "string", "Yes", "At least 8 characters."), ("full_name", "string", "Yes", ""), ("mobile_number", "string", "No", "")], notes="Super admin only. Creates a normal admin.", errors=SUPER_ERR + [("400", "Bad field or email taken")])
doc("a_misc", "Turn a staff account on or off", "POST", f"{ADMIN}/admins/{low['admin']['id']}/status", auth="admin", body={"active": False}, shown_path=ADMIN + "/admins/{id}/status", req=[("active", "boolean", "Yes", "")], notes="You cannot change your own account.", errors=SUPER_ERR + [("400", "Your own account"), ("404", "Not found")])

# ---------- older staff lists ----------
doc("a_older", "Dashboard (older)", "GET", ADMIN + "/dashboard", auth="admin", errors=STAFF_ERR)
doc("a_older", "Customers (older)", "GET", ADMIN + "/users", auth="admin", query="limit=100&offset=0", params=[("limit", "number", "1 to 1000."), ("offset", "number", "Rows to skip."), ("search", "string", "ID, name, mobile or email."), ("account_status", "string", "Filter by account status."), ("kyc_status", "string", "Filter by KYC status.")], errors=STAFF_ERR)
doc("a_older", "Loans (older)", "GET", ADMIN + "/loan/applications", auth="admin", query="limit=100&offset=0", params=[("limit", "number", "1 to 500."), ("offset", "number", "Rows to skip."), ("status", "string", "Filter by loan status."), ("search", "string", "Code, purpose, customer or mobile.")], errors=STAFF_ERR)
doc("a_loan", "One loan with guarantors", "GET", f"{ADMIN}/loan/applications/{L3}", auth="admin", shown_path=ADMIN + "/loan/applications/{id}", errors=ERR_NF)
doc("a_older", "EMI payments (older)", "GET", ADMIN + "/emi/payments", auth="admin", query="limit=100", params=[("limit", "number", "1 to 500.")], errors=STAFF_ERR)
doc("a_older", "Banners (older)", "GET", ADMIN + "/banners", auth="admin", query="limit=100", params=[("limit", "number", "1 to 500.")], errors=STAFF_ERR)
doc("a_older", "All tables (older)", "GET", ADMIN + "/database", auth="admin", query="limit=2", params=[("limit", "number", "Rows per table, 1 to 1000.")], notes="Rows have the table's own columns with secrets replaced by `[redacted]`. The `admins` table appears only for a super admin.", errors=STAFF_ERR)
doc("a_older", "One table (older)", "GET", ADMIN + "/database/loan_applications", auth="admin", query="limit=2", shown_path=ADMIN + "/database/{table}", params=[("limit", "number", "1 to 1000.")], errors=SUPER_ERR + [("400", "Unknown table")])

doc("internal", "Run EMI reminders", "POST", "/api/internal/emi-reminders", auth="cron", body={}, headers={"X-Cron-Secret": CRON}, doc_headers=[("X-Cron-Secret", "`cron.emi.secret` from server settings")],
    notes="The server already does this every hour by itself. Marks overdue EMIs and sends reminders.", errors=[("401", "Wrong secret")])

# ---------- last, because they end sessions or remove data ----------
ctx["user2"] = second["access_token"]
doc("auth", "Logout", "POST", V + "/auth/logout", body={"refresh_token": ctx["refresh"], "fcm_token": FCM, "all_devices": False}, doc_body={"refresh_token": "<refresh token>", "fcm_token": FCM, "all_devices": False},
    req=[("refresh_token", "string", "No", "Ends this login."), ("fcm_token", "string", "No", "Stops push to this phone."), ("all_devices", "boolean", "No", "`true` signs out everywhere.")], notes="Delete the stored tokens either way.", errors=USER_ERR)
ctx["user"] = ctx["user2"]
doc("user", "Delete my account", "POST", V + "/user/delete", body={"confirm": True}, req=[("confirm", "boolean", "Yes", "Must be `true`.")], notes="Required by the app stores. The mobile and email cannot be registered again.", errors=USER_ERR + [("400", "`confirm` missing"), ("409", "`OPEN_LOAN`: a loan is still open")])

if missing:
    print("\nFIELDS WITHOUT A DESCRIPTION:")
    for k, v in sorted(missing.items()):
        print(f"  {k}   ({v})")
    sys.exit(1)

# ---------- content shared by the outputs ----------
FLOW = """Splash
  Has tokens? -> GET /user/me
  No tokens?  -> Sign in (mobile OTP, email OTP or password)
After sign in
  POST /user/devices (FCM token)
  next_action = onboarding_step_N -> onboarding screens
  next_action = home              -> home screen

Onboarding:  step 1 -> step 2 -> upload Aadhaar + PAN -> step 3
             kyc_status: pending -> submitted -> verified or rejected (staff decide, push is sent)

Loan:        POST /loan/quote -> POST /loan/apply
             pending -> approved -> disbursed -> closed
                     -> rejected
                     -> cancelled (by the user)
             Staff need verified KYC to approve. A push goes out at every change.

EMI:         GET /emi/schedule -> pay by UPI/QR/bank (GET /emi/payment-instructions)
             -> upload screenshot -> POST /emi/payments/submit
             EMI: pending -> awaiting_proof -> paid
                  rejected proof goes back to pending or overdue
             Reminders start 3 days before the due date, once a day, also while overdue.
             The last EMI paid closes the loan."""
ERRORS = [("400", "VALIDATION_ERROR", "Bad input. Show `message`."), ("401", "TOKEN_EXPIRED", "Access token expired. Refresh and retry once."),
          ("401", "INVALID_TOKEN", "Token is bad or was ended, for example by a password change. Try refresh, else sign in."),
          ("401", "INVALID_REFRESH_TOKEN", "Sign in again."), ("401", "UNAUTHORIZED", "No token sent."),
          ("403", "FORBIDDEN", "Account blocked or closed, or no permission. Show `message`, stop retrying."), ("404", "NOT_FOUND", "Not found, or not yours."),
          ("409", "CONFLICT", "Duplicate value, for example an email already in use."), ("409", "KYC_REJECTED", "Send onboarding step 3 again before applying."),
          ("409", "KYC_NOT_VERIFIED", "Staff only: verify KYC before approving."), ("409", "LOAN_NOT_PENDING", "Only a pending application can be cancelled."),
          ("409", "LOAN_NOT_DISBURSED", "EMIs cannot be paid before disbursal."), ("409", "OPEN_LOAN", "Close the loan before deleting the account."),
          ("413", "PAYLOAD_TOO_LARGE", "JSON body over 2 MB."), ("429", "RATE_LIMITED", "Too many attempts, or account locked. Wait and retry."),
          ("503", "SERVICE_UNAVAILABLE", "OTP could not be sent, or the server is unhealthy. Retry shortly."),
          ("500", "SERVER_ERROR", "Show a generic error. Quote the `X-Request-Id` response header when reporting it.")]
PUSH_DATA = [("notification_id", "Id in `GET /notifications`."), ("category", "`loan_status`, `emi_reminder`, `emi_payment`, `kyc` or `general`. Decides which screen to open."),
             ("loan_application_id, status", "With loan pushes."), ("emi_schedule_id, due_date, amount_due, type", "With EMI reminders."),
             ("emi_payment_id, remaining_installments, status", "With EMI payment pushes."), ("kyc_status", "With KYC pushes.")]
PUSH_EVENTS = "KYC verified or rejected, loan approved, rejected, disbursed or closed, EMI proof received, approved or rejected, EMI reminders, messages sent by staff."
STATUSES = [("kyc_status", "`pending`, `submitted`, `verified`, `rejected`"), ("account_status", "`active`, `inactive`, `blocked`, `suspended`"), ("bank_verification_status", "`pending`, `verified`, `failed`"),
            ("Loan `status`", "`pending`, `approved`, `disbursed`, `closed`, `rejected`, `cancelled`"), ("EMI `status`", "`pending`, `awaiting_proof`, `overdue`, `paid`"), ("Payment `status`", "`pending`, `approved`, `rejected`"),
            ("Notification `category`", "`loan_status`, `emi_reminder`, `emi_payment`, `kyc`, `general`"), ("Staff alert `type`", "`new_user`, `new_loan`, `kyc_submitted`, `emi_proof`")]


def auth_label(a):
    return {"user": "Customer token", "admin": "Staff token", "cron": "Cron secret", None: "None"}[a]


def short(path):
    return path[len(V):] if path.startswith(V + "/") else path


def md():
    o = ["# Narainsons API Reference", "",
         f"All {len(endpoints)} APIs of the project, with every request and response field. Every example is a real response recorded from a test server, with tokens masked. Interest of 12% and a 2% fee in the samples are example settings.", "",
         "**Mobile app developers need Part 1.** Part 2 is what the admin panel uses; app tokens do not work there.", "", "## Basics", "", "| | |", "|---|---|",
         "| App base URL | `http://<host>:8082/NarainsonsProject/api/v1` (paths in Part 1 are relative to this) |",
         "| Staff base URL | `http://<host>:8082/NarainsonsProject` (paths in Part 2 are full) |",
         "| Format | JSON, unless an API says multipart |", "| Sign-in | `Authorization: Bearer <access_token>` |",
         "| Dates | `yyyy-MM-dd` and `yyyy-MM-ddTHH:mm:ss`, India time. List APIs show `yyyy-MM-dd HH:mm`. |", "| Field names | snake_case, exactly as shown. |", "| Older paths | `/api/auth`, `/api/user` and the like still work; use `/api/v1`. |", "",
         "Every JSON response has this shape:", "", "```json", '{ "success": true, "message": "Text for the user", "data": { }, "error_code": null }', "```", "",
         "On failure `success` is false, `data` is null and `error_code` is set. Base your logic on `error_code`, because `message` text can change.", "",
         "## App flow", "", "```text", FLOW, "```", "", "## Tokens", "",
         "- The **access token** is a JWT and lasts 60 minutes. The **refresh token** lasts 30 days and works once.",
         "- When an API returns `TOKEN_EXPIRED`: call refresh, save both new tokens, retry the call once.", "- If refresh returns 401, delete the tokens and go to sign-in.", "- Store tokens in Android Keystore or iOS Keychain, never in plain preferences.", "",
         "## Push notifications", "", "Push uses Firebase Cloud Messaging. The app must use the `google-services.json` or `GoogleService-Info.plist` of the **same Firebase project** as the server.", "",
         "1. Get the token from the Firebase SDK and send it to `POST /user/devices` after sign-in and on every token refresh.", "2. Create an Android notification channel with id `narainsons_default`.", "3. Ask for notification permission (Android 13+ and iOS).", "4. Read `data` to open the right screen.", "",
         f"**Sent on:** {PUSH_EVENTS}", "", "| `data` key | Meaning |", "|---|---|"]
    o += [f"| `{k}` | {v} |" for k, v in PUSH_DATA]
    o += ["", "All values in `data` are strings.", "", "## Status values", "", "| Field | Values |", "|---|---|"] + [f"| {a} | {b} |" for a, b in STATUSES]
    o += ["", "## Error codes", "", "| HTTP | error_code | What to do |", "|---|---|---|"] + [f"| {a} | `{b}` | {c} |" for a, b, c in ERRORS]
    o += ["", "## All APIs", "", "| Method | Path | Sign-in | Purpose |", "|---|---|---|---|"]
    for gid, part, gname, _ in GROUPS:
        for e in [x for x in endpoints if x["group"] == gid]:
            o.append(f"| {e['method']} | `{short(e['path'])}` | {auth_label(e['auth'])} | {e['title']} |")
    o.append("")
    last = None
    for gid, part, gname, intro in GROUPS:
        if part != last:
            o += ["# " + ("Part 1: Mobile app APIs" if part == "app" else "Part 2: Admin panel and internal APIs"), ""]
            last = part
        o += [f"## {gname}", "", intro, ""]
        for e in [x for x in endpoints if x["group"] == gid]:
            o += [f"### {e['title']}", "", f"`{e['method']} {short(e['path'])}`  ", f"Sign-in: {auth_label(e['auth'])}", ""]
            if e["notes"]:
                o += [e["notes"], ""]
            if e["headers"]:
                o += ["| Header | Value |", "|---|---|"] + [f"| `{a}` | {b} |" for a, b in e["headers"]] + [""]
            if e["params"]:
                o += ["Query parameters:", "", "| Name | Type | Meaning |", "|---|---|---|"] + [f"| `{a}` | {b} | {c} |" for a, b, c in e["params"]] + [""]
            if e["req"]:
                o += ["Request fields:", "", "| Field | Type | Required | Rules |", "|---|---|---|---|"] + [f"| `{a}` | {b} | {c} | {d} |" for a, b, c, d in e["req"]] + [""]
            elif e["multipart"]:
                o += [f"Form field: `{e['multipart']['field']}` (the file)", ""]
            if e["body"]:
                o += ["Request example:", "", "```json", json.dumps(e["body"], indent=2, ensure_ascii=False), "```", ""]
            if e["rows"]:
                o += ["Response `data` fields:", "", "| Field | Type | Meaning |", "|---|---|---|"] + [f"| `{r['field']}` | {r['type']} | {r['desc']} |" for r in e["rows"]] + [""]
            if e["binary"]:
                o += [f"Response: {e['binary']}.", ""]
            if e["response"] is not None:
                o += [f"Response example ({e['status']}):", "", "```json", json.dumps(e["response"], indent=2, ensure_ascii=False), "```", ""]
            if e["errors"]:
                o += ["Errors:", "", "| HTTP | When |", "|---|---|"] + [f"| {a} | {b} |" for a, b in e["errors"]] + [""]
    return "\n".join(o)


def postman():
    script = ["var j = {}; try { j = pm.response.json(); } catch (e) {}", "var u = pm.request.url.toString();", "if (j.data && j.data.access_token) {",
              "  if (u.indexOf('/api/admin') !== -1) { pm.collectionVariables.set('staff_token', j.data.access_token); }",
              "  else { pm.collectionVariables.set('access_token', j.data.access_token); pm.collectionVariables.set('refresh_token', j.data.refresh_token); }", "}",
              "if (j.data && j.data.dev_otp) { pm.collectionVariables.set('otp', j.data.dev_otp); }", "if (j.data && j.data.url) { pm.collectionVariables.set('file_url', j.data.url); }"]

    def item(e):
        path = re.sub(r"\{[a-z_]+\}", "1", e["path"]) + ("?" + e["query"] if e["query"] else "")
        req = {"method": e["method"], "header": [{"key": a, "value": "{{cron_secret}}"} for a, b in e["headers"]], "url": "{{baseUrl}}" + path, "description": e["notes"] or ""}
        tok = {"user": "access_token", "admin": "staff_token"}.get(e["auth"])
        req["auth"] = {"type": "bearer", "bearer": [{"key": "token", "value": "{{%s}}" % tok, "type": "string"}]} if tok else {"type": "noauth"}
        if e["multipart"]:
            req["body"] = {"mode": "formdata", "formdata": [{"key": e["multipart"]["field"], "type": "file", "src": []}] + [{"key": k, "value": v, "type": "text"} for k, v in e["multipart"]["fields"].items()]}
        elif e["body"] is not None:
            b = dict(e["body"])
            for key in ("refresh_token", "otp"):
                if key in b:
                    b[key] = "{{%s}}" % key
            if "screenshot_url" in b:
                b["screenshot_url"] = "{{file_url}}"
            req["body"] = {"mode": "raw", "raw": json.dumps(b, indent=2, ensure_ascii=False), "options": {"raw": {"language": "json"}}}
        return {"name": e["title"], "request": req, "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": script}}]}

    folders = []
    for part, label in (("app", "Part 1: Mobile app"), ("staff", "Part 2: Admin panel")):
        folders.append({"name": label, "item": [{"name": name, "item": [item(e) for e in endpoints if e["group"] == gid]} for gid, p, name, _ in GROUPS if p == part]})
    return {"info": {"name": "Narainsons API", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json", "description": "Sign in first: tokens and the dev OTP are saved into collection variables automatically."},
            "variable": [{"key": k, "value": v} for k, v in [("baseUrl", "http://localhost:8082/NarainsonsProject"), ("access_token", ""), ("refresh_token", ""), ("staff_token", ""), ("otp", ""), ("file_url", ""), ("cron_secret", "")]],
            "item": folders}


def inline(t):
    t = html.escape(t or "", quote=False)
    t = re.sub(r"`([^`]+)`", r"<code>\1</code>", t)
    return re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", t)


def slug(t):
    return re.sub(r"[^a-z0-9]+", "-", t.lower()).strip("-")


def code(obj):
    return f'<div class="code"><pre tabindex="0">{html.escape(json.dumps(obj, indent=2, ensure_ascii=False), quote=False)}</pre></div>'


def table(headers, rows, first_code=True):
    head = "".join(f"<th>{inline(h)}</th>" for h in headers)
    body = "".join("<tr>" + "".join(f"<td><code>{html.escape(str(c))}</code></td>" if i == 0 and first_code else f"<td>{inline(str(c))}</td>" for i, c in enumerate(r)) + "</tr>" for r in rows)
    return f'<div class="tw"><table><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table></div>'


def endpoint_html(e):
    used = [e["title"]]
    p = [f'<article class="ep" id="{slug(e["group"] + "-" + e["title"])}" data-search="{html.escape((e["title"] + " " + e["method"] + " " + short(e["path"])).lower())}">', f'<h4>{html.escape(e["title"])}</h4>',
         f'<p class="route"><span class="m m-{e["method"].lower()}">{e["method"]}</span><code>{html.escape(short(e["path"]))}</code><span class="auth{" open" if not e["auth"] else ""}">{auth_label(e["auth"])}</span></p>']
    if e["notes"]:
        p.append(f'<p class="note">{inline(e["notes"])}</p>')
    if e["headers"]:
        p.append('<p class="label">Headers</p>' + table(["Header", "Value"], e["headers"]))
    if e["params"]:
        p.append('<p class="label">Query parameters</p>' + table(["Name", "Type", "Meaning"], e["params"]))
    if e["req"]:
        p.append('<p class="label">Request fields</p>' + table(["Field", "Type", "Required", "Rules"], e["req"]))
    elif e["multipart"]:
        p.append(f'<p class="label">Form field</p><p class="note"><code>{e["multipart"]["field"]}</code> (the file)</p>')
    if e["body"]:
        p.append(f'<details><summary>Request example</summary>{code(e["body"])}</details>')
    if e["rows"]:
        p.append('<p class="label">Response data fields</p>' + table(["Field", "Type", "Meaning"], [(r["field"], r["type"], r["desc"]) for r in e["rows"]]))
    if e["binary"]:
        p.append(f'<p class="note"><strong>Response:</strong> {html.escape(e["binary"])}.</p>')
    if e["response"] is not None:
        p.append(f'<details><summary>Response example ({e["status"]})</summary>{code(e["response"])}</details>')
    if e["errors"]:
        p.append('<p class="label">Errors</p>' + table(["HTTP", "When"], e["errors"], first_code=False))
    p.append("</article>")
    return "".join(p)


def page(standalone):
    nav, sections, index, last = [], [], [], None
    for gid, part, name, intro in GROUPS:
        eps = [e for e in endpoints if e["group"] == gid]
        if part != last:
            label = "Part 1: Mobile app" if part == "app" else "Part 2: Admin panel"
            nav.append(f'<p class="nav-part">{label}</p>')
            sections.append(f'<h2 class="part" id="part-{part}">{"Part 1: Mobile app APIs" if part == "app" else "Part 2: Admin panel and internal APIs"}</h2>')
            last = part
        nav.append(f'<a href="#g-{gid}">{html.escape(name)}<span>{len(eps)}</span></a>')
        sections.append(f'<section class="group" id="g-{gid}"><h3>{html.escape(name)}</h3><p class="intro">{inline(intro)}</p>' + "".join(endpoint_html(e) for e in eps) + "</section>")
        for e in eps:
            index.append(f'<tr><td><span class="m m-{e["method"].lower()}">{e["method"]}</span></td><td><a href="#{slug(e["group"] + "-" + e["title"])}"><code>{html.escape(short(e["path"]))}</code></a></td><td>{html.escape(e["title"])}</td><td>{auth_label(e["auth"])}</td></tr>')
    head = '<!doctype html>\n<html lang="en">\n<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n' if standalone else ""
    tail = "</html>\n" if standalone else ""
    return head + f"""<title>Narainsons App API</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Poppins:wght@600;700&family=Source+Sans+3:wght@400;600&family=JetBrains+Mono:wght@400;500&display=swap">
<style>
/* Layout: a fixed contents rail beside one reading column, split into the app part and the staff part. Palette from the Narainsons logo: navy, blue, gold. */
:root {{
  --ground:#f6f8fc; --surface:#ffffff; --ink:#10223f; --muted:#556480; --line:#d9e1ee;
  --navy:#0b2f5e; --blue:#0b63c5; --gold:#a9780a; --gold-soft:#fbf1d6; --code-bg:#eef2f9;
  --post:#0b63c5; --get:#0f7a55; --on-accent:#ffffff;
  --display:"Poppins","Segoe UI",system-ui,sans-serif; --body:"Source Sans 3","Segoe UI",system-ui,sans-serif; --mono:"JetBrains Mono",Consolas,monospace;
}}
@media (prefers-color-scheme: dark) {{ :root:not([data-theme="light"]) {{
  color-scheme:dark; --ground:#0a1424; --surface:#111e33; --ink:#e7edf7; --muted:#9dacc4; --line:#26364f;
  --navy:#9cc4f5; --blue:#6db0f7; --gold:#e8bd4f; --gold-soft:#2e2711; --code-bg:#0d192c; --post:#6db0f7; --get:#54c79c; --on-accent:#0a1424;
}} }}
:root[data-theme="dark"] {{
  color-scheme:dark; --ground:#0a1424; --surface:#111e33; --ink:#e7edf7; --muted:#9dacc4; --line:#26364f;
  --navy:#9cc4f5; --blue:#6db0f7; --gold:#e8bd4f; --gold-soft:#2e2711; --code-bg:#0d192c; --post:#6db0f7; --get:#54c79c; --on-accent:#0a1424;
}}
* {{ box-sizing:border-box; }}
html {{ scroll-behavior:smooth; }}
@media (prefers-reduced-motion: reduce) {{ html {{ scroll-behavior:auto; }} }}
body {{ margin:0; background:var(--ground); color:var(--ink); font:400 16px/1.55 var(--body); }}
.shell {{ display:grid; grid-template-columns:250px minmax(0,1fr); gap:44px; max-width:1240px; margin:0 auto; padding-block:36px 80px; padding-left:max(24px, env(safe-area-inset-left, 0px)); padding-right:max(24px, env(safe-area-inset-right, 0px)); }}
aside {{ position:sticky; top:calc(20px + env(safe-area-inset-top, 0px)); align-self:start; display:flex; flex-direction:column; gap:12px; max-height:calc(100vh - 40px); overflow:auto; }}
.brand {{ font:700 18px/1.25 var(--display); color:var(--navy); }}
.brand small {{ display:block; font:400 13px var(--mono); color:var(--muted); margin-top:4px; }}
aside nav {{ display:flex; flex-direction:column; gap:1px; }}
aside nav a {{ display:flex; justify-content:space-between; gap:12px; padding:4px 8px; border-radius:6px; color:var(--ink); text-decoration:none; font-size:14px; }}
aside nav a:hover {{ background:var(--code-bg); }}
aside nav a span {{ color:var(--muted); font:400 12px var(--mono); }}
.nav-part {{ margin:10px 8px 2px; font-size:11px; font-weight:600; letter-spacing:.06em; text-transform:uppercase; color:var(--gold); }}
aside label {{ font-size:13px; color:var(--muted); display:flex; flex-direction:column; gap:4px; }}
input {{ font:400 15px var(--body); padding:7px 10px; border:1px solid var(--line); border-radius:6px; background:var(--surface); color:var(--ink); width:100%; }}
main {{ min-width:0; display:flex; flex-direction:column; gap:44px; }}
h1,h2,h3,h4 {{ font-family:var(--display); text-wrap:balance; margin:0; color:var(--navy); }}
h1 {{ font-size:clamp(28px,4vw,40px); line-height:1.15; font-weight:700; }}
h2 {{ font-size:24px; line-height:1.25; font-weight:700; }}
h2.part {{ font-size:28px; padding-top:18px; border-top:3px solid var(--gold); }}
h3 {{ font-size:21px; line-height:1.25; font-weight:700; }}
h4 {{ font-size:17px; font-weight:600; }}
p {{ margin:0; max-width:70ch; }}
ol {{ margin:0; padding-left:20px; }}
.stack {{ display:flex; flex-direction:column; gap:12px; }}
.lede {{ font-size:18px; color:var(--muted); }}
code {{ font:400 .86em var(--mono); background:var(--code-bg); padding:.1em .35em; border-radius:4px; overflow-wrap:anywhere; }}
.code {{ background:var(--code-bg); border-radius:8px; overflow-x:auto; }}
pre {{ margin:0; padding:12px 14px; font:400 13px/1.5 var(--mono); }}
.facts {{ display:grid; grid-template-columns:max-content minmax(0,1fr); gap:5px 20px; margin:0; }}
.facts dt {{ color:var(--muted); }} .facts dd {{ margin:0; }}
.tw {{ overflow-x:auto; }}
table {{ border-collapse:collapse; width:100%; font-size:14px; }}
th {{ text-align:left; font-size:11.5px; letter-spacing:.06em; text-transform:uppercase; color:var(--muted); font-weight:600; }}
th,td {{ padding:6px 12px 6px 0; border-bottom:1px solid var(--line); vertical-align:top; }}
td a {{ text-decoration:none; }}
.group {{ display:flex; flex-direction:column; gap:24px; scroll-margin-top:20px; }}
.intro {{ margin-top:-16px; color:var(--muted); }}
.ep {{ display:flex; flex-direction:column; gap:9px; padding-top:20px; border-top:1px solid var(--line); scroll-margin-top:16px; }}
.route {{ display:flex; flex-wrap:wrap; align-items:center; gap:10px; max-width:none; }}
.m {{ font:500 12px var(--mono); padding:2px 8px; border-radius:4px; color:var(--on-accent); }}
.m-post {{ background:var(--post); }} .m-get {{ background:var(--get); }}
.auth {{ font-size:13px; color:var(--gold); font-weight:600; }} .auth.open {{ color:var(--muted); font-weight:400; }}
.note {{ font-size:15.5px; }}
.label {{ font-size:11.5px; letter-spacing:.06em; text-transform:uppercase; color:var(--muted); font-weight:600; margin-top:4px; }}
summary {{ cursor:pointer; font-weight:600; font-size:14px; padding-block:3px; color:var(--blue); }}
details .code {{ margin-top:6px; }}
a {{ color:var(--blue); }}
:focus-visible {{ outline:2px solid var(--blue); outline-offset:2px; }}
[hidden] {{ display:none !important; }}
@media (max-width:860px) {{
  .shell {{ grid-template-columns:minmax(0,1fr); gap:28px; padding-block:22px 56px; padding-left:max(16px, env(safe-area-inset-left, 0px)); padding-right:max(16px, env(safe-area-inset-right, 0px)); }}
  aside {{ position:static; max-height:none; }}
  aside nav {{ flex-direction:row; flex-wrap:wrap; gap:4px; }}
  aside nav a {{ background:var(--surface); border:1px solid var(--line); }}
  .nav-part {{ width:100%; }}
  .facts {{ grid-template-columns:minmax(0,1fr); gap:2px; }} .facts dd {{ margin-bottom:8px; }}
}}
</style>
<div class="shell">
<aside>
  <div class="brand">Narainsons API<small>{len(endpoints)} endpoints</small></div>
  <label for="ep-filter">Find an API<input id="ep-filter" type="search" placeholder="emi, refresh, upload"></label>
  <nav><a href="#basics">Basics</a><a href="#flow">App flow</a><a href="#tokens">Tokens</a><a href="#push">Push notifications</a><a href="#statuses">Status values</a><a href="#errors">Error codes</a><a href="#index">All APIs</a>{"".join(nav)}</nav>
</aside>
<main>
<header class="stack" id="basics">
  <h1>Every Narainsons API, with every field</h1>
  <p class="lede">Every example is a real response recorded from a test server, with tokens masked. The 12% interest and 2% fee in the samples are example settings.</p>
  <p><strong>Mobile app developers need Part 1.</strong> Part 2 is what the admin panel uses; app tokens do not work there.</p>
  <dl class="facts">
    <dt>App base URL</dt><dd><code>http://&lt;host&gt;:8082/NarainsonsProject/api/v1</code></dd>
    <dt>Staff base URL</dt><dd><code>http://&lt;host&gt;:8082/NarainsonsProject</code></dd>
    <dt>Format</dt><dd>JSON, unless an API says multipart</dd>
    <dt>Sign-in</dt><dd><code>Authorization: Bearer &lt;access_token&gt;</code></dd>
    <dt>Dates</dt><dd><code>yyyy-MM-dd</code> and <code>yyyy-MM-ddTHH:mm:ss</code>, India time. List APIs show <code>yyyy-MM-dd HH:mm</code>.</dd>
    <dt>Field names</dt><dd>snake_case, exactly as shown.</dd>
    <dt>Older paths</dt><dd><code>/api/auth</code>, <code>/api/user</code> and the like still work; use <code>/api/v1</code>.</dd>
  </dl>
  <p>Every JSON response has the same shape. On failure <code>success</code> is false, <code>data</code> is null and <code>error_code</code> is set. Base your logic on <code>error_code</code>, because <code>message</code> text can change.</p>
  {code({"success": True, "message": "Text for the user", "data": {}, "error_code": None})}
</header>
<section class="stack" id="flow"><h2>App flow</h2><div class="code"><pre tabindex="0">{html.escape(FLOW, quote=False)}</pre></div></section>
<section class="stack" id="tokens"><h2>Tokens</h2>
  <p>The access token is a JWT that lasts 60 minutes. The refresh token lasts 30 days and works once.</p>
  <p>When an API returns <code>TOKEN_EXPIRED</code>, call refresh, save both new tokens and retry the call once. If refresh returns 401, delete the tokens and go to sign-in.</p>
  <p>Store tokens in Android Keystore or iOS Keychain, never in plain preferences.</p></section>
<section class="stack" id="push"><h2>Push notifications</h2>
  <p>Push uses Firebase Cloud Messaging. The app must use the <code>google-services.json</code> or <code>GoogleService-Info.plist</code> of the same Firebase project as the server.</p>
  <ol><li>Get the token from the Firebase SDK and send it to <code>POST /user/devices</code> after sign-in and on every token refresh.</li><li>Create an Android notification channel with id <code>narainsons_default</code>.</li><li>Ask for notification permission on Android 13+ and iOS.</li><li>Read <code>data</code> to open the right screen.</li></ol>
  <p><strong>Sent on:</strong> {inline(PUSH_EVENTS)}</p>
  {table(["data key", "Meaning"], PUSH_DATA)}
  <p>All values in <code>data</code> are strings.</p></section>
<section class="stack" id="statuses"><h2>Status values</h2>{table(["Field", "Values"], STATUSES, first_code=False)}</section>
<section class="stack" id="errors"><h2>Error codes</h2>{table(["HTTP", "error_code", "What to do"], [(b, a, c) for a, b, c in [(x[0], x[1], x[2]) for x in ERRORS]])}</section>
<section class="stack" id="index"><h2>All APIs</h2><div class="tw"><table><thead><tr><th>Method</th><th>Path</th><th>Purpose</th><th>Sign-in</th></tr></thead><tbody>{"".join(index)}</tbody></table></div></section>
{"".join(sections)}
<p id="no-match" hidden>No API matches that search.</p>
</main>
</div>
<script>
(function () {{
  var input = document.getElementById("ep-filter");
  var eps = Array.prototype.slice.call(document.querySelectorAll(".ep"));
  var groups = Array.prototype.slice.call(document.querySelectorAll(".group"));
  var other = ["flow", "tokens", "push", "statuses", "errors", "index", "basics", "part-app", "part-staff"].map(function (id) {{ return document.getElementById(id); }});
  var none = document.getElementById("no-match");
  input.addEventListener("input", function () {{
    var q = input.value.trim().toLowerCase();
    var shown = 0;
    eps.forEach(function (e) {{
      var hit = !q || e.getAttribute("data-search").indexOf(q) !== -1;
      e.hidden = !hit;
      if (hit) shown++;
    }});
    groups.forEach(function (g) {{ g.hidden = !g.querySelector(".ep:not([hidden])"); }});
    other.forEach(function (s) {{ s.hidden = !!q; }});
    none.hidden = shown > 0;
  }});
}})();
</script>
""" + tail


os.makedirs(OUT, exist_ok=True)
open(os.path.join(OUT, "API-REFERENCE.md"), "w", encoding="utf-8").write(md())
open(os.path.join(OUT, "API-REFERENCE.html"), "w", encoding="utf-8").write(page(True))
open(os.path.join(OUT, "API-REFERENCE.publish.html"), "w", encoding="utf-8").write(page(False))
json.dump(postman(), open(os.path.join(OUT, "Narainsons-API.postman_collection.json"), "w", encoding="utf-8"), indent=2, ensure_ascii=False)
total_fields = sum(len(e["rows"]) + len(e["req"]) for e in endpoints)
print(f"\nDocumented {len(endpoints)} endpoints, {total_fields} field rows")
