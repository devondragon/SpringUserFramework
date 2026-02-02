package com.digitalsanctuary.spring.user.gdpr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.audit.AuditLogQueryService;
import com.digitalsanctuary.spring.user.event.ConsentChangedEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@ServiceTest
@DisplayName("ConsentAuditService Tests")
class ConsentAuditServiceTest {

    @Mock
    private GdprConfig gdprConfig;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AuditLogQueryService auditLogQueryService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    @InjectMocks
    private ConsentAuditService consentAuditService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = UserTestDataBuilder.aVerifiedUser()
                .withId(1L)
                .withEmail("test@example.com")
                .build();
    }

    private void setupMocksForConsentRecording() {
        when(gdprConfig.isConsentTracking()).thenReturn(true);
        when(request.getSession()).thenReturn(session);
        when(session.getId()).thenReturn("test-session-id");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Nested
    @DisplayName("recordConsentGranted")
    class RecordConsentGranted {

        @Test
        @DisplayName("throws exception when user is null")
        void throwsException_whenUserIsNull() {
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            assertThatThrownBy(() -> consentAuditService.recordConsentGranted(
                    null, ConsentType.PRIVACY_POLICY, null, "web_form", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User cannot be null");
        }

        @Test
        @DisplayName("throws exception when consent type is null")
        void throwsException_whenConsentTypeIsNull() {
            when(gdprConfig.isConsentTracking()).thenReturn(true);
            assertThatThrownBy(() -> consentAuditService.recordConsentGranted(
                    testUser, null, null, "web_form", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Consent type cannot be null");
        }

        @Test
        @DisplayName("returns null when consent tracking is disabled")
        void returnsNull_whenConsentTrackingDisabled() {
            // Given
            when(gdprConfig.isConsentTracking()).thenReturn(false);

            // When
            ConsentRecord result = consentAuditService.recordConsentGranted(
                    testUser, ConsentType.PRIVACY_POLICY, null, "web_form", request);

            // Then
            assertThat(result).isNull();
            verify(eventPublisher, never()).publishEvent(any(AuditEvent.class));
        }

        @Test
        @DisplayName("creates consent record with correct data")
        void createsConsentRecord_withCorrectData() {
            // Given
            setupMocksForConsentRecording();

            // When
            ConsentRecord result = consentAuditService.recordConsentGranted(
                    testUser, ConsentType.PRIVACY_POLICY, "v1.0", "web_form", request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getType()).isEqualTo(ConsentType.PRIVACY_POLICY);
            assertThat(result.getPolicyVersion()).isEqualTo("v1.0");
            assertThat(result.getMethod()).isEqualTo("web_form");
            assertThat(result.getGrantedAt()).isNotNull();
            assertThat(result.getWithdrawnAt()).isNull();
            assertThat(result.isActive()).isTrue();
        }

        @Test
        @DisplayName("publishes audit event")
        void publishesAuditEvent() {
            // Given
            setupMocksForConsentRecording();

            // When
            consentAuditService.recordConsentGranted(
                    testUser, ConsentType.PRIVACY_POLICY, "v1.0", "web_form", request);

            // Then
            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            AuditEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getAction()).isEqualTo("CONSENT_GRANTED");
            assertThat(capturedEvent.getActionStatus()).isEqualTo("Success");
            assertThat(capturedEvent.getUser()).isEqualTo(testUser);
        }

        @Test
        @DisplayName("publishes ConsentChangedEvent")
        void publishesConsentChangedEvent() {
            // Given
            setupMocksForConsentRecording();

            // When
            consentAuditService.recordConsentGranted(
                    testUser, ConsentType.MARKETING_EMAILS, null, "api", request);

            // Then
            ArgumentCaptor<ConsentChangedEvent> eventCaptor = ArgumentCaptor.forClass(ConsentChangedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            ConsentChangedEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getUser()).isEqualTo(testUser);
            assertThat(capturedEvent.getConsentType()).isEqualTo(ConsentType.MARKETING_EMAILS);
            assertThat(capturedEvent.isGranted()).isTrue();
        }

        @Test
        @DisplayName("handles custom consent type")
        void handlesCustomConsentType() {
            // Given
            setupMocksForConsentRecording();

            // When
            ConsentRecord result = consentAuditService.recordConsentGranted(
                    testUser, ConsentType.CUSTOM, "my_custom_consent", null, "web_form", request);

            // Then
            assertThat(result.getType()).isEqualTo(ConsentType.CUSTOM);
            assertThat(result.getCustomType()).isEqualTo("my_custom_consent");
            assertThat(result.getEffectiveTypeName()).isEqualTo("my_custom_consent");
        }
    }

    @Nested
    @DisplayName("recordConsentWithdrawn")
    class RecordConsentWithdrawn {

        @Test
        @DisplayName("creates withdrawal record")
        void createsWithdrawalRecord() {
            // Given
            setupMocksForConsentRecording();

            // When
            ConsentRecord result = consentAuditService.recordConsentWithdrawn(
                    testUser, ConsentType.MARKETING_EMAILS, "web_form", request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getType()).isEqualTo(ConsentType.MARKETING_EMAILS);
            assertThat(result.getWithdrawnAt()).isNotNull();
            assertThat(result.getGrantedAt()).isNull();
            assertThat(result.isActive()).isFalse();
        }

        @Test
        @DisplayName("publishes audit event for withdrawal")
        void publishesAuditEvent_forWithdrawal() {
            // Given
            setupMocksForConsentRecording();

            // When
            consentAuditService.recordConsentWithdrawn(
                    testUser, ConsentType.ANALYTICS, "api", request);

            // Then
            ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            AuditEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getAction()).isEqualTo("CONSENT_WITHDRAWN");
        }

        @Test
        @DisplayName("publishes ConsentChangedEvent for withdrawal")
        void publishesConsentChangedEvent_forWithdrawal() {
            // Given
            setupMocksForConsentRecording();

            // When
            consentAuditService.recordConsentWithdrawn(
                    testUser, ConsentType.THIRD_PARTY_SHARING, "api", request);

            // Then
            ArgumentCaptor<ConsentChangedEvent> eventCaptor = ArgumentCaptor.forClass(ConsentChangedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            ConsentChangedEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.isWithdrawn()).isTrue();
        }
    }

    @Nested
    @DisplayName("getConsentStatus")
    class GetConsentStatus {

        @Test
        @DisplayName("returns empty map when user is null")
        void returnsEmptyMap_whenUserIsNull() {
            Map<String, ConsentAuditService.ConsentStatus> result = consentAuditService.getConsentStatus(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty map when no consent events")
        void returnsEmptyMap_whenNoConsentEvents() {
            // Given
            when(auditLogQueryService.findByUserAndAction(testUser, "CONSENT_GRANTED"))
                    .thenReturn(new ArrayList<>());
            when(auditLogQueryService.findByUserAndAction(testUser, "CONSENT_WITHDRAWN"))
                    .thenReturn(new ArrayList<>());

            // When
            Map<String, ConsentAuditService.ConsentStatus> result = consentAuditService.getConsentStatus(testUser);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("ConsentStatus")
    class ConsentStatusTests {

        @Test
        @DisplayName("correctly reports active status")
        void correctlyReportsActiveStatus() {
            ConsentAuditService.ConsentStatus status = new ConsentAuditService.ConsentStatus(
                    "privacy_policy", true, null, null);
            assertThat(status.isActive()).isTrue();
            assertThat(status.getConsentType()).isEqualTo("privacy_policy");
        }

        @Test
        @DisplayName("correctly reports inactive status")
        void correctlyReportsInactiveStatus() {
            ConsentAuditService.ConsentStatus status = new ConsentAuditService.ConsentStatus(
                    "marketing", false, null, null);
            assertThat(status.isActive()).isFalse();
        }
    }

}
