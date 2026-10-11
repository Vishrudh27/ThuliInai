package com.thuliinai.demo.hospital.controller;

import com.thuliinai.demo.hospital.entity.Hospital;
import com.thuliinai.demo.hospital.repository.HospitalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/hospitals")
public class HospitalAdminController {

    private final HospitalRepository hospitalRepository;
    private final S3Presigner r2Presigner;
    private final String bucket;

    public HospitalAdminController(
            HospitalRepository hospitalRepository,
            S3Presigner r2Presigner,
            @Value("${r2.bucket}") String bucket
    ) {
        this.hospitalRepository = hospitalRepository;
        this.r2Presigner = r2Presigner;
        this.bucket = bucket;
    }

    @GetMapping("/{publicId}/license-document")
    public ResponseEntity<?> licenseDocument(@PathVariable UUID publicId) {
        Hospital hospital = hospitalRepository.findByPublicId(publicId).orElse(null);
        if (hospital == null || hospital.getLicenseDocumentUrl() == null) {
            return ResponseEntity.notFound().build();
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(hospital.getLicenseDocumentUrl())
                        .build())
                .build();

        String url = r2Presigner.presignGetObject(presignRequest).url().toString();
        return ResponseEntity.ok(Map.of("url", url, "expiresInMinutes", 5));
    }

    @GetMapping
    public List<Hospital> list(@RequestParam(defaultValue = "PENDING") String status) {
        return hospitalRepository.findByStatus(status);
    }

    @PatchMapping("/{publicId}/approve")
    public ResponseEntity<?> approve(@PathVariable UUID publicId, @RequestParam Long verifiedBy) {
        Hospital hospital = hospitalRepository.findByPublicId(publicId).orElse(null);
        if (hospital == null) {
            return ResponseEntity.notFound().build();
        }

        hospital.setStatus("APPROVED");
        hospital.setVerifiedBy(verifiedBy);
        hospital.setVerifiedAt(Instant.now());
        hospital.setRejectionReason(null);

        return ResponseEntity.ok(hospitalRepository.save(hospital));
    }

    @PatchMapping("/{publicId}/reject")
    public ResponseEntity<?> reject(@PathVariable UUID publicId, @RequestParam Long verifiedBy, @RequestParam String reason) {
        Hospital hospital = hospitalRepository.findByPublicId(publicId).orElse(null);
        if (hospital == null) {
            return ResponseEntity.notFound().build();
        }

        hospital.setStatus("REJECTED");
        hospital.setVerifiedBy(verifiedBy);
        hospital.setVerifiedAt(Instant.now());
        hospital.setRejectionReason(reason);

        return ResponseEntity.ok(hospitalRepository.save(hospital));
    }
}
