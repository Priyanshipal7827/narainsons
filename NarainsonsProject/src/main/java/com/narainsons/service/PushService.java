package com.narainsons.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.narainsons.dao.DeviceDAO;
import com.narainsons.dao.UserDAO;
import com.narainsons.util.DBConnection;
import com.narainsons.util.GsonProvider;

/**
 * Firebase Cloud Messaging (HTTP v1). Sends in the background after the database commit, so a slow
 * or failing push can never hold up or undo a loan or payment. Tokens FCM reports as dead are removed.
 */
public final class PushService {

    private static final Logger LOG = Logger.getLogger(PushService.class.getName());
    private static final String CREDENTIALS_FILE = "firebase-service-account.json";
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final PushService INSTANCE = new PushService();

    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "fcm-push");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final DeviceDAO deviceDAO = new DeviceDAO();
    private final UserDAO userDAO = new UserDAO();

    private final JsonObject credentials = loadCredentials();
    private String accessToken;
    private Instant accessTokenExpiry = Instant.EPOCH;

    private PushService() {
    }

    public static PushService shared() {
        return INSTANCE;
    }

    public boolean isConfigured() {
        return credentials != null;
    }

    /** Queues a push to all of the user's phones, once the current transaction has committed. */
    public void sendToUser(long userId, String title, String body, Map<String, String> data) {
        if (!isConfigured()) {
            return;
        }
        DBConnection.afterCommit(() -> pool.submit(() -> deliver(userId, title, body, data)));
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    private void deliver(long userId, String title, String body, Map<String, String> data) {
        try {
            var user = userDAO.findById(userId);
            if (user.isEmpty() || Boolean.FALSE.equals(user.get().getNotificationEnable())) {
                return;
            }
            for (String token : deviceDAO.tokensForUser(userId)) {
                sendOne(token, title, body, data);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Push failed for user " + userId, e);
        }
    }

    private void sendOne(String token, String title, String body, Map<String, String> data) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("token", token);
        message.put("notification", Map.of("title", title, "body", body));
        message.put("data", data);
        message.put("android", Map.of("priority", "high", "notification", Map.of("channel_id", "narainsons_default")));
        message.put("apns", Map.of("payload", Map.of("aps", Map.of("sound", "default"))));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://fcm.googleapis.com/v1/projects/"
                        + credentials.get("project_id").getAsString() + "/messages:send"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GsonProvider.toJson(Map.of("message", message))))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 200) {
            LOG.fine("Push delivered to FCM");
            return;
        }
        // 404 UNREGISTERED: app removed. 400: the token itself is malformed.
        if (status == 404 || (status == 400 && response.body().contains("registration token"))) {
            deviceDAO.removeToken(token);
            LOG.info("Removed a dead FCM token");
            return;
        }
        if (status == 401) {
            clearAccessToken();
        }
        LOG.warning("FCM returned " + status + ": " + response.body());
    }

    private synchronized void clearAccessToken() {
        accessToken = null;
        accessTokenExpiry = Instant.EPOCH;
    }

    /** OAuth2 service-account flow: sign a short JWT with the key, swap it for an access token, cache it. */
    private synchronized String accessToken() throws Exception {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpiry)) {
            return accessToken;
        }
        String tokenUri = credentials.get("token_uri").getAsString();
        Instant now = Instant.now();
        String assertion = JWT.create()
                .withIssuer(credentials.get("client_email").getAsString())
                .withAudience(tokenUri)
                .withClaim("scope", SCOPE)
                .withIssuedAt(now)
                .withExpiresAt(now.plusSeconds(3600))
                .sign(Algorithm.RSA256(null, privateKey()));
        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
        HttpResponse<String> response = http.send(HttpRequest.newBuilder()
                .uri(URI.create(tokenUri))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Google token request failed with status " + response.statusCode());
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        accessToken = json.get("access_token").getAsString();
        accessTokenExpiry = now.plusSeconds(json.get("expires_in").getAsLong() - 120);
        return accessToken;
    }

    private RSAPrivateKey privateKey() throws Exception {
        String pem = credentials.get("private_key").getAsString()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    private static JsonObject loadCredentials() {
        try (InputStream in = PushService.class.getClassLoader().getResourceAsStream(CREDENTIALS_FILE)) {
            if (in == null) {
                LOG.warning(CREDENTIALS_FILE + " not found, push notifications are off");
                return null;
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not read " + CREDENTIALS_FILE + ", push notifications are off", e);
            return null;
        }
    }
}
