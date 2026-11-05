# Tomcat v10.1 — start fail fix (STS)

Error: **`ClassNotFoundException: ServletContextEvent`** → **`/NarainsonsProject` deploy fail** → Postman **ECONNREFUSED :8082**

---

## Fix 1 — Targeted Runtime (sabse important)

1. Project **NarainsonsProject** → right-click → **Properties**
2. **Targeted Runtimes**
3. ✓ **Apache Tomcat v10.1** (ya jo naam aapke Tomcat ka hai)
4. ✗ Tomcat 9 / 8 hata do
5. **Apply and Close**

Agar list khali hai:

- **Window → Preferences → Server → Runtime Environments → Add → Apache Tomcat v10.1** → tomcat folder select (`apache-tomcat-10.1.36`)

Phir dubara Targeted Runtimes mein tick karo.

---

## Fix 2 — Maven + Clean

1. Right-click project → **Maven → Update Project** (Force Update ✓)
2. **Project → Clean → NarainsonsProject**
3. **Project → Build Project**

**Problems** view mein **0 errors** honi chahiye.

---

## Fix 3 — Server dubara publish

1. **Servers** tab → Tomcat → **Clean...**
2. NarainsonsProject → **Remove**
3. **Add and Remove...** → NarainsonsProject → **Add** → Finish
4. Tomcat → **Start**

Deploy path check:  
`Servers` → Tomcat double-click → **Server Locations** → **Use Tomcat installation** (optional, recommended)

---

## Fix 4 — Classpath (project mein update ho chuka)

`.classpath` mein **Apache Tomcat v10.1** runtime add hai.  
Agar STS error de "Unbound classpath" → Fix 1 karo.

`web.xml` se **AppShutdownListener** hata diya (wo error trigger kar sakta tha).

---

## Success check

Console (red error ke bina):

```
Starting ProtocolHandler ["http-nio-8082"]
```

Browser:

```
http://localhost:8082/NarainsonsProject/api/health
```

---

## Ports

| Port | Use |
|------|-----|
| 8082 | APIs (Postman) |
| 8005 | Tomcat shutdown (normal, API nahi) |

---

## Ab bhi fail?

Console ka **pehla SEVERE** line copy karo.

- **`ClassNotFoundException: jakarta.servlet...`** → Tomcat 10 targeted runtime missing
- **`ClassNotFoundException: Gson`** → **`WEB-INF/lib` khali thi** (Eclipse Maven libs deploy nahi kar raha tha). Ab `src/main/webapp/WEB-INF/lib` mein gson, mysql, jbcrypt, mail jars hain. Server **Clean** + dubara **Start**.
- **`ClassNotFoundException: com.mysql...`** → upar wale lib folder check karo
- **`Address already in use 8082`** → doosra program port use kar raha hai ya purana Tomcat process
