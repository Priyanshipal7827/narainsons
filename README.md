# Narainsons

Loan app ka backend (Java Servlets, Tomcat 10.1, MySQL 8) aur admin panel (static HTML/JS).

## Folder structure

```
Narainsons/
├── NarainsonsProject/            Backend + admin UI (Maven WAR). Eclipse/STS is folder ko project ki tarah kholta hai.
│   ├── src/main/java/com/narainsons/
│   │   ├── servlet/              HTTP routes (web.xml mein mapped)
│   │   ├── service/              Business logic
│   │   ├── dao/                  SQL queries
│   │   ├── model/                Data classes
│   │   ├── filter/, middleware/  Errors, security headers, CORS, logging
│   │   └── config/, util/        Config, connection pool, validation, rate limit
│   ├── src/main/resources/       *.properties (real files git mein nahi jani chahiye, sirf *.example)
│   ├── src/main/webapp/admin/    Admin panel. UI yahin edit karo. Naye pages `assets/js/ns-pages.js` mein, shared table aur alerts `ns-ui.js` mein, styles `admin-ui.css` mein.
│   ├── src/test/java/            Unit tests
│   ├── database/schema/          SQL files, number ke order mein chalti hain
│   ├── docs/                     api/, auth/, loan/, admin/, setup/
│   ├── postman/                  Postman collections
│   └── scripts/
│       ├── db/                   InitDatabase, SeedAdmin, SeedFiveUsers, debug tools
│       ├── api-test/             e2e_regression.py, Run-*.ps1
│       └── deploy/               compile-and-deploy-classes
├── _archive/                     Purana admin UI, ab use nahi hota
├── Servers/                      Eclipse Tomcat config (port 8082)
└── .metadata/                    Eclipse workspace (generated)
```

## Chalane ke steps

**Zaroorat:** JDK 17+, MySQL 8, Tomcat 10.1, Maven 3.9 (ya Eclipse/STS).

1. **MySQL chalu karo** (Administrator terminal mein): `net start MySQL80`
2. **Config banao** (`NarainsonsProject/src/main/resources/` mein). `db.properties`,
   `app.properties` aur `secrets.properties` ko unki `.example` files se copy karke values bharo.
3. **Database banao ya update karo** (`NarainsonsProject/` se). Yeh dobara chalana safe hai, data nahi hatata:
   ```
   java -cp "src/main/webapp/WEB-INF/lib/*" scripts/db/InitDatabase.java
   ```
4. **Pehla admin banao** (agar nahi hai):
   ```
   java -cp "src/main/webapp/WEB-INF/lib/*" scripts/db/SeedAdmin.java admin@example.com "StrongPassword" "Full Name"
   ```
5. **Server chalao:**
   - **VS Code:** `Narainsons` folder kholo aur `Ctrl+Shift+B` dabao (task: *Narainsons: Run app*). Rokne ke liye terminal mein `Ctrl+C`. Eclipse wala Tomcat pehle band hona chahiye, dono port 8082 use karte hain.
   - **Eclipse/STS:** Maven → Update Project, phir Servers tab → Tomcat v10.1 → Start.
   - **Maven:** `mvn package`, phir `target/NarainsonsProject.war` ko Tomcat ke `webapps/` mein daalo.
6. **Kholo:**
   - Admin panel: http://localhost:8082/NarainsonsProject/admin/login.html
   - Health check: http://localhost:8082/NarainsonsProject/api/health

   Health "schema is out of date" bole to step 3 dobara chalao.

## Secrets (git mein nahi jate)

`secrets.properties` mein `jwt.secret` zaroori hai. Push ke liye `src/main/resources/firebase-service-account.json` chahiye; file na ho to app chalti hai, bas push nahi jata.

## Dependency badalne par

Eclipse `src/main/webapp/WEB-INF/lib` se jars deploy karta hai. `pom.xml` badalne ke baad: `mvn -Pcopy-libs process-resources`

## Testing

- **Unit tests:** `mvn test`
- **API docs dobara banana:** `scripts/api-test/build_api_docs.py` (har API chalakar asli response se docs banata hai; naya response field aane par `api_field_docs.py` mein uska matlab likhna hoga).
- **Admin regression:** `scripts/api-test/e2e_admin.py` (list, filter, download, alerts, roles). Yeh bhi sirf test database par.
- **API regression:** `scripts/api-test/e2e_regression.py`. Yeh test data likhta hai, isliye sirf khaali test database par chalao. Details file ke upar likhi hain.

## Settings (`app.properties`)

| Key | Default | Kaam |
|-----|---------|------|
| `app.dev.expose_otp` | `false` | `true` par OTP API response mein aata hai. Sirf local testing ke liye. |
| `app.sync.contacts_gallery.enabled` | `false` | User ke contacts/gallery save karna. Compliance se confirm karke hi on karein. |
| `cors.allowed.origins` | khaali | Alag jagah hosted UI ke origins. `/admin` aur mobile app ko zaroorat nahi. |
| `session.admin.hours` / `session.user.days` | `12` / `30` | Login kitni der valid rahe. |
| `emi.reminder.min_hours` | `24` | Ek EMI ke do reminders ke beech ka gap. |
| `jwt.access.minutes` | `60` | JWT access token kitni der chale. |
| `loan.interest.annual_percent` | `0` | Saalana byaj (reducing balance). |
| `loan.processing_fee.percent` | `0` | Processing fee. |
| `loan.approve.require_kyc_verified` | `true` | KYC verify hue bina loan approve nahi. |

## API docs

- **Client ke liye poora workflow:** `NarainsonsProject/docs/client/Application-Workflow.html` (browser mein kholo)
- **Saari APIs, har field ke saath:** `NarainsonsProject/docs/api/API-REFERENCE.html` (browser mein kholo) ya `API-REFERENCE.md`, Postman: `NarainsonsProject/postman/Narainsons-API.postman_collection.json`
- Admin APIs: `NarainsonsProject/docs/admin/ADMIN-PORTAL.md`
- Tomcat start issues: `NarainsonsProject/docs/setup/TOMCAT-START-FIX.md`
