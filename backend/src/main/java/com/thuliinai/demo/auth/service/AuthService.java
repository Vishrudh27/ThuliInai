package com.thuliinai.demo.auth.service;

import com.thuliinai.demo.auth.dto.LoginRequest;
import com.thuliinai.demo.auth.dto.LoginResponse;
import com.thuliinai.demo.hospital.repository.HospitalRepository;
import com.thuliinai.demo.user.entity.User;
import com.thuliinai.demo.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Business logic for authentication.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Locate the user by email.
 *   <li>Verify the supplied password against the stored BCrypt hash.
 *   <li>Reject soft-deleted accounts.
 *   <li>Issue a JWT via {@link JwtService}.
 *   <li>Build and return the {@link LoginResponse}.
 * </ol>
 *
 * <p><b>Timing-safe design:</b> We always call {@code passwordEncoder.matches()} even when no
 * user is found (using a dummy hash) to prevent timing-based user-enumeration attacks.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * A pre-hashed dummy value used only to prevent user-enumeration via timing analysis.
     * It is never persisted or compared against anything meaningful.
     */
    private static final String DUMMY_HASH =
            "$2a$10$dummyhashXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";

    private final UserRepository userRepository;
    private final HospitalRepository hospitalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            HospitalRepository hospitalRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.hospitalRepository = hospitalRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Authenticates the user and returns a JWT-based login response.
     *
     * @param request the validated {@link LoginRequest} from the controller
     * @return a fully populated {@link LoginResponse}
     * @throws InvalidCredentialsException for any authentication failure (intentionally opaque)
     */
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        // Always fetch — use dummy hash if user does not exist to maintain constant time.
        User user = userRepository.findByEmail(email).orElse(null);
        String hashToCheck = (user != null) ? user.getPasswordHash() : DUMMY_HASH;

        // Password check (runs regardless — prevents timing attack)
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null || !passwordMatches) {
            log.warn("Failed login attempt for email '{}'.", email);
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        // Reject soft-deleted accounts
        if (user.getDeletedAt() != null) {
            log.warn("Login attempt on deleted account: email='{}'.", email);
            throw new InvalidCredentialsException("Account is no longer active.");
        }

        // Hospital-role users may only log in once their hospital has been APPROVED.
        // PENDING → admin hasn't reviewed yet.  REJECTED → permanently blocked.
        if ("HOSPITAL".equals(user.getRole()) && user.getHospitalId() != null) {
            String hospitalStatus = hospitalRepository.findById(user.getHospitalId())
                    .map(h -> h.getStatus())
                    .orElse("UNKNOWN");

            if ("PENDING".equals(hospitalStatus)) {
                log.info("Login blocked — hospital pending approval for user '{}'.", email);
                throw new InvalidCredentialsException(
                        "Your hospital registration is still under review. You will be notified once approved.");
            }
            if ("REJECTED".equals(hospitalStatus)) {
                log.info("Login blocked — hospital rejected for user '{}'.", email);
                throw new InvalidCredentialsException(
                        "Your hospital registration was not approved. Please contact support.");
            }
        }

        UUID hospitalPublicId = resolveHospitalPublicId(user);

        String token = jwtService.generateToken(user.getEmail(), user.getPublicId(), user.getRole());

        log.info("User '{}' (role={}) logged in successfully.", email, user.getRole());

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpiryMs(),
                user.getPublicId(),
                user.getEmail(),
                user.getRole(),
                hospitalPublicId
        );
    }

    /**
     * Looks up the hospital's public UUID for HOSPITAL-role users.
     * Returns {@code null} for ADMIN and DONOR accounts.
     */
    private UUID resolveHospitalPublicId(User user) {
        if ("HOSPITAL".equals(user.getRole()) && user.getHospitalId() != null) {
            return hospitalRepository.findById(user.getHospitalId())
                    .map(h -> h.getPublicId())
                    .orElse(null);
        }
        return null;
    }
}
