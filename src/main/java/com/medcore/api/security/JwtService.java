package com.medcore.api.security;

import com.medcore.api.config.MedcoreProperties;
import com.medcore.api.user.Role;
import com.medcore.api.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Issues and validates short-lived HMAC-signed access tokens. */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(MedcoreProperties properties) {
        MedcoreProperties.Jwt jwt = properties.security().jwt();
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwt.secret()));
        this.issuer = jwt.issuer();
        this.accessTokenTtl = jwt.accessTokenTtl();
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public Optional<UserPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(new UserPrincipal(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    claims.getId(),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
