package com.medcore.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Redis-backed storage for opaque, single-use tokens (refresh tokens, password reset, email verification,
 * 2FA codes) and for the access-token revocation list. Only SHA-256 hashes of secrets are stored.
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String PREFIX = "medcore:token:";
    private static final String REVOKED_PREFIX = "medcore:jwt:revoked:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public enum Purpose {
        REFRESH, PASSWORD_RESET, EMAIL_VERIFICATION, TWO_FACTOR
    }

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String randomNumericCode(int digits) {
        StringBuilder sb = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public void store(Purpose purpose, String secret, String value, Duration ttl) {
        redis.opsForValue().set(key(purpose, secret), value, ttl);
    }

    /** Atomically reads and deletes the token, so each secret can be used exactly once. */
    public Optional<String> consume(Purpose purpose, String secret) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(key(purpose, secret)));
    }

    public void revoke(Purpose purpose, String secret) {
        redis.delete(key(purpose, secret));
    }

    public void revokeAccessToken(String jti, Duration remainingLifetime) {
        if (!remainingLifetime.isNegative() && !remainingLifetime.isZero()) {
            redis.opsForValue().set(REVOKED_PREFIX + jti, "1", remainingLifetime);
        }
    }

    public boolean isAccessTokenRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(REVOKED_PREFIX + jti));
    }

    private static String key(Purpose purpose, String secret) {
        return PREFIX + purpose.name().toLowerCase() + ":" + sha256(secret);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
