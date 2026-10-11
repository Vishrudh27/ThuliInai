package com.thuliinai.demo.hospital.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record HospitalRegistrationResponse(
        UUID hospitalId,
        String hospitalName,
        String hospitalStatus,
        String address,
        BigDecimal lat,
        BigDecimal lng,
        String phone,
        String licenseNumber,
        String contactPersonName,
        UUID userId,
        String email,
        String role,
        String message
) {}
