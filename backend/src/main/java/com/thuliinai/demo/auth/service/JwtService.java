package com.thuliinai.demo.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Sole owner of JWT creation and verification logic.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>HMAC-SHA256 — fast, symmetric, appropriate for a single-server deployment.
 *   <li>Secret is Base64-decoded at startup so the env-var is safe to paste (no special chars).
 *   <li>Only the non-sensitive public UUID is embedded in the token payload, never the Long PK.
 *   <li>Claims are minimal: sub (email), userId (UUID), role.  Richer data fetched from DB on
 *       each authenticated request instead of ballooning the token.
 * </ul>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long expiryMs;

    public JwtService(
            @Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.expiry-ms}") long expiryMs
    ) {
        // Keys.hmacShaKeyFor validates that the key is long enough for HMAC-SHA256 (≥32 bytes).
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
        this.expiryMs   = expiryMs;
    }

    // ------------------------------------------------------------------
    // Token generation
    // ------------------------------------------------------------------

    /**
     * Creates a signed JWT for the authenticated user.
     *
     * @param email    the user's email (used as JWT subject)
     * @param userId   the user's non-sequential public UUID
     * @param role     the user's role (ADMIN | HOSPITAL | DONOR)
     * @return the compact JWT string
     */
    public String generateToken(String email, UUID userId, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId.toString())
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMs))
                .signWith(signingKey)
                .compact();
    }

    // ------------------------------------------------------------------
    // Token verification & extraction
    // ------------------------------------------------------------------

    /**
     * Parses and validates the JWT signature + expiry.
     *
     * @param token the raw JWT string (without "Bearer " prefix)
     * @return parsed {@link Claims} on success
     * @throws JwtException if the token is invalid or expired
     */
    public Claims validateAndExtract(String token) {
        // Throws JwtException (incl. ExpiredJwtException) on any failure.
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpiryMs() {
        return expiryMs;
    }
}
