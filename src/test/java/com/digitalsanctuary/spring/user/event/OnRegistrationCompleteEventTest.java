package com.digitalsanctuary.spring.user.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

@DisplayName("OnRegistrationCompleteEvent Tests")
class OnRegistrationCompleteEventTest {

    private Long userId;
    private String userEmail;
    private String appUrl;
    private Locale locale;

    @BeforeEach
    void setUp() {
        userId = 1L;
        userEmail = "test@example.com";
        appUrl = "https://example.com";
        locale = Locale.ENGLISH;
    }

    @Test
    @DisplayName("Event creation with builder")
    void eventCreation_withBuilder() {
        // When
        OnRegistrationCompleteEvent event = OnRegistrationCompleteEvent.builder()
                .userId(userId)
                .userEmail(userEmail)
                .userEnabled(true)
                .locale(locale)
                .appUrl(appUrl)
                .build();

        // Then
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getUserEmail()).isEqualTo(userEmail);
        assertThat(event.isUserEnabled()).isTrue();
        assertThat(event.getLocale()).isEqualTo(locale);
        assertThat(event.getAppUrl()).isEqualTo(appUrl);
        assertThat(event.getSource()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Event creation with constructor")
    void eventCreation_withConstructor() {
        // When
        OnRegistrationCompleteEvent event = new OnRegistrationCompleteEvent(userId, userEmail, false, locale, appUrl);

        // Then
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getUserEmail()).isEqualTo(userEmail);
        assertThat(event.isUserEnabled()).isFalse();
        assertThat(event.getLocale()).isEqualTo(locale);
        assertThat(event.getAppUrl()).isEqualTo(appUrl);
        assertThat(event.getSource()).isEqualTo(userId);
    }

    @Test
    @DisplayName("Event with null app URL")
    void event_withNullAppUrl() {
        // When
        OnRegistrationCompleteEvent event = OnRegistrationCompleteEvent.builder()
                .userId(userId)
                .userEmail(userEmail)
                .userEnabled(false)
                .locale(locale)
                .appUrl(null)
                .build();

        // Then
        assertThat(event.getAppUrl()).isNull();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getLocale()).isEqualTo(locale);
    }

    @Test
    @DisplayName("Event with different locales")
    void event_withDifferentLocales() {
        // Given
        Locale frenchLocale = Locale.FRENCH;

        // When
        OnRegistrationCompleteEvent event = OnRegistrationCompleteEvent.builder()
                .userId(userId)
                .userEmail(userEmail)
                .locale(frenchLocale)
                .appUrl(appUrl)
                .build();

        // Then
        assertThat(event.getLocale()).isEqualTo(frenchLocale);
        assertThat(event.getLocale().getLanguage()).isEqualTo("fr");
    }

    @Test
    @DisplayName("Event equality includes all fields")
    void eventEquality_includesAllFields() {
        // When
        OnRegistrationCompleteEvent event1 = OnRegistrationCompleteEvent.builder()
                .userId(userId)
                .userEmail(userEmail)
                .locale(locale)
                .appUrl(appUrl)
                .build();
        OnRegistrationCompleteEvent event2 = OnRegistrationCompleteEvent.builder()
                .userId(userId)
                .userEmail(userEmail)
                .locale(locale)
                .appUrl(appUrl)
                .build();
        OnRegistrationCompleteEvent event3 = OnRegistrationCompleteEvent.builder()
                .userId(userId)
                .userEmail(userEmail)
                .locale(Locale.FRENCH)
                .appUrl("https://different.com")
                .build();
        OnRegistrationCompleteEvent event4 = OnRegistrationCompleteEvent.builder()
                .userId(2L)
                .userEmail("another@example.com")
                .locale(locale)
                .appUrl(appUrl)
                .build();

        // Then
        assertThat(event1).isEqualTo(event2); // Identical events
        assertThat(event1).isNotEqualTo(event3); // Different locale and URL
        assertThat(event1).isNotEqualTo(event4); // Different user
    }

    @Test
    @DisplayName("Event toString includes relevant information")
    void event_toStringIncludesRelevantInfo() {
        // When
        OnRegistrationCompleteEvent event = OnRegistrationCompleteEvent.builder()
                .userId(userId)
                .userEmail(userEmail)
                .locale(locale)
                .appUrl(appUrl)
                .build();

        // Then
        String eventString = event.toString();
        assertThat(eventString).contains("test@example.com");
        assertThat(eventString).contains(appUrl);
        assertThat(eventString).contains("en");
    }
}
