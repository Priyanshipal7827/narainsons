package com.narainsons.service;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.narainsons.config.AppConfig;
import com.narainsons.dao.RefreshTokenDAO;
import com.narainsons.dao.UserDAO;
import com.narainsons.model.User;
import com.narainsons.util.ApiException;
import com.narainsons.util.DBConnection;
import com.narainsons.util.Hashing;
import com.narainsons.util.JwtService;
import com.narainsons.util.ServletUtil;

/** Issues the access + refresh token pair for app users and rotates refresh tokens. */
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenDAO refreshDAO = new RefreshTokenDAO();
    private final UserDAO userDAO = new UserDAO();

    public Map<String, Object> issueFor(User user) throws SQLException {
        return issue(user, UUID.randomUUID().toString());
    }

    /**
     * Each refresh token works once. Seeing a used one again means it may have been stolen, so
     * that whole login (family) is ended and the user has to sign in again.
     */
    public Map<String, Object> refresh(String rawToken) throws SQLException {
        if (ServletUtil.isBlank(rawToken)) {
            throw new IllegalArgumentException("refresh_token is required");
        }
        RefreshTokenDAO.Row row = refreshDAO.findByHash(Hashing.sha256(rawToken.trim()))
                .orElseThrow(TokenService::invalidRefresh);
        if (row.revokedAt() != null) {
            refreshDAO.revokeFamily(row.familyId());
            throw invalidRefresh();
        }
        if (row.expiresAt().isBefore(LocalDateTime.now())) {
            throw invalidRefresh();
        }
        User user = userDAO.findById(row.userId()).orElseThrow(TokenService::invalidRefresh);
        if (!"active".equalsIgnoreCase(user.getAccountStatus())) {
            throw ApiException.forbidden("Account is not active. Contact Narainsons support.");
        }
        return DBConnection.inTransaction(() -> {
            if (!refreshDAO.revoke(row.id())) {
                throw invalidRefresh();
            }
            return issue(user, row.familyId());
        });
    }

    /** Ends the login this refresh token belongs to. Unknown tokens are ignored. */
    public void revoke(String rawToken) throws SQLException {
        if (!ServletUtil.isBlank(rawToken)) {
            var row = refreshDAO.findByHash(Hashing.sha256(rawToken.trim()));
            if (row.isPresent()) {
                refreshDAO.revokeFamily(row.get().familyId());
            }
        }
    }

    public void revokeAll(long userId) throws SQLException {
        refreshDAO.revokeAllForUser(userId);
    }

    private Map<String, Object> issue(User user, String familyId) throws SQLException {
        long accessSeconds = AppConfig.getAccessTokenMinutes() * 60L;
        int refreshDays = AppConfig.getUserSessionDays();
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        refreshDAO.insert(user.getId(), Hashing.sha256(refresh), familyId, LocalDateTime.now().plusDays(refreshDays));

        int version = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        Map<String, Object> data = new HashMap<>();
        data.put("access_token", JwtService.shared().issue(user.getId(), JwtService.SCOPE_USER, version, accessSeconds));
        data.put("token_type", "Bearer");
        data.put("expires_in", accessSeconds);
        data.put("refresh_token", refresh);
        data.put("refresh_expires_in", refreshDays * 86_400L);
        return data;
    }

    private static ApiException invalidRefresh() {
        return new ApiException(401, "INVALID_REFRESH_TOKEN", "Please sign in again");
    }
}
