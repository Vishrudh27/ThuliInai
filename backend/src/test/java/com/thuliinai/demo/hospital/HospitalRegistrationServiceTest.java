package com.thuliinai.demo.hospital;

import com.thuliinai.demo.common.storage.DocumentStorageService;
import com.thuliinai.demo.hospital.dto.HospitalRegistrationResponse;
import com.thuliinai.demo.hospital.entity.Hospital;
import com.thuliinai.demo.hospital.repository.HospitalRepository;
import com.thuliinai.demo.hospital.service.HospitalRegistrationService;
import com.thuliinai.demo.user.entity.User;
import com.thuliinai.demo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HospitalRegistrationServiceTest {

    @Mock
    private HospitalRepository hospitalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DocumentStorageService documentStorageService;

    private HospitalRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new HospitalRegistrationService(
                hospitalRepository,
                userRepository,
                passwordEncoder,
                documentStorageService
        );
    }

    private MockMultipartFile createSampleFile() {
        return new MockMultipartFile(
                "licenseDocument",
                "license.pdf",
                "application/pdf",
                "dummy pdf content".getBytes()
        );
    }

    @Test
    @DisplayName("Should successfully register a hospital and create its primary staff user")
    void shouldRegisterHospitalAndCreateStaffUserSuccessfully() throws IOException {
        MockMultipartFile file = createSampleFile();

        when(userRepository.existsByEmail("contact@cityhospital.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919876543210")).thenReturn(false);
        when(hospitalRepository.existsByLicenseNumber("LIC-KA-2026-001")).thenReturn(false);
        when(documentStorageService.storeDocument(eq("hospital-licenses"), any())).thenReturn("hospital-licenses/sample-uuid.pdf");
        when(passwordEncoder.encode("SecurePass123")).thenReturn("$2a$10$hashedPasswordValue");

        when(hospitalRepository.save(any(Hospital.class))).thenAnswer(invocation -> {
            Hospital h = invocation.getArgument(0);
            try {
                var idField = Hospital.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(h, 42L);
            } catch (Exception ignored) {}
            return h;
        });

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            try {
                var idField = User.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(u, 101L);
            } catch (Exception ignored) {}
            return u;
        });

        HospitalRegistrationResponse response = registrationService.registerHospital(
                "City Care Hospital",
                "123 Health Ave, Bangalore",
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                "+919876543210",
                "LIC-KA-2026-001",
                "Dr. Sharma",
                "Contact@CityHospital.com",
                "SecurePass123",
                file
        );

        assertThat(response).isNotNull();
        assertThat(response.hospitalId()).isNotNull();
        assertThat(response.hospitalName()).isEqualTo("City Care Hospital");
        assertThat(response.hospitalStatus()).isEqualTo("PENDING");
        assertThat(response.userId()).isNotNull();
        assertThat(response.email()).isEqualTo("contact@cityhospital.com");
        assertThat(response.role()).isEqualTo("HOSPITAL");

        // Verify password was hashed and user was linked to hospital
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertThat(capturedUser.getEmail()).isEqualTo("contact@cityhospital.com");
        assertThat(capturedUser.getPasswordHash()).isEqualTo("$2a$10$hashedPasswordValue");
        assertThat(capturedUser.getHospitalId()).isEqualTo(42L);
        assertThat(capturedUser.getRole()).isEqualTo("HOSPITAL");
        assertThat(capturedUser.getConsentAt()).isNotNull();

        // Verify document was uploaded
        verify(documentStorageService).storeDocument(eq("hospital-licenses"), eq(file));
    }

    @Test
    @DisplayName("Should reject registration if email is already registered without uploading document")
    void shouldRejectIfEmailAlreadyRegistered() {
        MockMultipartFile file = createSampleFile();
        when(userRepository.existsByEmail("existing@hospital.com")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.registerHospital(
                "City Care Hospital",
                "123 Health Ave, Bangalore",
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                "+919876543210",
                "LIC-KA-2026-001",
                "Dr. Sharma",
                "existing@hospital.com",
                "SecurePass123",
                file
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verifyNoInteractions(documentStorageService);
        verifyNoInteractions(hospitalRepository);
    }

    @Test
    @DisplayName("Should reject registration if license number is already registered")
    void shouldRejectIfLicenseNumberAlreadyRegistered() {
        MockMultipartFile file = createSampleFile();
        when(userRepository.existsByEmail("new@hospital.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919876543210")).thenReturn(false);
        when(hospitalRepository.existsByLicenseNumber("LIC-DUPLICATE")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.registerHospital(
                "City Care Hospital",
                "123 Health Ave, Bangalore",
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                "+919876543210",
                "LIC-DUPLICATE",
                "Dr. Sharma",
                "new@hospital.com",
                "SecurePass123",
                file
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("license number 'LIC-DUPLICATE' is already registered");

        verifyNoInteractions(documentStorageService);
    }

    @Test
    @DisplayName("Should reject registration if password is less than 8 characters")
    void shouldRejectShortPassword() {
        MockMultipartFile file = createSampleFile();

        assertThatThrownBy(() -> registrationService.registerHospital(
                "City Care Hospital",
                "123 Health Ave, Bangalore",
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                "+919876543210",
                "LIC-001",
                "Dr. Sharma",
                "doctor@hospital.com",
                "short",
                file
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password must be at least 8 characters long");

        verifyNoInteractions(documentStorageService);
    }

    @Test
    @DisplayName("Should reject registration if file is empty")
    void shouldRejectEmptyLicenseDocument() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "licenseDocument", "empty.pdf", "application/pdf", new byte[0]
        );

        assertThatThrownBy(() -> registrationService.registerHospital(
                "City Care Hospital",
                "123 Health Ave, Bangalore",
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                "+919876543210",
                "LIC-001",
                "Dr. Sharma",
                "doctor@hospital.com",
                "SecurePass123",
                emptyFile
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("License document file is required");

        verifyNoInteractions(documentStorageService);
    }

    @Test
    @DisplayName("Should clean up uploaded document if database persistence fails")
    void shouldCleanupUploadedDocumentIfDatabaseSaveFails() throws IOException {
        MockMultipartFile file = createSampleFile();

        when(userRepository.existsByEmail("fail@hospital.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919876543210")).thenReturn(false);
        when(hospitalRepository.existsByLicenseNumber("LIC-FAIL-01")).thenReturn(false);
        when(documentStorageService.storeDocument(eq("hospital-licenses"), eq(file)))
                .thenReturn("hospital-licenses/fail-uuid.pdf");

        // Hospital save fails
        when(hospitalRepository.save(any(Hospital.class))).thenThrow(new RuntimeException("DB Connection Dropped"));

        assertThatThrownBy(() -> registrationService.registerHospital(
                "Fail Hospital",
                "123 Health Ave, Bangalore",
                new BigDecimal("12.9716"),
                new BigDecimal("77.5946"),
                "+919876543210",
                "LIC-FAIL-01",
                "Dr. Sharma",
                "fail@hospital.com",
                "SecurePass123",
                file
        ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB Connection Dropped");

        // Verify cleanup was invoked to avoid orphaned storage objects
        verify(documentStorageService).deleteDocument("hospital-licenses/fail-uuid.pdf");
    }
}
