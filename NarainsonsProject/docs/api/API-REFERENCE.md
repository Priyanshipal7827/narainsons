# Narainsons API Reference

All 98 APIs of the project, with every request and response field. Every example is a real response recorded from a test server, with tokens masked. Interest of 12% and a 2% fee in the samples are example settings.

**Mobile app developers need Part 1.** Part 2 is what the admin panel uses; app tokens do not work there.

## Basics

| | |
|---|---|
| App base URL | `http://<host>:8082/NarainsonsProject/api/v1` (paths in Part 1 are relative to this) |
| Staff base URL | `http://<host>:8082/NarainsonsProject` (paths in Part 2 are full) |
| Format | JSON, unless an API says multipart |
| Sign-in | `Authorization: Bearer <access_token>` |
| Dates | `yyyy-MM-dd` and `yyyy-MM-ddTHH:mm:ss`, India time. List APIs show `yyyy-MM-dd HH:mm`. |
| Field names | snake_case, exactly as shown. |
| Older paths | `/api/auth`, `/api/user` and the like still work; use `/api/v1`. |

Every JSON response has this shape:

```json
{ "success": true, "message": "Text for the user", "data": { }, "error_code": null }
```

On failure `success` is false, `data` is null and `error_code` is set. Base your logic on `error_code`, because `message` text can change.

## App flow

```text
Splash
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
             The last EMI paid closes the loan.
```

## Tokens

- The **access token** is a JWT and lasts 60 minutes. The **refresh token** lasts 30 days and works once.
- When an API returns `TOKEN_EXPIRED`: call refresh, save both new tokens, retry the call once.
- If refresh returns 401, delete the tokens and go to sign-in.
- Store tokens in Android Keystore or iOS Keychain, never in plain preferences.

## Push notifications

Push uses Firebase Cloud Messaging. The app must use the `google-services.json` or `GoogleService-Info.plist` of the **same Firebase project** as the server.

1. Get the token from the Firebase SDK and send it to `POST /user/devices` after sign-in and on every token refresh.
2. Create an Android notification channel with id `narainsons_default`.
3. Ask for notification permission (Android 13+ and iOS).
4. Read `data` to open the right screen.

**Sent on:** KYC verified or rejected, loan approved, rejected, disbursed or closed, EMI proof received, approved or rejected, EMI reminders, messages sent by staff.

| `data` key | Meaning |
|---|---|
| `notification_id` | Id in `GET /notifications`. |
| `category` | `loan_status`, `emi_reminder`, `emi_payment`, `kyc` or `general`. Decides which screen to open. |
| `loan_application_id, status` | With loan pushes. |
| `emi_schedule_id, due_date, amount_due, type` | With EMI reminders. |
| `emi_payment_id, remaining_installments, status` | With EMI payment pushes. |
| `kyc_status` | With KYC pushes. |

All values in `data` are strings.

## Status values

| Field | Values |
|---|---|
| kyc_status | `pending`, `submitted`, `verified`, `rejected` |
| account_status | `active`, `inactive`, `blocked`, `suspended` |
| bank_verification_status | `pending`, `verified`, `failed` |
| Loan `status` | `pending`, `approved`, `disbursed`, `closed`, `rejected`, `cancelled` |
| EMI `status` | `pending`, `awaiting_proof`, `overdue`, `paid` |
| Payment `status` | `pending`, `approved`, `rejected` |
| Notification `category` | `loan_status`, `emi_reminder`, `emi_payment`, `kyc`, `general` |
| Staff alert `type` | `new_user`, `new_loan`, `kyc_submitted`, `emi_proof` |

## Error codes

| HTTP | error_code | What to do |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bad input. Show `message`. |
| 401 | `TOKEN_EXPIRED` | Access token expired. Refresh and retry once. |
| 401 | `INVALID_TOKEN` | Token is bad or was ended, for example by a password change. Try refresh, else sign in. |
| 401 | `INVALID_REFRESH_TOKEN` | Sign in again. |
| 401 | `UNAUTHORIZED` | No token sent. |
| 403 | `FORBIDDEN` | Account blocked or closed, or no permission. Show `message`, stop retrying. |
| 404 | `NOT_FOUND` | Not found, or not yours. |
| 409 | `CONFLICT` | Duplicate value, for example an email already in use. |
| 409 | `KYC_REJECTED` | Send onboarding step 3 again before applying. |
| 409 | `KYC_NOT_VERIFIED` | Staff only: verify KYC before approving. |
| 409 | `LOAN_NOT_PENDING` | Only a pending application can be cancelled. |
| 409 | `LOAN_NOT_DISBURSED` | EMIs cannot be paid before disbursal. |
| 409 | `OPEN_LOAN` | Close the loan before deleting the account. |
| 413 | `PAYLOAD_TOO_LARGE` | JSON body over 2 MB. |
| 429 | `RATE_LIMITED` | Too many attempts, or account locked. Wait and retry. |
| 503 | `SERVICE_UNAVAILABLE` | OTP could not be sent, or the server is unhealthy. Retry shortly. |
| 500 | `SERVER_ERROR` | Show a generic error. Quote the `X-Request-Id` response header when reporting it. |

## All APIs

| Method | Path | Sign-in | Purpose |
|---|---|---|---|
| GET | `/health` | None | Health check |
| POST | `/auth/otp/send` | None | Send mobile OTP |
| POST | `/auth/otp/verify` | None | Verify mobile OTP |
| POST | `/auth/login` | None | Password login |
| POST | `/auth/token/refresh` | None | Refresh tokens |
| POST | `/auth/password/forgot/send` | None | Forgot password: send OTP |
| POST | `/auth/password/forgot/reset` | None | Forgot password: reset |
| POST | `/auth/email/send` | None | Send email OTP |
| POST | `/auth/email/verify` | None | Verify email OTP |
| POST | `/auth/logout` | Customer token | Logout |
| GET | `/user/me` | Customer token | Get my profile |
| POST | `/user/onboarding` | Customer token | Onboarding step 1: basic details |
| POST | `/user/onboarding` | Customer token | Onboarding step 2: address and work |
| POST | `/user/onboarding` | Customer token | Onboarding step 3: KYC, bank, consent |
| POST | `/user/sync` | Customer token | Sync device details |
| POST | `/user/password` | Customer token | Set or change password |
| POST | `/user/delete` | Customer token | Delete my account |
| POST | `/files` | Customer token | Upload a file |
| GET | `/files/{file_id}` | Customer token | Download a file |
| POST | `/user/devices` | Customer token | Register this phone for push |
| POST | `/user/devices/remove` | Customer token | Unregister this phone |
| POST | `/user/notification-settings` | Customer token | Turn push on or off |
| GET | `/dashboard/banners` | Customer token | Banners |
| GET | `/dashboard/loan-config` | Customer token | Loan rules |
| GET | `/dashboard/loan-apply-video` | Customer token | How-to-apply video |
| GET | `/dashboard/home` | Customer token | Home screen |
| POST | `/loan/quote` | Customer token | Get a quote |
| POST | `/loan/apply` | Customer token | Apply for a loan |
| POST | `/loan/applications/{id}/cancel` | Customer token | Cancel an application |
| GET | `/loan/applications` | Customer token | My applications |
| GET | `/loan/applications/{id}` | Customer token | One application |
| GET | `/emi/summary` | Customer token | EMI summary |
| GET | `/emi/schedule` | Customer token | EMI schedule |
| GET | `/emi/payment-instructions` | Customer token | How to pay |
| POST | `/emi/payments/submit` | Customer token | Submit an EMI payment |
| GET | `/emi/payments` | Customer token | My payments |
| GET | `/emi/payments/{id}` | Customer token | One payment |
| GET | `/notifications` | Customer token | List notifications |
| POST | `/notifications/read/{id}` | Customer token | Mark one read |
| POST | `/notifications/read` | Customer token | Mark one read (by body) |
| POST | `/notifications/read-all` | Customer token | Mark all read |
| GET | `/api/media/banners/{file_name}` | None | Get a banner image |
| POST | `/api/admin/auth/register` | None | Create the first admin |
| POST | `/api/admin/auth/login` | None | Staff login |
| GET | `/api/admin/auth/me` | Staff token | My staff profile |
| POST | `/api/admin/auth/profile` | Staff token | Update my staff profile |
| POST | `/api/admin/auth/profile/photo` | Staff token | Upload my staff photo |
| GET | `/api/admin/auth/profile/photo` | Staff token | Get my staff photo |
| POST | `/api/admin/auth/password/change` | Staff token | Change my staff password |
| POST | `/api/admin/auth/password/forgot/send` | None | Staff forgot password: send OTP |
| POST | `/api/admin/auth/password/forgot/reset` | None | Staff forgot password: reset |
| GET | `/api/admin/list/{name}` | Staff token | How lists work: example |
| GET | `/api/admin/list/{name}/export` | Staff token | Download a list |
| GET | `/api/admin/list/users` | Staff token | List: Customers |
| GET | `/api/admin/list/loans` | Staff token | List: Loans |
| GET | `/api/admin/list/emi_payments` | Staff token | List: EMI payments |
| GET | `/api/admin/list/emi_schedules` | Staff token | List: EMI schedule |
| GET | `/api/admin/list/guarantors` | Staff token | List: Guarantors |
| GET | `/api/admin/list/user_notifications` | Staff token | List: Customer notifications |
| GET | `/api/admin/list/banners` | Staff token | List: Banners |
| GET | `/api/admin/list/videos` | Staff token | List: Guide videos |
| GET | `/api/admin/list/emi_settings` | Staff token | List: Payment details history |
| GET | `/api/admin/list/admin_alerts` | Staff token | List: Staff alerts |
| GET | `/api/admin/list/admins` | Staff token | List: Staff accounts |
| GET | `/api/admin/dashboard/summary` | Staff token | Dashboard figures |
| GET | `/api/admin/alerts` | Staff token | Alerts for the bell |
| POST | `/api/admin/alerts/read` | Staff token | Mark all alerts read |
| POST | `/api/admin/users/{id}/kyc` | Staff token | Verify or reject KYC |
| GET | `/api/admin/users/{id}` | Staff token | One customer, all details |
| POST | `/api/admin/users/{id}/status` | Staff token | Turn a customer on or off |
| POST | `/api/admin/users/{id}/delete` | Staff token | Delete a customer |
| POST | `/api/admin/loan/applications/{id}/reject` | Staff token | Reject a loan |
| POST | `/api/admin/loan/applications/{id}/approve` | Staff token | Approve a loan |
| POST | `/api/admin/loan/applications/{id}/disburse` | Staff token | Disburse a loan |
| GET | `/api/admin/loan/applications/{id}` | Staff token | One loan with guarantors |
| POST | `/api/admin/emi/payments/{id}/reject` | Staff token | Reject an EMI payment |
| POST | `/api/admin/emi/payments/{id}/approve` | Staff token | Approve an EMI payment |
| POST | `/api/admin/banners/upload-image` | Staff token | Upload a banner image |
| POST | `/api/admin/banners` | Staff token | Create a banner |
| GET | `/api/admin/banners/{id}` | Staff token | One banner |
| POST | `/api/admin/banners/{id}/update` | Staff token | Update a banner |
| POST | `/api/admin/banners/{id}/delete` | Staff token | Delete a banner |
| POST | `/api/admin/videos` | Staff token | Add a guide video |
| POST | `/api/admin/videos/{id}/update` | Staff token | Update a guide video |
| POST | `/api/admin/videos/{id}/delete` | Staff token | Delete a guide video |
| GET | `/api/admin/settings/emi-collection` | Staff token | Payment details: read |
| POST | `/api/admin/settings/emi-collection` | Staff token | Payment details: save |
| POST | `/api/admin/notifications/send` | Staff token | Send a message to customers |
| POST | `/api/admin/auth/register` | Staff token | Add a staff account |
| POST | `/api/admin/admins/{id}/status` | Staff token | Turn a staff account on or off |
| GET | `/api/admin/dashboard` | Staff token | Dashboard (older) |
| GET | `/api/admin/users` | Staff token | Customers (older) |
| GET | `/api/admin/loan/applications` | Staff token | Loans (older) |
| GET | `/api/admin/emi/payments` | Staff token | EMI payments (older) |
| GET | `/api/admin/banners` | Staff token | Banners (older) |
| GET | `/api/admin/database` | Staff token | All tables (older) |
| GET | `/api/admin/database/{table}` | Staff token | One table (older) |
| POST | `/api/internal/emi-reminders` | Cron secret | Run EMI reminders |

# Part 1: Mobile app APIs

## System

Check the server is up.

### Health check

`GET /health`  
Sign-in: None

200 when the app and database are fine, otherwise 503 with a reason. Also at `/api/health`.

Response example (200):

```json
{
  "success": true,
  "message": "Application running",
  "data": null,
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 503 | Database down, schema out of date, or `jwt.secret` missing |

## Sign-in and tokens

Mobile OTP, email OTP or password. All return the same token block.

### Send mobile OTP

`POST /auth/otp/send`  
Sign-in: None

Creates the account on first use. Limit: 5 per number per 15 minutes, 20 per IP per hour. `dev_otp` and `sms_provider_response` appear only when the server has `app.dev.expose_otp=true`, never in production.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `mobile_number` | string | Yes | 9876543210, +91 98765 43210 or 09876543210 are all accepted. |

Request example:

```json
{
  "mobile_number": "9000000001"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `sms_sent` | boolean | `true` if the SMS provider accepted the message. |
| `dev_otp` | string | The OTP. Only when the server has `app.dev.expose_otp=true`; never in production. |
| `sms_provider_response` | string | Provider's reply when the SMS failed. Only with `app.dev.expose_otp=true`. |
| `mobile_number` | string | The 10-digit mobile number used. |
| `expires_in_minutes` | number | Minutes the OTP stays valid. |

Response example (200):

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "sms_sent": false,
    "dev_otp": "123456",
    "sms_provider_response": "missing_api_key",
    "mobile_number": "9000000001",
    "expires_in_minutes": 10
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Bad mobile number |
| 403 | This account was closed |
| 429 | Rate limited |
| 503 | The SMS could not be sent |

### Verify mobile OTP

`POST /auth/otp/verify`  
Sign-in: None

Returns the token block.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `mobile_number` | string | Yes |  |
| `otp` | string | Yes | 6 digits, valid 10 minutes, works once. 5 wrong tries cancel it. |

Request example:

```json
{
  "mobile_number": "9000000001",
  "otp": "123456"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `access_token` | string | JWT. Send as `Authorization: Bearer <token>`. |
| `refresh_token` | string | Keep in secure storage. Used once to get new tokens. |
| `needs_registration` | boolean | `true` means show onboarding. |
| `onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `refresh_expires_in` | number | Seconds until the refresh token expires. |
| `next_action` | string | `onboarding_step_1`, `onboarding_step_2`, `onboarding_step_3` or `home`. |
| `token_type` | string | Always `Bearer`. |
| `expires_in` | number | Seconds until the access token expires. |
| `profile_complete` | boolean | `true` means go to the home screen. |

Response example (200):

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9.<payload>.<signature>",
    "refresh_token": "<opaque refresh token>",
    "needs_registration": true,
    "onboarding_step": 0,
    "refresh_expires_in": 2592000,
    "next_action": "onboarding_step_1",
    "token_type": "Bearer",
    "expires_in": 3600,
    "profile_complete": false
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Wrong or expired OTP |
| 403 | Account not active |

### Password login

`POST /auth/login`  
Sign-in: None

5 wrong passwords lock the account for 15 minutes. Limit: 60 sign-in calls per IP per 15 minutes.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `email` | string | Yes |  |
| `password` | string | Yes |  |

Request example:

```json
{
  "email": "asha.verma@test.local",
  "password": "AshaPass123"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `access_token` | string | JWT. Send as `Authorization: Bearer <token>`. |
| `refresh_token` | string | Keep in secure storage. Used once to get new tokens. |
| `needs_registration` | boolean | `true` means show onboarding. |
| `onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `refresh_expires_in` | number | Seconds until the refresh token expires. |
| `next_action` | string | `onboarding_step_1`, `onboarding_step_2`, `onboarding_step_3` or `home`. |
| `token_type` | string | Always `Bearer`. |
| `expires_in` | number | Seconds until the access token expires. |
| `profile_complete` | boolean | `true` means go to the home screen. |

Response example (200):

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9.<payload>.<signature>",
    "refresh_token": "<opaque refresh token>",
    "needs_registration": false,
    "onboarding_step": 3,
    "refresh_expires_in": 2592000,
    "next_action": "home",
    "token_type": "Bearer",
    "expires_in": 3600,
    "profile_complete": true
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Wrong email or password |
| 403 | Account not active |
| 429 | Locked or rate limited |

### Refresh tokens

`POST /auth/token/refresh`  
Sign-in: None

Call when an API returns `TOKEN_EXPIRED`. Each refresh token works once: always save the new one. Reusing an old one ends that login. Do not run two refreshes at once.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `refresh_token` | string | Yes |  |

Request example:

```json
{
  "refresh_token": "<refresh token>"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `access_token` | string | JWT. Send as `Authorization: Bearer <token>`. |
| `refresh_token` | string | Keep in secure storage. Used once to get new tokens. |
| `refresh_expires_in` | number | Seconds until the refresh token expires. |
| `token_type` | string | Always `Bearer`. |
| `expires_in` | number | Seconds until the access token expires. |

Response example (200):

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9.<payload>.<signature>",
    "refresh_token": "<opaque refresh token>",
    "refresh_expires_in": 2592000,
    "token_type": "Bearer",
    "expires_in": 3600
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Field missing |
| 401 | `INVALID_REFRESH_TOKEN`: send the user to sign-in |
| 403 | Account not active |

### Forgot password: send OTP

`POST /auth/password/forgot/send`  
Sign-in: None

Same response whether or not the email exists.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `email` | string | Yes |  |

Request example:

```json
{
  "email": "asha.verma@test.local"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `dev_otp` | string | The OTP. Only when the server has `app.dev.expose_otp=true`; never in production. |
| `message` | string | Text to show the user. |
| `email` | string | The email address used. |
| `expires_in_minutes` | number | Minutes the OTP stays valid. |

Response example (200):

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "dev_otp": "123456",
    "message": "Agar yeh email registered hai to OTP bhej diya gaya.",
    "email": "asha.verma@test.local",
    "expires_in_minutes": 10
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Bad email |
| 429 | Rate limited |

### Forgot password: reset

`POST /auth/password/forgot/reset`  
Sign-in: None

Ends all logins, so send the user to sign-in.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `email` | string | Yes |  |
| `otp` | string | Yes |  |
| `new_password` | string | Yes | At least 8 characters. |

Request example:

```json
{
  "email": "asha.verma@test.local",
  "otp": "123456",
  "new_password": "AshaPass456"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `password_updated` | boolean | `true` when the password was changed. |
| `email` | string | The email address used. |

Response example (200):

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "password_updated": true,
    "email": "asha.verma@test.local"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Wrong OTP or weak password |

### Send email OTP

`POST /auth/email/send`  
Sign-in: None

Same as mobile OTP, using email. Creates the account on first use.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `email` | string | Yes |  |

Request example:

```json
{
  "email": "second.user@test.local"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `dev_otp` | string | The OTP. Only when the server has `app.dev.expose_otp=true`; never in production. |
| `email` | string | The email address used. |
| `expires_in_minutes` | number | Minutes the OTP stays valid. |

Response example (200):

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "dev_otp": "123456",
    "email": "second.user@test.local",
    "expires_in_minutes": 10
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Bad email |
| 403 | Account closed |
| 429 | Rate limited |
| 503 | The email could not be sent |

### Verify email OTP

`POST /auth/email/verify`  
Sign-in: None

Returns the token block.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `email` | string | Yes |  |
| `otp` | string | Yes |  |

Request example:

```json
{
  "email": "second.user@test.local",
  "otp": "123456"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `access_token` | string | JWT. Send as `Authorization: Bearer <token>`. |
| `refresh_token` | string | Keep in secure storage. Used once to get new tokens. |
| `needs_registration` | boolean | `true` means show onboarding. |
| `onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `refresh_expires_in` | number | Seconds until the refresh token expires. |
| `next_action` | string | `onboarding_step_1`, `onboarding_step_2`, `onboarding_step_3` or `home`. |
| `token_type` | string | Always `Bearer`. |
| `expires_in` | number | Seconds until the access token expires. |
| `profile_complete` | boolean | `true` means go to the home screen. |

Response example (200):

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9.<payload>.<signature>",
    "refresh_token": "<opaque refresh token>",
    "needs_registration": true,
    "onboarding_step": 0,
    "refresh_expires_in": 2592000,
    "next_action": "onboarding_step_1",
    "token_type": "Bearer",
    "expires_in": 3600,
    "profile_complete": false
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Wrong or expired OTP |
| 403 | Account not active |

### Logout

`POST /auth/logout`  
Sign-in: Customer token

Delete the stored tokens either way.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `refresh_token` | string | No | Ends this login. |
| `fcm_token` | string | No | Stops push to this phone. |
| `all_devices` | boolean | No | `true` signs out everywhere. |

Request example:

```json
{
  "refresh_token": "<refresh token>",
  "fcm_token": "<token from Firebase SDK>",
  "all_devices": false
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `logged_out` | boolean | `true` when logout finished. |

Response example (200):

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "logged_out": true
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

## Profile and onboarding

Three onboarding steps in order, then the profile is complete.

### Get my profile

`GET /user/me`  
Sign-in: Customer token

Call on every app open to refresh status. Also accepts the token as an `X-Access-Token` header.

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `needs_registration` | boolean | `true` means show onboarding. |
| `onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user` | object | The customer. |
| `user.id` | number | Unique id of this record. |
| `user.customer_code` | string | Customer code, set at onboarding step 1. |
| `user.name` | string | Full name. |
| `user.first_name` | string | First name. |
| `user.last_name` | string | Last name. |
| `user.mobile_number` | string | 10-digit mobile number. |
| `user.alternate_mobile_number` | string | Second mobile number. |
| `user.email` | string | Email address. |
| `user.mobile_verified` | boolean | `true` once the mobile number passed an OTP. |
| `user.email_verified` | boolean | `true` once the email passed an OTP. |
| `user.has_password` | boolean | `true` if a password is set, so password login works. |
| `user.gender` | string | `Male`, `Female` or `Other`. |
| `user.date_of_birth` | string | Date of birth, `yyyy-MM-dd`. |
| `user.age` | number | Age in years, as sent by the app. |
| `user.marital_status` | string | `Single`, `Married`, `Divorced` or `Widowed`. |
| `user.nationality` | string | Nationality. |
| `user.profile_image` | string | Profile photo URL. |
| `user.kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `user.kyc_verified_at` | datetime | When staff verified KYC. |
| `user.kyc_rejection_reason` | string | Why staff rejected KYC. Show this to the customer. |
| `user.aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `user.pan_last4` | string | Last 4 characters of PAN. |
| `user.aadhaar_document` | string | URL of the uploaded Aadhaar. Open with a token. |
| `user.pan_document` | string | URL of the uploaded PAN. Open with a token. |
| `user.current_address` | string | Current address. |
| `user.current_city` | string | Current city. |
| `user.current_state` | string | Current state. |
| `user.current_pincode` | string | Current PIN code. |
| `user.current_country` | string | Current country. |
| `user.permanent_address` | string | Permanent address. |
| `user.permanent_city` | string | Permanent city. |
| `user.permanent_state` | string | Permanent state. |
| `user.permanent_pincode` | string | Permanent PIN code. |
| `user.permanent_country` | string | Permanent country. |
| `user.same_as_current_address` | boolean | `true` if permanent address equals current. |
| `user.employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `user.company_name` | string | Employer or business name. |
| `user.designation` | string | Job title. |
| `user.work_experience_months` | number | Work experience in months. |
| `user.monthly_income` | number | Monthly income in rupees. |
| `user.annual_income` | number | Annual income in rupees. |
| `user.income_source` | string | Where the income comes from. |
| `user.bank_name` | string | Bank name. |
| `user.bank_account_last4` | string | Last 4 digits of the bank account. |
| `user.ifsc_code` | string | Bank IFSC code. |
| `user.bank_verification_status` | string | `pending`, `verified` or `failed`. Only staff can change it. |
| `user.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `user.notification_enable` | boolean | `true` if push is on. |
| `user.onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user.privacy_consent` | boolean | Customer accepted the privacy policy. |
| `user.terms_accepted` | boolean | Customer accepted the terms. |
| `user.kyc_consent` | boolean | Customer agreed to KYC. |
| `user.last_login` | string | Last sign-in time. |
| `user.created_at` | string | When this record was created. |
| `profile_complete` | boolean | `true` means go to the home screen. |

Response example (200):

```json
{
  "success": true,
  "message": "Profile",
  "data": {
    "needs_registration": true,
    "onboarding_step": 0,
    "user": {
      "id": 1,
      "customer_code": null,
      "name": "New User",
      "first_name": null,
      "last_name": null,
      "mobile_number": "9000000001",
      "alternate_mobile_number": null,
      "email": null,
      "mobile_verified": true,
      "email_verified": false,
      "has_password": false,
      "gender": null,
      "date_of_birth": null,
      "age": null,
      "marital_status": null,
      "nationality": "Indian",
      "profile_image": null,
      "kyc_status": "pending",
      "kyc_verified_at": null,
      "kyc_rejection_reason": null,
      "aadhaar_last4": null,
      "pan_last4": null,
      "aadhaar_document": null,
      "pan_document": null,
      "current_address": null,
      "current_city": null,
      "current_state": null,
      "current_pincode": null,
      "current_country": "India",
      "permanent_address": null,
      "permanent_city": null,
      "permanent_state": null,
      "permanent_pincode": null,
      "permanent_country": "India",
      "same_as_current_address": null,
      "employment_type": null,
      "company_name": null,
      "designation": null,
      "work_experience_months": null,
      "monthly_income": null,
      "annual_income": null,
      "income_source": null,
      "bank_name": null,
      "bank_account_last4": null,
      "ifsc_code": null,
      "bank_verification_status": "pending",
      "account_status": "active",
      "notification_enable": null,
      "onboarding_step": 0,
      "privacy_consent": null,
      "terms_accepted": null,
      "kyc_consent": null,
      "last_login": "2026-11-05T16:18:01",
      "created_at": "2026-11-05T16:18:00"
    },
    "profile_complete": false
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

### Onboarding step 1: basic details

`POST /user/onboarding`  
Sign-in: Customer token

Steps must be done in order. An earlier step can be sent again to edit it.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `step` | number | Yes | 1 |
| `name` | string | Yes | Up to 150 characters. |
| `first_name` | string | Yes | Up to 80. |
| `last_name` | string | Yes | Up to 80. |
| `email` | string | Yes | Valid email, not used by another customer. Changing it clears email verification. |
| `gender` | string | Yes | `Male`, `Female` or `Other`. `m`, `f` and `o` also work. |
| `date_of_birth` | date | Yes | `yyyy-MM-dd`, not in the future. |
| `age` | number | No |  |
| `marital_status` | string | No | `Single`, `Married`, `Divorced` or `Widowed`. |
| `nationality` | string | No | Up to 80. Default `Indian`. |
| `profile_image` | string | No | `url` from the upload API. |
| `alternate_mobile_number` | string | No | 10-digit mobile. |

Request example:

```json
{
  "step": 1,
  "name": "Asha Verma",
  "first_name": "Asha",
  "last_name": "Verma",
  "email": "asha.verma@test.local",
  "gender": "Female",
  "date_of_birth": "1995-04-12",
  "age": 31,
  "marital_status": "Single",
  "nationality": "Indian",
  "alternate_mobile_number": "9000000002"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `needs_registration` | boolean | `true` means show onboarding. |
| `onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user` | object | The customer. |
| `user.id` | number | Unique id of this record. |
| `user.customer_code` | string | Customer code, set at onboarding step 1. |
| `user.name` | string | Full name. |
| `user.first_name` | string | First name. |
| `user.last_name` | string | Last name. |
| `user.mobile_number` | string | 10-digit mobile number. |
| `user.alternate_mobile_number` | string | Second mobile number. |
| `user.email` | string | Email address. |
| `user.mobile_verified` | boolean | `true` once the mobile number passed an OTP. |
| `user.email_verified` | boolean | `true` once the email passed an OTP. |
| `user.has_password` | boolean | `true` if a password is set, so password login works. |
| `user.gender` | string | `Male`, `Female` or `Other`. |
| `user.date_of_birth` | string | Date of birth, `yyyy-MM-dd`. |
| `user.age` | number | Age in years, as sent by the app. |
| `user.marital_status` | string | `Single`, `Married`, `Divorced` or `Widowed`. |
| `user.nationality` | string | Nationality. |
| `user.profile_image` | string | Profile photo URL. |
| `user.kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `user.kyc_verified_at` | datetime | When staff verified KYC. |
| `user.kyc_rejection_reason` | string | Why staff rejected KYC. Show this to the customer. |
| `user.aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `user.pan_last4` | string | Last 4 characters of PAN. |
| `user.aadhaar_document` | string | URL of the uploaded Aadhaar. Open with a token. |
| `user.pan_document` | string | URL of the uploaded PAN. Open with a token. |
| `user.current_address` | string | Current address. |
| `user.current_city` | string | Current city. |
| `user.current_state` | string | Current state. |
| `user.current_pincode` | string | Current PIN code. |
| `user.current_country` | string | Current country. |
| `user.permanent_address` | string | Permanent address. |
| `user.permanent_city` | string | Permanent city. |
| `user.permanent_state` | string | Permanent state. |
| `user.permanent_pincode` | string | Permanent PIN code. |
| `user.permanent_country` | string | Permanent country. |
| `user.same_as_current_address` | boolean | `true` if permanent address equals current. |
| `user.employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `user.company_name` | string | Employer or business name. |
| `user.designation` | string | Job title. |
| `user.work_experience_months` | number | Work experience in months. |
| `user.monthly_income` | number | Monthly income in rupees. |
| `user.annual_income` | number | Annual income in rupees. |
| `user.income_source` | string | Where the income comes from. |
| `user.bank_name` | string | Bank name. |
| `user.bank_account_last4` | string | Last 4 digits of the bank account. |
| `user.ifsc_code` | string | Bank IFSC code. |
| `user.bank_verification_status` | string | `pending`, `verified` or `failed`. Only staff can change it. |
| `user.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `user.notification_enable` | boolean | `true` if push is on. |
| `user.onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user.privacy_consent` | boolean | Customer accepted the privacy policy. |
| `user.terms_accepted` | boolean | Customer accepted the terms. |
| `user.kyc_consent` | boolean | Customer agreed to KYC. |
| `user.last_login` | string | Last sign-in time. |
| `user.created_at` | string | When this record was created. |
| `profile_complete` | boolean | `true` means go to the home screen. |

Response example (200):

```json
{
  "success": true,
  "message": "Step 1 saved",
  "data": {
    "needs_registration": true,
    "onboarding_step": 1,
    "user": {
      "id": 1,
      "customer_code": "CUST11793875682002",
      "name": "Asha Verma",
      "first_name": "Asha",
      "last_name": "Verma",
      "mobile_number": "9000000001",
      "alternate_mobile_number": "9000000002",
      "email": "asha.verma@test.local",
      "mobile_verified": true,
      "email_verified": false,
      "has_password": false,
      "gender": "Female",
      "date_of_birth": "1995-04-12",
      "age": 31,
      "marital_status": "Single",
      "nationality": "Indian",
      "profile_image": null,
      "kyc_status": "pending",
      "kyc_verified_at": null,
      "kyc_rejection_reason": null,
      "aadhaar_last4": null,
      "pan_last4": null,
      "aadhaar_document": null,
      "pan_document": null,
      "current_address": null,
      "current_city": null,
      "current_state": null,
      "current_pincode": null,
      "current_country": "India",
      "permanent_address": null,
      "permanent_city": null,
      "permanent_state": null,
      "permanent_pincode": null,
      "permanent_country": "India",
      "same_as_current_address": null,
      "employment_type": null,
      "company_name": null,
      "designation": null,
      "work_experience_months": null,
      "monthly_income": null,
      "annual_income": null,
      "income_source": null,
      "bank_name": null,
      "bank_account_last4": null,
      "ifsc_code": null,
      "bank_verification_status": "pending",
      "account_status": "active",
      "notification_enable": null,
      "onboarding_step": 1,
      "privacy_consent": null,
      "terms_accepted": null,
      "kyc_consent": null,
      "last_login": "2026-11-05T16:18:01",
      "created_at": "2026-11-05T16:18:00"
    },
    "profile_complete": false
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | Missing or invalid field, or an earlier step is not done |
| 409 | Email already in use |

### Onboarding step 2: address and work

`POST /user/onboarding`  
Sign-in: Customer token

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `step` | number | Yes | 2 |
| `current_address` | string | Yes |  |
| `current_city` | string | Yes | Up to 100. |
| `current_state` | string | Yes | Up to 100. |
| `current_pincode` | string | Yes | 6 digits. |
| `current_country` | string | No | Default `India`. |
| `permanent_address` | string | No |  |
| `permanent_city` | string | No |  |
| `permanent_state` | string | No |  |
| `permanent_pincode` | string | No |  |
| `permanent_country` | string | No |  |
| `same_as_current_address` | boolean | No |  |
| `employment_type` | string | Yes | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `company_name` | string | No | Up to 200. |
| `designation` | string | No | Up to 150. |
| `work_experience_months` | number | No |  |
| `monthly_income` | number | Yes | 0 or more. |
| `annual_income` | number | No |  |
| `income_source` | string | No | Up to 150. |

Request example:

```json
{
  "step": 2,
  "current_address": "12 MG Road",
  "current_city": "Jaipur",
  "current_state": "Rajasthan",
  "current_pincode": "302001",
  "current_country": "India",
  "permanent_address": "12 MG Road",
  "permanent_city": "Jaipur",
  "permanent_state": "Rajasthan",
  "permanent_pincode": "302001",
  "permanent_country": "India",
  "same_as_current_address": true,
  "employment_type": "Salaried",
  "company_name": "Acme Pvt Ltd",
  "designation": "Accountant",
  "work_experience_months": 48,
  "monthly_income": 35000,
  "annual_income": 420000,
  "income_source": "Salary"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `needs_registration` | boolean | `true` means show onboarding. |
| `onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user` | object | The customer. |
| `user.id` | number | Unique id of this record. |
| `user.customer_code` | string | Customer code, set at onboarding step 1. |
| `user.name` | string | Full name. |
| `user.first_name` | string | First name. |
| `user.last_name` | string | Last name. |
| `user.mobile_number` | string | 10-digit mobile number. |
| `user.alternate_mobile_number` | string | Second mobile number. |
| `user.email` | string | Email address. |
| `user.mobile_verified` | boolean | `true` once the mobile number passed an OTP. |
| `user.email_verified` | boolean | `true` once the email passed an OTP. |
| `user.has_password` | boolean | `true` if a password is set, so password login works. |
| `user.gender` | string | `Male`, `Female` or `Other`. |
| `user.date_of_birth` | string | Date of birth, `yyyy-MM-dd`. |
| `user.age` | number | Age in years, as sent by the app. |
| `user.marital_status` | string | `Single`, `Married`, `Divorced` or `Widowed`. |
| `user.nationality` | string | Nationality. |
| `user.profile_image` | string | Profile photo URL. |
| `user.kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `user.kyc_verified_at` | datetime | When staff verified KYC. |
| `user.kyc_rejection_reason` | string | Why staff rejected KYC. Show this to the customer. |
| `user.aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `user.pan_last4` | string | Last 4 characters of PAN. |
| `user.aadhaar_document` | string | URL of the uploaded Aadhaar. Open with a token. |
| `user.pan_document` | string | URL of the uploaded PAN. Open with a token. |
| `user.current_address` | string | Current address. |
| `user.current_city` | string | Current city. |
| `user.current_state` | string | Current state. |
| `user.current_pincode` | string | Current PIN code. |
| `user.current_country` | string | Current country. |
| `user.permanent_address` | string | Permanent address. |
| `user.permanent_city` | string | Permanent city. |
| `user.permanent_state` | string | Permanent state. |
| `user.permanent_pincode` | string | Permanent PIN code. |
| `user.permanent_country` | string | Permanent country. |
| `user.same_as_current_address` | boolean | `true` if permanent address equals current. |
| `user.employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `user.company_name` | string | Employer or business name. |
| `user.designation` | string | Job title. |
| `user.work_experience_months` | number | Work experience in months. |
| `user.monthly_income` | number | Monthly income in rupees. |
| `user.annual_income` | number | Annual income in rupees. |
| `user.income_source` | string | Where the income comes from. |
| `user.bank_name` | string | Bank name. |
| `user.bank_account_last4` | string | Last 4 digits of the bank account. |
| `user.ifsc_code` | string | Bank IFSC code. |
| `user.bank_verification_status` | string | `pending`, `verified` or `failed`. Only staff can change it. |
| `user.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `user.notification_enable` | boolean | `true` if push is on. |
| `user.onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user.privacy_consent` | boolean | Customer accepted the privacy policy. |
| `user.terms_accepted` | boolean | Customer accepted the terms. |
| `user.kyc_consent` | boolean | Customer agreed to KYC. |
| `user.last_login` | string | Last sign-in time. |
| `user.created_at` | string | When this record was created. |
| `profile_complete` | boolean | `true` means go to the home screen. |

Response example (200):

```json
{
  "success": true,
  "message": "Step 2 saved",
  "data": {
    "needs_registration": true,
    "onboarding_step": 2,
    "user": {
      "id": 1,
      "customer_code": "CUST11793875682002",
      "name": "Asha Verma",
      "first_name": "Asha",
      "last_name": "Verma",
      "mobile_number": "9000000001",
      "alternate_mobile_number": "9000000002",
      "email": "asha.verma@test.local",
      "mobile_verified": true,
      "email_verified": false,
      "has_password": false,
      "gender": "Female",
      "date_of_birth": "1995-04-12",
      "age": 31,
      "marital_status": "Single",
      "nationality": "Indian",
      "profile_image": null,
      "kyc_status": "pending",
      "kyc_verified_at": null,
      "kyc_rejection_reason": null,
      "aadhaar_last4": null,
      "pan_last4": null,
      "aadhaar_document": null,
      "pan_document": null,
      "current_address": "12 MG Road",
      "current_city": "Jaipur",
      "current_state": "Rajasthan",
      "current_pincode": "302001",
      "current_country": "India",
      "permanent_address": "12 MG Road",
      "permanent_city": "Jaipur",
      "permanent_state": "Rajasthan",
      "permanent_pincode": "302001",
      "permanent_country": "India",
      "same_as_current_address": true,
      "employment_type": "Salaried",
      "company_name": "Acme Pvt Ltd",
      "designation": "Accountant",
      "work_experience_months": 48,
      "monthly_income": 35000.0,
      "annual_income": 420000.0,
      "income_source": "Salary",
      "bank_name": null,
      "bank_account_last4": null,
      "ifsc_code": null,
      "bank_verification_status": "pending",
      "account_status": "active",
      "notification_enable": null,
      "onboarding_step": 2,
      "privacy_consent": null,
      "terms_accepted": null,
      "kyc_consent": null,
      "last_login": "2026-11-05T16:18:01",
      "created_at": "2026-11-05T16:18:00"
    },
    "profile_complete": false
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | Missing or invalid field, or step 1 is not done |

### Onboarding step 3: KYC, bank, consent

`POST /user/onboarding`  
Sign-in: Customer token

Sets `kyc_status` to `submitted` and bank verification to `pending`. Staff then verify or reject. If rejected, the reason is in `kyc_rejection_reason`; send this step again to resubmit.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `step` | number | Yes | 3 |
| `aadhaar_last4` | string | Yes | 4 digits. |
| `pan_last4` | string | Yes | 4 letters or digits. |
| `aadhaar_encrypted` | string | No | Encrypted full number, if the app has one. Never returned. |
| `pan_encrypted` | string | No | Same, for PAN. |
| `aadhaar_document` | string | No | `url` from the upload API. |
| `pan_document` | string | No | `url` from the upload API. |
| `bank_name` | string | Yes | Up to 150. |
| `ifsc_code` | string | Yes | Like `HDFC0001234`. |
| `bank_account_encrypted` | string | No | Encrypted full number. Never returned. |
| `bank_account_last4` | string | Yes | 4 digits. |
| `privacy_consent` | boolean | Yes |  |
| `terms_accepted` | boolean | Yes |  |
| `kyc_consent` | boolean | Yes |  |

Request example:

```json
{
  "step": 3,
  "aadhaar_last4": "1234",
  "pan_last4": "567F",
  "aadhaar_document": "/NarainsonsProject/api/v1/files/e711feab43924504a7cd99813714a9b2",
  "pan_document": "/NarainsonsProject/api/v1/files/53c72ab0731b4a5e9f4b05caf3537d96",
  "bank_name": "HDFC Bank",
  "ifsc_code": "HDFC0001234",
  "bank_account_last4": "6789",
  "privacy_consent": true,
  "terms_accepted": true,
  "kyc_consent": true
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `needs_registration` | boolean | `true` means show onboarding. |
| `onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user` | object | The customer. |
| `user.id` | number | Unique id of this record. |
| `user.customer_code` | string | Customer code, set at onboarding step 1. |
| `user.name` | string | Full name. |
| `user.first_name` | string | First name. |
| `user.last_name` | string | Last name. |
| `user.mobile_number` | string | 10-digit mobile number. |
| `user.alternate_mobile_number` | string | Second mobile number. |
| `user.email` | string | Email address. |
| `user.mobile_verified` | boolean | `true` once the mobile number passed an OTP. |
| `user.email_verified` | boolean | `true` once the email passed an OTP. |
| `user.has_password` | boolean | `true` if a password is set, so password login works. |
| `user.gender` | string | `Male`, `Female` or `Other`. |
| `user.date_of_birth` | string | Date of birth, `yyyy-MM-dd`. |
| `user.age` | number | Age in years, as sent by the app. |
| `user.marital_status` | string | `Single`, `Married`, `Divorced` or `Widowed`. |
| `user.nationality` | string | Nationality. |
| `user.profile_image` | string | Profile photo URL. |
| `user.kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `user.kyc_verified_at` | datetime | When staff verified KYC. |
| `user.kyc_rejection_reason` | string | Why staff rejected KYC. Show this to the customer. |
| `user.aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `user.pan_last4` | string | Last 4 characters of PAN. |
| `user.aadhaar_document` | string | URL of the uploaded Aadhaar. Open with a token. |
| `user.pan_document` | string | URL of the uploaded PAN. Open with a token. |
| `user.current_address` | string | Current address. |
| `user.current_city` | string | Current city. |
| `user.current_state` | string | Current state. |
| `user.current_pincode` | string | Current PIN code. |
| `user.current_country` | string | Current country. |
| `user.permanent_address` | string | Permanent address. |
| `user.permanent_city` | string | Permanent city. |
| `user.permanent_state` | string | Permanent state. |
| `user.permanent_pincode` | string | Permanent PIN code. |
| `user.permanent_country` | string | Permanent country. |
| `user.same_as_current_address` | boolean | `true` if permanent address equals current. |
| `user.employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `user.company_name` | string | Employer or business name. |
| `user.designation` | string | Job title. |
| `user.work_experience_months` | number | Work experience in months. |
| `user.monthly_income` | number | Monthly income in rupees. |
| `user.annual_income` | number | Annual income in rupees. |
| `user.income_source` | string | Where the income comes from. |
| `user.bank_name` | string | Bank name. |
| `user.bank_account_last4` | string | Last 4 digits of the bank account. |
| `user.ifsc_code` | string | Bank IFSC code. |
| `user.bank_verification_status` | string | `pending`, `verified` or `failed`. Only staff can change it. |
| `user.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `user.notification_enable` | boolean | `true` if push is on. |
| `user.onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user.privacy_consent` | boolean | Customer accepted the privacy policy. |
| `user.terms_accepted` | boolean | Customer accepted the terms. |
| `user.kyc_consent` | boolean | Customer agreed to KYC. |
| `user.last_login` | string | Last sign-in time. |
| `user.created_at` | string | When this record was created. |
| `profile_complete` | boolean | `true` means go to the home screen. |

Response example (200):

```json
{
  "success": true,
  "message": "Step 3 saved",
  "data": {
    "needs_registration": false,
    "onboarding_step": 3,
    "user": {
      "id": 1,
      "customer_code": "CUST11793875682002",
      "name": "Asha Verma",
      "first_name": "Asha",
      "last_name": "Verma",
      "mobile_number": "9000000001",
      "alternate_mobile_number": "9000000002",
      "email": "asha.verma@test.local",
      "mobile_verified": true,
      "email_verified": false,
      "has_password": false,
      "gender": "Female",
      "date_of_birth": "1995-04-12",
      "age": 31,
      "marital_status": "Single",
      "nationality": "Indian",
      "profile_image": null,
      "kyc_status": "submitted",
      "kyc_verified_at": null,
      "kyc_rejection_reason": null,
      "aadhaar_last4": "1234",
      "pan_last4": "567F",
      "aadhaar_document": "/NarainsonsProject/api/v1/files/e711feab43924504a7cd99813714a9b2",
      "pan_document": "/NarainsonsProject/api/v1/files/53c72ab0731b4a5e9f4b05caf3537d96",
      "current_address": "12 MG Road",
      "current_city": "Jaipur",
      "current_state": "Rajasthan",
      "current_pincode": "302001",
      "current_country": "India",
      "permanent_address": "12 MG Road",
      "permanent_city": "Jaipur",
      "permanent_state": "Rajasthan",
      "permanent_pincode": "302001",
      "permanent_country": "India",
      "same_as_current_address": true,
      "employment_type": "Salaried",
      "company_name": "Acme Pvt Ltd",
      "designation": "Accountant",
      "work_experience_months": 48,
      "monthly_income": 35000.0,
      "annual_income": 420000.0,
      "income_source": "Salary",
      "bank_name": "HDFC Bank",
      "bank_account_last4": "6789",
      "ifsc_code": "HDFC0001234",
      "bank_verification_status": "pending",
      "account_status": "active",
      "notification_enable": null,
      "onboarding_step": 3,
      "privacy_consent": true,
      "terms_accepted": true,
      "kyc_consent": true,
      "last_login": "2026-11-05T16:18:01",
      "created_at": "2026-11-05T16:18:00"
    },
    "profile_complete": true
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | Missing or invalid field, or step 2 is not done |

### Sync device details

`POST /user/sync`  
Sign-in: Customer token

All fields are optional. Contacts and gallery are not stored while `app.sync.contacts_gallery.enabled=false`, so do not ask users for those permissions.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `device_id` | string | No |  |
| `device_type` | string | No | `android`, `ios` or `web`. |
| `device_name` | string | No |  |
| `device_model` | string | No |  |
| `os_version` | string | No |  |
| `app_version` | string | No |  |
| `time_zone` | string | No |  |
| `latitude` | number | No |  |
| `longitude` | number | No |  |
| `fcm_token` | string | No | Also registers the phone for push. |
| `notification_enable` | boolean | No |  |
| `notification_disable_at` | datetime | No | `yyyy-MM-ddTHH:mm:ss` |
| `is_app_installed` | boolean | No |  |
| `application_install_time` | datetime | No |  |
| `application_uninstall_time` | datetime | No |  |
| `is_login` | boolean | No |  |
| `contacts` | array | No | Ignored unless the server enables it. |
| `gallery` | array | No | Ignored unless the server enables it. |

Request example:

```json
{
  "device_id": "android-abc123",
  "device_type": "android",
  "device_name": "Asha's phone",
  "device_model": "Pixel 8",
  "os_version": "14",
  "app_version": "1.0.0",
  "time_zone": "Asia/Kolkata",
  "latitude": 26.9124,
  "longitude": 75.7873
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `needs_registration` | boolean | `true` means show onboarding. |
| `onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user` | object | The customer. |
| `user.id` | number | Unique id of this record. |
| `user.customer_code` | string | Customer code, set at onboarding step 1. |
| `user.name` | string | Full name. |
| `user.first_name` | string | First name. |
| `user.last_name` | string | Last name. |
| `user.mobile_number` | string | 10-digit mobile number. |
| `user.alternate_mobile_number` | string | Second mobile number. |
| `user.email` | string | Email address. |
| `user.mobile_verified` | boolean | `true` once the mobile number passed an OTP. |
| `user.email_verified` | boolean | `true` once the email passed an OTP. |
| `user.has_password` | boolean | `true` if a password is set, so password login works. |
| `user.gender` | string | `Male`, `Female` or `Other`. |
| `user.date_of_birth` | string | Date of birth, `yyyy-MM-dd`. |
| `user.age` | number | Age in years, as sent by the app. |
| `user.marital_status` | string | `Single`, `Married`, `Divorced` or `Widowed`. |
| `user.nationality` | string | Nationality. |
| `user.profile_image` | string | Profile photo URL. |
| `user.kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `user.kyc_verified_at` | datetime | When staff verified KYC. |
| `user.kyc_rejection_reason` | string | Why staff rejected KYC. Show this to the customer. |
| `user.aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `user.pan_last4` | string | Last 4 characters of PAN. |
| `user.aadhaar_document` | string | URL of the uploaded Aadhaar. Open with a token. |
| `user.pan_document` | string | URL of the uploaded PAN. Open with a token. |
| `user.current_address` | string | Current address. |
| `user.current_city` | string | Current city. |
| `user.current_state` | string | Current state. |
| `user.current_pincode` | string | Current PIN code. |
| `user.current_country` | string | Current country. |
| `user.permanent_address` | string | Permanent address. |
| `user.permanent_city` | string | Permanent city. |
| `user.permanent_state` | string | Permanent state. |
| `user.permanent_pincode` | string | Permanent PIN code. |
| `user.permanent_country` | string | Permanent country. |
| `user.same_as_current_address` | boolean | `true` if permanent address equals current. |
| `user.employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `user.company_name` | string | Employer or business name. |
| `user.designation` | string | Job title. |
| `user.work_experience_months` | number | Work experience in months. |
| `user.monthly_income` | number | Monthly income in rupees. |
| `user.annual_income` | number | Annual income in rupees. |
| `user.income_source` | string | Where the income comes from. |
| `user.bank_name` | string | Bank name. |
| `user.bank_account_last4` | string | Last 4 digits of the bank account. |
| `user.ifsc_code` | string | Bank IFSC code. |
| `user.bank_verification_status` | string | `pending`, `verified` or `failed`. Only staff can change it. |
| `user.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `user.notification_enable` | boolean | `true` if push is on. |
| `user.onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user.privacy_consent` | boolean | Customer accepted the privacy policy. |
| `user.terms_accepted` | boolean | Customer accepted the terms. |
| `user.kyc_consent` | boolean | Customer agreed to KYC. |
| `user.last_login` | string | Last sign-in time. |
| `user.created_at` | string | When this record was created. |
| `profile_complete` | boolean | `true` means go to the home screen. |

Response example (200):

```json
{
  "success": true,
  "message": "Contacts & gallery sync",
  "data": {
    "needs_registration": false,
    "onboarding_step": 3,
    "user": {
      "id": 1,
      "customer_code": "CUST11793875682002",
      "name": "Asha Verma",
      "first_name": "Asha",
      "last_name": "Verma",
      "mobile_number": "9000000001",
      "alternate_mobile_number": "9000000002",
      "email": "asha.verma@test.local",
      "mobile_verified": true,
      "email_verified": false,
      "has_password": false,
      "gender": "Female",
      "date_of_birth": "1995-04-12",
      "age": 31,
      "marital_status": "Single",
      "nationality": "Indian",
      "profile_image": null,
      "kyc_status": "submitted",
      "kyc_verified_at": null,
      "kyc_rejection_reason": null,
      "aadhaar_last4": "1234",
      "pan_last4": "567F",
      "aadhaar_document": "/NarainsonsProject/api/v1/files/e711feab43924504a7cd99813714a9b2",
      "pan_document": "/NarainsonsProject/api/v1/files/53c72ab0731b4a5e9f4b05caf3537d96",
      "current_address": "12 MG Road",
      "current_city": "Jaipur",
      "current_state": "Rajasthan",
      "current_pincode": "302001",
      "current_country": "India",
      "permanent_address": "12 MG Road",
      "permanent_city": "Jaipur",
      "permanent_state": "Rajasthan",
      "permanent_pincode": "302001",
      "permanent_country": "India",
      "same_as_current_address": true,
      "employment_type": "Salaried",
      "company_name": "Acme Pvt Ltd",
      "designation": "Accountant",
      "work_experience_months": 48,
      "monthly_income": 35000.0,
      "annual_income": 420000.0,
      "income_source": "Salary",
      "bank_name": "HDFC Bank",
      "bank_account_last4": "6789",
      "ifsc_code": "HDFC0001234",
      "bank_verification_status": "pending",
      "account_status": "active",
      "notification_enable": null,
      "onboarding_step": 3,
      "privacy_consent": true,
      "terms_accepted": true,
      "kyc_consent": true,
      "last_login": "2026-11-05T16:18:01",
      "created_at": "2026-11-05T16:18:00"
    },
    "profile_complete": true
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | Invalid field value |

### Set or change password

`POST /user/password`  
Sign-in: Customer token

Ends all other logins and returns new tokens. Replace the stored ones.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `current_password` | string | If one exists | Not needed the first time. |
| `new_password` | string | Yes | At least 8 characters. |

Request example:

```json
{
  "new_password": "AshaPass123"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `access_token` | string | JWT. Send as `Authorization: Bearer <token>`. |
| `refresh_token` | string | Keep in secure storage. Used once to get new tokens. |
| `needs_registration` | boolean | `true` means show onboarding. |
| `onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `refresh_expires_in` | number | Seconds until the refresh token expires. |
| `next_action` | string | `onboarding_step_1`, `onboarding_step_2`, `onboarding_step_3` or `home`. |
| `token_type` | string | Always `Bearer`. |
| `expires_in` | number | Seconds until the access token expires. |
| `profile_complete` | boolean | `true` means go to the home screen. |

Response example (200):

```json
{
  "success": true,
  "message": "Password updated",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9.<payload>.<signature>",
    "refresh_token": "<opaque refresh token>",
    "needs_registration": false,
    "onboarding_step": 3,
    "refresh_expires_in": 2592000,
    "next_action": "home",
    "token_type": "Bearer",
    "expires_in": 3600,
    "profile_complete": true
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | Current password wrong, or new one too short |

### Delete my account

`POST /user/delete`  
Sign-in: Customer token

Required by the app stores. The mobile and email cannot be registered again.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `confirm` | boolean | Yes | Must be `true`. |

Request example:

```json
{
  "confirm": true
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `deleted` | boolean | `true` when the record was deleted. |

Response example (200):

```json
{
  "success": true,
  "message": "Account deleted",
  "data": {
    "deleted": true
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | `confirm` missing |
| 409 | `OPEN_LOAN`: a loan is still open |

## File upload

Upload first, then send the returned `url` in the API that needs it.

### Upload a file

`POST /files`  
Sign-in: Customer token

Multipart form. Limit: 30 per hour per customer.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `file` | file | Yes | Up to 5 MB. The server checks the real file type. |
| `purpose` | string | Yes | `payment_proof` or `profile_image` (JPG, PNG, WebP); `aadhaar` or `pan` (also PDF). |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `size_bytes` | number | File size in bytes. |
| `content_type` | string | File type the server detected. |
| `file_id` | string | Id of the uploaded file. |
| `url` | string | Path to send in other APIs, such as `screenshot_url` or `aadhaar_document`. |

Response example (201):

```json
{
  "success": true,
  "message": "File uploaded",
  "data": {
    "size_bytes": 41,
    "content_type": "application/pdf",
    "file_id": "e711feab43924504a7cd99813714a9b2",
    "url": "/NarainsonsProject/api/v1/files/e711feab43924504a7cd99813714a9b2"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 400 | Wrong type, bad purpose or no file |
| 429 | Rate limited |

### Download a file

`GET /files/{file_id}`  
Sign-in: Customer token

Only the owner and staff can read it; anyone else gets 404. Image widgets must send the Authorization header.

Response: The file itself.

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 404 | Not found, or not yours |

## Devices and push

Register the FCM token so this phone gets push notifications.

### Register this phone for push

`POST /user/devices`  
Sign-in: Customer token

Call after every login and whenever Firebase gives a new token. Dead tokens are removed automatically.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `fcm_token` | string | Yes |  |
| `platform` | string | No | `android` (default), `ios` or `web`. |
| `device_id` | string | No |  |
| `app_version` | string | No |  |

Request example:

```json
{
  "fcm_token": "<token from Firebase SDK>",
  "platform": "android",
  "device_id": "android-abc123",
  "app_version": "1.0.0"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `registered` | boolean | `true` when the phone was registered. |

Response example (200):

```json
{
  "success": true,
  "message": "Device registered",
  "data": {
    "registered": true
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | Token missing or bad platform |

### Unregister this phone

`POST /user/devices/remove`  
Sign-in: Customer token

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `fcm_token` | string | Yes |  |

Request example:

```json
{
  "fcm_token": "<token from Firebase SDK>"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `removed` | boolean | `true` when the phone was removed. |

Response example (200):

```json
{
  "success": true,
  "message": "Device removed",
  "data": {
    "removed": true
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | Token missing |

### Turn push on or off

`POST /user/notification-settings`  
Sign-in: Customer token

Off stops push only; the in-app list still fills up.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `enabled` | boolean | Yes |  |

Request example:

```json
{
  "enabled": true
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `notification_enable` | boolean | New push setting. |

Response example (200):

```json
{
  "success": true,
  "message": "Notification settings saved",
  "data": {
    "notification_enable": true
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | Field missing |

## Home screen

Everything the home screen needs.

### Banners

`GET /dashboard/banners`  
Sign-in: Customer token

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `banners` | array | List of banners. |
| `banners[].action_type` | string | On tap: `none`, `url` opens a link, `screen` opens an app screen. |
| `banners[].image_url` | string | Image URL. |
| `banners[].subtitle` | string | Line under the title. |
| `banners[].action_value` | string | The link or screen name to open. |
| `banners[].id` | number | Unique id of this record. |
| `banners[].title` | string | Title. |
| `banners[].sort_order` | number | Order; lower comes first. |

Response example (200):

```json
{
  "success": true,
  "message": "Banners",
  "data": {
    "banners": [
      {
        "action_type": "screen",
        "image_url": "https://cdn.example.com/banners/loan-hero.png",
        "subtitle": "Apply in minutes — admin approval shortly",
        "action_value": "loan_apply",
        "id": 1,
        "title": "Instant Loan upto ₹50,000",
        "sort_order": 1
      },
      {
        "action_type": "screen",
        "image_url": "https://cdn.example.com/banners/kyc.png",
        "subtitle": "Faster approval when profile is complete",
        "action_value": "onboarding",
        "id": 2,
        "title": "Complete KYC",
        "sort_order": 2
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

### Loan rules

`GET /dashboard/loan-config`  
Sign-in: Customer token

Build the apply form from this: amounts, tenure, purposes, relations, guarantor count.

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `guarantor_per_amount` | number | One guarantor per this many rupees. |
| `amount_step` | number | Amounts go up in this step. |
| `allowed_amounts` | array | Every amount that can be requested. |
| `min_tenure_months` | number | Shortest loan in months. |
| `guarantor_rule` | string | The guarantor rule in words. |
| `guarantors_required_by_amount` | object | Guarantors needed for each allowed amount. |
| `guarantors_required_by_amount.<amount>` | number | Number of guarantors for this amount. |
| `guarantor_required_fields` | array | Fields each guarantor must have. |
| `relation_options` | array | Values to offer for a guarantor's `relation`. |
| `min_amount` | number | Smallest loan in rupees. |
| `interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `how_to_apply_video` | object | Main guide video. Null if none is active. |
| `how_to_apply_video.duration_seconds` | number | Length in seconds. |
| `how_to_apply_video.video_url` | string | Video URL. |
| `how_to_apply_video.description` | string | Description. |
| `how_to_apply_video.id` | number | Unique id of this record. |
| `how_to_apply_video.title` | string | Title. |
| `how_to_apply_video.thumbnail_url` | string | Thumbnail image URL. |
| `how_to_apply_video.locale` | string | Language code. |
| `how_to_apply_video.video_type` | string | `mp4`, `youtube`, `vimeo` or `hls`. |
| `processing_fee_percent` | number | Processing fee percent. |
| `max_tenure_months` | number | Longest loan in months. |
| `purpose_options` | array | Values to offer for `purpose`. |
| `max_amount` | number | Largest loan in rupees. |
| `guarantor_optional_fields` | array | Fields each guarantor may have. |

Response example (200):

```json
{
  "success": true,
  "message": "Loan config",
  "data": {
    "guarantor_per_amount": 10000,
    "amount_step": 5000,
    "allowed_amounts": [
      10000,
      15000
    ],
    "min_tenure_months": 3,
    "guarantor_rule": "har ₹10,000 par 1 guarantor (10k→1, 20k→2, 50k→5)",
    "guarantors_required_by_amount": {
      "10000": 1,
      "20000": 2,
      "30000": 3,
      "40000": 4,
      "50000": 5,
      "15000": 1,
      "25000": 2,
      "35000": 3,
      "45000": 4
    },
    "guarantor_required_fields": [
      "full_name",
      "mobile_number"
    ],
    "relation_options": [
      "father",
      "mother"
    ],
    "min_amount": 10000,
    "interest_rate_annual": 12,
    "how_to_apply_video": {
      "duration_seconds": 180,
      "video_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-hi.mp4",
      "description": "Step-by-step: amount select karo, guarantor details bharo, submit — admin approval pending.",
      "id": 1,
      "title": "Loan kaise apply karein",
      "thumbnail_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-thumb.jpg",
      "locale": "hi",
      "video_type": "mp4"
    },
    "processing_fee_percent": 2,
    "max_tenure_months": 24,
    "purpose_options": [
      "personal",
      "business"
    ],
    "max_amount": 50000,
    "guarantor_optional_fields": [
      "address",
      "aadhaar_last4"
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

### How-to-apply video

`GET /dashboard/loan-apply-video`  
Sign-in: Customer token

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `how_to_apply_loan_video` | object | Main guide video. Null if none is active. |
| `how_to_apply_loan_video.duration_seconds` | number | Length in seconds. |
| `how_to_apply_loan_video.video_url` | string | Video URL. |
| `how_to_apply_loan_video.description` | string | Description. |
| `how_to_apply_loan_video.id` | number | Unique id of this record. |
| `how_to_apply_loan_video.title` | string | Title. |
| `how_to_apply_loan_video.thumbnail_url` | string | Thumbnail image URL. |
| `how_to_apply_loan_video.locale` | string | Language code. |
| `how_to_apply_loan_video.video_type` | string | `mp4`, `youtube`, `vimeo` or `hls`. |
| `videos` | array | All active guide videos. |
| `videos[].duration_seconds` | number | Length in seconds. |
| `videos[].video_url` | string | Video URL. |
| `videos[].description` | string | Description. |
| `videos[].id` | number | Unique id of this record. |
| `videos[].title` | string | Title. |
| `videos[].thumbnail_url` | string | Thumbnail image URL. |
| `videos[].locale` | string | Language code. |
| `videos[].video_type` | string | `mp4`, `youtube`, `vimeo` or `hls`. |

Response example (200):

```json
{
  "success": true,
  "message": "Loan apply guide video",
  "data": {
    "how_to_apply_loan_video": {
      "duration_seconds": 180,
      "video_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-hi.mp4",
      "description": "Step-by-step: amount select karo, guarantor details bharo, submit — admin approval pending.",
      "id": 1,
      "title": "Loan kaise apply karein",
      "thumbnail_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-thumb.jpg",
      "locale": "hi",
      "video_type": "mp4"
    },
    "videos": [
      {
        "duration_seconds": 180,
        "video_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-hi.mp4",
        "description": "Step-by-step: amount select karo, guarantor details bharo, submit — admin approval pending.",
        "id": 1,
        "title": "Loan kaise apply karein",
        "thumbnail_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-thumb.jpg",
        "locale": "hi",
        "video_type": "mp4"
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

### Home screen

`GET /dashboard/home`  
Sign-in: Customer token

Everything for the home screen in one call.

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `loan_config` | object | The loan rules. Same as `GET /dashboard/loan-config`. |
| `loan_config.guarantor_per_amount` | number | One guarantor per this many rupees. |
| `loan_config.amount_step` | number | Amounts go up in this step. |
| `loan_config.allowed_amounts` | array | Every amount that can be requested. |
| `loan_config.min_tenure_months` | number | Shortest loan in months. |
| `loan_config.guarantor_rule` | string | The guarantor rule in words. |
| `loan_config.guarantors_required_by_amount` | object | Guarantors needed for each allowed amount. |
| `loan_config.guarantors_required_by_amount.<amount>` | number | Number of guarantors for this amount. |
| `loan_config.guarantor_required_fields` | array | Fields each guarantor must have. |
| `loan_config.relation_options` | array | Values to offer for a guarantor's `relation`. |
| `loan_config.min_amount` | number | Smallest loan in rupees. |
| `loan_config.interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `loan_config.how_to_apply_video` | object | Main guide video. Null if none is active. |
| `loan_config.how_to_apply_video.duration_seconds` | number | Length in seconds. |
| `loan_config.how_to_apply_video.video_url` | string | Video URL. |
| `loan_config.how_to_apply_video.description` | string | Description. |
| `loan_config.how_to_apply_video.id` | number | Unique id of this record. |
| `loan_config.how_to_apply_video.title` | string | Title. |
| `loan_config.how_to_apply_video.thumbnail_url` | string | Thumbnail image URL. |
| `loan_config.how_to_apply_video.locale` | string | Language code. |
| `loan_config.how_to_apply_video.video_type` | string | `mp4`, `youtube`, `vimeo` or `hls`. |
| `loan_config.processing_fee_percent` | number | Processing fee percent. |
| `loan_config.max_tenure_months` | number | Longest loan in months. |
| `loan_config.purpose_options` | array | Values to offer for `purpose`. |
| `loan_config.max_amount` | number | Largest loan in rupees. |
| `loan_config.guarantor_optional_fields` | array | Fields each guarantor may have. |
| `how_to_apply_loan_video` | object | Main guide video. Null if none is active. |
| `how_to_apply_loan_video.duration_seconds` | number | Length in seconds. |
| `how_to_apply_loan_video.video_url` | string | Video URL. |
| `how_to_apply_loan_video.description` | string | Description. |
| `how_to_apply_loan_video.id` | number | Unique id of this record. |
| `how_to_apply_loan_video.title` | string | Title. |
| `how_to_apply_loan_video.thumbnail_url` | string | Thumbnail image URL. |
| `how_to_apply_loan_video.locale` | string | Language code. |
| `how_to_apply_loan_video.video_type` | string | `mp4`, `youtube`, `vimeo` or `hls`. |
| `latest_loan_application` | object | The customer's newest loan. Missing if none. |
| `latest_loan_application.status_message` | string | Status text to show the customer. |
| `latest_loan_application.closed_at` | datetime | When the loan closed. |
| `latest_loan_application.purpose` | string | Why the loan is needed. |
| `latest_loan_application.reviewed_at` | string | When staff decided. |
| `latest_loan_application.purpose_detail` | string | More about the purpose. |
| `latest_loan_application.created_at` | string | When this record was created. |
| `latest_loan_application.processing_fee` | number | Processing fee in rupees. |
| `latest_loan_application.disbursement_reference` | string | Bank or UTR reference of the money sent. |
| `latest_loan_application.interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `latest_loan_application.requested_amount` | number | Loan amount in rupees. |
| `latest_loan_application.updated_at` | string | When this record last changed. |
| `latest_loan_application.emi_amount` | number | Monthly EMI in rupees. |
| `latest_loan_application.application_code` | string | Loan code shown to people. |
| `latest_loan_application.total_payable` | number | Total of all EMIs in rupees. |
| `latest_loan_application.guarantors_required` | number | Number of guarantors this loan needs. |
| `latest_loan_application.id` | number | Unique id of this record. |
| `latest_loan_application.tenure_months` | number | Loan length in months. |
| `latest_loan_application.disbursed_at` | string | When the money was sent. |
| `latest_loan_application.guarantors` | array | The loan's guarantors. |
| `latest_loan_application.guarantors[].pan_last4` | string | Last 4 characters of PAN. |
| `latest_loan_application.guarantors[].full_name` | string | Full name. |
| `latest_loan_application.guarantors[].address` | string | Address. |
| `latest_loan_application.guarantors[].index` | number | Guarantor number, from 1. |
| `latest_loan_application.guarantors[].mobile_number` | string | 10-digit mobile number. |
| `latest_loan_application.guarantors[].aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `latest_loan_application.guarantors[].relation` | string | Relation to the customer. |
| `latest_loan_application.status` | string | Current status. See Status values. |
| `user` | object | The customer. |
| `user.id` | number | Unique id of this record. |
| `user.customer_code` | string | Customer code, set at onboarding step 1. |
| `user.name` | string | Full name. |
| `user.first_name` | string | First name. |
| `user.last_name` | string | Last name. |
| `user.mobile_number` | string | 10-digit mobile number. |
| `user.alternate_mobile_number` | string | Second mobile number. |
| `user.email` | string | Email address. |
| `user.mobile_verified` | boolean | `true` once the mobile number passed an OTP. |
| `user.email_verified` | boolean | `true` once the email passed an OTP. |
| `user.has_password` | boolean | `true` if a password is set, so password login works. |
| `user.gender` | string | `Male`, `Female` or `Other`. |
| `user.date_of_birth` | string | Date of birth, `yyyy-MM-dd`. |
| `user.age` | number | Age in years, as sent by the app. |
| `user.marital_status` | string | `Single`, `Married`, `Divorced` or `Widowed`. |
| `user.nationality` | string | Nationality. |
| `user.profile_image` | string | Profile photo URL. |
| `user.kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `user.kyc_verified_at` | string | When staff verified KYC. |
| `user.kyc_rejection_reason` | string | Why staff rejected KYC. Show this to the customer. |
| `user.aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `user.pan_last4` | string | Last 4 characters of PAN. |
| `user.aadhaar_document` | string | URL of the uploaded Aadhaar. Open with a token. |
| `user.pan_document` | string | URL of the uploaded PAN. Open with a token. |
| `user.current_address` | string | Current address. |
| `user.current_city` | string | Current city. |
| `user.current_state` | string | Current state. |
| `user.current_pincode` | string | Current PIN code. |
| `user.current_country` | string | Current country. |
| `user.permanent_address` | string | Permanent address. |
| `user.permanent_city` | string | Permanent city. |
| `user.permanent_state` | string | Permanent state. |
| `user.permanent_pincode` | string | Permanent PIN code. |
| `user.permanent_country` | string | Permanent country. |
| `user.same_as_current_address` | boolean | `true` if permanent address equals current. |
| `user.employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `user.company_name` | string | Employer or business name. |
| `user.designation` | string | Job title. |
| `user.work_experience_months` | number | Work experience in months. |
| `user.monthly_income` | number | Monthly income in rupees. |
| `user.annual_income` | number | Annual income in rupees. |
| `user.income_source` | string | Where the income comes from. |
| `user.bank_name` | string | Bank name. |
| `user.bank_account_last4` | string | Last 4 digits of the bank account. |
| `user.ifsc_code` | string | Bank IFSC code. |
| `user.bank_verification_status` | string | `pending`, `verified` or `failed`. Only staff can change it. |
| `user.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `user.notification_enable` | boolean | `true` if push is on. |
| `user.onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user.privacy_consent` | boolean | Customer accepted the privacy policy. |
| `user.terms_accepted` | boolean | Customer accepted the terms. |
| `user.kyc_consent` | boolean | Customer agreed to KYC. |
| `user.last_login` | string | Last sign-in time. |
| `user.created_at` | string | When this record was created. |
| `banners` | array | List of banners. |
| `banners[].action_type` | string | On tap: `none`, `url` opens a link, `screen` opens an app screen. |
| `banners[].image_url` | string | Image URL. |
| `banners[].subtitle` | string | Line under the title. |
| `banners[].action_value` | string | The link or screen name to open. |
| `banners[].id` | number | Unique id of this record. |
| `banners[].title` | string | Title. |
| `banners[].sort_order` | number | Order; lower comes first. |
| `profile_complete` | boolean | `true` means go to the home screen. |
| `can_apply_loan` | boolean | `false` while the profile is incomplete or a loan is open. |
| `emi_summary` | object | EMI position. Same as `GET /emi/summary`. |
| `emi_summary.loan_status` | string | Status of the loan. |
| `emi_summary.has_active_loan` | boolean | `false` when there is no approved loan. |
| `emi_summary.total_installments` | number | Number of EMIs. |
| `emi_summary.paid_installments` | number | EMIs paid. |
| `emi_summary.can_pay` | boolean | `true` once the money has been sent. |
| `emi_summary.remaining_installments` | number | EMIs not yet paid. |
| `emi_summary.total_amount` | number | Loan amount in rupees. |
| `emi_summary.application_code` | string | Loan code shown to people. |
| `emi_summary.total_payable` | number | Total of all EMIs in rupees. |
| `emi_summary.loan_application_id` | number | Loan id. |
| `emi_summary.outstanding_amount` | number | Total of unpaid EMIs in rupees. |
| `emi_summary.disbursed_at` | string | When the money was sent. |
| `emi_summary.next_emi` | object | The next EMI to pay. Missing when none is left. |
| `emi_summary.next_emi.paid_at` | datetime | When the EMI was marked paid. |
| `emi_summary.next_emi.amount_paid` | number | Amount recorded as paid. |
| `emi_summary.next_emi.installment_number` | number | EMI number, from 1. |
| `emi_summary.next_emi.due_date` | string | Due date, `yyyy-MM-dd`. |
| `emi_summary.next_emi.amount_due` | number | EMI amount in rupees. |
| `emi_summary.next_emi.id` | number | Unique id of this record. |
| `emi_summary.next_emi.status` | string | Current status. See Status values. |

Response example (200):

```json
{
  "success": true,
  "message": "Dashboard",
  "data": {
    "onboarding_step": 3,
    "loan_config": {
      "guarantor_per_amount": 10000,
      "amount_step": 5000,
      "allowed_amounts": [
        10000,
        15000
      ],
      "min_tenure_months": 3,
      "guarantor_rule": "har ₹10,000 par 1 guarantor (10k→1, 20k→2, 50k→5)",
      "guarantors_required_by_amount": {
        "10000": 1,
        "20000": 2,
        "30000": 3,
        "40000": 4,
        "50000": 5,
        "15000": 1,
        "25000": 2,
        "35000": 3,
        "45000": 4
      },
      "guarantor_required_fields": [
        "full_name",
        "mobile_number"
      ],
      "relation_options": [
        "father",
        "mother"
      ],
      "min_amount": 10000,
      "interest_rate_annual": 12,
      "how_to_apply_video": {
        "duration_seconds": 180,
        "video_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-hi.mp4",
        "description": "Step-by-step: amount select karo, guarantor details bharo, submit — admin approval pending.",
        "id": 1,
        "title": "Loan kaise apply karein",
        "thumbnail_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-thumb.jpg",
        "locale": "hi",
        "video_type": "mp4"
      },
      "processing_fee_percent": 2,
      "max_tenure_months": 24,
      "purpose_options": [
        "personal",
        "business"
      ],
      "max_amount": 50000,
      "guarantor_optional_fields": [
        "address",
        "aadhaar_last4"
      ]
    },
    "how_to_apply_loan_video": {
      "duration_seconds": 180,
      "video_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-hi.mp4",
      "description": "Step-by-step: amount select karo, guarantor details bharo, submit — admin approval pending.",
      "id": 1,
      "title": "Loan kaise apply karein",
      "thumbnail_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-thumb.jpg",
      "locale": "hi",
      "video_type": "mp4"
    },
    "latest_loan_application": {
      "status_message": "Aapka loan amount bank account mein bhej diya gaya hai. EMI schedule app mein dekhein.",
      "closed_at": null,
      "purpose": "personal",
      "reviewed_at": "2026-11-05T16:18:06",
      "purpose_detail": "Home repair",
      "created_at": "2026-11-05T16:18:06",
      "processing_fee": 400.0,
      "disbursement_reference": "UTR123456",
      "interest_rate_annual": 12.0,
      "requested_amount": 20000.0,
      "updated_at": "2026-11-05T16:18:07",
      "emi_amount": 3450.97,
      "application_code": "LN179387568679444E1",
      "total_payable": 20705.81,
      "guarantors_required": 2,
      "id": 3,
      "tenure_months": 6,
      "disbursed_at": "2026-11-05T16:18:07",
      "guarantors": [
        {
          "pan_last4": "222A",
          "full_name": "Ravi Verma",
          "address": "Jaipur",
          "index": 1,
          "mobile_number": "9000000101",
          "aadhaar_last4": "1111",
          "relation": "brother"
        },
        {
          "pan_last4": null,
          "full_name": "Neha Sharma",
          "address": null,
          "index": 2,
          "mobile_number": "9000000102",
          "aadhaar_last4": null,
          "relation": "friend"
        }
      ],
      "status": "disbursed"
    },
    "user": {
      "id": 1,
      "customer_code": "CUST11793875682002",
      "name": "Asha Verma",
      "first_name": "Asha",
      "last_name": "Verma",
      "mobile_number": "9000000001",
      "alternate_mobile_number": "9000000002",
      "email": "asha.verma@test.local",
      "mobile_verified": true,
      "email_verified": true,
      "has_password": true,
      "gender": "Female",
      "date_of_birth": "1995-04-12",
      "age": 31,
      "marital_status": "Single",
      "nationality": "Indian",
      "profile_image": null,
      "kyc_status": "verified",
      "kyc_verified_at": "2026-11-05T16:18:06",
      "kyc_rejection_reason": null,
      "aadhaar_last4": "1234",
      "pan_last4": "567F",
      "aadhaar_document": "/NarainsonsProject/api/v1/files/e711feab43924504a7cd99813714a9b2",
      "pan_document": "/NarainsonsProject/api/v1/files/53c72ab0731b4a5e9f4b05caf3537d96",
      "current_address": "12 MG Road",
      "current_city": "Jaipur",
      "current_state": "Rajasthan",
      "current_pincode": "302001",
      "current_country": "India",
      "permanent_address": "12 MG Road",
      "permanent_city": "Jaipur",
      "permanent_state": "Rajasthan",
      "permanent_pincode": "302001",
      "permanent_country": "India",
      "same_as_current_address": true,
      "employment_type": "Salaried",
      "company_name": "Acme Pvt Ltd",
      "designation": "Accountant",
      "work_experience_months": 48,
      "monthly_income": 35000.0,
      "annual_income": 420000.0,
      "income_source": "Salary",
      "bank_name": "HDFC Bank",
      "bank_account_last4": "6789",
      "ifsc_code": "HDFC0001234",
      "bank_verification_status": "verified",
      "account_status": "active",
      "notification_enable": true,
      "onboarding_step": 3,
      "privacy_consent": true,
      "terms_accepted": true,
      "kyc_consent": true,
      "last_login": "2026-11-05T16:18:05",
      "created_at": "2026-11-05T16:18:00"
    },
    "banners": [
      {
        "action_type": "screen",
        "image_url": "https://cdn.example.com/banners/loan-hero.png",
        "subtitle": "Apply in minutes — admin approval shortly",
        "action_value": "loan_apply",
        "id": 1,
        "title": "Instant Loan upto ₹50,000",
        "sort_order": 1
      },
      {
        "action_type": "screen",
        "image_url": "https://cdn.example.com/banners/kyc.png",
        "subtitle": "Faster approval when profile is complete",
        "action_value": "onboarding",
        "id": 2,
        "title": "Complete KYC",
        "sort_order": 2
      }
    ],
    "profile_complete": true,
    "can_apply_loan": false,
    "emi_summary": {
      "loan_status": "disbursed",
      "has_active_loan": true,
      "total_installments": 6,
      "paid_installments": 0,
      "can_pay": true,
      "remaining_installments": 6,
      "total_amount": 20000.0,
      "application_code": "LN179387568679444E1",
      "total_payable": 20705.81,
      "loan_application_id": 3,
      "outstanding_amount": 20705.81,
      "disbursed_at": "2026-11-05T16:18:07",
      "next_emi": {
        "paid_at": null,
        "amount_paid": 0.0,
        "installment_number": 1,
        "due_date": "2026-12-05",
        "amount_due": 3450.97,
        "id": 1,
        "status": "pending"
      }
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

## Loans

Quote, apply, track, cancel.

### Get a quote

`POST /loan/quote`  
Sign-in: Customer token

What the loan will cost, before applying. Rates come from server settings. Samples here use 12% and 2%.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `requested_amount` | number | Yes | 10,000 to 50,000 in steps of 5,000. |
| `tenure_months` | number | Yes | 3 to 24. |

Request example:

```json
{
  "requested_amount": 20000,
  "tenure_months": 6
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `requested_amount` | number | Loan amount in rupees. |
| `tenure_months` | number | Loan length in months. |
| `interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `processing_fee` | number | Processing fee in rupees. |
| `net_disbursal` | number | Loan amount minus the fee. |
| `emi_amount` | number | Monthly EMI in rupees. |
| `last_emi_amount` | number | Last EMI, which can differ by a few paise. |
| `total_interest` | number | Total interest in rupees. |
| `total_payable` | number | Total of all EMIs in rupees. |
| `guarantors_required` | number | Number of guarantors this loan needs. |

Response example (200):

```json
{
  "success": true,
  "message": "Loan quote",
  "data": {
    "requested_amount": 20000,
    "tenure_months": 6,
    "interest_rate_annual": 12,
    "processing_fee": 400.0,
    "net_disbursal": 19600.0,
    "emi_amount": 3450.97,
    "last_emi_amount": 3450.96,
    "total_interest": 705.81,
    "total_payable": 20705.81,
    "guarantors_required": 2
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | Amount or months not allowed |

### Apply for a loan

`POST /loan/apply`  
Sign-in: Customer token

Needs a complete profile. Refused while another loan is pending or has unpaid EMIs.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `requested_amount` | number | Yes | 10,000 to 50,000 in steps of 5,000. |
| `tenure_months` | number | Yes | 3 to 24. |
| `purpose` | string | Yes | Use `purpose_options` from loan rules. |
| `purpose_detail` | string | No |  |
| `monthly_income` | number | No | Defaults to the profile. |
| `employment_type` | string | No | Defaults to the profile. |
| `company_name` | string | No | Defaults to the profile. |
| `bank_name` | string | No | Defaults to the profile. |
| `ifsc_code` | string | No | Defaults to the profile. |
| `bank_account_last4` | string | No | Defaults to the profile. |
| `guarantors` | array | Yes | Exactly 1 per ₹10,000, minimum 1. See `guarantors_required_by_amount`. |
| `guarantors[].full_name` | string | Yes |  |
| `guarantors[].mobile_number` | string | Yes | 10 digits, different for each. |
| `guarantors[].relation` | string | Yes | Use `relation_options`. |
| `guarantors[].address` | string | No |  |
| `guarantors[].aadhaar_last4` | string | No |  |
| `guarantors[].pan_last4` | string | No |  |

Request example:

```json
{
  "requested_amount": 20000,
  "tenure_months": 6,
  "purpose": "personal",
  "purpose_detail": "Home repair",
  "monthly_income": 35000,
  "employment_type": "Salaried",
  "company_name": "Acme Pvt Ltd",
  "bank_name": "HDFC Bank",
  "ifsc_code": "HDFC0001234",
  "bank_account_last4": "6789",
  "guarantors": [
    {
      "full_name": "Ravi Verma",
      "mobile_number": "9000000101",
      "relation": "brother",
      "address": "Jaipur",
      "aadhaar_last4": "1111",
      "pan_last4": "222A"
    },
    {
      "full_name": "Neha Sharma",
      "mobile_number": "9000000102",
      "relation": "friend"
    }
  ]
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `loan_application` | object | The loan application. |
| `loan_application.status_message` | string | Status text to show the customer. |
| `loan_application.closed_at` | datetime | When the loan closed. |
| `loan_application.purpose` | string | Why the loan is needed. |
| `loan_application.reviewed_at` | datetime | When staff decided. |
| `loan_application.purpose_detail` | string | More about the purpose. |
| `loan_application.created_at` | string | When this record was created. |
| `loan_application.processing_fee` | number | Processing fee in rupees. |
| `loan_application.disbursement_reference` | string | Bank or UTR reference of the money sent. |
| `loan_application.interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `loan_application.requested_amount` | number | Loan amount in rupees. |
| `loan_application.updated_at` | string | When this record last changed. |
| `loan_application.emi_amount` | number | Monthly EMI in rupees. |
| `loan_application.application_code` | string | Loan code shown to people. |
| `loan_application.total_payable` | number | Total of all EMIs in rupees. |
| `loan_application.guarantors_required` | number | Number of guarantors this loan needs. |
| `loan_application.id` | number | Unique id of this record. |
| `loan_application.tenure_months` | number | Loan length in months. |
| `loan_application.disbursed_at` | datetime | When the money was sent. |
| `loan_application.guarantors` | array | The loan's guarantors. |
| `loan_application.guarantors[].pan_last4` | string | Last 4 characters of PAN. |
| `loan_application.guarantors[].full_name` | string | Full name. |
| `loan_application.guarantors[].address` | string | Address. |
| `loan_application.guarantors[].index` | number | Guarantor number, from 1. |
| `loan_application.guarantors[].mobile_number` | string | 10-digit mobile number. |
| `loan_application.guarantors[].aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `loan_application.guarantors[].relation` | string | Relation to the customer. |
| `loan_application.status` | string | Current status. See Status values. |
| `message` | string | Text to show the user. |

Response example (201):

```json
{
  "success": true,
  "message": "Loan submitted",
  "data": {
    "loan_application": {
      "status_message": "Aapka loan application pending hai. Admin jald approve karega — shortly update milega.",
      "closed_at": null,
      "purpose": "personal",
      "reviewed_at": null,
      "purpose_detail": "Home repair",
      "created_at": "2026-11-05T16:18:06",
      "processing_fee": 400.0,
      "disbursement_reference": null,
      "interest_rate_annual": 12.0,
      "requested_amount": 20000.0,
      "updated_at": "2026-11-05T16:18:06",
      "emi_amount": 3450.97,
      "application_code": "LN1793875686505B0D7",
      "total_payable": 20705.81,
      "guarantors_required": 2,
      "id": 1,
      "tenure_months": 6,
      "disbursed_at": null,
      "guarantors": [
        {
          "pan_last4": "222A",
          "full_name": "Ravi Verma",
          "address": "Jaipur",
          "index": 1,
          "mobile_number": "9000000101",
          "aadhaar_last4": "1111",
          "relation": "brother"
        },
        {
          "pan_last4": null,
          "full_name": "Neha Sharma",
          "address": null,
          "index": 2,
          "mobile_number": "9000000102",
          "aadhaar_last4": null,
          "relation": "friend"
        }
      ],
      "status": "pending"
    },
    "message": "Aapka loan application pending hai. Admin jald approve karega — shortly update milega."
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | Bad field, profile incomplete, or a loan is already open |
| 409 | `KYC_REJECTED`: send step 3 again first |

### Cancel an application

`POST /loan/applications/{id}/cancel`  
Sign-in: Customer token

Only while pending.

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `application` | object | The loan application. |
| `application.status_message` | string | Status text to show the customer. |
| `application.closed_at` | datetime | When the loan closed. |
| `application.purpose` | string | Why the loan is needed. |
| `application.reviewed_at` | datetime | When staff decided. |
| `application.purpose_detail` | string | More about the purpose. |
| `application.created_at` | string | When this record was created. |
| `application.processing_fee` | number | Processing fee in rupees. |
| `application.disbursement_reference` | string | Bank or UTR reference of the money sent. |
| `application.interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `application.requested_amount` | number | Loan amount in rupees. |
| `application.updated_at` | string | When this record last changed. |
| `application.emi_amount` | number | Monthly EMI in rupees. |
| `application.application_code` | string | Loan code shown to people. |
| `application.total_payable` | number | Total of all EMIs in rupees. |
| `application.guarantors_required` | number | Number of guarantors this loan needs. |
| `application.id` | number | Unique id of this record. |
| `application.tenure_months` | number | Loan length in months. |
| `application.disbursed_at` | datetime | When the money was sent. |
| `application.guarantors` | array | The loan's guarantors. |
| `application.guarantors[].pan_last4` | string | Last 4 characters of PAN. |
| `application.guarantors[].full_name` | string | Full name. |
| `application.guarantors[].address` | string | Address. |
| `application.guarantors[].index` | number | Guarantor number, from 1. |
| `application.guarantors[].mobile_number` | string | 10-digit mobile number. |
| `application.guarantors[].aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `application.guarantors[].relation` | string | Relation to the customer. |
| `application.status` | string | Current status. See Status values. |

Response example (200):

```json
{
  "success": true,
  "message": "Application cancelled",
  "data": {
    "application": {
      "status_message": "Application aapne cancel kar di.",
      "closed_at": null,
      "purpose": "personal",
      "reviewed_at": null,
      "purpose_detail": "Home repair",
      "created_at": "2026-11-05T16:18:06",
      "processing_fee": 400.0,
      "disbursement_reference": null,
      "interest_rate_annual": 12.0,
      "requested_amount": 20000.0,
      "updated_at": "2026-11-05T16:18:06",
      "emi_amount": 3450.97,
      "application_code": "LN1793875686505B0D7",
      "total_payable": 20705.81,
      "guarantors_required": 2,
      "id": 1,
      "tenure_months": 6,
      "disbursed_at": null,
      "guarantors": [
        {
          "pan_last4": "222A",
          "full_name": "Ravi Verma",
          "address": "Jaipur",
          "index": 1,
          "mobile_number": "9000000101",
          "aadhaar_last4": "1111",
          "relation": "brother"
        },
        {
          "pan_last4": null,
          "full_name": "Neha Sharma",
          "address": null,
          "index": 2,
          "mobile_number": "9000000102",
          "aadhaar_last4": null,
          "relation": "friend"
        }
      ],
      "status": "cancelled"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 404 | Not found, or not yours |
| 409 | `LOAN_NOT_PENDING` |

### My applications

`GET /loan/applications`  
Sign-in: Customer token

Newest first.

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `limit` | number | 1 to 50. Default 20. |
| `offset` | number | Default 0. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `applications` | array | Loan applications in the period. |
| `applications[].status_message` | string | Status text to show the customer. |
| `applications[].closed_at` | datetime | When the loan closed. |
| `applications[].purpose` | string | Why the loan is needed. |
| `applications[].reviewed_at` | datetime | When staff decided. |
| `applications[].purpose_detail` | string | More about the purpose. |
| `applications[].created_at` | string | When this record was created. |
| `applications[].processing_fee` | number | Processing fee in rupees. |
| `applications[].disbursement_reference` | string | Bank or UTR reference of the money sent. |
| `applications[].interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `applications[].requested_amount` | number | Loan amount in rupees. |
| `applications[].updated_at` | string | When this record last changed. |
| `applications[].emi_amount` | number | Monthly EMI in rupees. |
| `applications[].application_code` | string | Loan code shown to people. |
| `applications[].total_payable` | number | Total of all EMIs in rupees. |
| `applications[].guarantors_required` | number | Number of guarantors this loan needs. |
| `applications[].id` | number | Unique id of this record. |
| `applications[].tenure_months` | number | Loan length in months. |
| `applications[].disbursed_at` | datetime | When the money was sent. |
| `applications[].guarantors` | array | The loan's guarantors. |
| `applications[].guarantors[].pan_last4` | string | Last 4 characters of PAN. |
| `applications[].guarantors[].full_name` | string | Full name. |
| `applications[].guarantors[].address` | string | Address. |
| `applications[].guarantors[].index` | number | Guarantor number, from 1. |
| `applications[].guarantors[].mobile_number` | string | 10-digit mobile number. |
| `applications[].guarantors[].aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `applications[].guarantors[].relation` | string | Relation to the customer. |
| `applications[].status` | string | Current status. See Status values. |

Response example (200):

```json
{
  "success": true,
  "message": "Loan applications",
  "data": {
    "applications": [
      {
        "status_message": "Aapka loan application pending hai. Admin jald approve karega — shortly update milega.",
        "closed_at": null,
        "purpose": "personal",
        "reviewed_at": null,
        "purpose_detail": "Home repair",
        "created_at": "2026-11-05T16:18:06",
        "processing_fee": 400.0,
        "disbursement_reference": null,
        "interest_rate_annual": 12.0,
        "requested_amount": 20000.0,
        "updated_at": "2026-11-05T16:18:06",
        "emi_amount": 3450.97,
        "application_code": "LN179387568679444E1",
        "total_payable": 20705.81,
        "guarantors_required": 2,
        "id": 3,
        "tenure_months": 6,
        "disbursed_at": null,
        "guarantors": [
          {
            "pan_last4": "222A",
            "full_name": "Ravi Verma",
            "address": "Jaipur",
            "index": 1,
            "mobile_number": "9000000101",
            "aadhaar_last4": "1111",
            "relation": "brother"
          },
          {
            "pan_last4": null,
            "full_name": "Neha Sharma",
            "address": null,
            "index": 2,
            "mobile_number": "9000000102",
            "aadhaar_last4": null,
            "relation": "friend"
          }
        ],
        "status": "pending"
      },
      {
        "status_message": "Maaf kijiye, aapka loan application approve nahi hua. Support se sampark karein.",
        "closed_at": null,
        "purpose": "personal",
        "reviewed_at": "2026-11-05T16:18:06",
        "purpose_detail": "Home repair",
        "created_at": "2026-11-05T16:18:06",
        "processing_fee": 400.0,
        "disbursement_reference": null,
        "interest_rate_annual": 12.0,
        "requested_amount": 20000.0,
        "updated_at": "2026-11-05T16:18:06",
        "emi_amount": 3450.97,
        "application_code": "LN1793875686609D351",
        "total_payable": 20705.81,
        "guarantors_required": 2,
        "id": 2,
        "tenure_months": 6,
        "disbursed_at": null,
        "guarantors": [
          {
            "pan_last4": "222A",
            "full_name": "Ravi Verma",
            "address": "Jaipur",
            "index": 1,
            "mobile_number": "9000000101",
            "aadhaar_last4": "1111",
            "relation": "brother"
          },
          {
            "pan_last4": null,
            "full_name": "Neha Sharma",
            "address": null,
            "index": 2,
            "mobile_number": "9000000102",
            "aadhaar_last4": null,
            "relation": "friend"
          }
        ],
        "status": "rejected"
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

### One application

`GET /loan/applications/{id}`  
Sign-in: Customer token

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `application` | object | The loan application. |
| `application.status_message` | string | Status text to show the customer. |
| `application.closed_at` | datetime | When the loan closed. |
| `application.purpose` | string | Why the loan is needed. |
| `application.reviewed_at` | datetime | When staff decided. |
| `application.purpose_detail` | string | More about the purpose. |
| `application.created_at` | string | When this record was created. |
| `application.processing_fee` | number | Processing fee in rupees. |
| `application.disbursement_reference` | string | Bank or UTR reference of the money sent. |
| `application.interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `application.requested_amount` | number | Loan amount in rupees. |
| `application.updated_at` | string | When this record last changed. |
| `application.emi_amount` | number | Monthly EMI in rupees. |
| `application.application_code` | string | Loan code shown to people. |
| `application.total_payable` | number | Total of all EMIs in rupees. |
| `application.guarantors_required` | number | Number of guarantors this loan needs. |
| `application.id` | number | Unique id of this record. |
| `application.tenure_months` | number | Loan length in months. |
| `application.disbursed_at` | datetime | When the money was sent. |
| `application.guarantors` | array | The loan's guarantors. |
| `application.guarantors[].pan_last4` | string | Last 4 characters of PAN. |
| `application.guarantors[].full_name` | string | Full name. |
| `application.guarantors[].address` | string | Address. |
| `application.guarantors[].index` | number | Guarantor number, from 1. |
| `application.guarantors[].mobile_number` | string | 10-digit mobile number. |
| `application.guarantors[].aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `application.guarantors[].relation` | string | Relation to the customer. |
| `application.status` | string | Current status. See Status values. |

Response example (200):

```json
{
  "success": true,
  "message": "Loan application",
  "data": {
    "application": {
      "status_message": "Aapka loan application pending hai. Admin jald approve karega — shortly update milega.",
      "closed_at": null,
      "purpose": "personal",
      "reviewed_at": null,
      "purpose_detail": "Home repair",
      "created_at": "2026-11-05T16:18:06",
      "processing_fee": 400.0,
      "disbursement_reference": null,
      "interest_rate_annual": 12.0,
      "requested_amount": 20000.0,
      "updated_at": "2026-11-05T16:18:06",
      "emi_amount": 3450.97,
      "application_code": "LN179387568679444E1",
      "total_payable": 20705.81,
      "guarantors_required": 2,
      "id": 3,
      "tenure_months": 6,
      "disbursed_at": null,
      "guarantors": [
        {
          "pan_last4": "222A",
          "full_name": "Ravi Verma",
          "address": "Jaipur",
          "index": 1,
          "mobile_number": "9000000101",
          "aadhaar_last4": "1111",
          "relation": "brother"
        },
        {
          "pan_last4": null,
          "full_name": "Neha Sharma",
          "address": null,
          "index": 2,
          "mobile_number": "9000000102",
          "aadhaar_last4": null,
          "relation": "friend"
        }
      ],
      "status": "pending"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 404 | Not found, or not yours |

## EMI

Schedule and paying an EMI.

### EMI summary

`GET /emi/summary`  
Sign-in: Customer token

`can_pay` stays false until the money is sent. When there is no approved loan, only `has_active_loan: false` is returned.

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `loan_status` | string | Status of the loan. |
| `has_active_loan` | boolean | `false` when there is no approved loan. |
| `total_installments` | number | Number of EMIs. |
| `paid_installments` | number | EMIs paid. |
| `can_pay` | boolean | `true` once the money has been sent. |
| `remaining_installments` | number | EMIs not yet paid. |
| `total_amount` | number | Loan amount in rupees. |
| `application_code` | string | Loan code shown to people. |
| `total_payable` | number | Total of all EMIs in rupees. |
| `loan_application_id` | number | Loan id. |
| `outstanding_amount` | number | Total of unpaid EMIs in rupees. |
| `disbursed_at` | datetime | When the money was sent. |
| `next_emi` | object | The next EMI to pay. Missing when none is left. |
| `next_emi.paid_at` | datetime | When the EMI was marked paid. |
| `next_emi.amount_paid` | number | Amount recorded as paid. |
| `next_emi.installment_number` | number | EMI number, from 1. |
| `next_emi.due_date` | string | Due date, `yyyy-MM-dd`. |
| `next_emi.amount_due` | number | EMI amount in rupees. |
| `next_emi.id` | number | Unique id of this record. |
| `next_emi.status` | string | Current status. See Status values. |

Response example (200):

```json
{
  "success": true,
  "message": "EMI summary",
  "data": {
    "loan_status": "approved",
    "has_active_loan": true,
    "total_installments": 6,
    "paid_installments": 0,
    "can_pay": false,
    "remaining_installments": 6,
    "total_amount": 20000.0,
    "application_code": "LN179387568679444E1",
    "total_payable": 20705.81,
    "loan_application_id": 3,
    "outstanding_amount": 20705.81,
    "disbursed_at": null,
    "next_emi": {
      "paid_at": null,
      "amount_paid": 0.0,
      "installment_number": 1,
      "due_date": "2026-12-05",
      "amount_due": 3450.97,
      "id": 1,
      "status": "pending"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

### EMI schedule

`GET /emi/schedule`  
Sign-in: Customer token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `loan_id` | number | Optional. Any of the customer's own loans, including closed ones. Default: the current loan. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `loan_status` | string | Status of the loan. |
| `remaining_installments` | number | EMIs not yet paid. |
| `installments` | array | The EMIs in order. |
| `installments[].paid_at` | datetime | When the EMI was marked paid. |
| `installments[].amount_paid` | number | Amount recorded as paid. |
| `installments[].installment_number` | number | EMI number, from 1. |
| `installments[].due_date` | string | Due date, `yyyy-MM-dd`. |
| `installments[].amount_due` | number | EMI amount in rupees. |
| `installments[].id` | number | Unique id of this record. |
| `installments[].status` | string | Current status. See Status values. |
| `loan_application_id` | number | Loan id. |

Response example (200):

```json
{
  "success": true,
  "message": "EMI schedule",
  "data": {
    "loan_status": "disbursed",
    "remaining_installments": 6,
    "installments": [
      {
        "paid_at": null,
        "amount_paid": 0.0,
        "installment_number": 1,
        "due_date": "2026-12-05",
        "amount_due": 3450.97,
        "id": 1,
        "status": "pending"
      },
      {
        "paid_at": null,
        "amount_paid": 0.0,
        "installment_number": 2,
        "due_date": "2027-01-05",
        "amount_due": 3450.97,
        "id": 2,
        "status": "pending"
      }
    ],
    "loan_application_id": 3
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 404 | No approved loan, or not yours |

### How to pay

`GET /emi/payment-instructions`  
Sign-in: Customer token

UPI, QR and bank details set by staff.

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `payment_methods` | object | UPI, QR and bank details set by staff. |
| `payment_methods.ifsc_code` | string | Bank IFSC code. |
| `payment_methods.bank_account_name` | string | Name on the bank account. |
| `payment_methods.bank_account_number` | string | Bank account number. |
| `payment_methods.payment_note` | string | Note to show when paying. |
| `payment_methods.payee_name` | string | Name to pay. |
| `payment_methods.bank_name` | string | Bank name. |
| `payment_methods.qr_image_url` | string | QR code image URL. |
| `payment_methods.upi_id` | string | UPI id. |
| `instructions_hindi` | string | Payment steps in Hinglish. |

Response example (200):

```json
{
  "success": true,
  "message": "Payment instructions",
  "data": {
    "payment_methods": {
      "ifsc_code": "HDFC0001234",
      "bank_account_name": "Narainsons Finance Pvt Ltd",
      "bank_account_number": "XXXX1234567890",
      "payment_note": "EMI pay karte waqt application code note mein likho. Screenshot yahi app se upload karo.",
      "payee_name": "Narainsons Finance",
      "bank_name": "HDFC Bank",
      "qr_image_url": "https://cdn.example.com/emi-payment-qr.png",
      "upi_id": "narainsons@upi"
    },
    "instructions_hindi": "UPI / QR / bank transfer se EMI pay karo, phir payment ka screenshot upload karo. Admin verify ke baad EMI paid mark hogi."
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

### Submit an EMI payment

`POST /emi/payments/submit`  
Sign-in: Customer token

The EMI becomes `awaiting_proof` until staff decide.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `emi_schedule_id` | number | Yes | `id` from the EMI schedule. |
| `screenshot_url` | string | Yes | `url` from the upload API. |
| `amount_claimed` | number | No | Defaults to the EMI amount. Less than the EMI is refused. |
| `utr_reference` | string | No | Up to 100 characters. |

Request example:

```json
{
  "emi_schedule_id": 1,
  "screenshot_url": "/NarainsonsProject/api/v1/files/a9a763f4462f40bbbbb6e7bfab3e1b28",
  "utr_reference": "UPI998877"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `payment_submission_id` | number | Id of the submitted payment. |
| `message` | string | Text to show the user. |
| `status` | string | New status after this action. |

Response example (201):

```json
{
  "success": true,
  "message": "Payment submitted",
  "data": {
    "payment_submission_id": 1,
    "message": "Screenshot upload ho gaya. Admin EMI verify karega — tab remaining EMI kam dikhegi.",
    "status": "pending"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 400 | Bad field, already paid, or proof already pending |
| 404 | EMI not found, or not yours |
| 409 | `LOAN_NOT_DISBURSED` |

### My payments

`GET /emi/payments`  
Sign-in: Customer token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `limit` | number | 1 to 50. Default 20. |
| `offset` | number | Default 0. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `payments` | array | List of payments. |
| `payments[].admin_note` | string | Note from staff. For a rejected payment, the reason. |
| `payments[].submitted_at` | string | When the proof was submitted. |
| `payments[].reviewed_at` | string | When staff decided. |
| `payments[].amount_claimed` | number | Amount the customer says they paid. |
| `payments[].id` | number | Unique id of this record. |
| `payments[].screenshot_url` | string | URL of the payment screenshot. |
| `payments[].utr_reference` | string | UTR or UPI reference. |
| `payments[].emi_schedule_id` | number | EMI id. |
| `payments[].status` | string | Current status. See Status values. |

Response example (200):

```json
{
  "success": true,
  "message": "EMI payments",
  "data": {
    "payments": [
      {
        "admin_note": "Received in bank",
        "submitted_at": "2026-11-05T16:18:07",
        "reviewed_at": "2026-11-05T16:18:07",
        "amount_claimed": 3450.97,
        "id": 2,
        "screenshot_url": "/NarainsonsProject/api/v1/files/a9a763f4462f40bbbbb6e7bfab3e1b28",
        "utr_reference": "UPI998878",
        "emi_schedule_id": 1,
        "status": "approved"
      },
      {
        "admin_note": "Screenshot is blurred",
        "submitted_at": "2026-11-05T16:18:07",
        "reviewed_at": "2026-11-05T16:18:07",
        "amount_claimed": 3450.97,
        "id": 1,
        "screenshot_url": "/NarainsonsProject/api/v1/files/a9a763f4462f40bbbbb6e7bfab3e1b28",
        "utr_reference": "UPI998877",
        "emi_schedule_id": 1,
        "status": "rejected"
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

### One payment

`GET /emi/payments/{id}`  
Sign-in: Customer token

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `payment` | object | The payment. |
| `payment.admin_note` | string | Note from staff. For a rejected payment, the reason. |
| `payment.submitted_at` | string | When the proof was submitted. |
| `payment.reviewed_at` | string | When staff decided. |
| `payment.amount_claimed` | number | Amount the customer says they paid. |
| `payment.id` | number | Unique id of this record. |
| `payment.screenshot_url` | string | URL of the payment screenshot. |
| `payment.utr_reference` | string | UTR or UPI reference. |
| `payment.emi_schedule_id` | number | EMI id. |
| `payment.status` | string | Current status. See Status values. |

Response example (200):

```json
{
  "success": true,
  "message": "EMI payment",
  "data": {
    "payment": {
      "admin_note": "Received in bank",
      "submitted_at": "2026-11-05T16:18:07",
      "reviewed_at": "2026-11-05T16:18:07",
      "amount_claimed": 3450.97,
      "id": 2,
      "screenshot_url": "/NarainsonsProject/api/v1/files/a9a763f4462f40bbbbb6e7bfab3e1b28",
      "utr_reference": "UPI998878",
      "emi_schedule_id": 1,
      "status": "approved"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |
| 404 | Not found, or not yours |

## Notifications

The in-app list. Every push is also saved here.

### List notifications

`GET /notifications`  
Sign-in: Customer token

Includes `unread_count` for the bell badge. `payload` depends on the category.

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `limit` | number | 1 to 100. Default 50. |
| `offset` | number | Default 0. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `unread_count` | number | Number of unread items, for the bell badge. |
| `notifications` | array | The customer's notifications, newest first. |
| `notifications[].read` | boolean | `true` once read. |
| `notifications[].payload` | string | Extra values for opening the right screen. Depends on `category`. |
| `notifications[].created_at` | string | When this record was created. |
| `notifications[].id` | number | Unique id of this record. |
| `notifications[].category` | string | `loan_status`, `emi_reminder`, `emi_payment`, `kyc` or `general`. |
| `notifications[].title` | string | Title. |
| `notifications[].body` | string | Message text. |
| `notifications[].payload.emi_payment_id` | number | Payment id. |
| `notifications[].payload.remaining_installments` | number | EMIs not yet paid. |
| `notifications[].payload.emi_schedule_id` | number | EMI id. |
| `notifications[].payload.status` | string | New status. |
| `notifications[].payload.loan_application_id` | number | Loan id. |
| `notifications[].payload.kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |

Response example (200):

```json
{
  "success": true,
  "message": "Notifications",
  "data": {
    "unread_count": 9,
    "notifications": [
      {
        "read": false,
        "payload": null,
        "created_at": "2026-11-05T16:18:07",
        "id": 9,
        "category": "general",
        "title": "Office closed",
        "body": "Our office is closed on Monday."
      },
      {
        "read": false,
        "payload": {
          "emi_payment_id": 2,
          "remaining_installments": 5
        },
        "created_at": "2026-11-05T16:18:07",
        "id": 8,
        "category": "emi_payment",
        "title": "EMI payment approved",
        "body": "Aapki EMI verify ho gayi. Shukriya! Baaki EMI: 5"
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

### Mark one read

`POST /notifications/read/{id}`  
Sign-in: Customer token

Response example (200):

```json
{
  "success": true,
  "message": "Marked read",
  "data": null,
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

### Mark one read (by body)

`POST /notifications/read`  
Sign-in: Customer token

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `notification_id` | number | Yes |  |

Request example:

```json
{
  "notification_id": 9
}
```

Response example (200):

```json
{
  "success": true,
  "message": "Marked read",
  "data": null,
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

### Mark all read

`POST /notifications/read-all`  
Sign-in: Customer token

Response example (200):

```json
{
  "success": true,
  "message": "All marked read",
  "data": null,
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Token missing, expired (`TOKEN_EXPIRED`) or no longer valid (`INVALID_TOKEN`) |
| 403 | Account not active |

## Public media

Banner images, no sign-in needed.

### Get a banner image

`GET /api/media/banners/{file_name}`  
Sign-in: None

Public. Banner `image_url` values already point here.

Response: The image file.

Errors:

| HTTP | When |
|---|---|
| 404 | Not found |

# Part 2: Admin panel and internal APIs

## Staff sign-in and profile

Admin panel login. Staff tokens do not work on app APIs, and app tokens do not work here.

### Create the first admin

`POST /api/admin/auth/register`  
Sign-in: None

Without a token this works only while no admin exists, and creates a super admin. A signed-in super admin can call it any time to add a normal admin.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `email` | string | Yes | Valid email, not already registered. |
| `password` | string | Yes | At least 8 characters. |
| `full_name` | string | Yes | Up to 150 characters. |
| `mobile_number` | string | No | Up to 20 characters. |

Request example:

```json
{
  "email": "first.admin@test.local",
  "password": "FirstAdmin123",
  "full_name": "First Admin",
  "mobile_number": "9000000900"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `admin` | object | The staff account. |
| `admin.full_name` | string | Full name. |
| `admin.role` | string | `super_admin` or `admin`. |
| `admin.updated_at` | string | When this record last changed. |
| `admin.last_login` | datetime | Last sign-in time. |
| `admin.has_profile_photo` | boolean | `true` if a photo is uploaded. |
| `admin.created_at` | string | When this record was created. |
| `admin.id` | number | Unique id of this record. |
| `admin.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `admin.mobile_number` | string | 10-digit mobile number. |
| `admin.email` | string | Email address. |
| `message` | string | Text to show the user. |

Response example (201):

```json
{
  "success": true,
  "message": "Admin registered",
  "data": {
    "admin": {
      "full_name": "First Admin",
      "role": "super_admin",
      "updated_at": "2026-11-05T16:17:54",
      "last_login": null,
      "has_profile_photo": false,
      "created_at": "2026-11-05T16:17:54",
      "id": 1,
      "account_status": "active",
      "mobile_number": "9000000900",
      "email": "first.admin@test.local"
    },
    "message": "Admin account created"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Bad field, email taken, or an admin already exists and no token was sent |

### Staff login

`POST /api/admin/auth/login`  
Sign-in: None

Staff tokens last 12 hours and have no refresh token. 5 wrong passwords lock the account for 15 minutes. Limit: 30 logins per IP per 15 minutes.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `email` | string | Yes |  |
| `password` | string | Yes |  |

Request example:

```json
{
  "email": "first.admin@test.local",
  "password": "FirstAdmin123"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `access_token` | string | JWT. Send as `Authorization: Bearer <token>`. |
| `admin` | object | The staff account. |
| `admin.full_name` | string | Full name. |
| `admin.role` | string | `super_admin` or `admin`. |
| `admin.updated_at` | string | When this record last changed. |
| `admin.last_login` | string | Last sign-in time. |
| `admin.has_profile_photo` | boolean | `true` if a photo is uploaded. |
| `admin.created_at` | string | When this record was created. |
| `admin.id` | number | Unique id of this record. |
| `admin.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `admin.mobile_number` | string | 10-digit mobile number. |
| `admin.email` | string | Email address. |
| `token_type` | string | Always `Bearer`. |
| `expires_in` | number | Seconds until the access token expires. |

Response example (200):

```json
{
  "success": true,
  "message": "Admin login",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9.<payload>.<signature>",
    "admin": {
      "full_name": "First Admin",
      "role": "super_admin",
      "updated_at": "2026-11-05T16:17:55",
      "last_login": "2026-11-05T16:17:55",
      "has_profile_photo": false,
      "created_at": "2026-11-05T16:17:54",
      "id": 1,
      "account_status": "active",
      "mobile_number": "9000000900",
      "email": "first.admin@test.local"
    },
    "token_type": "Bearer",
    "expires_in": 43200
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Wrong email or password |
| 403 | Account is not active |
| 429 | Locked or rate limited |

### My staff profile

`GET /api/admin/auth/me`  
Sign-in: Staff token

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `admin` | object | The staff account. |
| `admin.full_name` | string | Full name. |
| `admin.role` | string | `super_admin` or `admin`. |
| `admin.updated_at` | string | When this record last changed. |
| `admin.last_login` | string | Last sign-in time. |
| `admin.has_profile_photo` | boolean | `true` if a photo is uploaded. |
| `admin.created_at` | string | When this record was created. |
| `admin.id` | number | Unique id of this record. |
| `admin.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `admin.mobile_number` | string | 10-digit mobile number. |
| `admin.email` | string | Email address. |

Response example (200):

```json
{
  "success": true,
  "message": "Admin profile",
  "data": {
    "admin": {
      "full_name": "First Admin",
      "role": "super_admin",
      "updated_at": "2026-11-05T16:17:55",
      "last_login": "2026-11-05T16:17:55",
      "has_profile_photo": false,
      "created_at": "2026-11-05T16:17:54",
      "id": 1,
      "account_status": "active",
      "mobile_number": "9000000900",
      "email": "first.admin@test.local"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### Update my staff profile

`POST /api/admin/auth/profile`  
Sign-in: Staff token

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `full_name` | string | Yes | The update is skipped if this is blank. |
| `mobile_number` | string | No |  |

Request example:

```json
{
  "full_name": "First Admin",
  "mobile_number": "9000000901"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `admin` | object | The staff account. |
| `admin.full_name` | string | Full name. |
| `admin.role` | string | `super_admin` or `admin`. |
| `admin.updated_at` | string | When this record last changed. |
| `admin.last_login` | string | Last sign-in time. |
| `admin.has_profile_photo` | boolean | `true` if a photo is uploaded. |
| `admin.created_at` | string | When this record was created. |
| `admin.id` | number | Unique id of this record. |
| `admin.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `admin.mobile_number` | string | 10-digit mobile number. |
| `admin.email` | string | Email address. |

Response example (200):

```json
{
  "success": true,
  "message": "Profile updated",
  "data": {
    "admin": {
      "full_name": "First Admin",
      "role": "super_admin",
      "updated_at": "2026-11-05T16:17:56",
      "last_login": "2026-11-05T16:17:55",
      "has_profile_photo": false,
      "created_at": "2026-11-05T16:17:54",
      "id": 1,
      "account_status": "active",
      "mobile_number": "9000000901",
      "email": "first.admin@test.local"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### Upload my staff photo

`POST /api/admin/auth/profile/photo`  
Sign-in: Staff token

Multipart form with `photo`. JPG, PNG, WebP or GIF, up to 3 MB.

Form field: `photo` (the file)

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `admin` | object | The staff account. |
| `admin.full_name` | string | Full name. |
| `admin.role` | string | `super_admin` or `admin`. |
| `admin.updated_at` | string | When this record last changed. |
| `admin.last_login` | string | Last sign-in time. |
| `admin.has_profile_photo` | boolean | `true` if a photo is uploaded. |
| `admin.created_at` | string | When this record was created. |
| `admin.id` | number | Unique id of this record. |
| `admin.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `admin.mobile_number` | string | 10-digit mobile number. |
| `admin.email` | string | Email address. |

Response example (200):

```json
{
  "success": true,
  "message": "Profile photo saved",
  "data": {
    "admin": {
      "full_name": "First Admin",
      "role": "super_admin",
      "updated_at": "2026-11-05T16:17:57",
      "last_login": "2026-11-05T16:17:55",
      "has_profile_photo": true,
      "created_at": "2026-11-05T16:17:54",
      "id": 1,
      "account_status": "active",
      "mobile_number": "9000000901",
      "email": "first.admin@test.local"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 400 | Wrong file type or too large |

### Get my staff photo

`GET /api/admin/auth/profile/photo`  
Sign-in: Staff token

Response: The image file.

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | No photo uploaded yet |

### Change my staff password

`POST /api/admin/auth/password/change`  
Sign-in: Staff token

Ends every staff session for this account and returns a new token. Replace the stored one.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `current_password` | string | Yes |  |
| `new_password` | string | Yes | At least 8 characters. |

Request example:

```json
{
  "current_password": "FirstAdmin123",
  "new_password": "FirstAdmin456"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `access_token` | string | JWT. Send as `Authorization: Bearer <token>`. |
| `admin` | object | The staff account. |
| `admin.full_name` | string | Full name. |
| `admin.role` | string | `super_admin` or `admin`. |
| `admin.updated_at` | string | When this record last changed. |
| `admin.last_login` | string | Last sign-in time. |
| `admin.has_profile_photo` | boolean | `true` if a photo is uploaded. |
| `admin.created_at` | string | When this record was created. |
| `admin.id` | number | Unique id of this record. |
| `admin.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `admin.mobile_number` | string | 10-digit mobile number. |
| `admin.email` | string | Email address. |
| `password_updated` | boolean | `true` when the password was changed. |
| `token_type` | string | Always `Bearer`. |
| `expires_in` | number | Seconds until the access token expires. |

Response example (200):

```json
{
  "success": true,
  "message": "Password updated",
  "data": {
    "access_token": "eyJhbGciOiJIUzI1NiJ9.<payload>.<signature>",
    "admin": {
      "full_name": "First Admin",
      "role": "super_admin",
      "updated_at": "2026-11-05T16:17:57",
      "last_login": "2026-11-05T16:17:57",
      "has_profile_photo": true,
      "created_at": "2026-11-05T16:17:54",
      "id": 1,
      "account_status": "active",
      "mobile_number": "9000000901",
      "email": "first.admin@test.local"
    },
    "password_updated": true,
    "token_type": "Bearer",
    "expires_in": 43200
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 400 | Current password wrong, or new one too short |

### Staff forgot password: send OTP

`POST /api/admin/auth/password/forgot/send`  
Sign-in: None

Same response whether or not the email exists. Limit: 10 per IP per hour.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `email` | string | Yes |  |

Request example:

```json
{
  "email": "first.admin@test.local"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `dev_otp` | string | The OTP. Only when the server has `app.dev.expose_otp=true`; never in production. |
| `message` | string | Text to show the user. |
| `email` | string | The email address used. |

Response example (200):

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "dev_otp": "123456",
    "message": "If this admin email is registered, an OTP has been sent.",
    "email": "first.admin@test.local"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Bad email |
| 429 | Rate limited |

### Staff forgot password: reset

`POST /api/admin/auth/password/forgot/reset`  
Sign-in: None

Ends every staff session for this account.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `email` | string | Yes |  |
| `otp` | string | Yes | Valid 10 minutes; 5 wrong tries cancel it. |
| `new_password` | string | Yes | At least 8 characters. |

Request example:

```json
{
  "email": "first.admin@test.local",
  "otp": "123456",
  "new_password": "FirstAdmin789"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `password_updated` | boolean | `true` when the password was changed. |

Response example (200):

```json
{
  "success": true,
  "message": "Password updated",
  "data": {
    "password_updated": true
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 400 | Wrong or expired OTP, or weak password |
| 429 | Rate limited |

## Staff lists

One API for every table: search, filters, dates, sorting, paging. See the list parameters below.

### How lists work: example

`GET /api/admin/list/{name}`  
Sign-in: Staff token

`counts` gives per-status totals for the cards, and appears on lists that have a main status. Replace `{name}` with a list name below.

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `limit` | number | 25, 50, 100, 200 or 500. Any other value becomes 25. Default 25. |
| `offset` | number | Rows to skip. Default 0. |
| `search` | string | Text search across the main columns. |
| `date_from` | date | From this day, `yyyy-MM-dd`. |
| `date_to` | date | To this day inclusive, `yyyy-MM-dd`. |
| `sort` | string | Any column in the response. Unknown columns are ignored. |
| `dir` | string | `asc` or `desc`. Default `desc` when `sort` is given. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].application_code` | string | Loan code shown to people. |
| `rows[].user_id` | number | Customer id. |
| `rows[].customer` | string | Customer name. |
| `rows[].mobile_number` | string | 10-digit mobile number. |
| `rows[].email` | string | Email address. |
| `rows[].kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `rows[].requested_amount` | number | Loan amount in rupees. |
| `rows[].tenure_months` | number | Loan length in months. |
| `rows[].interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `rows[].processing_fee` | number | Processing fee in rupees. |
| `rows[].emi_amount` | number | Monthly EMI in rupees. |
| `rows[].total_payable` | number | Total of all EMIs in rupees. |
| `rows[].purpose` | string | Why the loan is needed. |
| `rows[].purpose_detail` | string | More about the purpose. |
| `rows[].monthly_income` | number | Monthly income in rupees. |
| `rows[].employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `rows[].company_name` | string | Employer or business name. |
| `rows[].bank_name` | string | Bank name. |
| `rows[].ifsc_code` | string | Bank IFSC code. |
| `rows[].bank_account_last4` | string | Last 4 digits of the bank account. |
| `rows[].status` | string | Current status. See Status values. |
| `rows[].admin_note` | string | Note from staff. For a rejected payment, the reason. |
| `rows[].disbursement_reference` | string | Bank or UTR reference of the money sent. |
| `rows[].reviewed_at` | string | When staff decided. |
| `rows[].disbursed_at` | string | When the money was sent. |
| `rows[].closed_at` | datetime | When the loan closed. |
| `rows[].created_at` | string | When this record was created. |
| `counts` | object | Rows per status with the other filters applied, for the cards. |
| `counts.<value>` | number | Number of rows with this value. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 1,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 3,
        "application_code": "LN179387568679444E1",
        "user_id": 1,
        "customer": "Asha Verma",
        "mobile_number": "9000000001",
        "email": "asha.verma@test.local",
        "kyc_status": "verified",
        "requested_amount": 20000.0,
        "tenure_months": 6,
        "interest_rate_annual": 12.0,
        "processing_fee": 400.0,
        "emi_amount": 3450.97,
        "total_payable": 20705.81,
        "purpose": "personal",
        "purpose_detail": "Home repair",
        "monthly_income": 35000.0,
        "employment_type": "Salaried",
        "company_name": "Acme Pvt Ltd",
        "bank_name": "HDFC Bank",
        "ifsc_code": "HDFC0001234",
        "bank_account_last4": "6789",
        "status": "disbursed",
        "admin_note": "Checked",
        "disbursement_reference": "UTR123456",
        "reviewed_at": "2026-11-05 16:18",
        "disbursed_at": "2026-11-05 16:18",
        "closed_at": null,
        "created_at": "2026-11-05 16:18"
      }
    ],
    "counts": {
      "rejected": 1,
      "disbursed": 1,
      "cancelled": 1
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |
| 400 | Bad date |

### Download a list

`GET /api/admin/list/{name}/export`  
Sign-in: Staff token

Takes the same filters as the list, without paging. Up to 100,000 rows. Text that a spreadsheet would run as a formula is neutralised.

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `format` | string | `csv` or `xlsx`. |
| `search` | string | Text search across the main columns. |
| `date_from` | date | From this day, `yyyy-MM-dd`. |
| `date_to` | date | To this day inclusive, `yyyy-MM-dd`. |
| `sort` | string | Any column in the response. Unknown columns are ignored. |
| `dir` | string | `asc` or `desc`. Default `desc` when `sort` is given. |

Response: A CSV or Excel file.

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |
| 400 | Bad date |

### List: Customers

`GET /api/admin/list/users`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `Filters` | string | `kyc_status`, `account_status`, `onboarding_step`, `employment_type`, `gender`, `income_min`, `income_max` |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].customer_code` | string | Customer code, set at onboarding step 1. |
| `rows[].name` | string | Full name. |
| `rows[].mobile_number` | string | 10-digit mobile number. |
| `rows[].email` | string | Email address. |
| `rows[].gender` | string | `Male`, `Female` or `Other`. |
| `rows[].date_of_birth` | string | Date of birth, `yyyy-MM-dd`. |
| `rows[].current_city` | string | Current city. |
| `rows[].current_state` | string | Current state. |
| `rows[].employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `rows[].company_name` | string | Employer or business name. |
| `rows[].monthly_income` | number | Monthly income in rupees. |
| `rows[].bank_name` | string | Bank name. |
| `rows[].bank_account_last4` | string | Last 4 digits of the bank account. |
| `rows[].ifsc_code` | string | Bank IFSC code. |
| `rows[].bank_verification_status` | string | `pending`, `verified` or `failed`. Only staff can change it. |
| `rows[].kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `rows[].account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `rows[].onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `rows[].last_login` | string | Last sign-in time. |
| `rows[].created_at` | string | When this record was created. |
| `counts` | object | Rows per status with the other filters applied, for the cards. |
| `counts.<value>` | number | Number of rows with this value. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 2,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 2,
        "customer_code": null,
        "name": "New User",
        "mobile_number": "TEMP1793875685201",
        "email": "second.user@test.local",
        "gender": null,
        "date_of_birth": null,
        "current_city": null,
        "current_state": null,
        "employment_type": null,
        "company_name": null,
        "monthly_income": null,
        "bank_name": null,
        "bank_account_last4": null,
        "ifsc_code": null,
        "bank_verification_status": "pending",
        "kyc_status": "pending",
        "account_status": "active",
        "onboarding_step": 0,
        "last_login": "2026-11-05 16:18",
        "created_at": "2026-11-05 16:18"
      },
      {
        "id": 1,
        "customer_code": "CUST11793875682002",
        "name": "Asha Verma",
        "mobile_number": "9000000001",
        "email": "asha.verma@test.local",
        "gender": "Female",
        "date_of_birth": "1995-04-12",
        "current_city": "Jaipur",
        "current_state": "Rajasthan",
        "employment_type": "Salaried",
        "company_name": "Acme Pvt Ltd",
        "monthly_income": 35000.0,
        "bank_name": "HDFC Bank",
        "bank_account_last4": "6789",
        "ifsc_code": "HDFC0001234",
        "bank_verification_status": "verified",
        "kyc_status": "verified",
        "account_status": "active",
        "onboarding_step": 3,
        "last_login": "2026-11-05 16:18",
        "created_at": "2026-11-05 16:18"
      }
    ],
    "counts": {
      "pending": 1,
      "verified": 1
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### List: Loans

`GET /api/admin/list/loans`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `Filters` | string | `status`, `purpose`, `kyc_status`, `user_id`, `amount_min`, `amount_max`, `tenure_min`, `tenure_max` |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].application_code` | string | Loan code shown to people. |
| `rows[].user_id` | number | Customer id. |
| `rows[].customer` | string | Customer name. |
| `rows[].mobile_number` | string | 10-digit mobile number. |
| `rows[].email` | string | Email address. |
| `rows[].kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `rows[].requested_amount` | number | Loan amount in rupees. |
| `rows[].tenure_months` | number | Loan length in months. |
| `rows[].interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `rows[].processing_fee` | number | Processing fee in rupees. |
| `rows[].emi_amount` | number | Monthly EMI in rupees. |
| `rows[].total_payable` | number | Total of all EMIs in rupees. |
| `rows[].purpose` | string | Why the loan is needed. |
| `rows[].purpose_detail` | string | More about the purpose. |
| `rows[].monthly_income` | number | Monthly income in rupees. |
| `rows[].employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `rows[].company_name` | string | Employer or business name. |
| `rows[].bank_name` | string | Bank name. |
| `rows[].ifsc_code` | string | Bank IFSC code. |
| `rows[].bank_account_last4` | string | Last 4 digits of the bank account. |
| `rows[].status` | string | Current status. See Status values. |
| `rows[].admin_note` | string | Note from staff. For a rejected payment, the reason. |
| `rows[].disbursement_reference` | string | Bank or UTR reference of the money sent. |
| `rows[].reviewed_at` | string | When staff decided. |
| `rows[].disbursed_at` | string | When the money was sent. |
| `rows[].closed_at` | datetime | When the loan closed. |
| `rows[].created_at` | string | When this record was created. |
| `counts` | object | Rows per status with the other filters applied, for the cards. |
| `counts.<value>` | number | Number of rows with this value. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 3,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 3,
        "application_code": "LN179387568679444E1",
        "user_id": 1,
        "customer": "Asha Verma",
        "mobile_number": "9000000001",
        "email": "asha.verma@test.local",
        "kyc_status": "verified",
        "requested_amount": 20000.0,
        "tenure_months": 6,
        "interest_rate_annual": 12.0,
        "processing_fee": 400.0,
        "emi_amount": 3450.97,
        "total_payable": 20705.81,
        "purpose": "personal",
        "purpose_detail": "Home repair",
        "monthly_income": 35000.0,
        "employment_type": "Salaried",
        "company_name": "Acme Pvt Ltd",
        "bank_name": "HDFC Bank",
        "ifsc_code": "HDFC0001234",
        "bank_account_last4": "6789",
        "status": "disbursed",
        "admin_note": "Checked",
        "disbursement_reference": "UTR123456",
        "reviewed_at": "2026-11-05 16:18",
        "disbursed_at": "2026-11-05 16:18",
        "closed_at": null,
        "created_at": "2026-11-05 16:18"
      },
      {
        "id": 2,
        "application_code": "LN1793875686609D351",
        "user_id": 1,
        "customer": "Asha Verma",
        "mobile_number": "9000000001",
        "email": "asha.verma@test.local",
        "kyc_status": "verified",
        "requested_amount": 20000.0,
        "tenure_months": 6,
        "interest_rate_annual": 12.0,
        "processing_fee": 400.0,
        "emi_amount": 3450.97,
        "total_payable": 20705.81,
        "purpose": "personal",
        "purpose_detail": "Home repair",
        "monthly_income": 35000.0,
        "employment_type": "Salaried",
        "company_name": "Acme Pvt Ltd",
        "bank_name": "HDFC Bank",
        "ifsc_code": "HDFC0001234",
        "bank_account_last4": "6789",
        "status": "rejected",
        "admin_note": "Income proof missing",
        "disbursement_reference": null,
        "reviewed_at": "2026-11-05 16:18",
        "disbursed_at": null,
        "closed_at": null,
        "created_at": "2026-11-05 16:18"
      }
    ],
    "counts": {
      "rejected": 1,
      "disbursed": 1,
      "cancelled": 1
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### List: EMI payments

`GET /api/admin/list/emi_payments`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `Filters` | string | `status`, `user_id`, `loan_id`, `amount_min`, `amount_max` |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].application_code` | string | Loan code shown to people. |
| `rows[].loan_application_id` | number | Loan id. |
| `rows[].user_id` | number | Customer id. |
| `rows[].customer` | string | Customer name. |
| `rows[].mobile_number` | string | 10-digit mobile number. |
| `rows[].installment_number` | number | EMI number, from 1. |
| `rows[].due_date` | string | Due date, `yyyy-MM-dd`. |
| `rows[].amount_due` | number | EMI amount in rupees. |
| `rows[].amount_claimed` | number | Amount the customer says they paid. |
| `rows[].utr_reference` | string | UTR or UPI reference. |
| `rows[].screenshot_url` | string | URL of the payment screenshot. |
| `rows[].status` | string | Current status. See Status values. |
| `rows[].admin_note` | string | Note from staff. For a rejected payment, the reason. |
| `rows[].submitted_at` | string | When the proof was submitted. |
| `rows[].reviewed_at` | string | When staff decided. |
| `counts` | object | Rows per status with the other filters applied, for the cards. |
| `counts.<value>` | number | Number of rows with this value. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 2,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 2,
        "application_code": "LN179387568679444E1",
        "loan_application_id": 3,
        "user_id": 1,
        "customer": "Asha Verma",
        "mobile_number": "9000000001",
        "installment_number": 1,
        "due_date": "2026-12-05",
        "amount_due": 3450.97,
        "amount_claimed": 3450.97,
        "utr_reference": "UPI998878",
        "screenshot_url": "/NarainsonsProject/api/v1/files/a9a763f4462f40bbbbb6e7bfab3e1b28",
        "status": "approved",
        "admin_note": "Received in bank",
        "submitted_at": "2026-11-05 16:18",
        "reviewed_at": "2026-11-05 16:18"
      },
      {
        "id": 1,
        "application_code": "LN179387568679444E1",
        "loan_application_id": 3,
        "user_id": 1,
        "customer": "Asha Verma",
        "mobile_number": "9000000001",
        "installment_number": 1,
        "due_date": "2026-12-05",
        "amount_due": 3450.97,
        "amount_claimed": 3450.97,
        "utr_reference": "UPI998877",
        "screenshot_url": "/NarainsonsProject/api/v1/files/a9a763f4462f40bbbbb6e7bfab3e1b28",
        "status": "rejected",
        "admin_note": "Screenshot is blurred",
        "submitted_at": "2026-11-05 16:18",
        "reviewed_at": "2026-11-05 16:18"
      }
    ],
    "counts": {
      "rejected": 1,
      "approved": 1
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### List: EMI schedule

`GET /api/admin/list/emi_schedules`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `Filters` | string | `status`, `loan_status`, `loan_id`, `user_id`, `amount_min`, `amount_max`. Dates filter the due date. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].application_code` | string | Loan code shown to people. |
| `rows[].loan_application_id` | number | Loan id. |
| `rows[].user_id` | number | Customer id. |
| `rows[].customer` | string | Customer name. |
| `rows[].mobile_number` | string | 10-digit mobile number. |
| `rows[].installment_number` | number | EMI number, from 1. |
| `rows[].due_date` | string | Due date, `yyyy-MM-dd`. |
| `rows[].amount_due` | number | EMI amount in rupees. |
| `rows[].amount_paid` | number | Amount recorded as paid. |
| `rows[].status` | string | Current status. See Status values. |
| `rows[].loan_status` | string | Status of the loan. |
| `rows[].last_reminder_at` | datetime | When the last reminder went out. |
| `rows[].paid_at` | string | When the EMI was marked paid. |
| `counts` | object | Rows per status with the other filters applied, for the cards. |
| `counts.<value>` | number | Number of rows with this value. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 6,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 1,
        "application_code": "LN179387568679444E1",
        "loan_application_id": 3,
        "user_id": 1,
        "customer": "Asha Verma",
        "mobile_number": "9000000001",
        "installment_number": 1,
        "due_date": "2026-12-05",
        "amount_due": 3450.97,
        "amount_paid": 3450.97,
        "status": "paid",
        "loan_status": "disbursed",
        "last_reminder_at": null,
        "paid_at": "2026-11-05 16:18"
      },
      {
        "id": 2,
        "application_code": "LN179387568679444E1",
        "loan_application_id": 3,
        "user_id": 1,
        "customer": "Asha Verma",
        "mobile_number": "9000000001",
        "installment_number": 2,
        "due_date": "2027-01-05",
        "amount_due": 3450.97,
        "amount_paid": 0.0,
        "status": "pending",
        "loan_status": "disbursed",
        "last_reminder_at": null,
        "paid_at": null
      }
    ],
    "counts": {
      "paid": 1,
      "pending": 5
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### List: Guarantors

`GET /api/admin/list/guarantors`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `Filters` | string | `relation`, `loan_id` |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].loan_application_id` | number | Loan id. |
| `rows[].application_code` | string | Loan code shown to people. |
| `rows[].customer` | string | Customer name. |
| `rows[].guarantor_index` | number | Guarantor number, from 1. |
| `rows[].full_name` | string | Full name. |
| `rows[].mobile_number` | string | 10-digit mobile number. |
| `rows[].relation` | string | Relation to the customer. |
| `rows[].address` | string | Address. |
| `rows[].aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `rows[].pan_last4` | string | Last 4 characters of PAN. |
| `rows[].created_at` | string | When this record was created. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 6,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 6,
        "loan_application_id": 3,
        "application_code": "LN179387568679444E1",
        "customer": "Asha Verma",
        "guarantor_index": 2,
        "full_name": "Neha Sharma",
        "mobile_number": "9000000102",
        "relation": "friend",
        "address": null,
        "aadhaar_last4": null,
        "pan_last4": null,
        "created_at": "2026-11-05 16:18"
      },
      {
        "id": 5,
        "loan_application_id": 3,
        "application_code": "LN179387568679444E1",
        "customer": "Asha Verma",
        "guarantor_index": 1,
        "full_name": "Ravi Verma",
        "mobile_number": "9000000101",
        "relation": "brother",
        "address": "Jaipur",
        "aadhaar_last4": "1111",
        "pan_last4": "222A",
        "created_at": "2026-11-05 16:18"
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### List: Customer notifications

`GET /api/admin/list/user_notifications`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `Filters` | string | `category`, `user_id` |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].user_id` | number | Customer id. |
| `rows[].customer` | string | Customer name. |
| `rows[].category` | string | `loan_status`, `emi_reminder`, `emi_payment`, `kyc` or `general`. |
| `rows[].title` | string | Title. |
| `rows[].body` | string | Message text. |
| `rows[].read_at` | string | When it was read. |
| `rows[].created_at` | string | When this record was created. |
| `counts` | object | Rows per status with the other filters applied, for the cards. |
| `counts.<value>` | number | Number of rows with this value. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 9,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 9,
        "user_id": 1,
        "customer": "Asha Verma",
        "category": "general",
        "title": "Office closed",
        "body": "Our office is closed on Monday.",
        "read_at": "2026-11-05 16:18",
        "created_at": "2026-11-05 16:18"
      },
      {
        "id": 8,
        "user_id": 1,
        "customer": "Asha Verma",
        "category": "emi_payment",
        "title": "EMI payment approved",
        "body": "Aapki EMI verify ho gayi. Shukriya! Baaki EMI: 5",
        "read_at": "2026-11-05 16:18",
        "created_at": "2026-11-05 16:18"
      }
    ],
    "counts": {
      "loan_status": 3,
      "kyc": 1,
      "emi_payment": 4,
      "general": 1
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### List: Banners

`GET /api/admin/list/banners`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `Filters` | string | `is_active`, `action_type` |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].title` | string | Title. |
| `rows[].subtitle` | string | Line under the title. |
| `rows[].image_url` | string | Image URL. |
| `rows[].action_type` | string | On tap: `none`, `url` opens a link, `screen` opens an app screen. |
| `rows[].action_value` | string | The link or screen name to open. |
| `rows[].sort_order` | number | Order; lower comes first. |
| `rows[].is_active` | number | `true` or 1 if shown in the app. |
| `rows[].starts_at` | datetime | Show from this time. |
| `rows[].ends_at` | datetime | Show until this time. |
| `rows[].created_at` | string | When this record was created. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 3,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 1,
        "title": "Instant Loan upto ₹50,000",
        "subtitle": "Apply in minutes — admin approval shortly",
        "image_url": "https://cdn.example.com/banners/loan-hero.png",
        "action_type": "screen",
        "action_value": "loan_apply",
        "sort_order": 1,
        "is_active": 1,
        "starts_at": null,
        "ends_at": null,
        "created_at": "2026-11-05 16:17"
      },
      {
        "id": 2,
        "title": "Complete KYC",
        "subtitle": "Faster approval when profile is complete",
        "image_url": "https://cdn.example.com/banners/kyc.png",
        "action_type": "screen",
        "action_value": "onboarding",
        "sort_order": 2,
        "is_active": 1,
        "starts_at": null,
        "ends_at": null,
        "created_at": "2026-11-05 16:17"
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### List: Guide videos

`GET /api/admin/list/videos`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `Filters` | string | `is_active`, `video_type` |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].title` | string | Title. |
| `rows[].description` | string | Description. |
| `rows[].video_url` | string | Video URL. |
| `rows[].thumbnail_url` | string | Thumbnail image URL. |
| `rows[].video_type` | string | `mp4`, `youtube`, `vimeo` or `hls`. |
| `rows[].duration_seconds` | number | Length in seconds. |
| `rows[].locale` | string | Language code. |
| `rows[].sort_order` | number | Order; lower comes first. |
| `rows[].is_active` | number | `true` or 1 if shown in the app. |
| `rows[].created_at` | string | When this record was created. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 1,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 1,
        "title": "Loan kaise apply karein",
        "description": "Step-by-step: amount select karo, guarantor details bharo, submit — admin approval pending.",
        "video_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-hi.mp4",
        "thumbnail_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-thumb.jpg",
        "video_type": "mp4",
        "duration_seconds": 180,
        "locale": "hi",
        "sort_order": 1,
        "is_active": 1,
        "created_at": "2026-11-05 16:17"
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### List: Payment details history

`GET /api/admin/list/emi_settings`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `Filters` | string | `is_active` |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].payee_name` | string | Name to pay. |
| `rows[].upi_id` | string | UPI id. |
| `rows[].qr_image_url` | string | QR code image URL. |
| `rows[].bank_name` | string | Bank name. |
| `rows[].bank_account_name` | string | Name on the bank account. |
| `rows[].bank_account_number` | string | Bank account number. |
| `rows[].ifsc_code` | string | Bank IFSC code. |
| `rows[].payment_note` | string | Note to show when paying. |
| `rows[].is_active` | number | `true` or 1 if shown in the app. |
| `rows[].updated_at` | string | When this record last changed. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 1,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 1,
        "payee_name": "Narainsons Finance",
        "upi_id": "narainsons@upi",
        "qr_image_url": "https://cdn.example.com/emi-payment-qr.png",
        "bank_name": "HDFC Bank",
        "bank_account_name": "Narainsons Finance Pvt Ltd",
        "bank_account_number": "XXXX1234567890",
        "ifsc_code": "HDFC0001234",
        "payment_note": "EMI pay karte waqt application code note mein likho. Screenshot yahi app se upload karo.",
        "is_active": 1,
        "updated_at": "2026-11-05 16:17"
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### List: Staff alerts

`GET /api/admin/list/admin_alerts`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `Filters` | string | `type` |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].type` | string | `new_user`, `new_loan`, `kyc_submitted` or `emi_proof`. |
| `rows[].title` | string | Title. |
| `rows[].body` | string | Message text. |
| `rows[].link` | string | Admin page to open. |
| `rows[].created_at` | string | When this record was created. |
| `counts` | object | Rows per status with the other filters applied, for the cards. |
| `counts.<value>` | number | Number of rows with this value. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 8,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 8,
        "type": "emi_proof",
        "title": "EMI proof received",
        "body": "Asha Verma sent proof for an EMI payment",
        "link": "admin-emi-payments.html?status=pending",
        "created_at": "2026-11-05 16:18"
      },
      {
        "id": 7,
        "type": "emi_proof",
        "title": "EMI proof received",
        "body": "Asha Verma sent proof for an EMI payment",
        "link": "admin-emi-payments.html?status=pending",
        "created_at": "2026-11-05 16:18"
      }
    ],
    "counts": {
      "emi_proof": 2,
      "kyc_submitted": 1,
      "new_loan": 3,
      "new_user": 2
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### List: Staff accounts

`GET /api/admin/list/admins`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `Filters` | string | `role`, `account_status`. Super admin only. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `limit` | number | Page size used. |
| `offset` | number | Rows skipped. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].full_name` | string | Full name. |
| `rows[].email` | string | Email address. |
| `rows[].mobile_number` | string | 10-digit mobile number. |
| `rows[].role` | string | `super_admin` or `admin`. |
| `rows[].account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `rows[].last_login` | string | Last sign-in time. |
| `rows[].created_at` | string | When this record was created. |
| `counts` | object | Rows per status with the other filters applied, for the cards. |
| `counts.<value>` | number | Number of rows with this value. |

Response example (200):

```json
{
  "success": true,
  "message": "List",
  "data": {
    "total": 1,
    "limit": 25,
    "offset": 0,
    "rows": [
      {
        "id": 1,
        "full_name": "First Admin",
        "email": "first.admin@test.local",
        "mobile_number": "9000000901",
        "role": "super_admin",
        "account_status": "active",
        "last_login": "2026-11-05 16:17",
        "created_at": "2026-11-05 16:17"
      }
    ],
    "counts": {
      "active": 1
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 403 | Signed in, but not a super admin |

## Staff dashboard and alerts

Dashboard figures and the alert bell.

### Dashboard figures

`GET /api/admin/dashboard/summary`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `date_from` | date | Default: 29 days before `date_to`. |
| `date_to` | date | Default: today. At most 366 days apart. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `date_from` | string | Start of the period. |
| `date_to` | string | End of the period. |
| `pending_loans` | number | Loans waiting for review. |
| `approved_to_disburse` | number | Approved loans where money is not yet sent. |
| `kyc_to_review` | number | Customers with KYC waiting for review. |
| `emi_proofs_pending` | number | EMI payments waiting for verification. |
| `total_users` | number | All customers. |
| `active_loans` | number | Loans with EMIs running. |
| `closed_loans` | number | Loans fully repaid. |
| `rejected_loans` | number | Rejected loans. |
| `outstanding_amount` | number | Total of unpaid EMIs in rupees. |
| `overdue_emis` | number | EMIs past their due date. |
| `overdue_amount` | number | Total of overdue EMIs in rupees. |
| `due_next_7_days` | number | EMIs due in the next 7 days. |
| `new_users` | number | New customers in the period. |
| `applications` | number | Loan applications in the period. |
| `amount_requested` | number | Total requested in the period. |
| `amount_disbursed` | number | Total sent in the period. |
| `amount_collected` | number | Total EMI collected in the period. |
| `series_applications` | array | Loan applications per day, one entry per day. |
| `series_applications[].date` | string | The day. |
| `series_applications[].count` | number | Number on that day. |
| `series_users` | array | New customers per day, one entry per day. |
| `series_users[].date` | string | The day. |
| `series_users[].count` | number | Number on that day. |

Response example (200):

```json
{
  "success": true,
  "message": "Dashboard",
  "data": {
    "date_from": "2026-10-07",
    "date_to": "2026-11-05",
    "pending_loans": 0,
    "approved_to_disburse": 0,
    "kyc_to_review": 0,
    "emi_proofs_pending": 0,
    "total_users": 2,
    "active_loans": 1,
    "closed_loans": 0,
    "rejected_loans": 1,
    "outstanding_amount": 17254.84,
    "overdue_emis": 0,
    "overdue_amount": 0.0,
    "due_next_7_days": 0,
    "new_users": 2,
    "applications": 3,
    "amount_requested": 60000.0,
    "amount_disbursed": 20000.0,
    "amount_collected": 3450.97,
    "series_applications": [
      {
        "date": "2026-10-07",
        "count": 0
      },
      {
        "date": "2026-10-08",
        "count": 0
      }
    ],
    "series_users": [
      {
        "date": "2026-10-07",
        "count": 0
      },
      {
        "date": "2026-10-08",
        "count": 0
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 400 | Bad date |

### Alerts for the bell

`GET /api/admin/alerts`  
Sign-in: Staff token

The panel polls this every 15 seconds and plays the alert sound for `new_items`.

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `after_id` | number | Last id this browser has seen. `new_items` holds anything newer. 0 returns none. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `latest_id` | number | Newest alert id. Send it as `after_id` next time. |
| `unread_count` | number | Number of unread items, for the bell badge. |
| `recent` | array | The latest alerts, newest first. |
| `recent[].id` | number | Unique id of this record. |
| `recent[].type` | string | `new_user`, `new_loan`, `kyc_submitted` or `emi_proof`. |
| `recent[].title` | string | Title. |
| `recent[].body` | string | Message text. |
| `recent[].link` | string | Admin page to open. |
| `recent[].created_at` | string | When this record was created. |
| `new_items` | array | Alerts newer than `after_id`. Play the sound for these. |

Response example (200):

```json
{
  "success": true,
  "message": "Alerts",
  "data": {
    "latest_id": 8,
    "unread_count": 8,
    "recent": [
      {
        "id": 8,
        "type": "emi_proof",
        "title": "EMI proof received",
        "body": "Asha Verma sent proof for an EMI payment",
        "link": "admin-emi-payments.html?status=pending",
        "created_at": "2026-11-05 16:18"
      },
      {
        "id": 7,
        "type": "emi_proof",
        "title": "EMI proof received",
        "body": "Asha Verma sent proof for an EMI payment",
        "link": "admin-emi-payments.html?status=pending",
        "created_at": "2026-11-05 16:18"
      }
    ],
    "new_items": []
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### Mark all alerts read

`POST /api/admin/alerts/read`  
Sign-in: Staff token

Per staff account.

Response example (200):

```json
{
  "success": true,
  "message": "Marked read",
  "data": null,
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

## Staff: customers

Customer details, KYC review, on/off and delete.

### Verify or reject KYC

`POST /api/admin/users/{id}/kyc`  
Sign-in: Staff token

Only for submitted KYC. Verifying also marks the bank details verified. The customer gets a push.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `action` | string | Yes | `verify` or `reject`. |
| `reason` | string | To reject | Shown to the customer. |

Request example:

```json
{
  "action": "verify"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `user_id` | number | Customer id. |

Response example (200):

```json
{
  "success": true,
  "message": "KYC updated",
  "data": {
    "kyc_status": "verified",
    "user_id": 1
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |
| 400 | KYC not submitted, or reason missing |

### One customer, all details

`GET /api/admin/users/{id}`  
Sign-in: Staff token

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `loan_applications` | array | This customer's latest loans. |
| `loan_applications[].purpose` | string | Why the loan is needed. |
| `loan_applications[].application_code` | string | Loan code shown to people. |
| `loan_applications[].created_at` | string | When this record was created. |
| `loan_applications[].id` | number | Unique id of this record. |
| `loan_applications[].tenure_months` | number | Loan length in months. |
| `loan_applications[].requested_amount` | number | Loan amount in rupees. |
| `loan_applications[].status` | string | Current status. See Status values. |
| `source` | object | Which table this came from. |
| `source.database` | string | Database name. |
| `source.table` | string | Table name. |
| `user` | object | The customer. |
| `user.id` | number | Unique id of this record. |
| `user.customer_code` | string | Customer code, set at onboarding step 1. |
| `user.name` | string | Full name. |
| `user.first_name` | string | First name. |
| `user.last_name` | string | Last name. |
| `user.mobile_number` | string | 10-digit mobile number. |
| `user.alternate_mobile_number` | string | Second mobile number. |
| `user.email` | string | Email address. |
| `user.gender` | string | `Male`, `Female` or `Other`. |
| `user.date_of_birth` | string | Date of birth, `yyyy-MM-dd`. |
| `user.age` | number | Age in years, as sent by the app. |
| `user.marital_status` | string | `Single`, `Married`, `Divorced` or `Widowed`. |
| `user.nationality` | string | Nationality. |
| `user.profile_image` | string | Profile photo URL. |
| `user.aadhaar_encrypted` | string | Hidden. Always null here. |
| `user.aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `user.pan_encrypted` | string | Hidden. Always null here. |
| `user.pan_last4` | string | Last 4 characters of PAN. |
| `user.aadhaar_document` | string | URL of the uploaded Aadhaar. Open with a token. |
| `user.pan_document` | string | URL of the uploaded PAN. Open with a token. |
| `user.kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `user.kyc_verified_at` | string | When staff verified KYC. |
| `user.kyc_rejection_reason` | string | Why staff rejected KYC. Show this to the customer. |
| `user.current_address` | string | Current address. |
| `user.current_city` | string | Current city. |
| `user.current_state` | string | Current state. |
| `user.current_pincode` | string | Current PIN code. |
| `user.current_country` | string | Current country. |
| `user.permanent_address` | string | Permanent address. |
| `user.permanent_city` | string | Permanent city. |
| `user.permanent_state` | string | Permanent state. |
| `user.permanent_pincode` | string | Permanent PIN code. |
| `user.permanent_country` | string | Permanent country. |
| `user.same_as_current_address` | boolean | `true` if permanent address equals current. |
| `user.employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `user.company_name` | string | Employer or business name. |
| `user.designation` | string | Job title. |
| `user.work_experience_months` | number | Work experience in months. |
| `user.monthly_income` | number | Monthly income in rupees. |
| `user.annual_income` | number | Annual income in rupees. |
| `user.income_source` | string | Where the income comes from. |
| `user.bank_name` | string | Bank name. |
| `user.bank_account_encrypted` | string | Hidden. Always null here. |
| `user.bank_account_last4` | string | Last 4 digits of the bank account. |
| `user.ifsc_code` | string | Bank IFSC code. |
| `user.bank_verification_status` | string | `pending`, `verified` or `failed`. Only staff can change it. |
| `user.bank_verified_at` | string | When bank details were verified. |
| `user.password_hash` | string | Hidden. Always null here. |
| `user.otp_hash` | string | Hidden. Always null here. |
| `user.otp_expires_at` | datetime | When the current OTP expires. |
| `user.otp_verified_at` | datetime | When an OTP last passed. |
| `user.email_verified_at` | string | When the email passed an OTP. |
| `user.mobile_verified_at` | string | When the mobile number passed an OTP. |
| `user.login_attempts` | number | Wrong password attempts since the last success. |
| `user.locked_until` | datetime | Sign-in is locked until this time. |
| `user.last_login` | string | Last sign-in time. |
| `user.last_logout_at` | datetime | Last sign-out time. |
| `user.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `user.fcm_token` | string | Hidden. Always null here. |
| `user.fcm_token_updated_at` | datetime | When the push token last changed. |
| `user.notification_enable` | boolean | `true` if push is on. |
| `user.notification_disable_at` | datetime | When push was turned off. |
| `user.device_id` | string | Device id sent by the app. |
| `user.device_type` | string | Device type sent by the app. |
| `user.device_name` | string | Device name. |
| `user.device_model` | string | Device model. |
| `user.os_version` | string | Operating system version. |
| `user.app_version` | string | App version. |
| `user.time_zone` | string | Device time zone. |
| `user.is_login` | boolean | `true` if the customer is signed in. |
| `user.is_app_installed` | boolean | `true` if the app is installed. |
| `user.application_install_time` | datetime | When the app was installed. |
| `user.application_uninstall_time` | datetime | When the app was uninstalled. |
| `user.latitude` | number | Last latitude sent by the app. |
| `user.longitude` | number | Last longitude sent by the app. |
| `user.privacy_consent` | boolean | Customer accepted the privacy policy. |
| `user.privacy_consent_at` | string | When privacy consent was given. |
| `user.terms_accepted` | boolean | Customer accepted the terms. |
| `user.terms_accepted_at` | string | When the terms were accepted. |
| `user.kyc_consent` | boolean | Customer agreed to KYC. |
| `user.kyc_consent_at` | string | When KYC consent was given. |
| `user.created_at` | string | When this record was created. |
| `user.updated_at` | string | When this record last changed. |
| `user.deleted_at` | datetime | When this record was deleted. Null if not deleted. |
| `user.onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `user.session_token` | string | Hidden. Always null here. |
| `user.contacts_data` | string | Sync data, cut to 2,000 characters. Empty while contacts sync is off. |
| `user.gallery_data` | string | Sync data, cut to 2,000 characters. Empty while gallery sync is off. |

Response example (200):

```json
{
  "success": true,
  "message": "User details",
  "data": {
    "loan_applications": [
      {
        "purpose": "personal",
        "application_code": "LN179387568679444E1",
        "created_at": "2026-11-05T16:18:06",
        "id": 3,
        "tenure_months": 6,
        "requested_amount": 20000.0,
        "status": "disbursed"
      },
      {
        "purpose": "personal",
        "application_code": "LN1793875686609D351",
        "created_at": "2026-11-05T16:18:06",
        "id": 2,
        "tenure_months": 6,
        "requested_amount": 20000.0,
        "status": "rejected"
      }
    ],
    "source": {
      "database": "narainson_db",
      "table": "users"
    },
    "user": {
      "id": 1,
      "customer_code": "CUST11793875682002",
      "name": "Asha Verma",
      "first_name": "Asha",
      "last_name": "Verma",
      "mobile_number": "9000000001",
      "alternate_mobile_number": "9000000002",
      "email": "asha.verma@test.local",
      "gender": "Female",
      "date_of_birth": "1995-04-12",
      "age": 31,
      "marital_status": "Single",
      "nationality": "Indian",
      "profile_image": null,
      "aadhaar_encrypted": null,
      "aadhaar_last4": "1234",
      "pan_encrypted": null,
      "pan_last4": "567F",
      "aadhaar_document": "/NarainsonsProject/api/v1/files/e711feab43924504a7cd99813714a9b2",
      "pan_document": "/NarainsonsProject/api/v1/files/53c72ab0731b4a5e9f4b05caf3537d96",
      "kyc_status": "verified",
      "kyc_verified_at": "2026-11-05T16:18:06",
      "kyc_rejection_reason": null,
      "current_address": "12 MG Road",
      "current_city": "Jaipur",
      "current_state": "Rajasthan",
      "current_pincode": "302001",
      "current_country": "India",
      "permanent_address": "12 MG Road",
      "permanent_city": "Jaipur",
      "permanent_state": "Rajasthan",
      "permanent_pincode": "302001",
      "permanent_country": "India",
      "same_as_current_address": true,
      "employment_type": "Salaried",
      "company_name": "Acme Pvt Ltd",
      "designation": "Accountant",
      "work_experience_months": 48,
      "monthly_income": 35000.0,
      "annual_income": 420000.0,
      "income_source": "Salary",
      "bank_name": "HDFC Bank",
      "bank_account_encrypted": null,
      "bank_account_last4": "6789",
      "ifsc_code": "HDFC0001234",
      "bank_verification_status": "verified",
      "bank_verified_at": "2026-11-05T16:18:06",
      "password_hash": null,
      "otp_hash": null,
      "otp_expires_at": null,
      "otp_verified_at": null,
      "email_verified_at": "2026-11-05T16:18:04",
      "mobile_verified_at": "2026-11-05T16:18:01",
      "login_attempts": 0,
      "locked_until": null,
      "last_login": "2026-11-05T16:18:05",
      "last_logout_at": null,
      "account_status": "active",
      "fcm_token": null,
      "fcm_token_updated_at": null,
      "notification_enable": true,
      "notification_disable_at": null,
      "device_id": "android-abc123",
      "device_type": "android",
      "device_name": "Asha's phone",
      "device_model": "Pixel 8",
      "os_version": "14",
      "app_version": "1.0.0",
      "time_zone": "Asia/Kolkata",
      "is_login": true,
      "is_app_installed": null,
      "application_install_time": null,
      "application_uninstall_time": null,
      "latitude": 26.9124,
      "longitude": 75.7873,
      "privacy_consent": true,
      "privacy_consent_at": "2026-11-05T16:18:02",
      "terms_accepted": true,
      "terms_accepted_at": "2026-11-05T16:18:02",
      "kyc_consent": true,
      "kyc_consent_at": "2026-11-05T16:18:02",
      "created_at": "2026-11-05T16:18:00",
      "updated_at": "2026-11-05T16:18:06",
      "deleted_at": null,
      "onboarding_step": 3,
      "session_token": null,
      "contacts_data": null,
      "gallery_data": null
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |

### Turn a customer on or off

`POST /api/admin/users/{id}/status`  
Sign-in: Staff token

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `active` | boolean | Yes | `false` stops them using the app. |
| `account_status` | string | No | Older form: `active` or anything else. |

Request example:

```json
{
  "active": false
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `user_id` | number | Customer id. |
| `active` | boolean | `true` if turned on. |
| `account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |

Response example (200):

```json
{
  "success": true,
  "message": "User status updated",
  "data": {
    "user_id": 2,
    "active": false,
    "account_status": "inactive"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 403 | Signed in, but not a super admin |
| 404 | Not found |

### Delete a customer

`POST /api/admin/users/{id}/delete`  
Sign-in: Staff token

Super admin only. Ends their logins. The record stays for history, and the mobile and email cannot be registered again.

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `deleted` | boolean | `true` when the record was deleted. |
| `user_id` | number | Customer id. |

Response example (200):

```json
{
  "success": true,
  "message": "User deleted",
  "data": {
    "deleted": true,
    "user_id": 3
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 403 | Signed in, but not a super admin |
| 404 | Not found, or already deleted |

## Staff: loans

Approve, reject and disburse.

### Reject a loan

`POST /api/admin/loan/applications/{id}/reject`  
Sign-in: Staff token

Only while pending. The customer gets a push.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `admin_note` | string | No | Reason, saved on the loan. |

Request example:

```json
{
  "admin_note": "Income proof missing"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `loan_application_id` | number | Loan id. |
| `status` | string | New status after this action. |

Response example (200):

```json
{
  "success": true,
  "message": "Loan rejected",
  "data": {
    "loan_application_id": 2,
    "status": "rejected"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |
| 400 | Not pending |

### Approve a loan

`POST /api/admin/loan/applications/{id}/approve`  
Sign-in: Staff token

Only while pending, and KYC must be verified. Creates the EMI schedule. The customer gets a push.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `admin_note` | string | No |  |

Request example:

```json
{
  "admin_note": "Checked"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `loan_application_id` | number | Loan id. |
| `status` | string | New status after this action. |

Response example (200):

```json
{
  "success": true,
  "message": "Loan approved",
  "data": {
    "loan_application_id": 3,
    "status": "approved"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |
| 400 | Not pending |
| 409 | `KYC_NOT_VERIFIED` |

### Disburse a loan

`POST /api/admin/loan/applications/{id}/disburse`  
Sign-in: Staff token

Record this after sending the money. EMIs then run from today: the first is due in a month. The customer gets a push.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `disbursement_reference` | string | Yes | Bank or UTR reference, up to 100 characters. |

Request example:

```json
{
  "disbursement_reference": "UTR123456"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `loan_application_id` | number | Loan id. |
| `status` | string | New status after this action. |

Response example (200):

```json
{
  "success": true,
  "message": "Loan disbursed",
  "data": {
    "loan_application_id": 3,
    "status": "disbursed"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |
| 400 | Not approved, or reference missing |

### One loan with guarantors

`GET /api/admin/loan/applications/{id}`  
Sign-in: Staff token

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `application` | object | The loan application. |
| `application.status_message` | string | Status text to show the customer. |
| `application.closed_at` | datetime | When the loan closed. |
| `application.purpose` | string | Why the loan is needed. |
| `application.reviewed_at` | string | When staff decided. |
| `application.purpose_detail` | string | More about the purpose. |
| `application.created_at` | string | When this record was created. |
| `application.processing_fee` | number | Processing fee in rupees. |
| `application.disbursement_reference` | string | Bank or UTR reference of the money sent. |
| `application.interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `application.requested_amount` | number | Loan amount in rupees. |
| `application.updated_at` | string | When this record last changed. |
| `application.emi_amount` | number | Monthly EMI in rupees. |
| `application.application_code` | string | Loan code shown to people. |
| `application.total_payable` | number | Total of all EMIs in rupees. |
| `application.guarantors_required` | number | Number of guarantors this loan needs. |
| `application.id` | number | Unique id of this record. |
| `application.tenure_months` | number | Loan length in months. |
| `application.disbursed_at` | string | When the money was sent. |
| `application.guarantors` | array | The loan's guarantors. |
| `application.guarantors[].pan_last4` | string | Last 4 characters of PAN. |
| `application.guarantors[].full_name` | string | Full name. |
| `application.guarantors[].address` | string | Address. |
| `application.guarantors[].index` | number | Guarantor number, from 1. |
| `application.guarantors[].mobile_number` | string | 10-digit mobile number. |
| `application.guarantors[].aadhaar_last4` | string | Last 4 digits of Aadhaar. |
| `application.guarantors[].relation` | string | Relation to the customer. |
| `application.status` | string | Current status. See Status values. |
| `application.user_id` | number | Customer id. |
| `application.monthly_income` | number | Monthly income in rupees. |
| `application.employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `application.company_name` | string | Employer or business name. |
| `application.bank_name` | string | Bank name. |
| `application.ifsc_code` | string | Bank IFSC code. |
| `application.bank_account_last4` | string | Last 4 digits of the bank account. |
| `application.admin_note` | string | Note from staff. For a rejected payment, the reason. |

Response example (200):

```json
{
  "success": true,
  "message": "Loan",
  "data": {
    "application": {
      "status_message": "Aapka loan amount bank account mein bhej diya gaya hai. EMI schedule app mein dekhein.",
      "closed_at": null,
      "purpose": "personal",
      "reviewed_at": "2026-11-05T16:18:06",
      "purpose_detail": "Home repair",
      "created_at": "2026-11-05T16:18:06",
      "processing_fee": 400.0,
      "disbursement_reference": "UTR123456",
      "interest_rate_annual": 12.0,
      "requested_amount": 20000.0,
      "updated_at": "2026-11-05T16:18:07",
      "emi_amount": 3450.97,
      "application_code": "LN179387568679444E1",
      "total_payable": 20705.81,
      "guarantors_required": 2,
      "id": 3,
      "tenure_months": 6,
      "disbursed_at": "2026-11-05T16:18:07",
      "guarantors": [
        {
          "pan_last4": "222A",
          "full_name": "Ravi Verma",
          "address": "Jaipur",
          "index": 1,
          "mobile_number": "9000000101",
          "aadhaar_last4": "1111",
          "relation": "brother"
        },
        {
          "pan_last4": null,
          "full_name": "Neha Sharma",
          "address": null,
          "index": 2,
          "mobile_number": "9000000102",
          "aadhaar_last4": null,
          "relation": "friend"
        }
      ],
      "status": "disbursed",
      "user_id": 1,
      "monthly_income": 35000.0,
      "employment_type": "Salaried",
      "company_name": "Acme Pvt Ltd",
      "bank_name": "HDFC Bank",
      "ifsc_code": "HDFC0001234",
      "bank_account_last4": "6789",
      "admin_note": "Checked"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |

## Staff: EMI payments

Verify payment proof.

### Reject an EMI payment

`POST /api/admin/emi/payments/{id}/reject`  
Sign-in: Staff token

Only while pending. The EMI returns to pending, or overdue if past due.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `admin_note` | string | No | Shown to the customer. |

Request example:

```json
{
  "admin_note": "Screenshot is blurred"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `payment_id` | number | Payment id. |
| `status` | string | New status after this action. |

Response example (200):

```json
{
  "success": true,
  "message": "EMI payment rejected",
  "data": {
    "payment_id": 1,
    "status": "rejected"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |
| 400 | Already decided |

### Approve an EMI payment

`POST /api/admin/emi/payments/{id}/approve`  
Sign-in: Staff token

Check your bank first. Approving the last EMI closes the loan. The customer gets a push.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `admin_note` | string | No |  |

Request example:

```json
{
  "admin_note": "Received in bank"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `remaining_installments` | number | EMIs not yet paid. |
| `payment_id` | number | Payment id. |
| `status` | string | New status after this action. |

Response example (200):

```json
{
  "success": true,
  "message": "EMI payment approved",
  "data": {
    "remaining_installments": 5,
    "payment_id": 2,
    "status": "approved"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |
| 400 | Already decided, or the claim is below the EMI |

## Staff: banners, videos and settings

What customers see in the app.

### Upload a banner image

`POST /api/admin/banners/upload-image`  
Sign-in: Staff token

Multipart form with `image`. JPG, PNG, WebP or GIF, up to 5 MB. Returns a public URL.

Form field: `image` (the file)

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `file_name` | string | Stored file name. |
| `image_url` | string | Image URL. |

Response example (200):

```json
{
  "success": true,
  "message": "Banner image uploaded",
  "data": {
    "file_name": "banner-8f33ee764e8245788b072c175f33ab6c.png",
    "image_url": "http://localhost:8092/NarainsonsProject/api/media/banners/banner-8f33ee764e8245788b072c175f33ab6c.png"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 400 | Wrong type or too large |

### Create a banner

`POST /api/admin/banners`  
Sign-in: Staff token

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `title` | string | To create | Up to 200. |
| `subtitle` | string | No | Up to 500. |
| `image_url` | string | To create | http(s) URL or the upload result. |
| `action_type` | string | No | `none`, `url` or `screen`. |
| `action_value` | string | With url or screen | URL or screen name, up to 500. |
| `sort_order` | number | No |  |
| `is_active` | boolean | No |  |
| `starts_at` | datetime | No | `yyyy-MM-ddTHH:mm:ss` |
| `ends_at` | datetime | No | After `starts_at`. |

Request example:

```json
{
  "title": "Festival offer",
  "subtitle": "Apply today",
  "image_url": "https://example.com/banner.png",
  "action_type": "screen",
  "action_value": "loan_apply",
  "sort_order": 5,
  "is_active": true
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `banner` | object | The banner. |
| `banner.id` | number | Unique id of this record. |
| `banner.title` | string | Title. |
| `banner.subtitle` | string | Line under the title. |
| `banner.image_url` | string | Image URL. |
| `banner.action_type` | string | On tap: `none`, `url` opens a link, `screen` opens an app screen. |
| `banner.action_value` | string | The link or screen name to open. |
| `banner.sort_order` | number | Order; lower comes first. |
| `banner.is_active` | boolean | `true` or 1 if shown in the app. |
| `banner.starts_at` | datetime | Show from this time. |
| `banner.ends_at` | datetime | Show until this time. |
| `banner.created_at` | string | When this record was created. |
| `banner.updated_at` | string | When this record last changed. |

Response example (201):

```json
{
  "success": true,
  "message": "Banner created",
  "data": {
    "banner": {
      "id": 4,
      "title": "Festival offer",
      "subtitle": "Apply today",
      "image_url": "http://localhost:8092/NarainsonsProject/api/media/banners/banner-8f33ee764e8245788b072c175f33ab6c.png",
      "action_type": "screen",
      "action_value": "loan_apply",
      "sort_order": 5,
      "is_active": true,
      "starts_at": null,
      "ends_at": null,
      "created_at": "2026-11-05T16:18:09",
      "updated_at": "2026-11-05T16:18:09"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 400 | Bad field |

### One banner

`GET /api/admin/banners/{id}`  
Sign-in: Staff token

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `banner` | object | The banner. |
| `banner.id` | number | Unique id of this record. |
| `banner.title` | string | Title. |
| `banner.subtitle` | string | Line under the title. |
| `banner.image_url` | string | Image URL. |
| `banner.action_type` | string | On tap: `none`, `url` opens a link, `screen` opens an app screen. |
| `banner.action_value` | string | The link or screen name to open. |
| `banner.sort_order` | number | Order; lower comes first. |
| `banner.is_active` | boolean | `true` or 1 if shown in the app. |
| `banner.starts_at` | datetime | Show from this time. |
| `banner.ends_at` | datetime | Show until this time. |
| `banner.created_at` | string | When this record was created. |
| `banner.updated_at` | string | When this record last changed. |

Response example (200):

```json
{
  "success": true,
  "message": "Banner",
  "data": {
    "banner": {
      "id": 4,
      "title": "Festival offer",
      "subtitle": "Apply today",
      "image_url": "http://localhost:8092/NarainsonsProject/api/media/banners/banner-8f33ee764e8245788b072c175f33ab6c.png",
      "action_type": "screen",
      "action_value": "loan_apply",
      "sort_order": 5,
      "is_active": true,
      "starts_at": null,
      "ends_at": null,
      "created_at": "2026-11-05T16:18:09",
      "updated_at": "2026-11-05T16:18:09"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |

### Update a banner

`POST /api/admin/banners/{id}/update`  
Sign-in: Staff token

Send only what changes.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `title` | string | To create | Up to 200. |
| `subtitle` | string | No | Up to 500. |
| `image_url` | string | To create | http(s) URL or the upload result. |
| `action_type` | string | No | `none`, `url` or `screen`. |
| `action_value` | string | With url or screen | URL or screen name, up to 500. |
| `sort_order` | number | No |  |
| `is_active` | boolean | No |  |
| `starts_at` | datetime | No | `yyyy-MM-ddTHH:mm:ss` |
| `ends_at` | datetime | No | After `starts_at`. |

Request example:

```json
{
  "is_active": false
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `banner` | object | The banner. |
| `banner.id` | number | Unique id of this record. |
| `banner.title` | string | Title. |
| `banner.subtitle` | string | Line under the title. |
| `banner.image_url` | string | Image URL. |
| `banner.action_type` | string | On tap: `none`, `url` opens a link, `screen` opens an app screen. |
| `banner.action_value` | string | The link or screen name to open. |
| `banner.sort_order` | number | Order; lower comes first. |
| `banner.is_active` | boolean | `true` or 1 if shown in the app. |
| `banner.starts_at` | datetime | Show from this time. |
| `banner.ends_at` | datetime | Show until this time. |
| `banner.created_at` | string | When this record was created. |
| `banner.updated_at` | string | When this record last changed. |

Response example (200):

```json
{
  "success": true,
  "message": "Banner updated",
  "data": {
    "banner": {
      "id": 4,
      "title": "Festival offer",
      "subtitle": "Apply today",
      "image_url": "http://localhost:8092/NarainsonsProject/api/media/banners/banner-8f33ee764e8245788b072c175f33ab6c.png",
      "action_type": "screen",
      "action_value": "loan_apply",
      "sort_order": 5,
      "is_active": false,
      "starts_at": null,
      "ends_at": null,
      "created_at": "2026-11-05T16:18:09",
      "updated_at": "2026-11-05T16:18:09"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |
| 400 | Bad field |

### Delete a banner

`POST /api/admin/banners/{id}/delete`  
Sign-in: Staff token

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `deleted` | boolean | `true` when the record was deleted. |
| `id` | number | Unique id of this record. |

Response example (200):

```json
{
  "success": true,
  "message": "Banner deleted",
  "data": {
    "deleted": true,
    "id": 4
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |

### Add a guide video

`POST /api/admin/videos`  
Sign-in: Staff token

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `title` | string | Yes | Up to 200. |
| `video_url` | string | Yes | http(s) URL. |
| `video_type` | string | Yes | `mp4`, `youtube`, `vimeo` or `hls`. |
| `description` | string | No | Up to 500. |
| `thumbnail_url` | string | No | http(s) URL. |
| `duration_seconds` | number | No |  |
| `sort_order` | number | No |  |
| `is_active` | boolean | No | Default true. |

Request example:

```json
{
  "title": "How to apply",
  "video_url": "https://example.com/apply.mp4",
  "video_type": "mp4",
  "duration_seconds": 120,
  "sort_order": 9,
  "is_active": true
}
```

Response example (200):

```json
{
  "success": true,
  "message": "Saved",
  "data": null,
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 400 | Bad field |

### Update a guide video

`POST /api/admin/videos/{id}/update`  
Sign-in: Staff token

Send the whole video, not only what changes.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `title` | string | Yes | Up to 200. |
| `video_url` | string | Yes | http(s) URL. |
| `video_type` | string | Yes | `mp4`, `youtube`, `vimeo` or `hls`. |
| `description` | string | No | Up to 500. |
| `thumbnail_url` | string | No | http(s) URL. |
| `duration_seconds` | number | No |  |
| `sort_order` | number | No |  |
| `is_active` | boolean | No | Default true. |

Request example:

```json
{
  "title": "How to apply",
  "video_url": "https://example.com/apply.mp4",
  "video_type": "mp4",
  "duration_seconds": 120,
  "sort_order": 9,
  "is_active": false
}
```

Response example (200):

```json
{
  "success": true,
  "message": "Saved",
  "data": null,
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |
| 400 | Bad field |

### Delete a guide video

`POST /api/admin/videos/{id}/delete`  
Sign-in: Staff token

Response example (200):

```json
{
  "success": true,
  "message": "Saved",
  "data": null,
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 404 | Not found |

### Payment details: read

`GET /api/admin/settings/emi-collection`  
Sign-in: Staff token

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `settings` | object | The payment details now in use. Null if none. |
| `settings.ifsc_code` | string | Bank IFSC code. |
| `settings.bank_account_name` | string | Name on the bank account. |
| `settings.bank_account_number` | string | Bank account number. |
| `settings.payment_note` | string | Note to show when paying. |
| `settings.payee_name` | string | Name to pay. |
| `settings.bank_name` | string | Bank name. |
| `settings.qr_image_url` | string | QR code image URL. |
| `settings.upi_id` | string | UPI id. |

Response example (200):

```json
{
  "success": true,
  "message": "EMI collection settings",
  "data": {
    "settings": {
      "ifsc_code": "HDFC0001234",
      "bank_account_name": "Narainsons Finance Pvt Ltd",
      "bank_account_number": "XXXX1234567890",
      "payment_note": "EMI pay karte waqt application code note mein likho. Screenshot yahi app se upload karo.",
      "payee_name": "Narainsons Finance",
      "bank_name": "HDFC Bank",
      "qr_image_url": "https://cdn.example.com/emi-payment-qr.png",
      "upi_id": "narainsons@upi"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### Payment details: save

`POST /api/admin/settings/emi-collection`  
Sign-in: Staff token

This is what the app shows under How to pay.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `payee_name` | string | Yes |  |
| `upi_id` | string | No |  |
| `qr_image_url` | string | No | http(s) URL. |
| `bank_name` | string | No |  |
| `bank_account_name` | string | No |  |
| `bank_account_number` | string | No |  |
| `ifsc_code` | string | No |  |
| `payment_note` | string | No |  |

Request example:

```json
{
  "payee_name": "Narainsons Finance",
  "upi_id": "narainsons@upi",
  "qr_image_url": "https://example.com/qr.png",
  "bank_name": "HDFC Bank",
  "bank_account_name": "Narainsons Finance Pvt Ltd",
  "bank_account_number": "XXXX1234567890",
  "ifsc_code": "HDFC0001234",
  "payment_note": "Write the loan code in the payment note."
}
```

Response example (200):

```json
{
  "success": true,
  "message": "Settings saved",
  "data": null,
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 403 | Signed in, but not a super admin |
| 400 | Bad field |

## Staff: messages and accounts

Messages to customers and staff accounts.

### Send a message to customers

`POST /api/admin/notifications/send`  
Sign-in: Staff token

Saved in the customer's list and sent as push.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `user_id` | number | Unless `all` |  |
| `all` | boolean | No | `true` messages every active customer. Super admin only. |
| `title` | string | Yes |  |
| `body` | string | Yes |  |

Request example:

```json
{
  "user_id": 1,
  "title": "Office closed",
  "body": "Our office is closed on Monday."
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `sent` | number | Number of customers messaged. |

Response example (200):

```json
{
  "success": true,
  "message": "Notification sent",
  "data": {
    "sent": 1
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 403 | Signed in, but not a super admin |
| 400 | Field missing |
| 404 | No such customer |

### Add a staff account

`POST /api/admin/auth/register`  
Sign-in: Staff token

Super admin only. Creates a normal admin.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `email` | string | Yes |  |
| `password` | string | Yes | At least 8 characters. |
| `full_name` | string | Yes |  |
| `mobile_number` | string | No |  |

Request example:

```json
{
  "email": "staff.two@test.local",
  "password": "StaffTwo123",
  "full_name": "Staff Two"
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `admin` | object | The staff account. |
| `admin.full_name` | string | Full name. |
| `admin.role` | string | `super_admin` or `admin`. |
| `admin.updated_at` | string | When this record last changed. |
| `admin.last_login` | datetime | Last sign-in time. |
| `admin.has_profile_photo` | boolean | `true` if a photo is uploaded. |
| `admin.created_at` | string | When this record was created. |
| `admin.id` | number | Unique id of this record. |
| `admin.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `admin.mobile_number` | string | 10-digit mobile number. |
| `admin.email` | string | Email address. |
| `message` | string | Text to show the user. |

Response example (201):

```json
{
  "success": true,
  "message": "Admin registered",
  "data": {
    "admin": {
      "full_name": "Staff Two",
      "role": "admin",
      "updated_at": "2026-11-05T16:18:09",
      "last_login": null,
      "has_profile_photo": false,
      "created_at": "2026-11-05T16:18:09",
      "id": 2,
      "account_status": "active",
      "mobile_number": null,
      "email": "staff.two@test.local"
    },
    "message": "Admin account created"
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 403 | Signed in, but not a super admin |
| 400 | Bad field or email taken |

### Turn a staff account on or off

`POST /api/admin/admins/{id}/status`  
Sign-in: Staff token

You cannot change your own account.

Request fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `active` | boolean | Yes |  |

Request example:

```json
{
  "active": false
}
```

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `active` | boolean | `true` if turned on. |

Response example (200):

```json
{
  "success": true,
  "message": "Staff account updated",
  "data": {
    "active": false
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 403 | Signed in, but not a super admin |
| 400 | Your own account |
| 404 | Not found |

## Staff: older list APIs

Still work. New work should use the list API.

### Dashboard (older)

`GET /api/admin/dashboard`  
Sign-in: Staff token

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `stats` | object | Totals for this list. |
| `stats.total_applications` | number | All loan applications. |
| `stats.active_admins` | number | Active staff accounts. |
| `stats.pending_emi` | number | EMI payments waiting. |
| `stats.approved_loans` | number | Approved loans. |
| `stats.rejected_loans` | number | Rejected loans. |
| `stats.pending_loans` | number | Loans waiting for review. |
| `admin` | object | The staff account. |
| `admin.full_name` | string | Full name. |
| `admin.role` | string | `super_admin` or `admin`. |
| `admin.updated_at` | string | When this record last changed. |
| `admin.last_login` | string | Last sign-in time. |
| `admin.has_profile_photo` | boolean | `true` if a photo is uploaded. |
| `admin.created_at` | string | When this record was created. |
| `admin.id` | number | Unique id of this record. |
| `admin.account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `admin.mobile_number` | string | 10-digit mobile number. |
| `admin.email` | string | Email address. |
| `pending_loan_count` | number | Loans waiting for review. |
| `pending_loans` | array | Loans waiting for review. |
| `pending_emi_payments` | array | EMI payments waiting for verification. |

Response example (200):

```json
{
  "success": true,
  "message": "Admin dashboard",
  "data": {
    "stats": {
      "total_applications": 3,
      "active_admins": 1,
      "pending_emi": 0,
      "approved_loans": 0,
      "rejected_loans": 1,
      "pending_loans": 0
    },
    "admin": {
      "full_name": "First Admin",
      "role": "super_admin",
      "updated_at": "2026-11-05T16:18:08",
      "last_login": "2026-11-05T16:17:59",
      "has_profile_photo": true,
      "created_at": "2026-11-05T16:17:54",
      "id": 1,
      "account_status": "active",
      "mobile_number": "9000000901",
      "email": "first.admin@test.local"
    },
    "pending_loan_count": 0,
    "pending_loans": [],
    "pending_emi_payments": []
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### Customers (older)

`GET /api/admin/users`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `limit` | number | 1 to 1000. |
| `offset` | number | Rows to skip. |
| `search` | string | ID, name, mobile or email. |
| `account_status` | string | Filter by account status. |
| `kyc_status` | string | Filter by KYC status. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `offset` | number | Rows skipped. |
| `stats` | object | Totals for this list. |
| `stats.active_users` | number | Active customers. |
| `stats.inactive_users` | number | Inactive customers. |
| `stats.kyc_verified` | number | Customers with verified KYC. |
| `stats.kyc_pending` | number | Customers with KYC not done. |
| `stats.total_users` | number | All customers. |
| `limit` | number | Page size used. |
| `source` | object | Which table this came from. |
| `source.database` | string | Database name. |
| `source.table` | string | Table name. |
| `users` | array | List of customers. |
| `users[].kyc_status` | string | `pending`, `submitted`, `verified` or `rejected`. |
| `users[].employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `users[].onboarding_step` | number | 0 to 3: how many onboarding steps are saved. |
| `users[].last_login` | string | Last sign-in time. |
| `users[].name` | string | Full name. |
| `users[].created_at` | string | When this record was created. |
| `users[].id` | number | Unique id of this record. |
| `users[].account_status` | string | `active`, `inactive`, `blocked` or `suspended`. |
| `users[].mobile_number` | string | 10-digit mobile number. |
| `users[].customer_code` | string | Customer code, set at onboarding step 1. |
| `users[].email` | string | Email address. |

Response example (200):

```json
{
  "success": true,
  "message": "App users",
  "data": {
    "offset": 0,
    "stats": {
      "active_users": 2,
      "inactive_users": 0,
      "kyc_verified": 1,
      "kyc_pending": 1,
      "total_users": 2
    },
    "limit": 100,
    "source": {
      "database": "narainson_db",
      "table": "users"
    },
    "users": [
      {
        "kyc_status": "pending",
        "employment_type": null,
        "onboarding_step": 0,
        "last_login": "2026-11-05T16:18:06",
        "name": "New User",
        "created_at": "2026-11-05T16:18:05",
        "id": 2,
        "account_status": "active",
        "mobile_number": "TEMP1793875685201",
        "customer_code": null,
        "email": "second.user@test.local"
      },
      {
        "kyc_status": "verified",
        "employment_type": "Salaried",
        "onboarding_step": 3,
        "last_login": "2026-11-05T16:18:05",
        "name": "Asha Verma",
        "created_at": "2026-11-05T16:18:00",
        "id": 1,
        "account_status": "active",
        "mobile_number": "9000000001",
        "customer_code": "CUST11793875682002",
        "email": "asha.verma@test.local"
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### Loans (older)

`GET /api/admin/loan/applications`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `limit` | number | 1 to 500. |
| `offset` | number | Rows to skip. |
| `status` | string | Filter by loan status. |
| `search` | string | Code, purpose, customer or mobile. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `total` | number | Number of matching rows across all pages. |
| `offset` | number | Rows skipped. |
| `stats` | object | Totals for this list. |
| `stats.approved` | number | Approved rows. |
| `stats.pending` | number | Pending rows. |
| `stats.rejected` | number | Rejected rows. |
| `stats.closed` | number | Closed rows. |
| `stats.disbursed` | number | Disbursed rows. |
| `stats.total` | number | All rows. |
| `limit` | number | Page size used. |
| `source` | object | Which table this came from. |
| `source.database` | string | Database name. |
| `source.table` | string | Table name. |
| `applications` | array | Loan applications in the period. |
| `applications[].id` | number | Unique id of this record. |
| `applications[].user_id` | number | Customer id. |
| `applications[].application_code` | string | Loan code shown to people. |
| `applications[].requested_amount` | number | Loan amount in rupees. |
| `applications[].tenure_months` | number | Loan length in months. |
| `applications[].purpose` | string | Why the loan is needed. |
| `applications[].purpose_detail` | string | More about the purpose. |
| `applications[].monthly_income` | number | Monthly income in rupees. |
| `applications[].employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `applications[].company_name` | string | Employer or business name. |
| `applications[].bank_name` | string | Bank name. |
| `applications[].ifsc_code` | string | Bank IFSC code. |
| `applications[].bank_account_last4` | string | Last 4 digits of the bank account. |
| `applications[].status` | string | Current status. See Status values. |
| `applications[].interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `applications[].processing_fee` | number | Processing fee in rupees. |
| `applications[].total_payable` | number | Total of all EMIs in rupees. |
| `applications[].emi_amount` | number | Monthly EMI in rupees. |
| `applications[].disbursed_at` | datetime | When the money was sent. |
| `applications[].disbursement_reference` | string | Bank or UTR reference of the money sent. |
| `applications[].closed_at` | datetime | When the loan closed. |
| `applications[].status_message` | string | Status text to show the customer. |
| `applications[].admin_note` | string | Note from staff. For a rejected payment, the reason. |
| `applications[].reviewed_at` | datetime | When staff decided. |
| `applications[].created_at` | string | When this record was created. |
| `applications[].updated_at` | string | When this record last changed. |
| `applications[].user_name` | string | Customer name. |
| `applications[].user_email` | string | Customer email. |
| `applications[].user_mobile` | string | Customer mobile. |

Response example (200):

```json
{
  "success": true,
  "message": "Loan applications",
  "data": {
    "total": 3,
    "offset": 0,
    "stats": {
      "approved": 0,
      "pending": 0,
      "rejected": 1,
      "closed": 0,
      "disbursed": 1,
      "total": 3
    },
    "limit": 100,
    "source": {
      "database": "narainson_db",
      "table": "loan_applications"
    },
    "applications": [
      {
        "id": 1,
        "user_id": 1,
        "application_code": "LN1793875686505B0D7",
        "requested_amount": 20000.0,
        "tenure_months": 6,
        "purpose": "personal",
        "purpose_detail": "Home repair",
        "monthly_income": 35000.0,
        "employment_type": "Salaried",
        "company_name": "Acme Pvt Ltd",
        "bank_name": "HDFC Bank",
        "ifsc_code": "HDFC0001234",
        "bank_account_last4": "6789",
        "status": "cancelled",
        "interest_rate_annual": 12.0,
        "processing_fee": 400.0,
        "total_payable": 20705.81,
        "emi_amount": 3450.97,
        "disbursed_at": null,
        "disbursement_reference": null,
        "closed_at": null,
        "status_message": "Application aapne cancel kar di.",
        "admin_note": null,
        "reviewed_at": null,
        "created_at": "2026-11-05T16:18:06",
        "updated_at": "2026-11-05T16:18:06",
        "user_name": "Asha Verma",
        "user_email": "asha.verma@test.local",
        "user_mobile": "9000000001"
      },
      {
        "id": 2,
        "user_id": 1,
        "application_code": "LN1793875686609D351",
        "requested_amount": 20000.0,
        "tenure_months": 6,
        "purpose": "personal",
        "purpose_detail": "Home repair",
        "monthly_income": 35000.0,
        "employment_type": "Salaried",
        "company_name": "Acme Pvt Ltd",
        "bank_name": "HDFC Bank",
        "ifsc_code": "HDFC0001234",
        "bank_account_last4": "6789",
        "status": "rejected",
        "interest_rate_annual": 12.0,
        "processing_fee": 400.0,
        "total_payable": 20705.81,
        "emi_amount": 3450.97,
        "disbursed_at": null,
        "disbursement_reference": null,
        "closed_at": null,
        "status_message": "Maaf kijiye, aapka loan application approve nahi hua. Support se sampark karein.",
        "admin_note": "Income proof missing",
        "reviewed_at": "2026-11-05T16:18:06",
        "created_at": "2026-11-05T16:18:06",
        "updated_at": "2026-11-05T16:18:06",
        "user_name": "Asha Verma",
        "user_email": "asha.verma@test.local",
        "user_mobile": "9000000001"
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### EMI payments (older)

`GET /api/admin/emi/payments`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `limit` | number | 1 to 500. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `stats` | object | Totals for this list. |
| `stats.total` | number | All rows. |
| `stats.approved` | number | Approved rows. |
| `stats.rejected` | number | Rejected rows. |
| `stats.pending` | number | Pending rows. |
| `payments` | array | List of payments. |
| `payments[].id` | number | Unique id of this record. |
| `payments[].emi_schedule_id` | number | EMI id. |
| `payments[].user_id` | number | Customer id. |
| `payments[].loan_application_id` | number | Loan id. |
| `payments[].amount_claimed` | number | Amount the customer says they paid. |
| `payments[].screenshot_url` | string | URL of the payment screenshot. |
| `payments[].utr_reference` | string | UTR or UPI reference. |
| `payments[].status` | string | Current status. See Status values. |
| `payments[].admin_note` | string | Note from staff. For a rejected payment, the reason. |
| `payments[].submitted_at` | string | When the proof was submitted. |
| `payments[].reviewed_at` | string | When staff decided. |
| `source` | object | Which table this came from. |
| `source.database` | string | Database name. |
| `source.table` | string | Table name. |

Response example (200):

```json
{
  "success": true,
  "message": "EMI payments",
  "data": {
    "stats": {
      "total": 2,
      "approved": 1,
      "rejected": 1,
      "pending": 0
    },
    "payments": [
      {
        "id": 1,
        "emi_schedule_id": 1,
        "user_id": 1,
        "loan_application_id": 3,
        "amount_claimed": 3450.97,
        "screenshot_url": "/NarainsonsProject/api/v1/files/a9a763f4462f40bbbbb6e7bfab3e1b28",
        "utr_reference": "UPI998877",
        "status": "rejected",
        "admin_note": "Screenshot is blurred",
        "submitted_at": "2026-11-05T16:18:07",
        "reviewed_at": "2026-11-05T16:18:07"
      },
      {
        "id": 2,
        "emi_schedule_id": 1,
        "user_id": 1,
        "loan_application_id": 3,
        "amount_claimed": 3450.97,
        "screenshot_url": "/NarainsonsProject/api/v1/files/a9a763f4462f40bbbbb6e7bfab3e1b28",
        "utr_reference": "UPI998878",
        "status": "approved",
        "admin_note": "Received in bank",
        "submitted_at": "2026-11-05T16:18:07",
        "reviewed_at": "2026-11-05T16:18:07"
      }
    ],
    "source": {
      "database": "narainson_db",
      "table": "emi_payment_submissions"
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### Banners (older)

`GET /api/admin/banners`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `limit` | number | 1 to 500. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `stats` | object | Totals for this list. |
| `stats.active` | number | Banners turned on. |
| `stats.total` | number | All rows. |
| `stats.inactive` | number | Banners turned off. |
| `limit` | number | Page size used. |
| `source` | object | Which table this came from. |
| `source.database` | string | Database name. |
| `source.table` | string | Table name. |
| `banners` | array | List of banners. |
| `banners[].id` | number | Unique id of this record. |
| `banners[].title` | string | Title. |
| `banners[].subtitle` | string | Line under the title. |
| `banners[].image_url` | string | Image URL. |
| `banners[].action_type` | string | On tap: `none`, `url` opens a link, `screen` opens an app screen. |
| `banners[].action_value` | string | The link or screen name to open. |
| `banners[].sort_order` | number | Order; lower comes first. |
| `banners[].is_active` | boolean | `true` or 1 if shown in the app. |
| `banners[].starts_at` | datetime | Show from this time. |
| `banners[].ends_at` | datetime | Show until this time. |
| `banners[].created_at` | string | When this record was created. |
| `banners[].updated_at` | string | When this record last changed. |

Response example (200):

```json
{
  "success": true,
  "message": "Dashboard banners",
  "data": {
    "stats": {
      "active": 3,
      "total": 3,
      "inactive": 0
    },
    "limit": 100,
    "source": {
      "database": "narainson_db",
      "table": "dashboard_banners"
    },
    "banners": [
      {
        "id": 1,
        "title": "Instant Loan upto ₹50,000",
        "subtitle": "Apply in minutes — admin approval shortly",
        "image_url": "https://cdn.example.com/banners/loan-hero.png",
        "action_type": "screen",
        "action_value": "loan_apply",
        "sort_order": 1,
        "is_active": true,
        "starts_at": null,
        "ends_at": null,
        "created_at": "2026-11-05T16:17:39",
        "updated_at": "2026-11-05T16:17:39"
      },
      {
        "id": 2,
        "title": "Complete KYC",
        "subtitle": "Faster approval when profile is complete",
        "image_url": "https://cdn.example.com/banners/kyc.png",
        "action_type": "screen",
        "action_value": "onboarding",
        "sort_order": 2,
        "is_active": true,
        "starts_at": null,
        "ends_at": null,
        "created_at": "2026-11-05T16:17:39",
        "updated_at": "2026-11-05T16:17:39"
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### All tables (older)

`GET /api/admin/database`  
Sign-in: Staff token

Rows have the table's own columns with secrets replaced by `[redacted]`. The `admins` table appears only for a super admin.

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `limit` | number | Rows per table, 1 to 1000. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `database` | string | Database name. |
| `limit_per_table` | number | Rows returned per table. |
| `tables` | object | Each table with `table`, `count`, `limit` and `rows`. |

Response example (200):

```json
{
  "success": true,
  "message": "Database tables",
  "data": {
    "database": "narainson_db",
    "limit_per_table": 2,
    "tables": {
      "users": {
        "table": "users",
        "count": 2,
        "limit": 2,
        "rows": [
          {
            "id": 2,
            "customer_code": null,
            "name": "New User",
            "first_name": null,
            "last_name": null,
            "mobile_number": "TEMP1793875685201",
            "alternate_mobile_number": null,
            "email": "second.user@test.local",
            "gender": null,
            "date_of_birth": null,
            "age": null,
            "marital_status": null,
            "nationality": "Indian",
            "profile_image": null,
            "aadhaar_encrypted": null,
            "aadhaar_last4": null,
            "pan_encrypted": null,
            "pan_last4": null,
            "aadhaar_document": null,
            "pan_document": null,
            "kyc_status": "pending",
            "kyc_verified_at": null,
            "kyc_rejection_reason": null,
            "current_address": null,
            "current_city": null,
            "current_state": null,
            "current_pincode": null,
            "current_country": "India",
            "permanent_address": null,
            "permanent_city": null,
            "permanent_state": null,
            "permanent_pincode": null,
            "permanent_country": "India",
            "same_as_current_address": null,
            "employment_type": null,
            "company_name": null,
            "designation": null,
            "work_experience_months": null,
            "monthly_income": null,
            "annual_income": null,
            "income_source": null,
            "bank_name": null,
            "bank_account_encrypted": null,
            "bank_account_last4": null,
            "ifsc_code": null,
            "bank_verification_status": "pending",
            "bank_verified_at": null,
            "password_hash": null,
            "otp_hash": null,
            "otp_expires_at": null,
            "otp_verified_at": null,
            "email_verified_at": "2026-11-05T16:18:06",
            "mobile_verified_at": null,
            "login_attempts": 0,
            "locked_until": null,
            "last_login": "2026-11-05T16:18:06",
            "last_logout_at": null,
            "account_status": "active",
            "fcm_token": null,
            "fcm_token_updated_at": null,
            "notification_enable": null,
            "notification_disable_at": null,
            "device_id": null,
            "device_type": null,
            "device_name": null,
            "device_model": null,
            "os_version": null,
            "app_version": null,
            "time_zone": null,
            "is_login": true,
            "is_app_installed": null,
            "application_install_time": null,
            "application_uninstall_time": null,
            "latitude": null,
            "longitude": null,
            "privacy_consent": null,
            "privacy_consent_at": null,
            "terms_accepted": null,
            "terms_accepted_at": null,
            "kyc_consent": null,
            "kyc_consent_at": null,
            "created_at": "2026-11-05T16:18:05",
            "updated_at": "2026-11-05T16:18:08",
            "deleted_at": null,
            "onboarding_step": 0,
            "session_token": null,
            "contacts_data": null,
            "gallery_data": null,
            "otp_attempts": 0,
            "session_expires_at": null,
            "token_version": 0
          },
          {
            "id": 1,
            "customer_code": "CUST11793875682002",
            "name": "Asha Verma",
            "first_name": "Asha",
            "last_name": "Verma",
            "mobile_number": "9000000001",
            "alternate_mobile_number": "9000000002",
            "email": "asha.verma@test.local",
            "gender": "Female",
            "date_of_birth": "1995-04-12",
            "age": 31,
            "marital_status": "Single",
            "nationality": "Indian",
            "profile_image": null,
            "aadhaar_encrypted": null,
            "aadhaar_last4": "1234",
            "pan_encrypted": null,
            "pan_last4": "567F",
            "aadhaar_document": "/NarainsonsProject/api/v1/files/e711feab43924504a7cd99813714a9b2",
            "pan_document": "/NarainsonsProject/api/v1/files/53c72ab0731b4a5e9f4b05caf3537d96",
            "kyc_status": "verified",
            "kyc_verified_at": "2026-11-05T16:18:06",
            "kyc_rejection_reason": null,
            "current_address": "12 MG Road",
            "current_city": "Jaipur",
            "current_state": "Rajasthan",
            "current_pincode": "302001",
            "current_country": "India",
            "permanent_address": "12 MG Road",
            "permanent_city": "Jaipur",
            "permanent_state": "Rajasthan",
            "permanent_pincode": "302001",
            "permanent_country": "India",
            "same_as_current_address": true,
            "employment_type": "Salaried",
            "company_name": "Acme Pvt Ltd",
            "designation": "Accountant",
            "work_experience_months": 48,
            "monthly_income": 35000.0,
            "annual_income": 420000.0,
            "income_source": "Salary",
            "bank_name": "HDFC Bank",
            "bank_account_encrypted": null,
            "bank_account_last4": "6789",
            "ifsc_code": "HDFC0001234",
            "bank_verification_status": "verified",
            "bank_verified_at": "2026-11-05T16:18:06",
            "password_hash": "[redacted]",
            "otp_hash": null,
            "otp_expires_at": null,
            "otp_verified_at": null,
            "email_verified_at": "2026-11-05T16:18:04",
            "mobile_verified_at": "2026-11-05T16:18:01",
            "login_attempts": 0,
            "locked_until": null,
            "last_login": "2026-11-05T16:18:05",
            "last_logout_at": null,
            "account_status": "active",
            "fcm_token": null,
            "fcm_token_updated_at": null,
            "notification_enable": true,
            "notification_disable_at": null,
            "device_id": "android-abc123",
            "device_type": "android",
            "device_name": "Asha's phone",
            "device_model": "Pixel 8",
            "os_version": "14",
            "app_version": "1.0.0",
            "time_zone": "Asia/Kolkata",
            "is_login": true,
            "is_app_installed": null,
            "application_install_time": null,
            "application_uninstall_time": null,
            "latitude": 26.9124,
            "longitude": 75.7873,
            "privacy_consent": true,
            "privacy_consent_at": "2026-11-05T16:18:02",
            "terms_accepted": true,
            "terms_accepted_at": "2026-11-05T16:18:02",
            "kyc_consent": true,
            "kyc_consent_at": "2026-11-05T16:18:02",
            "created_at": "2026-11-05T16:18",
            "updated_at": "2026-11-05T16:18:06",
            "deleted_at": null,
            "onboarding_step": 3,
            "session_token": null,
            "contacts_data": null,
            "gallery_data": null,
            "otp_attempts": 0,
            "session_expires_at": null,
            "token_version": 2
          }
        ]
      },
      "admins": {
        "table": "admins",
        "count": 2,
        "limit": 2,
        "rows": [
          {
            "id": 2,
            "email": "staff.two@test.local",
            "password_hash": "[redacted]",
            "full_name": "Staff Two",
            "mobile_number": null,
            "profile_image_path": null,
            "role": "admin",
            "account_status": "inactive",
            "session_token": null,
            "otp_hash": null,
            "otp_expires_at": null,
            "last_login": null,
            "created_at": "2026-11-05T16:18:09",
            "updated_at": "2026-11-05T16:18:09",
            "deleted_at": null,
            "otp_attempts": 0,
            "login_attempts": 0,
            "locked_until": null,
            "session_expires_at": null,
            "token_version": 0,
            "alerts_read_id": 0
          },
          {
            "id": 1,
            "email": "first.admin@test.local",
            "password_hash": "[redacted]",
            "full_name": "First Admin",
            "mobile_number": "9000000901",
            "profile_image_path": "admin-1.png",
            "role": "super_admin",
            "account_status": "active",
            "session_token": null,
            "otp_hash": null,
            "otp_expires_at": null,
            "last_login": "2026-11-05T16:17:59",
            "created_at": "2026-11-05T16:17:54",
            "updated_at": "2026-11-05T16:18:08",
            "deleted_at": null,
            "otp_attempts": 0,
            "login_attempts": 0,
            "locked_until": null,
            "session_expires_at": null,
            "token_version": 2,
            "alerts_read_id": 8
          }
        ]
      },
      "dashboard_banners": {
        "table": "dashboard_banners",
        "count": 3,
        "limit": 2,
        "rows": [
          {
            "id": 1,
            "title": "Instant Loan upto ₹50,000",
            "subtitle": "Apply in minutes — admin approval shortly",
            "image_url": "https://cdn.example.com/banners/loan-hero.png",
            "action_type": "screen",
            "action_value": "loan_apply",
            "sort_order": 1,
            "is_active": true,
            "starts_at": null,
            "ends_at": null,
            "created_at": "2026-11-05T16:17:39",
            "updated_at": "2026-11-05T16:17:39"
          },
          {
            "id": 2,
            "title": "Complete KYC",
            "subtitle": "Faster approval when profile is complete",
            "image_url": "https://cdn.example.com/banners/kyc.png",
            "action_type": "screen",
            "action_value": "onboarding",
            "sort_order": 2,
            "is_active": true,
            "starts_at": null,
            "ends_at": null,
            "created_at": "2026-11-05T16:17:39",
            "updated_at": "2026-11-05T16:17:39"
          }
        ]
      },
      "emi_collection_settings": {
        "table": "emi_collection_settings",
        "count": 2,
        "limit": 2,
        "rows": [
          {
            "id": 2,
            "payee_name": "Narainsons Finance",
            "upi_id": "narainsons@upi",
            "qr_image_url": "https://example.com/qr.png",
            "bank_name": "HDFC Bank",
            "bank_account_name": "Narainsons Finance Pvt Ltd",
            "bank_account_number": "XXXX1234567890",
            "ifsc_code": "HDFC0001234",
            "payment_note": "Write the loan code in the payment note.",
            "is_active": true,
            "updated_at": "2026-11-05T16:18:09"
          },
          {
            "id": 1,
            "payee_name": "Narainsons Finance",
            "upi_id": "narainsons@upi",
            "qr_image_url": "https://cdn.example.com/emi-payment-qr.png",
            "bank_name": "HDFC Bank",
            "bank_account_name": "Narainsons Finance Pvt Ltd",
            "bank_account_number": "XXXX1234567890",
            "ifsc_code": "HDFC0001234",
            "payment_note": "EMI pay karte waqt application code note mein likho. Screenshot yahi app se upload karo.",
            "is_active": false,
            "updated_at": "2026-11-05T16:18:09"
          }
        ]
      },
      "emi_payment_submissions": {
        "table": "emi_payment_submissions",
        "count": 2,
        "limit": 2,
        "rows": [
          {
            "id": 1,
            "emi_schedule_id": 1,
            "user_id": 1,
            "loan_application_id": 3,
            "amount_claimed": 3450.97,
            "screenshot_url": "/NarainsonsProject/api/v1/files/a9a763f4462f40bbbbb6e7bfab3e1b28",
            "utr_reference": "UPI998877",
            "status": "rejected",
            "admin_note": "Screenshot is blurred",
            "submitted_at": "2026-11-05T16:18:07",
            "reviewed_at": "2026-11-05T16:18:07"
          },
          {
            "id": 2,
            "emi_schedule_id": 1,
            "user_id": 1,
            "loan_application_id": 3,
            "amount_claimed": 3450.97,
            "screenshot_url": "/NarainsonsProject/api/v1/files/a9a763f4462f40bbbbb6e7bfab3e1b28",
            "utr_reference": "UPI998878",
            "status": "approved",
            "admin_note": "Received in bank",
            "submitted_at": "2026-11-05T16:18:07",
            "reviewed_at": "2026-11-05T16:18:07"
          }
        ]
      },
      "user_notifications": {
        "table": "user_notifications",
        "count": 9,
        "limit": 2,
        "rows": [
          {
            "id": 4,
            "user_id": 1,
            "category": "loan_status",
            "title": "Loan disbursed",
            "body": "Aapka loan amount bank account mein bhej diya gaya hai. EMI schedule app mein dekhein.",
            "payload_json": "{\"status\": \"disbursed\", \"loan_application_id\": 3}",
            "read_at": "2026-11-05T16:18:07",
            "created_at": "2026-11-05T16:18:07"
          },
          {
            "id": 5,
            "user_id": 1,
            "category": "emi_payment",
            "title": "EMI payment submitted",
            "body": "Aapka payment proof admin ko bhej diya gaya. Verify hone par EMI paid mark hogi.",
            "payload_json": "{\"emi_payment_id\": 1, \"emi_schedule_id\": 1}",
            "read_at": "2026-11-05T16:18:07",
            "created_at": "2026-11-05T16:18:07"
          }
        ]
      },
      "loan_emi_schedules": {
        "table": "loan_emi_schedules",
        "count": 6,
        "limit": 2,
        "rows": [
          {
            "id": 6,
            "loan_application_id": 3,
            "user_id": 1,
            "installment_number": 6,
            "due_date": "2027-05-05",
            "amount_due": 3450.96,
            "amount_paid": 0.0,
            "status": "pending",
            "last_reminder_at": null,
            "paid_at": null,
            "created_at": "2026-11-05T16:18:06",
            "updated_at": "2026-11-05T16:18:06"
          },
          {
            "id": 5,
            "loan_application_id": 3,
            "user_id": 1,
            "installment_number": 5,
            "due_date": "2027-04-05",
            "amount_due": 3450.97,
            "amount_paid": 0.0,
            "status": "pending",
            "last_reminder_at": null,
            "paid_at": null,
            "created_at": "2026-11-05T16:18:06",
            "updated_at": "2026-11-05T16:18:06"
          }
        ]
      },
      "loan_apply_guide_video": {
        "table": "loan_apply_guide_video",
        "count": 1,
        "limit": 2,
        "rows": [
          {
            "id": 1,
            "title": "Loan kaise apply karein",
            "description": "Step-by-step: amount select karo, guarantor details bharo, submit — admin approval pending.",
            "video_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-hi.mp4",
            "thumbnail_url": "https://storage.example.com/narainsons/videos/how-to-apply-loan-thumb.jpg",
            "video_type": "mp4",
            "duration_seconds": 180,
            "locale": "hi",
            "sort_order": 1,
            "is_active": true,
            "created_at": "2026-11-05T16:17:39",
            "updated_at": "2026-11-05T16:17:39"
          }
        ]
      },
      "loan_applications": {
        "table": "loan_applications",
        "count": 3,
        "limit": 2,
        "rows": [
          {
            "id": 1,
            "user_id": 1,
            "application_code": "LN1793875686505B0D7",
            "requested_amount": 20000.0,
            "tenure_months": 6,
            "purpose": "personal",
            "purpose_detail": "Home repair",
            "monthly_income": 35000.0,
            "employment_type": "Salaried",
            "company_name": "Acme Pvt Ltd",
            "bank_name": "HDFC Bank",
            "ifsc_code": "HDFC0001234",
            "bank_account_last4": "6789",
            "status": "cancelled",
            "status_message": "Application aapne cancel kar di.",
            "admin_note": null,
            "reviewed_at": null,
            "created_at": "2026-11-05T16:18:06",
            "updated_at": "2026-11-05T16:18:06",
            "interest_rate_annual": 12.0,
            "processing_fee": 400.0,
            "total_payable": 20705.81,
            "emi_amount": 3450.97,
            "disbursed_at": null,
            "disbursement_reference": null,
            "closed_at": null
          },
          {
            "id": 2,
            "user_id": 1,
            "application_code": "LN1793875686609D351",
            "requested_amount": 20000.0,
            "tenure_months": 6,
            "purpose": "personal",
            "purpose_detail": "Home repair",
            "monthly_income": 35000.0,
            "employment_type": "Salaried",
            "company_name": "Acme Pvt Ltd",
            "bank_name": "HDFC Bank",
            "ifsc_code": "HDFC0001234",
            "bank_account_last4": "6789",
            "status": "rejected",
            "status_message": "Maaf kijiye, aapka loan application approve nahi hua. Support se sampark karein.",
            "admin_note": "Income proof missing",
            "reviewed_at": "2026-11-05T16:18:06",
            "created_at": "2026-11-05T16:18:06",
            "updated_at": "2026-11-05T16:18:06",
            "interest_rate_annual": 12.0,
            "processing_fee": 400.0,
            "total_payable": 20705.81,
            "emi_amount": 3450.97,
            "disbursed_at": null,
            "disbursement_reference": null,
            "closed_at": null
          }
        ]
      },
      "loan_guarantors": {
        "table": "loan_guarantors",
        "count": 6,
        "limit": 2,
        "rows": [
          {
            "id": 6,
            "loan_application_id": 3,
            "guarantor_index": 2,
            "full_name": "Neha Sharma",
            "mobile_number": "9000000102",
            "relation": "friend",
            "address": null,
            "aadhaar_last4": null,
            "pan_last4": null,
            "created_at": "2026-11-05T16:18:06"
          },
          {
            "id": 5,
            "loan_application_id": 3,
            "guarantor_index": 1,
            "full_name": "Ravi Verma",
            "mobile_number": "9000000101",
            "relation": "brother",
            "address": "Jaipur",
            "aadhaar_last4": "1111",
            "pan_last4": "222A",
            "created_at": "2026-11-05T16:18:06"
          }
        ]
      }
    }
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |

### One table (older)

`GET /api/admin/database/{table}`  
Sign-in: Staff token

Query parameters:

| Name | Type | Meaning |
|---|---|---|
| `limit` | number | 1 to 1000. |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `table` | string | Table name. |
| `count` | number | Number of rows in this table. |
| `limit` | number | Page size used. |
| `rows` | array | The rows on this page. |
| `rows[].id` | number | Unique id of this record. |
| `rows[].user_id` | number | Customer id. |
| `rows[].application_code` | string | Loan code shown to people. |
| `rows[].requested_amount` | number | Loan amount in rupees. |
| `rows[].tenure_months` | number | Loan length in months. |
| `rows[].purpose` | string | Why the loan is needed. |
| `rows[].purpose_detail` | string | More about the purpose. |
| `rows[].monthly_income` | number | Monthly income in rupees. |
| `rows[].employment_type` | string | `Salaried`, `Self Employed`, `Business`, `Professional`, `Unemployed` or `Other`. |
| `rows[].company_name` | string | Employer or business name. |
| `rows[].bank_name` | string | Bank name. |
| `rows[].ifsc_code` | string | Bank IFSC code. |
| `rows[].bank_account_last4` | string | Last 4 digits of the bank account. |
| `rows[].status` | string | Current status. See Status values. |
| `rows[].status_message` | string | Status text to show the customer. |
| `rows[].admin_note` | string | Note from staff. For a rejected payment, the reason. |
| `rows[].reviewed_at` | datetime | When staff decided. |
| `rows[].created_at` | string | When this record was created. |
| `rows[].updated_at` | string | When this record last changed. |
| `rows[].interest_rate_annual` | number | Yearly interest percent, reducing balance. |
| `rows[].processing_fee` | number | Processing fee in rupees. |
| `rows[].total_payable` | number | Total of all EMIs in rupees. |
| `rows[].emi_amount` | number | Monthly EMI in rupees. |
| `rows[].disbursed_at` | datetime | When the money was sent. |
| `rows[].disbursement_reference` | string | Bank or UTR reference of the money sent. |
| `rows[].closed_at` | datetime | When the loan closed. |

Response example (200):

```json
{
  "success": true,
  "message": "Table rows",
  "data": {
    "table": "loan_applications",
    "count": 3,
    "limit": 2,
    "rows": [
      {
        "id": 1,
        "user_id": 1,
        "application_code": "LN1793875686505B0D7",
        "requested_amount": 20000.0,
        "tenure_months": 6,
        "purpose": "personal",
        "purpose_detail": "Home repair",
        "monthly_income": 35000.0,
        "employment_type": "Salaried",
        "company_name": "Acme Pvt Ltd",
        "bank_name": "HDFC Bank",
        "ifsc_code": "HDFC0001234",
        "bank_account_last4": "6789",
        "status": "cancelled",
        "status_message": "Application aapne cancel kar di.",
        "admin_note": null,
        "reviewed_at": null,
        "created_at": "2026-11-05T16:18:06",
        "updated_at": "2026-11-05T16:18:06",
        "interest_rate_annual": 12.0,
        "processing_fee": 400.0,
        "total_payable": 20705.81,
        "emi_amount": 3450.97,
        "disbursed_at": null,
        "disbursement_reference": null,
        "closed_at": null
      },
      {
        "id": 2,
        "user_id": 1,
        "application_code": "LN1793875686609D351",
        "requested_amount": 20000.0,
        "tenure_months": 6,
        "purpose": "personal",
        "purpose_detail": "Home repair",
        "monthly_income": 35000.0,
        "employment_type": "Salaried",
        "company_name": "Acme Pvt Ltd",
        "bank_name": "HDFC Bank",
        "ifsc_code": "HDFC0001234",
        "bank_account_last4": "6789",
        "status": "rejected",
        "status_message": "Maaf kijiye, aapka loan application approve nahi hua. Support se sampark karein.",
        "admin_note": "Income proof missing",
        "reviewed_at": "2026-11-05T16:18:06",
        "created_at": "2026-11-05T16:18:06",
        "updated_at": "2026-11-05T16:18:06",
        "interest_rate_annual": 12.0,
        "processing_fee": 400.0,
        "total_payable": 20705.81,
        "emi_amount": 3450.97,
        "disbursed_at": null,
        "disbursement_reference": null,
        "closed_at": null
      }
    ]
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Not signed in as staff |
| 403 | Signed in, but not a super admin |
| 400 | Unknown table |

## Internal

For scheduled jobs on the server.

### Run EMI reminders

`POST /api/internal/emi-reminders`  
Sign-in: Cron secret

The server already does this every hour by itself. Marks overdue EMIs and sends reminders.

| Header | Value |
|---|---|
| `X-Cron-Secret` | `cron.emi.secret` from server settings |

Response `data` fields:

| Field | Type | Meaning |
|---|---|---|
| `window_days_before` | number | Reminders start this many days before the due date. |
| `reminders_sent` | number | Reminders sent in this run. |
| `overdue_marked` | number | EMIs marked overdue in this run. |

Response example (200):

```json
{
  "success": true,
  "message": "EMI reminders processed",
  "data": {
    "window_days_before": 3,
    "reminders_sent": 0,
    "overdue_marked": 0
  },
  "error_code": null
}
```

Errors:

| HTTP | When |
|---|---|
| 401 | Wrong secret |
