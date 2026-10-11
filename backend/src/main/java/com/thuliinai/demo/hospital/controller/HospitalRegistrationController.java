package com.thuliinai.demo.hospital.controller;

import com.thuliinai.demo.hospital.dto.HospitalRegistrationResponse;
import com.thuliinai.demo.hospital.service.HospitalRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

@RestController
public class HospitalRegistrationController {

    private final HospitalRegistrationService registrationService;

    public HospitalRegistrationController(HospitalRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping(
            value = {"/hospitals/register", "/api/auth/register/hospital"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> register(
            @RequestParam("name") String name,
            @RequestParam("address") String address,
            @RequestParam("lat") BigDecimal lat,
            @RequestParam("lng") BigDecimal lng,
            @RequestParam("phone") String phone,
            @RequestParam("licenseNumber") String licenseNumber,
            @RequestParam("contactPersonName") String contactPersonName,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestPart("licenseDocument") MultipartFile licenseDocument
    ) {
        try {
            HospitalRegistrationResponse response = registrationService.registerHospital(
                    name, address, lat, lng, phone, licenseNumber, contactPersonName, email, password, licenseDocument
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process and store license document: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An error occurred during registration: " + e.getMessage()));
        }
    }
}
