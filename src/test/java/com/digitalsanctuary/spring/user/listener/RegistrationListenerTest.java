package com.digitalsanctuary.spring.user.listener;

import static org.mockito.Mockito.*;

import com.digitalsanctuary.spring.user.event.OnRegistrationCompleteEvent;
import com.digitalsanctuary.spring.user.service.UserEmailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationListener Tests")
class RegistrationListenerTest {

    @Mock
    private UserEmailService userEmailService;

    @InjectMocks
    private RegistrationListener registrationListener;

    private static final Long USER_ID = 1L;
    private static final String USER_EMAIL = "test@example.com";
    private String appUrl;
    private Locale locale;

    @BeforeEach
    void setUp() {
        appUrl = "https://example.com";
        locale = Locale.ENGLISH;
    }

    private OnRegistrationCompleteEvent eventFor(Long userId, String userEmail, boolean enabled, String url) {
        return OnRegistrationCompleteEvent.builder()
                .userId(userId)
                .userEmail(userEmail)
                .userEnabled(enabled)
                .locale(locale)
                .appUrl(url)
                .build();
    }

    @Nested
    @DisplayName("Registration Event Handling Tests")
    class RegistrationEventHandlingTests {

        @Test
        @DisplayName("onApplicationEvent - sends verification email when enabled and user is not yet verified")
        void onApplicationEvent_sendsVerificationEmailWhenEnabled() {
            // Given - a not-yet-verified (disabled) user, as produced by the form-registration path when
            // email verification is required.
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            OnRegistrationCompleteEvent event = eventFor(2L, "unverified@example.com", false, appUrl);

            // When
            registrationListener.onApplicationEvent(event);

            // Then - the listener passes the user id; the email service reloads the entity in its own transaction.
            verify(userEmailService).sendRegistrationVerificationEmail(2L, appUrl);
        }

        @Test
        @DisplayName("onApplicationEvent - skips verification email for already-enabled (OAuth/OIDC) user")
        void onApplicationEvent_skipsVerificationEmailForEnabledUser() {
            // Given - sending is enabled, but the user is already enabled (e.g. a first-time OAuth2/OIDC
            // registration where the provider has already verified the email). They must NOT receive an email.
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            OnRegistrationCompleteEvent event = eventFor(3L, "oauth@example.com", true, appUrl);

            // When
            registrationListener.onApplicationEvent(event);

            // Then
            verify(userEmailService, never()).sendRegistrationVerificationEmail(anyLong(), any());
        }

        @Test
        @DisplayName("onApplicationEvent - does not send email when disabled")
        void onApplicationEvent_doesNotSendEmailWhenDisabled() {
            // Given
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", false);
            OnRegistrationCompleteEvent event = eventFor(USER_ID, USER_EMAIL, false, appUrl);

            // When
            registrationListener.onApplicationEvent(event);

            // Then
            verify(userEmailService, never()).sendRegistrationVerificationEmail(anyLong(), any());
        }

        @Test
        @DisplayName("onApplicationEvent - passes null app URL through to email service")
        void onApplicationEvent_handlesNullAppUrl() {
            // Given
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            OnRegistrationCompleteEvent event = eventFor(USER_ID, USER_EMAIL, false, null);

            // When
            registrationListener.onApplicationEvent(event);

            // Then
            verify(userEmailService).sendRegistrationVerificationEmail(USER_ID, null);
        }

        @Test
        @DisplayName("onApplicationEvent - handles different locales")
        void onApplicationEvent_handlesDifferentLocales() {
            // Given
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            OnRegistrationCompleteEvent event = OnRegistrationCompleteEvent.builder()
                    .userId(USER_ID)
                    .userEmail(USER_EMAIL)
                    .userEnabled(false)
                    .locale(Locale.FRENCH)
                    .appUrl(appUrl)
                    .build();

            // When
            registrationListener.onApplicationEvent(event);

            // Then
            verify(userEmailService).sendRegistrationVerificationEmail(USER_ID, appUrl);
        }
    }

    @Nested
    @DisplayName("Multiple User Registration Tests")
    class MultipleUserRegistrationTests {

        @Test
        @DisplayName("Multiple registration events are handled independently")
        void multipleRegistrationEvents_handledIndependently() {
            // Given
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            OnRegistrationCompleteEvent event1 = eventFor(11L, "user1@example.com", false, appUrl);
            OnRegistrationCompleteEvent event2 = eventFor(12L, "user2@example.com", false, appUrl);

            // When
            registrationListener.onApplicationEvent(event1);
            registrationListener.onApplicationEvent(event2);

            // Then
            verify(userEmailService).sendRegistrationVerificationEmail(11L, appUrl);
            verify(userEmailService).sendRegistrationVerificationEmail(12L, appUrl);
        }

        @Test
        @DisplayName("Configuration change affects subsequent events")
        void configurationChange_affectsSubsequentEvents() {
            // Given
            OnRegistrationCompleteEvent event1 = eventFor(11L, "user1@example.com", false, appUrl);
            OnRegistrationCompleteEvent event2 = eventFor(12L, "user2@example.com", false, appUrl);

            // When
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            registrationListener.onApplicationEvent(event1);

            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", false);
            registrationListener.onApplicationEvent(event2);

            // Then
            verify(userEmailService).sendRegistrationVerificationEmail(11L, appUrl);
            verify(userEmailService, never()).sendRegistrationVerificationEmail(12L, appUrl);
        }
    }
}
