package com.thuliinai.demo.hospital;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/hospitals")
public class HospitalRegistrationController {

    private final HospitalRepository hospitalRepository;
    private final S3Client r2Client;
    private final String bucket;

    public HospitalRegistrationController(
            HospitalRepository hospitalRepository,
            S3Client r2Client,
            @Value("${r2.bucket}") String bucket
    ) {
        this.hospitalRepository = hospitalRepository;
        this.r2Client = r2Client;
        this.bucket = bucket;
    }

    @PostMapping(value = "/register", consumes = "multipart/form-data")
    public ResponseEntity<?> register(
            @RequestParam String name,
            @RequestParam String address,
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            @RequestParam String phone,
            @RequestParam String licenseNumber,
            @RequestParam String contactPersonName,
            @RequestPart MultipartFile licenseDocument
    ) throws IOException {
        if (licenseDocument.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "licenseDocument is required"));
        }

        String objectKey = storeDocument(licenseDocument);

        Hospital hospital = new Hospital();
        hospital.setName(name);
        hospital.setAddress(address);
        hospital.setLat(lat);
        hospital.setLng(lng);
        hospital.setPhone(phone);
        hospital.setLicenseNumber(licenseNumber);
        hospital.setContactPersonName(contactPersonName);
        hospital.setLicenseDocumentUrl(objectKey);
        // status defaults to PENDING (Hospital entity default)

        Hospital saved = hospitalRepository.save(hospital);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    private String storeDocument(MultipartFile file) throws IOException {
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            extension = original.substring(dot);
        }

        String objectKey = "hospital-licenses/" + UUID.randomUUID() + extension;

        r2Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );

        // ponytail: bucket is private, objectKey stored, not a public URL.
        // Admin viewing needs a presigned GET URL generated on demand — not built yet.
        return objectKey;
    }
}
