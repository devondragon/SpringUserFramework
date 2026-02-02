package com.digitalsanctuary.spring.user.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import com.digitalsanctuary.spring.user.audit.AuditEventDTO;
import com.digitalsanctuary.spring.user.audit.AuditLogQueryService;
import com.digitalsanctuary.spring.user.dto.GdprExportDTO;
import com.digitalsanctuary.spring.user.event.UserDataExportedEvent;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

@ServiceTest
@DisplayName("GdprExportService Tests")
class GdprExportServiceTest {

    @Mock
    private GdprConfig gdprConfig;

    @Mock
    private AuditLogQueryService auditLogQueryService;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GdprExportService gdprExportService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = UserTestDataBuilder.aVerifiedUser()
                .withId(1L)
                .withEmail("test@example.com")
                .withFirstName("Test")
                .withLastName("User")
                .build();
    }

    @Nested
    @DisplayName("exportUserData")
    class ExportUserData {

        @Test
        @DisplayName("throws exception when user is null")
        void throwsException_whenUserIsNull() {
            assertThatThrownBy(() -> gdprExportService.exportUserData(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User cannot be null");
        }

        @Test
        @DisplayName("exports basic user data")
        void exportsBasicUserData() {
            // Given
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            when(auditLogQueryService.findByUser(testUser)).thenReturn(new ArrayList<>());
            when(auditLogQueryService.findByUserAndAction(any(), any())).thenReturn(new ArrayList<>());

            // When
            GdprExportDTO export = gdprExportService.exportUserData(testUser);

            // Then
            assertThat(export).isNotNull();
            assertThat(export.getUserData()).isNotNull();
            assertThat(export.getUserData().getId()).isEqualTo(1L);
            assertThat(export.getUserData().getEmail()).isEqualTo("test@example.com");
            assertThat(export.getUserData().getFirstName()).isEqualTo("Test");
            assertThat(export.getUserData().getLastName()).isEqualTo("User");
            assertThat(export.getUserData().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("includes export metadata")
        void includesExportMetadata() {
            // Given
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            when(auditLogQueryService.findByUser(testUser)).thenReturn(new ArrayList<>());
            when(auditLogQueryService.findByUserAndAction(any(), any())).thenReturn(new ArrayList<>());

            // When
            GdprExportDTO export = gdprExportService.exportUserData(testUser);

            // Then
            assertThat(export.getMetadata()).isNotNull();
            assertThat(export.getMetadata().getExportedAt()).isNotNull();
            assertThat(export.getMetadata().getFormatVersion()).isEqualTo("1.0");
            assertThat(export.getMetadata().getExportedBy()).isEqualTo("Spring User Framework");
        }

        @Test
        @DisplayName("exports audit history")
        void exportsAuditHistory() {
            // Given
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            List<AuditEventDTO> auditEvents = List.of(
                    AuditEventDTO.builder()
                            .timestamp(Instant.now())
                            .action("Login")
                            .actionStatus("Success")
                            .userEmail("test@example.com")
                            .build()
            );
            when(auditLogQueryService.findByUser(testUser)).thenReturn(auditEvents);
            when(auditLogQueryService.findByUserAndAction(any(), any())).thenReturn(new ArrayList<>());

            // When
            GdprExportDTO export = gdprExportService.exportUserData(testUser);

            // Then
            assertThat(export.getAuditHistory()).hasSize(1);
            assertThat(export.getAuditHistory().get(0).getAction()).isEqualTo("Login");
        }

        @Test
        @DisplayName("exports token metadata without exposing tokens")
        void exportsTokenMetadata_withoutExposingTokens() {
            // Given
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            VerificationToken verificationToken = new VerificationToken("secret-token", testUser);
            when(verificationTokenRepository.findByUser(testUser)).thenReturn(verificationToken);
            when(passwordResetTokenRepository.findByUser(testUser)).thenReturn(null);
            when(auditLogQueryService.findByUser(testUser)).thenReturn(new ArrayList<>());
            when(auditLogQueryService.findByUserAndAction(any(), any())).thenReturn(new ArrayList<>());

            // When
            GdprExportDTO export = gdprExportService.exportUserData(testUser);

            // Then
            assertThat(export.getTokens()).isNotNull();
            assertThat(export.getTokens().isHasVerificationToken()).isTrue();
            assertThat(export.getTokens().getVerificationTokenExpiry()).isNotNull();
            assertThat(export.getTokens().isHasPasswordResetToken()).isFalse();
        }

        @Test
        @DisplayName("publishes UserDataExportedEvent")
        void publishesUserDataExportedEvent() {
            // Given
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            when(auditLogQueryService.findByUser(testUser)).thenReturn(new ArrayList<>());
            when(auditLogQueryService.findByUserAndAction(any(), any())).thenReturn(new ArrayList<>());

            // When
            gdprExportService.exportUserData(testUser);

            // Then
            ArgumentCaptor<UserDataExportedEvent> eventCaptor = ArgumentCaptor.forClass(UserDataExportedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getUser()).isEqualTo(testUser);
        }
    }

    @Nested
    @DisplayName("GdprDataContributor integration")
    class DataContributorIntegration {

        @Test
        @DisplayName("aggregates data from contributors")
        void aggregatesDataFromContributors() {
            // Given - contributors are injected as a List
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            when(auditLogQueryService.findByUser(testUser)).thenReturn(new ArrayList<>());
            when(auditLogQueryService.findByUserAndAction(any(), any())).thenReturn(new ArrayList<>());

            // When
            GdprExportDTO export = gdprExportService.exportUserData(testUser);

            // Then
            assertThat(export.getAdditionalData()).isNotNull();
        }
    }

}
