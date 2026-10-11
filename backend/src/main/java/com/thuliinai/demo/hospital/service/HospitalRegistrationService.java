package com.thuliinai.demo.hospital.service;

import com.thuliinai.demo.common.storage.DocumentStorageService;
import com.thuliinai.demo.hospital.dto.HospitalRegistrationResponse;
import com.thuliinai.demo.hospital.entity.Hospital;
import com.thuliinai.demo.hospital.repository.HospitalRepository;
import com.thuliinai.demo.user.entity.User;
import com.thuliinai.demo.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class HospitalRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(HospitalRegistrationService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final HospitalRepository hospitalRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DocumentStorageService documentStorageService;

    public HospitalRegistrationService(
            HospitalRepository hospitalRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DocumentStorageService documentStorageService
    ) {
        this.hospitalRepository = hospitalRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.documentStorageService = documentStorageService;
    }

    @Transactional(rollbackFor = Exception.class)
    public HospitalRegistrationResponse registerHospital(
            String name,
            String address,
            BigDecimal lat,
            BigDecimal lng,
            String phone,
            String licenseNumber,
            String contactPersonName,
            String email,
            String password,
            MultipartFile licenseDocument
    ) throws IOException {
        validateInputs(name, address, lat, lng, phone, licenseNumber, contactPersonName, email, password, licenseDocument);

        String normalizedEmail = email.trim().toLowerCase();
        String normalizedPhone = phone.trim();
        String normalizedLicenseNumber = licenseNumber.trim();

        // 1. Uniqueness checks before performing any external side-effects
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with email '" + normalizedEmail + "' already exists.");
        }
        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new IllegalArgumentException("An account with phone number '" + normalizedPhone + "' already exists.");
        }
        if (hospitalRepository.existsByLicenseNumber(normalizedLicenseNumber)) {
            throw new IllegalArgumentException("A hospital with license number '" + normalizedLicenseNumber + "' is already registered.");
        }

        // 2. Upload license document via storage service
        String objectKey = documentStorageService.storeDocument("hospital-licenses", licenseDocument);

        try {
            // 3. Persist Hospital entity
            Hospital hospital = new Hospital();
            hospital.setName(name.trim());
            hospital.setAddress(address.trim());
            hospital.setLat(lat);
            hospital.setLng(lng);
            hospital.setPhone(normalizedPhone);
            hospital.setLicenseNumber(normalizedLicenseNumber);
            hospital.setContactPersonName(contactPersonName.trim());
            hospital.setLicenseDocumentUrl(objectKey);
            hospital.setStatus("PENDING");

            Hospital savedHospital = hospitalRepository.save(hospital);

            // 4. Persist primary staff User entity linked to Hospital
            User user = new User();
            user.setEmail(normalizedEmail);
            user.setPhone(normalizedPhone);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole("HOSPITAL");
            user.setHospitalId(savedHospital.getId());
            user.setConsentAt(Instant.now());

            User savedUser = userRepository.save(user);

            log.info("Hospital '{}' (ID: {}) registered successfully with primary staff user (ID: {}). Pending admin verification.",
                    savedHospital.getName(), savedHospital.getId(), savedUser.getId());

            return new HospitalRegistrationResponse(
                    savedHospital.getPublicId(),
                    savedHospital.getName(),
                    savedHospital.getStatus(),
                    savedHospital.getAddress(),
                    savedHospital.getLat(),
                    savedHospital.getLng(),
                    savedHospital.getPhone(),
                    savedHospital.getLicenseNumber(),
                    savedHospital.getContactPersonName(),
                    savedUser.getPublicId(),
                    savedUser.getEmail(),
                    savedUser.getRole(),
                    "Hospital registration submitted successfully and is pending administrative approval."
            );
        } catch (Exception ex) {
            // Clean up uploaded document if database persistence fails
            log.error("Failed to persist hospital or user. Cleaning up uploaded document: {}", objectKey, ex);
            documentStorageService.deleteDocument(objectKey);
            throw ex;
        }
    }

    private void validateInputs(
            String name,
            String address,
            BigDecimal lat,
            BigDecimal lng,
            String phone,
            String licenseNumber,
            String contactPersonName,
            String email,
            String password,
            MultipartFile licenseDocument
    ) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Hospital name is required.");
        }
        if (!StringUtils.hasText(address)) {
            throw new IllegalArgumentException("Hospital address is required.");
        }
        if (lat == null || lat.compareTo(new BigDecimal("-90")) < 0 || lat.compareTo(new BigDecimal("90")) > 0) {
            throw new IllegalArgumentException("Valid latitude between -90 and 90 is required.");
        }
        if (lng == null || lng.compareTo(new BigDecimal("-180")) < 0 || lng.compareTo(new BigDecimal("180")) > 0) {
            throw new IllegalArgumentException("Valid longitude between -180 and 180 is required.");
        }
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("Phone number is required.");
        }
        if (!StringUtils.hasText(licenseNumber)) {
            throw new IllegalArgumentException("License number is required.");
        }
        if (!StringUtils.hasText(contactPersonName)) {
            throw new IllegalArgumentException("Contact person name is required.");
        }
        if (!StringUtils.hasText(email) || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("A valid email address is required.");
        }
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }
        if (licenseDocument == null || licenseDocument.isEmpty()) {
            throw new IllegalArgumentException("License document file is required.");
        }
    }
}
