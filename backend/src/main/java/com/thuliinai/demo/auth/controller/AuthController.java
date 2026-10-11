package com.thuliinai.demo.auth.controller;

import com.thuliinai.demo.auth.dto.LoginRequest;
import com.thuliinai.demo.auth.dto.LoginResponse;
import com.thuliinai.demo.auth.service.AuthService;
import com.thuliinai.demo.auth.service.InvalidCredentialsException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Handles authentication endpoints.
 *
 * <p>Exposed routes (all publicly accessible — no token required):
 * <ul>
 *   <li>{@code POST /api/auth/login} — returns a JWT on valid credentials
 * </ul>
 *
 * <p>Hospital self-registration lives at
 * {@code POST /api/auth/register/hospital} (in {@link com.thuliinai.demo.hospital.controller.HospitalRegistrationController})
 * and is intentionally kept there to co-locate hospital business logic.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates a user and returns a signed JWT.
     *
     * <p>HTTP 200 with {@link LoginResponse} on success.
     * <p>HTTP 401 with {@code {"error": "..."}} on bad credentials / deleted account.
     * <p>HTTP 400 with validation errors when the JSON body is malformed.
     *
     * @param request validated login credentials
     * @return response entity containing the JWT and user metadata
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (InvalidCredentialsException e) {
            // Return 401 — intentionally generic message to prevent user enumeration.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
