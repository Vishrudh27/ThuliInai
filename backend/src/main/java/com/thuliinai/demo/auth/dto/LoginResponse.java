package com.thuliinai.demo.auth.dto;

import java.util.UUID;

/**
 * Payload returned to the client on a successful login.
 * Only the public-facing UUID is exposed — the internal Long PK is never serialised.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,        // Always "Bearer"
        long   expiresInMs,
        UUID   userId,           // user.publicId — non-sequential, safe to expose
        String email,
        String role,
        UUID   hospitalId        // null for ADMIN / DONOR accounts
) {}
