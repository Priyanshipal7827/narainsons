package com.narainsons.util;

import java.time.Instant;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.narainsons.config.SecretsConfig;

/**
 * HS256 access tokens. The token only proves who the caller is; callers still load the account and
 * compare {@link Claims#tokenVersion()}, so a password change or block takes effect immediately.
 */
public final class JwtService {

    public static final String SCOPE_USER = "user";
    public static final String SCOPE_ADMIN = "admin";
    private static final String ISSUER = "narainsons";
    private static final int MIN_SECRET_LENGTH = 32;

    public record Claims(long subjectId, String scope, int tokenVersion) {
    }

    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtService(String secret) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("jwt.secret must be at least " + MIN_SECRET_LENGTH + " characters (secrets.properties)");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
    }

    private static final class Holder {
        private static final JwtService INSTANCE = new JwtService(SecretsConfig.get("jwt.secret"));
    }

    public static JwtService shared() {
        return Holder.INSTANCE;
    }

    public static boolean isConfigured() {
        return SecretsConfig.get("jwt.secret").length() >= MIN_SECRET_LENGTH;
    }

    public String issue(long subjectId, String scope, int tokenVersion, long ttlSeconds) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(String.valueOf(subjectId))
                .withClaim("scope", scope)
                .withClaim("ver", tokenVersion)
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(now)
                .withExpiresAt(now.plusSeconds(ttlSeconds))
                .sign(algorithm);
    }

    /** @throws ApiException 401 TOKEN_EXPIRED or INVALID_TOKEN */
    public Claims verify(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            return new Claims(Long.parseLong(jwt.getSubject()), jwt.getClaim("scope").asString(),
                    jwt.getClaim("ver").asInt());
        } catch (TokenExpiredException e) {
            throw new ApiException(401, "TOKEN_EXPIRED", "Access token expired. Refresh it and retry.");
        } catch (JWTVerificationException | NumberFormatException | NullPointerException e) {
            throw new ApiException(401, "INVALID_TOKEN", "Invalid access token");
        }
    }

    /** Verifies the token and that it was issued for this scope, so a user token cannot call admin APIs. */
    public Claims verify(String token, String requiredScope) {
        Claims claims = verify(token);
        if (!requiredScope.equals(claims.scope())) {
            throw new ApiException(401, "INVALID_TOKEN", "Invalid access token");
        }
        return claims;
    }
}
