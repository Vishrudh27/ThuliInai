package com.thuliinai.demo.auth.filter;

import com.thuliinai.demo.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Servlet filter that runs once per request to extract and validate the JWT
 * from the {@code Authorization: Bearer <token>} header.
 *
 * <p>On success, it populates the {@link SecurityContextHolder} so that
 * Spring Security's downstream authorization rules can evaluate the user's role.
 *
 * <p>On failure (missing / invalid / expired token), the filter simply does NOT
 * populate the context — the request continues unauthenticated and will be
 * rejected at the authorization layer if the route requires authentication.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_PREFIX   = "ROLE_";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.validateAndExtract(token);

                String email  = claims.getSubject();
                String role   = claims.get("role", String.class);
                UUID   userId = UUID.fromString(claims.get("userId", String.class));

                // Build a minimal principal object — no DB lookup on every request.
                // The token itself is the source of truth for role; for sensitive operations
                // the service layer can still verify against the DB if needed.
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                email,        // principal  (email string)
                                userId,       // credentials (public UUID — convenient for services)
                                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role))
                        );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException e) {
                // Expired / tampered / malformed — log and continue without authentication.
                log.debug("JWT validation failed for request {}: {}", request.getRequestURI(), e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw token string from the {@code Authorization} header, or {@code null}
     * if the header is absent or does not start with "Bearer ".
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
