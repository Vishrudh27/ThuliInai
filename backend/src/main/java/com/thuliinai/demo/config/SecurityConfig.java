package com.thuliinai.demo.config;

import com.thuliinai.demo.auth.filter.JwtAuthenticationFilter;
import com.thuliinai.demo.auth.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration.
 *
 * <p>Architecture:
 * <ul>
 *   <li>Stateless JWT — no HTTP session is ever created.
 *   <li>CSRF disabled — safe because we are a REST API (no browser-form submission).
 *   <li>HTTP Basic disabled — replaced entirely by the JWT filter.
 *   <li>{@link JwtAuthenticationFilter} runs before Spring's own auth filter to populate
 *       the {@link org.springframework.security.core.context.SecurityContext}.
 *   <li>{@code @EnableMethodSecurity} enables {@code @PreAuthorize("hasRole('ADMIN')")} etc.
 *       on service/controller methods for fine-grained access control.
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // --- Session & CSRF ---
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)

            // --- Disable HTTP Basic (we use JWT exclusively) ---
            .httpBasic(AbstractHttpConfigurer::disable)

            // --- Authorization rules ---
            .authorizeHttpRequests(auth -> auth
                // Health check
                .requestMatchers("/health").permitAll()

                // Public auth endpoints (login + hospital self-registration)
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register/hospital").permitAll()
                .requestMatchers(HttpMethod.POST, "/hospitals/register").permitAll()   // legacy alias

                // Admin management routes — requires valid JWT with role ADMIN
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // Everything else requires a valid JWT (role checked at service layer via @PreAuthorize)
                .anyRequest().authenticated()
            )

            // --- JWT filter before Spring's UsernamePasswordAuthenticationFilter ---
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
