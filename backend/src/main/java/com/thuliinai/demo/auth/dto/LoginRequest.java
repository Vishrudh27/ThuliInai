package com.thuliinai.demo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload sent by the client on POST /api/auth/login.
 * Using a record so the DTO is immutable by construction — no accidental mutation possible.
 */
public record LoginRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "A valid email address is required.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, message = "Password must be at least 8 characters.")
        String password
) {}
