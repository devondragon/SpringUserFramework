package com.digitalsanctuary.spring.user.listener;

import static org.mockito.Mockito.*;

import com.digitalsanctuary.spring.user.event.OnRegistrationCompleteEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.UserEmailService;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

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

    private User testUser;
    private String appUrl;
    private Locale locale;

    @BeforeEach
    void setUp() {
        // Default the shared fixture to a DISABLED (not-yet-verified) user so the "send verification email"
        // tests exercise the email-sending path. The skip-for-enabled-users behavior is covered separately.
        testUser = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("test@example.com")
                .withFirstName("Test")
                .withLastName("User")
                .disabled()
                .build();
        appUrl = "https://example.com";
        locale = Locale.ENGLISH;
    }

    @Nested
    @DisplayName("Registration Event Handling Tests")
    class RegistrationEventHandlingTests {

        @Test
        @DisplayName("onApplicationEvent - sends verification email when enabled and user is not yet verified")
        void onApplicationEvent_sendsVerificationEmailWhenEnabled() {
            // Given - a DISABLED (not yet verified) user, as produced by the form-registration path when
            // email verification is required.
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            User unverifiedUser = UserTestDataBuilder.aUser()
                    .withEmail("unverified@example.com")
                    .disabled()
                    .build();
            OnRegistrationCompleteEvent event = OnRegistrationCompleteEvent.builder()
                    .user(unverifiedUser)
                    .locale(locale)
                    .appUrl(appUrl)
                    .build();

            // When
            registrationListener.onApplicationEvent(event);

            // Then
            verify(userEmailService).sendRegistrationVerificationEmail(unverifiedUser, appUrl);
        }

        @Test
        @DisplayName("onApplicationEvent - skips verification email for already-enabled (OAuth/OIDC) user")
        void onApplicationEvent_skipsVerificationEmailForEnabledUser() {
            // Given - sending is enabled, but the user is already enabled (e.g. a first-time OAuth2/OIDC
            // registration where the provider has already verified the email). They must NOT receive an email.
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            User enabledUser = UserTestDataBuilder.aUser()
                    .withEmail("oauth@example.com")
                    .enabled()
                    .build();
            OnRegistrationCompleteEvent event = OnRegistrationCompleteEvent.builder()
                    .user(enabledUser)
                    .locale(locale)
                    .appUrl(appUrl)
                    .build();

            // When
            registrationListener.onApplicationEvent(event);

            // Then
            verify(userEmailService, never()).sendRegistrationVerificationEmail(any(), any());
        }

        @Test
        @DisplayName("onApplicationEvent - does not send email when disabled")
        void onApplicationEvent_doesNotSendEmailWhenDisabled() {
            // Given
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", false);
            OnRegistrationCompleteEvent event = OnRegistrationCompleteEvent.builder()
                    .user(testUser)
                    .locale(locale)
                    .appUrl(appUrl)
                    .build();

            // When
            registrationListener.onApplicationEvent(event);

            // Then
            verify(userEmailService, never()).sendRegistrationVerificationEmail(any(), any());
        }

        @Test
        @DisplayName("onApplicationEvent - handles null app URL")
        void onApplicationEvent_handlesNullAppUrl() {
            // Given
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            OnRegistrationCompleteEvent event = OnRegistrationCompleteEvent.builder()
                    .user(testUser)
                    .locale(locale)
                    .appUrl(null)
                    .build();

            // When
            registrationListener.onApplicationEvent(event);

            // Then
            verify(userEmailService).sendRegistrationVerificationEmail(testUser, null);
        }

        @Test
        @DisplayName("onApplicationEvent - handles different locales")
        void onApplicationEvent_handlesDifferentLocales() {
            // Given
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            Locale frenchLocale = Locale.FRENCH;
            OnRegistrationCompleteEvent event = OnRegistrationCompleteEvent.builder()
                    .user(testUser)
                    .locale(frenchLocale)
                    .appUrl(appUrl)
                    .build();

            // When
            registrationListener.onApplicationEvent(event);

            // Then
            verify(userEmailService).sendRegistrationVerificationEmail(testUser, appUrl);
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
            User user1 = UserTestDataBuilder.aUser()
                    .withEmail("user1@example.com")
                    .build();
            User user2 = UserTestDataBuilder.aUser()
                    .withEmail("user2@example.com")
                    .build();

            OnRegistrationCompleteEvent event1 = OnRegistrationCompleteEvent.builder()
                    .user(user1)
                    .locale(locale)
                    .appUrl(appUrl)
                    .build();
            OnRegistrationCompleteEvent event2 = OnRegistrationCompleteEvent.builder()
                    .user(user2)
                    .locale(locale)
                    .appUrl(appUrl)
                    .build();

            // When
            registrationListener.onApplicationEvent(event1);
            registrationListener.onApplicationEvent(event2);

            // Then
            verify(userEmailService).sendRegistrationVerificationEmail(user1, appUrl);
            verify(userEmailService).sendRegistrationVerificationEmail(user2, appUrl);
        }

        @Test
        @DisplayName("Configuration change affects subsequent events")
        void configurationChange_affectsSubsequentEvents() {
            // Given
            User user1 = UserTestDataBuilder.aUser()
                    .withEmail("user1@example.com")
                    .build();
            User user2 = UserTestDataBuilder.aUser()
                    .withEmail("user2@example.com")
                    .build();

            OnRegistrationCompleteEvent event1 = OnRegistrationCompleteEvent.builder()
                    .user(user1)
                    .locale(locale)
                    .appUrl(appUrl)
                    .build();
            OnRegistrationCompleteEvent event2 = OnRegistrationCompleteEvent.builder()
                    .user(user2)
                    .locale(locale)
                    .appUrl(appUrl)
                    .build();

            // When
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            registrationListener.onApplicationEvent(event1);
            
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", false);
            registrationListener.onApplicationEvent(event2);

            // Then
            verify(userEmailService).sendRegistrationVerificationEmail(user1, appUrl);
            verify(userEmailService, never()).sendRegistrationVerificationEmail(user2, appUrl);
        }
    }
}