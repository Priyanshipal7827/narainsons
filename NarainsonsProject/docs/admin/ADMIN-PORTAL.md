# Admin Portal — DB login + dashboard

## Database: `admins` table

| Field | Notes |
|-------|--------|
| email | UNIQUE, login id |
| password_hash | BCrypt |
| full_name, mobile_number | profile |
| role | `super_admin` \| `admin` |
| account_status | **`active`** required for login |

Seed first admin:

```bash
java -cp "src/main/webapp/WEB-INF/lib/*" scripts/db/SeedAdmin.java admin@example.com "StrongPassword" "Full Name"
```

Email, password aur naam command mein do: `SeedAdmin <email> <password> "Full Name"`

## Auth APIs (public)

| Method | Path |
|--------|------|
| POST | `/api/admin/auth/login` `{ "email", "password" }` |
| POST | `/api/admin/auth/register` — pehla admin bina token; baad mein super_admin token |
| POST | `/api/admin/auth/password/forgot/send` |
| POST | `/api/admin/auth/password/forgot/reset` |
| GET | `/api/admin/auth/me` Bearer admin token |
| POST | `/api/admin/auth/profile` update name/mobile |

**Inactive** admin → login block.

## Protected admin APIs

Header: `Authorization: Bearer {access_token}`

| GET | `/api/admin/dashboard` |
| GET | `/api/admin/loan/applications` |
| POST | `/api/admin/loan/applications/{id}/approve` |
| POST | `/api/admin/emi/payments/{id}/approve` |

(Legacy `X-Admin-Key` ab bhi chalega.)

## UI (purana, archived): `_archive/Narainsons-Admin-WebApp` — ab `src/main/webapp/admin` use hota hai

Alag folder — Live Server se kholo:

- `login.html` — admin email
- `forgot-password.html`
- `dashboard.html` — loans + EMI approve

`assets/js/config.js` → API base `http://localhost:8082/NarainsonsProject`

## Loan flow APIs (Bearer admin JWT)

| Method | Path | Kaam |
|--------|------|------|
| POST | `/api/admin/users/{id}/kyc` | `{ "action": "verify" }` ya `{ "action": "reject", "reason": "..." }` |
| POST | `/api/admin/loan/applications/{id}/approve` | KYC verified hona chahiye |
| POST | `/api/admin/loan/applications/{id}/reject` | Sirf pending loan |
| POST | `/api/admin/loan/applications/{id}/disburse` | `{ "disbursement_reference": "UTR..." }` |
| POST | `/api/admin/emi/payments/{id}/approve` | Aakhri EMI par loan close hota hai |
| POST | `/api/admin/emi/payments/{id}/reject` | EMI wapas pending ya overdue |
| GET, POST | `/api/admin/settings/emi-collection` | UPI, QR, bank details. POST sirf super admin. |
| POST | `/api/admin/notifications/send` | `{ "user_id": 1, "title": "...", "body": "..." }` |

## List, filter and download (every table in the panel)

`GET /api/admin/list/{name}`: `users`, `loans`, `emi_payments`, `emi_schedules`, `guarantors`, `user_notifications`, `banners`, `videos`, `emi_settings`, `admin_alerts`, `admins` (super admin only).

| Parameter | Kaam |
|-----------|------|
| `limit`, `offset` | Page size 25, 50, 100, 200 ya 500 |
| `search` | Text search |
| `date_from`, `date_to` | `yyyy-MM-dd` |
| `status`, `kyc_status`, `amount_min`, `amount_max`, ... | Har list ke apne filters |
| `sort`, `dir` | Column aur `asc` ya `desc` |

`GET /api/admin/list/{name}/export?format=csv` ya `format=xlsx` wahi filters leta hai.

## Dashboard, alerts, staff

| Method | Path | Kaam |
|--------|------|------|
| GET | `/api/admin/dashboard/summary?date_from=&date_to=` | Dashboard ke cards aur charts |
| GET | `/api/admin/loan/applications/{id}` | Ek loan, guarantors ke saath |
| GET | `/api/admin/alerts?after_id=` | Bell aur sound ke liye naye alerts |
| POST | `/api/admin/alerts/read` | Sab read |
| POST | `/api/admin/admins/{id}/status` | `{ "active": false }`, sirf super admin |
| POST | `/api/admin/videos`, `/{id}/update`, `/{id}/delete` | Guide videos |
| POST | `/api/admin/notifications/send` | `{ "user_id": 1, ... }` ya `{ "all": true, ... }` (sirf super admin) |

Alerts aate hain: new customer (pehli verified login par), new loan, KYC submitted, EMI proof. Browser mein sound page par ek baar click karne ke baad hi baj sakti hai; topbar ke **Sound on** button se band ya chalu hoti hai.
