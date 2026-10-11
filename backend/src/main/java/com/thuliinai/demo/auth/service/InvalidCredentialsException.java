package com.thuliinai.demo.auth.service;

/**
 * Thrown by {@link AuthService} when authentication fails for any reason.
 *
 * <p>A single, opaque exception is intentional: the HTTP layer always maps this to
 * 401 Unauthorized with a generic message — callers cannot distinguish "email not found"
 * from "wrong password", preventing user-enumeration.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
