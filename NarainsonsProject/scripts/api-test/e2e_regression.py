#!/usr/bin/env python3
"""End-to-end regression test for the Narainsons API.

WRITES TEST DATA and needs an EMPTY database, so point it at a throwaway instance only:
  * app.dev.expose_otp=true, secrets.properties blank (no real SMS/mail)
  * restart Tomcat first, because rate-limit counters live in memory

Usage: e2e_regression.py <base-url> <mysql.exe> <mysql-host> <mysql-port>
"""
import json
import subprocess
import sys
import urllib.error
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")
BASE, MYSQL, DB_HOST, DB_PORT = sys.argv[1:5]
failures = []


def call(method, path, body=None, tok=None, hdr=None, raw=None, ct="application/json"):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    headers = dict(hdr or {})
    if data is not None and ct:
        headers["Content-Type"] = ct
    if tok:
        headers["Authorization"] = "Bearer " + tok
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        res = urllib.request.urlopen(req, timeout=60)
        status, text = res.status, res.read()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read()
    text = text.decode("utf-8", "replace")
    try:
        return status, json.loads(text)
    except ValueError:
        return status, {"message": "NON-JSON: " + text[:80]}


def sql(query):
    out = subprocess.run([MYSQL, f"-h{DB_HOST}", f"-P{DB_PORT}", "-uroot", "-N", "narainson_db", "-e", query],
                         capture_output=True, text=True)
    if out.returncode:
        raise RuntimeError(out.stderr)
    return out.stdout.strip()


def check(name, condition, detail=""):
    print(("PASS " if condition else "FAIL ") + name + ("" if condition else f"   -> {detail}"))
    if not condition:
        failures.append(name)


def expect(name, res, status):
    check(name, res[0] == status, f"got {res[0]} {res[1].get('message')!r}")
    return res[1].get("data")


def user_session(mobile):
    data = expect(f"otp send {mobile}", call("POST", "/api/auth/otp/send", {"mobile_number": mobile}), 200)
    data = expect("otp verify", call("POST", "/api/auth/otp/verify", {"mobile_number": mobile, "otp": data["dev_otp"]}), 200)
    return data["access_token"]


STEP1 = {"step": 1, "name": "T User", "first_name": "T", "last_name": "User", "gender": "m", "date_of_birth": "1990-01-01"}
STEP2 = {"step": 2, "current_address": "A", "current_city": "C", "current_state": "S", "current_pincode": "110001",
         "employment_type": "salaried", "monthly_income": 30000}
STEP3 = {"step": 3, "aadhaar_last4": "1234", "pan_last4": "123F", "bank_name": "B", "ifsc_code": "hdfc0001234",
         "bank_account_last4": "1234", "privacy_consent": True, "terms_accepted": True, "kyc_consent": True}


def onboard(tok, email):
    expect("step1", call("POST", "/api/user/onboarding", dict(STEP1, email=email), tok), 200)
    expect("step2", call("POST", "/api/user/onboarding", STEP2, tok), 200)
    expect("step3", call("POST", "/api/user/onboarding", STEP3, tok), 200)


def guarantors(n):
    return [{"full_name": f"G{i}", "mobile_number": f"900000010{i}", "relation": "friend"} for i in range(n)]


def apply_loan(tok, amount=10000, months=3):
    return call("POST", "/api/loan/apply", {"requested_amount": amount, "tenure_months": months, "purpose": "personal",
                                            "guarantors": guarantors(max(1, amount // 10000))}, tok)


print("=== health and static")
expect("health ok", call("GET", "/api/health"), 200)

print("=== admin auth")
PW = "TestAdmin1!"
expect("first admin", call("POST", "/api/admin/auth/register", {"email": "t.admin@test.local", "password": PW, "full_name": "T"}), 201)
expect("second admin without login is refused", call("POST", "/api/admin/auth/register", {"email": "x@test.local", "password": PW, "full_name": "X"}), 400)
A = expect("admin login", call("POST", "/api/admin/auth/login", {"email": "t.admin@test.local", "password": PW}), 200)["access_token"]
expect("register, no email", call("POST", "/api/admin/auth/register", {"password": PW, "full_name": "N"}, A), 400)
expect("reset, no email", call("POST", "/api/admin/auth/password/forgot/reset", {"otp": "1", "new_password": "Abcd12345"}), 400)
expect("unknown email gets the OTP error", call("POST", "/api/admin/auth/password/forgot/reset", {"email": "no@test.local", "otp": "1", "new_password": "Abcd12345"}), 400)
data = expect("change password", call("POST", "/api/admin/auth/password/change", {"current_password": PW, "new_password": "NewAdmin12"}, A), 200)
expect("old session is dead after password change", call("GET", "/api/admin/auth/me", tok=A), 401)
A = data["access_token"]
expect("new token from change works", call("GET", "/api/admin/auth/me", tok=A), 200)
check("admin token is a JWT", A.count(".") == 2, A[:12])
res = call("GET", "/api/admin/auth/me", tok=A[:-3] + "abc")
check("tampered token refused", res[0] == 401 and res[1].get("error_code") == "INVALID_TOKEN", res)
expect("second admin by super admin", call("POST", "/api/admin/auth/register", {"email": "low@test.local", "password": PW, "full_name": "Low"}, A), 201)
for _ in range(5):
    last = call("POST", "/api/admin/auth/login", {"email": "low@test.local", "password": "wrong"})
expect("fifth wrong password still generic", last, 400)
expect("account locked after 5 wrong", call("POST", "/api/admin/auth/login", {"email": "low@test.local", "password": PW}), 429)
sql("update admins set locked_until=NULL where email='low@test.local'")
LOW = expect("low admin login", call("POST", "/api/admin/auth/login", {"email": "low@test.local", "password": PW}), 200)["access_token"]

print("=== user OTP and input validation")
expect("bad mobile", call("POST", "/api/auth/otp/send", {"mobile_number": "abc"}), 400)
check("bad mobile did not create a user", sql("select count(*) from users") == "0")
first = call("POST", "/api/auth/otp/send", {"mobile_number": "9000000001"})[1]["data"]["dev_otp"]
pair = expect("otp verify gives tokens", call("POST", "/api/auth/otp/verify", {"mobile_number": "9000000001", "otp": first}), 200)
U, RT = pair["access_token"], pair["refresh_token"]
check("user token is a JWT", U.count(".") == 2)
check("token block complete", pair["token_type"] == "Bearer" and pair["expires_in"] > 0 and pair["refresh_expires_in"] > 0, pair)
expect("user token cannot call admin API", call("GET", "/api/admin/dashboard", tok=U), 401)
expect("admin token cannot call user API", call("GET", "/api/v1/user/me", tok=A), 401)
expect("v1 path works", call("GET", "/api/v1/user/me", tok=U), 200)
res = call("GET", "/api/v1/user/me", tok=U)[1]["data"]["user"]
leaked = [k for k in ("password_hash", "otp_hash", "session_token", "fcm_token", "contacts_data", "login_attempts") if k in res]
check("profile hides internal fields", not leaked, leaked)

sql("update users set email='t.user@test.local' where mobile_number='9000000001'")
expect("set first password", call("POST", "/api/v1/user/password", {"new_password": "UserPass123"}, U), 200)
expect("old access token dead after password change", call("GET", "/api/v1/user/me", tok=U), 401)
expect("old refresh token dead after password change", call("POST", "/api/v1/auth/token/refresh", {"refresh_token": RT}), 401)
pair = call("POST", "/api/v1/auth/login", {"email": "t.user@test.local", "password": "UserPass123"})[1]["data"]
U, RT = pair["access_token"], pair["refresh_token"]

print("=== refresh tokens")
two = expect("refresh", call("POST", "/api/v1/auth/token/refresh", {"refresh_token": RT}), 200)
check("refresh token rotated", two["refresh_token"] != RT)
res = call("POST", "/api/v1/auth/token/refresh", {"refresh_token": RT})
check("used refresh token refused", res[0] == 401 and res[1]["error_code"] == "INVALID_REFRESH_TOKEN", res)
expect("reuse ends the whole login", call("POST", "/api/v1/auth/token/refresh", {"refresh_token": two["refresh_token"]}), 401)
expect("refresh, garbage", call("POST", "/api/v1/auth/token/refresh", {"refresh_token": "nope"}), 401)
expect("refresh, missing", call("POST", "/api/v1/auth/token/refresh", {}), 400)
pair = call("POST", "/api/v1/auth/login", {"email": "t.user@test.local", "password": "UserPass123"})[1]["data"]
U, RT = pair["access_token"], pair["refresh_token"]
expect("password login, unknown email", call("POST", "/api/v1/auth/login", {"email": "none@test.local", "password": "x"}), 400)
pair = call("POST", "/api/v1/auth/login", {"email": "t.user@test.local", "password": "UserPass123"})[1]["data"]
U, RT = pair["access_token"], pair["refresh_token"]
expect("change needs current password", call("POST", "/api/v1/user/password", {"new_password": "UserPass456"}, U), 400)
expect("+91 maps to same user", call("POST", "/api/auth/otp/send", {"mobile_number": "+91 9000000001"}), 200)
check("still one user", sql("select count(*) from users") == "1")
good = call("POST", "/api/auth/otp/send", {"mobile_number": "9000000002"})[1]["data"]["dev_otp"]
wrong = "000000" if good != "000000" else "111111"
for _ in range(5):
    call("POST", "/api/auth/otp/verify", {"mobile_number": "9000000002", "otp": wrong})
expect("right OTP refused after 5 wrong", call("POST", "/api/auth/otp/verify", {"mobile_number": "9000000002", "otp": good}), 400)
results = [call("POST", "/api/auth/otp/send", {"mobile_number": "9000000003"})[0] for _ in range(6)]
check("sixth send to one number is rate limited", results[-1] == 429 and results[0] == 200, results)
expect("bad json", call("POST", "/api/auth/login", raw=b"{bad"), 400)
expect("array in text field", call("POST", "/api/auth/login", {"email": ["a", "b"], "password": "x"}), 400)
expect("big body refused", call("POST", "/api/auth/login", raw=b"x" * (3 * 1024 * 1024)), 413)

print("=== onboarding")
U2 = user_session("9000000004")
expect("no step", call("POST", "/api/user/onboarding", {"x": 1}, U), 400)
expect("step as array", call("POST", "/api/user/onboarding", {"step": [1, 2]}, U), 400)
expect("step 3 before 1", call("POST", "/api/user/onboarding", dict(STEP3, bank_verification_status="verified"), U), 400)
expect("bad date", call("POST", "/api/user/onboarding", dict(STEP1, email="t.user@test.local", date_of_birth="01-01-1990"), U), 400)
expect("bad gender", call("POST", "/api/user/onboarding", dict(STEP1, email="t.user@test.local", gender="zzz"), U), 400)
onboard(U, "t.user@test.local")
user = call("GET", "/api/user/me", tok=U)[1]["data"]["user"]
check("employment type normalised", user["employment_type"] == "Salaried", user["employment_type"])
expect("send verified flag", call("POST", "/api/user/onboarding", dict(STEP3, bank_verification_status="verified"), U), 200)
user = call("GET", "/api/user/me", tok=U)[1]["data"]["user"]
check("user cannot self-verify bank", user["bank_verification_status"] == "pending", user["bank_verification_status"])
expect("duplicate email is a clean 409", call("POST", "/api/user/onboarding", dict(STEP1, email="t.user@test.local"), U2), 409)
expect("sync accepted", call("POST", "/api/user/sync", {"contacts": [{"p": "1"}], "gallery": [{"u": "x"}], "device_id": "d"}, U), 200)
check("contacts not stored while sync flag is off", sql("select contacts_data is null and gallery_data is null from users where mobile_number='9000000001'") == "1")

print("=== loan and EMI")
expect("bad token GET loan", call("GET", "/api/loan/applications", tok="bad"), 401)
expect("bad token GET emi", call("GET", "/api/emi/summary", tok="bad"), 401)
q = expect("quote", call("POST", "/api/v1/loan/quote", {"requested_amount": 10000, "tenure_months": 3}, U), 200)
check("quote adds up", float(q["total_payable"]) == 10000.0 and q["guarantors_required"] == 1, q)
expect("quote, bad amount", call("POST", "/api/v1/loan/quote", {"requested_amount": 12345, "tenure_months": 3}, U), 400)
C = expect("apply to cancel", apply_loan(U), 201)["loan_application"]["id"]
expect("cancel", call("POST", f"/api/v1/loan/applications/{C}/cancel", {}, U), 200)
res = call("POST", f"/api/v1/loan/applications/{C}/cancel", {}, U)
check("cancel twice is 409", res[0] == 409 and res[1]["error_code"] == "LOAN_NOT_PENDING", res)
expect("cancel someone else's loan", call("POST", f"/api/v1/loan/applications/{C}/cancel", {}, U2), 404)
L = expect("apply", apply_loan(U), 201)["loan_application"]["id"]
expect("second while pending", apply_loan(U), 400)
res = call("POST", f"/api/admin/loan/applications/{L}/approve", {}, A)
check("approve blocked until KYC verified", res[0] == 409 and res[1]["error_code"] == "KYC_NOT_VERIFIED", res)
expect("low admin may review too, but reason needed to reject", call("POST", f"/api/admin/users/{sql("select id from users where mobile_number='9000000001'")}/kyc", {"action": "reject"}, LOW), 400)
MEID = sql("select id from users where mobile_number='9000000001'")
expect("reject KYC", call("POST", f"/api/admin/users/{MEID}/kyc", {"action": "reject", "reason": "Blurred PAN"}, A), 200)
user = call("GET", "/api/v1/user/me", tok=U)[1]["data"]["user"]
check("rejection reason visible", user["kyc_rejection_reason"] == "Blurred PAN" and user["kyc_status"] == "rejected", user["kyc_status"])
expect("resubmit KYC", call("POST", "/api/user/onboarding", STEP3, U), 200)
expect("verify KYC", call("POST", f"/api/admin/users/{MEID}/kyc", {"action": "verify"}, A), 200)
user = call("GET", "/api/v1/user/me", tok=U)[1]["data"]["user"]
check("KYC and bank verified", user["kyc_status"] == "verified" and user["bank_verification_status"] == "verified", user)
expect("low admin may approve", call("POST", "/api/admin/loan/applications/999/approve", {}, LOW), 404)
expect("approve", call("POST", f"/api/admin/loan/applications/{L}/approve", {"admin_note": "ok"}, A), 200)
expect("approve twice", call("POST", f"/api/admin/loan/applications/{L}/approve", {}, A), 400)
expect("reject after approve", call("POST", f"/api/admin/loan/applications/{L}/reject", {}, A), 400)
check("loan stays approved", sql(f"select status from loan_applications where id={L}") == "approved")
rows = expect("schedule", call("GET", "/api/emi/schedule", tok=U), 200)["installments"]
total = round(sum(float(r["amount_due"]) for r in rows), 2)
check("EMIs add up to the loan", total == 10000.0, total)
expect("new loan while EMIs unpaid", apply_loan(U), 400)
home = call("GET", "/api/dashboard/home", tok=U)[1]["data"]
check("dashboard says cannot apply", home["can_apply_loan"] is False)

E = sorted(rows, key=lambda r: r["installment_number"])
SHOT = "https://cdn.example.com/p.png"
res = call("POST", "/api/v1/emi/payments/submit", {"emi_schedule_id": E[0]["id"], "screenshot_url": SHOT}, U)
check("no EMI payment before disbursal", res[0] == 409 and res[1]["error_code"] == "LOAN_NOT_DISBURSED", res)
summ = call("GET", "/api/v1/emi/summary", tok=U)[1]["data"]
check("summary says cannot pay yet", summ["can_pay"] is False and summ["loan_status"] == "approved", summ)
expect("disburse needs a reference", call("POST", f"/api/admin/loan/applications/{L}/disburse", {}, A), 400)
expect("disburse", call("POST", f"/api/admin/loan/applications/{L}/disburse", {"disbursement_reference": "UTR1"}, A), 200)
expect("disburse twice", call("POST", f"/api/admin/loan/applications/{L}/disburse", {"disbursement_reference": "UTR1"}, A), 400)
summ = call("GET", "/api/v1/emi/summary", tok=U)[1]["data"]
check("summary after disbursal", summ["can_pay"] is True and float(summ["outstanding_amount"]) == 10000.0, summ)
check("first EMI one month after disbursal", sql(f"select due_date = DATE_ADD(CURDATE(), INTERVAL 1 MONTH) from loan_emi_schedules where id={E[0]['id']}") == "1")


def pay(emi, **extra):
    return call("POST", "/api/emi/payments/submit", dict({"emi_schedule_id": emi["id"], "screenshot_url": SHOT}, **extra), U)


expect("javascript url refused", pay(E[0], screenshot_url="javascript:alert(1)"), 400)
expect("underpayment refused", pay(E[0], amount_claimed="1"), 400)
expect("negative refused", pay(E[0], amount_claimed="-500"), 400)
P = expect("valid payment", pay(E[0]), 201)["payment_submission_id"]
expect("duplicate pending", pay(E[0]), 400)
expect("reject payment", call("POST", f"/api/admin/emi/payments/{P}/reject", {"admin_note": "blur"}, A), 200)
check("EMI back to pending", sql(f"select status from loan_emi_schedules where id={E[0]['id']}") == "pending")
P = expect("pay again", pay(E[0]), 201)["payment_submission_id"]
expect("approve payment", call("POST", f"/api/admin/emi/payments/{P}/approve", {}, A), 200)
expect("reject approved payment", call("POST", f"/api/admin/emi/payments/{P}/reject", {}, A), 400)
check("EMI stays paid", sql(f"select status from loan_emi_schedules where id={E[0]['id']}") == "paid")
sql("insert into emi_payment_submissions (emi_schedule_id,user_id,loan_application_id,amount_claimed,screenshot_url) "
    f"select id,user_id,loan_application_id,1,'{SHOT}' from loan_emi_schedules where id={E[1]['id']}")
legacy = sql(f"select id from emi_payment_submissions where emi_schedule_id={E[1]['id']}")
expect("old underpaid claim cannot be approved", call("POST", f"/api/admin/emi/payments/{legacy}/approve", {}, A), 400)
expect("reject old claim", call("POST", f"/api/admin/emi/payments/{legacy}/reject", {}, A), 200)

print("=== reminders")
secret = sys.argv[5] if len(sys.argv) > 5 else ""
sql(f"update loan_emi_schedules set due_date=CURDATE() - INTERVAL 5 DAY, status='pending' where id={E[2]['id']}")
sql(f"update loan_emi_schedules set due_date=CURDATE() + INTERVAL 40 DAY where id={E[1]['id']}")
data = expect("cron", call("POST", "/api/internal/emi-reminders", {}, hdr={"X-Cron-Secret": secret}), 200)
check("overdue marked", data["overdue_marked"] == 1, data)
check("overdue EMI reminded", data["reminders_sent"] == 1, data)
check("status overdue", sql(f"select status from loan_emi_schedules where id={E[2]['id']}") == "overdue")
data = expect("cron again", call("POST", "/api/internal/emi-reminders", {}, hdr={"X-Cron-Secret": secret}), 200)
check("no second reminder right away", data["reminders_sent"] == 0, data)
sql(f"update loan_emi_schedules set last_reminder_at=NOW() - INTERVAL 61 MINUTE where id={E[2]['id']}")
data = expect("cron an hour later", call("POST", "/api/internal/emi-reminders", {}, hdr={"X-Cron-Secret": secret}), 200)
check("still no reminder after 1 hour", data["reminders_sent"] == 0, data)
sql(f"update loan_emi_schedules set last_reminder_at=NOW() - INTERVAL 25 HOUR where id={E[2]['id']}")
data = expect("cron a day later", call("POST", "/api/internal/emi-reminders", {}, hdr={"X-Cron-Secret": secret}), 200)
check("reminder after a day", data["reminders_sent"] == 1, data)
P3 = expect("pay overdue EMI", pay(E[2]), 201)["payment_submission_id"]
expect("reject it", call("POST", f"/api/admin/emi/payments/{P3}/reject", {}, A), 200)
check("rejected overdue EMI goes back to overdue", sql(f"select status from loan_emi_schedules where id={E[2]['id']}") == "overdue")

print("=== after all EMIs are paid a new loan is allowed")
for emi in (E[1], E[2]):
    pid = expect("pay", pay(emi), 201)["payment_submission_id"]
    expect("approve", call("POST", f"/api/admin/emi/payments/{pid}/approve", {}, A), 200)
check("loan closed after last EMI", sql(f"select status from loan_applications where id={L}") == "closed")
hist = expect("schedule of a closed loan", call("GET", f"/api/v1/emi/schedule?loan_id={L}", tok=U), 200)
check("closed loan history", hist["loan_status"] == "closed" and len(hist["installments"]) == 3, hist["loan_status"])
expect("someone else's schedule", call("GET", f"/api/v1/emi/schedule?loan_id={L}", tok=U2), 404)
expect("new loan after full repayment", apply_loan(U), 201)
data = call("GET", "/api/v1/notifications?limit=100", tok=U)[1]["data"]
cats = {n["category"] for n in data["notifications"]}
check("loan, emi and kyc notifications saved", {"loan_status", "emi_payment", "emi_reminder", "kyc"} <= cats, cats)
check("payload is an object", isinstance(next(n for n in data["notifications"] if n["payload"])["payload"], dict))
check("unread count", data["unread_count"] > 0)
expect("mark all read", call("POST", "/api/v1/notifications/read-all", {}, U), 200)
check("unread now zero", call("GET", "/api/v1/notifications", tok=U)[1]["data"]["unread_count"] == 0)

print("=== files")
bd = "FILEBOUNDARY"


def upload(tok, purpose, content, name="a.png"):
    body = (f'--{bd}\r\nContent-Disposition: form-data; name="purpose"\r\n\r\n{purpose}\r\n'
            f'--{bd}\r\nContent-Disposition: form-data; name="file"; filename="{name}"\r\nContent-Type: image/png\r\n\r\n').encode() + content + f"\r\n--{bd}--\r\n".encode()
    return call("POST", "/api/v1/files", raw=body, tok=tok, ct=f"multipart/form-data; boundary={bd}")


PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 32
up = expect("upload proof", upload(U, "payment_proof", PNG), 201)
expect("html named .png refused", upload(U, "payment_proof", b"<html><script>alert(1)</script></html>"), 400)
expect("pdf refused for payment proof", upload(U, "payment_proof", b"%PDF-1.4 xxxxxxxx"), 400)
expect("pdf accepted for aadhaar", upload(U, "aadhaar", b"%PDF-1.4 xxxxxxxx"), 201)
expect("bad purpose", upload(U, "virus", PNG), 400)
expect("upload without login", upload(None, "payment_proof", PNG), 401)


def fetch(tok):
    req = urllib.request.Request(BASE + "/api/v1/files/" + up["file_id"], headers={"Authorization": "Bearer " + tok} if tok else {})
    try:
        return urllib.request.urlopen(req).status
    except urllib.error.HTTPError as e:
        return e.code


check("owner can read file", fetch(U) == 200)
check("admin can read file", fetch(A) == 200)
check("other user gets 404", fetch(U2) == 404, fetch(U2))
check("anonymous gets 401", fetch(None) == 401)

print("=== push (real call to Firebase with a fake token; nothing can be delivered)")
expect("device needs a token", call("POST", "/api/v1/user/devices", {"platform": "android"}, U), 400)
expect("bad platform", call("POST", "/api/v1/user/devices", {"fcm_token": "x", "platform": "nokia"}, U), 400)
expect("register device", call("POST", "/api/v1/user/devices", {"fcm_token": "fake-token-regression", "platform": "android", "device_id": "d1"}, U), 200)
expect("register again is fine", call("POST", "/api/v1/user/devices", {"fcm_token": "fake-token-regression", "platform": "android"}, U), 200)
check("one row per token", sql("select count(*) from user_devices") == "1")
expect("push off", call("POST", "/api/v1/user/notification-settings", {"enabled": False}, U), 200)
expect("admin message with push off", call("POST", "/api/admin/notifications/send", {"user_id": MEID, "title": "T", "body": "B"}, A), 200)
import time
time.sleep(4)
check("push off: Firebase not called, token kept", sql("select count(*) from user_devices") == "1")
expect("push on", call("POST", "/api/v1/user/notification-settings", {"enabled": True}, U), 200)
expect("admin message", call("POST", "/api/admin/notifications/send", {"user_id": MEID, "title": "T", "body": "B"}, A), 200)
for _ in range(20):
    time.sleep(1)
    if sql("select count(*) from user_devices") == "0":
        break
check("Firebase accepted our login and rejected the fake token, so it was removed", sql("select count(*) from user_devices") == "0")
expect("register for logout", call("POST", "/api/v1/user/devices", {"fcm_token": "fake-token-2", "platform": "ios"}, U), 200)
expect("remove device", call("POST", "/api/v1/user/devices/remove", {"fcm_token": "fake-token-2"}, U), 200)
check("device removed", sql("select count(*) from user_devices") == "0")

print("=== logout and account delete")
sess = None
sess = call("POST", "/api/v1/auth/login", {"email": "t.user@test.local", "password": "UserPass123"})[1]["data"]
expect("logout", call("POST", "/api/v1/auth/logout", {"refresh_token": sess["refresh_token"]}, sess["access_token"]), 200)
expect("refresh dead after logout", call("POST", "/api/v1/auth/token/refresh", {"refresh_token": sess["refresh_token"]}), 401)
expect("logout everywhere", call("POST", "/api/v1/auth/logout", {"all_devices": True}, sess["access_token"]), 200)
expect("access token dead after logout everywhere", call("GET", "/api/v1/user/me", tok=sess["access_token"]), 401)
U = call("POST", "/api/v1/auth/login", {"email": "t.user@test.local", "password": "UserPass123"})[1]["data"]["access_token"]
res = call("POST", "/api/v1/user/delete", {"confirm": True}, U)
check("cannot delete with an open loan", res[0] == 409 and res[1]["error_code"] == "OPEN_LOAN", res)

print("=== settings")
expect("save EMI settings", call("POST", "/api/admin/settings/emi-collection", {"payee_name": "Narainsons", "upi_id": "real@upi"}, A), 200)
expect("low admin cannot", call("POST", "/api/admin/settings/emi-collection", {"payee_name": "X"}, LOW), 403)
pm = call("GET", "/api/v1/emi/payment-instructions", tok=U)[1]["data"]["payment_methods"]
check("app sees new settings", pm["upi_id"] == "real@upi", pm)

print("=== roles and errors")
expect("db bad table is 400, not 401", call("GET", "/api/admin/database/mysql_users", tok=A), 400)
expect("bad id is 400, not 401", call("GET", "/api/admin/users/abc", tok=A), 400)
expect("missing user is 404", call("GET", "/api/admin/users/9999", tok=A), 404)
expect("no auth is 401", call("GET", "/api/admin/dashboard"), 401)
expect("low admin cannot read admins", call("GET", "/api/admin/database/admins", tok=LOW), 403)
tables = expect("low admin browse all", call("GET", "/api/admin/database?limit=1", tok=LOW), 200)["tables"]
check("admins table hidden from low admin", "admins" not in tables)
uid = sql("select id from users where mobile_number='9000000004'")
expect("low admin cannot change user status", call("POST", f"/api/admin/users/{uid}/status", {"active": False}, LOW), 403)
expect("low admin cannot delete user", call("POST", f"/api/admin/users/{uid}/delete", {}, LOW), 403)
stats = call("GET", "/api/admin/dashboard", tok=A)[1]["data"]["stats"]
check("active_admins is real", stats["active_admins"] == 2, stats)
row = call("GET", "/api/admin/database/users?limit=1", tok=A)[1]["data"]["rows"][0]
check("contacts redacted in browse", row["contacts_data"] in (None, "[redacted]"), row["contacts_data"])

print("=== delete and block")
expect("block", call("POST", f"/api/admin/users/{uid}/status", {"active": False}, A), 200)
expect("blocked user gets 403", call("GET", "/api/user/me", tok=U2), 403)
expect("delete", call("POST", f"/api/admin/users/{uid}/delete", {}, A), 200)
expect("deleted user's session", call("GET", "/api/user/me", tok=U2), 401)
expect("deleted user's mobile is a clean 403", call("POST", "/api/auth/otp/send", {"mobile_number": "9000000004"}), 403)

print("=== uploads, banners, CORS")
bd = "XBOUNDARY"
mp = (f'--{bd}\r\nContent-Disposition: form-data; name="image"; filename="a.png"\r\nContent-Type: image/png\r\n\r\n').encode() + b"\x89PNG0000" + f"\r\n--{bd}--\r\n".encode()
url = expect("banner upload", call("POST", "/api/admin/banners/upload-image", raw=mp, tok=A, ct=f"multipart/form-data; boundary={bd}"), 200)["image_url"]
expect("banner with uploaded url", call("POST", "/api/admin/banners", {"title": "T", "image_url": url}, A), 201)
expect("banner javascript url", call("POST", "/api/admin/banners", {"title": "T", "image_url": "javascript:alert(1)"}, A), 400)
expect("photo upload", call("POST", "/api/admin/auth/profile/photo", raw=mp.replace(b'name="image"', b'name="photo"'), tok=A, ct=f"multipart/form-data; boundary={bd}"), 200)


def cors(origin):
    req = urllib.request.Request(BASE + "/api/health", method="OPTIONS", headers={"Origin": origin, "Access-Control-Request-Method": "GET"})
    return urllib.request.urlopen(req).headers.get("Access-Control-Allow-Origin")


check("unknown origin gets no CORS", cors("http://evil.example") is None)
check("configured origin gets CORS", cors("http://localhost:5500") == "http://localhost:5500")

print(f"\n{'ALL PASSED' if not failures else str(len(failures)) + ' FAILED: ' + ', '.join(failures)}")
sys.exit(1 if failures else 0)
