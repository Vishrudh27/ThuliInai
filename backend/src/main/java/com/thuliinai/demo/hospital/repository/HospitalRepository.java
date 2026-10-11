package com.thuliinai.demo.hospital.repository;

import com.thuliinai.demo.hospital.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, Long> {
    List<Hospital> findByStatus(String status);
    boolean existsByLicenseNumber(String licenseNumber);
    Optional<Hospital> findByLicenseNumber(String licenseNumber);
    Optional<Hospital> findByPublicId(UUID publicId);
}
