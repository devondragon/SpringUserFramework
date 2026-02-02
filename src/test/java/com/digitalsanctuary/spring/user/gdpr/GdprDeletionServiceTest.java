package com.digitalsanctuary.spring.user.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import com.digitalsanctuary.spring.user.dto.GdprExportDTO;
import com.digitalsanctuary.spring.user.event.UserDeletedEvent;
import com.digitalsanctuary.spring.user.event.UserPreDeleteEvent;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

@ServiceTest
@DisplayName("GdprDeletionService Tests")
class GdprDeletionServiceTest {

    @Mock
    private GdprConfig gdprConfig;

    @Mock
    private GdprExportService gdprExportService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GdprDeletionService gdprDeletionService;

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
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("throws exception when user is null")
        void throwsException_whenUserIsNull() {
            assertThatThrownBy(() -> gdprDeletionService.deleteUser(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User cannot be null");
        }

        @Test
        @DisplayName("successfully deletes user")
        void successfullyDeletesUser() {
            // Given
            when(gdprConfig.isExportBeforeDeletion()).thenReturn(false);

            // When
            GdprDeletionService.DeletionResult result = gdprDeletionService.deleteUser(testUser);

            // Then
            assertThat(result.isSuccess()).isTrue();
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("publishes UserPreDeleteEvent before deletion")
        void publishesUserPreDeleteEvent_beforeDeletion() {
            // Given
            when(gdprConfig.isExportBeforeDeletion()).thenReturn(false);

            // When
            gdprDeletionService.deleteUser(testUser);

            // Then
            ArgumentCaptor<UserPreDeleteEvent> eventCaptor = ArgumentCaptor.forClass(UserPreDeleteEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getUser()).isEqualTo(testUser);
        }

        @Test
        @DisplayName("publishes UserDeletedEvent after deletion")
        void publishesUserDeletedEvent_afterDeletion() {
            // Given
            when(gdprConfig.isExportBeforeDeletion()).thenReturn(false);

            // When
            gdprDeletionService.deleteUser(testUser);

            // Then
            ArgumentCaptor<UserDeletedEvent> eventCaptor = ArgumentCaptor.forClass(UserDeletedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getUserId()).isEqualTo(1L);
            assertThat(eventCaptor.getValue().getUserEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("deletes verification token")
        void deletesVerificationToken() {
            // Given
            VerificationToken token = new VerificationToken("token", testUser);
            when(gdprConfig.isExportBeforeDeletion()).thenReturn(false);
            when(verificationTokenRepository.findByUser(testUser)).thenReturn(token);

            // When
            gdprDeletionService.deleteUser(testUser);

            // Then
            verify(verificationTokenRepository).delete(token);
        }

        @Test
        @DisplayName("deletes password reset token")
        void deletesPasswordResetToken() {
            // Given
            PasswordResetToken token = new PasswordResetToken("token", testUser);
            when(gdprConfig.isExportBeforeDeletion()).thenReturn(false);
            when(passwordResetTokenRepository.findByUser(testUser)).thenReturn(token);

            // When
            gdprDeletionService.deleteUser(testUser);

            // Then
            verify(passwordResetTokenRepository).delete(token);
        }
    }

    @Nested
    @DisplayName("deleteUser with export")
    class DeleteUserWithExport {

        @Test
        @DisplayName("exports data before deletion when configured")
        void exportsData_beforeDeletion_whenConfigured() {
            // Given
            GdprExportDTO mockExport = GdprExportDTO.builder().build();
            when(gdprConfig.isExportBeforeDeletion()).thenReturn(true);
            when(gdprExportService.exportUserData(testUser)).thenReturn(mockExport);

            // When
            GdprDeletionService.DeletionResult result = gdprDeletionService.deleteUser(testUser);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getExportedData()).isNotNull();
            verify(gdprExportService).exportUserData(testUser);
        }

        @Test
        @DisplayName("does not export when disabled")
        void doesNotExport_whenDisabled() {
            // When - pass explicit false for exportBeforeDeletion
            GdprDeletionService.DeletionResult result = gdprDeletionService.deleteUser(testUser, false);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getExportedData()).isNull();
            verify(gdprExportService, never()).exportUserData(any());
        }

        @Test
        @DisplayName("includes exported data in result")
        void includesExportedData_inResult() {
            // Given
            GdprExportDTO mockExport = GdprExportDTO.builder()
                    .metadata(GdprExportDTO.ExportMetadata.builder()
                            .formatVersion("1.0")
                            .build())
                    .build();
            when(gdprExportService.exportUserData(testUser)).thenReturn(mockExport);

            // When
            GdprDeletionService.DeletionResult result = gdprDeletionService.deleteUser(testUser, true);

            // Then
            assertThat(result.getExportedData()).isEqualTo(mockExport);
            assertThat(result.getMessage()).contains("exported");
        }
    }

    @Nested
    @DisplayName("DeletionResult")
    class DeletionResultTests {

        @Test
        @DisplayName("success creates successful result")
        void success_createsSuccessfulResult() {
            GdprDeletionService.DeletionResult result = GdprDeletionService.DeletionResult.success(null);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getExportedData()).isNull();
        }

        @Test
        @DisplayName("successWithExport includes export data")
        void successWithExport_includesExportData() {
            GdprExportDTO export = GdprExportDTO.builder().build();
            GdprDeletionService.DeletionResult result = GdprDeletionService.DeletionResult.successWithExport(export);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getExportedData()).isEqualTo(export);
        }

        @Test
        @DisplayName("failure creates failed result")
        void failure_createsFailedResult() {
            GdprDeletionService.DeletionResult result = GdprDeletionService.DeletionResult.failure("Error message");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Error message");
        }
    }

}
