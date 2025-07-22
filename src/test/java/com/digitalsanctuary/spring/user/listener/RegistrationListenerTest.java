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
        testUser = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("test@example.com")
                .withFirstName("Test")
                .withLastName("User")
                .enabled()
                .build();
        appUrl = "https://example.com";
        locale = Locale.ENGLISH;
    }

    @Nested
    @DisplayName("Registration Event Handling Tests")
    class RegistrationEventHandlingTests {

        @Test
        @DisplayName("onApplicationEvent - sends verification email when enabled")
        void onApplicationEvent_sendsVerificationEmailWhenEnabled() {
            // Given
            ReflectionTestUtils.setField(registrationListener, "sendRegistrationVerificationEmail", true);
            OnRegistrationCompleteEvent event = OnRegistrationCompleteEvent.builder()
                    .user(testUser)
                    .locale(locale)
                    .appUrl(appUrl)
                    .build();

            // When
            registrationListener.onApplicationEvent(event);

            // Then
            verify(userEmailService).sendRegistrationVerificationEmail(testUser, appUrl);
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