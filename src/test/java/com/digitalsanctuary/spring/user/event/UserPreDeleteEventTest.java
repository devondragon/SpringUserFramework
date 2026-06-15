package com.digitalsanctuary.spring.user.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserPreDeleteEvent Tests")
class UserPreDeleteEventTest {

    private Long userId;
    private String userEmail;
    private Object eventSource;

    @BeforeEach
    void setUp() {
        userId = 1L;
        userEmail = "test@example.com";
        eventSource = this;
    }

    @Test
    @DisplayName("Event creation stores user data and source")
    void eventCreation_storesUserDataAndSource() {
        // When
        UserPreDeleteEvent event = new UserPreDeleteEvent(eventSource, userId, userEmail);

        // Then
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getUserEmail()).isEqualTo(userEmail);
        assertThat(event.getSource()).isEqualTo(eventSource);
    }

    @Test
    @DisplayName("getUserId returns user's ID")
    void getUserId_returnsUserId() {
        // When
        UserPreDeleteEvent event = new UserPreDeleteEvent(eventSource, userId, userEmail);

        // Then
        assertThat(event.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Event with different sources")
    void event_withDifferentSources() {
        // Given
        Object source1 = new Object();
        Object source2 = "Different Source";

        // When
        UserPreDeleteEvent event1 = new UserPreDeleteEvent(source1, userId, userEmail);
        UserPreDeleteEvent event2 = new UserPreDeleteEvent(source2, userId, userEmail);

        // Then
        assertThat(event1.getSource()).isEqualTo(source1);
        assertThat(event2.getSource()).isEqualTo(source2);
        assertThat(event1.getUserId()).isEqualTo(event2.getUserId());
    }

    @Test
    @DisplayName("Event preserves user information")
    void event_preservesUserInformation() {
        // When
        UserPreDeleteEvent event = new UserPreDeleteEvent(eventSource, userId, userEmail);

        // Then
        assertThat(event.getUserId()).isEqualTo(1L);
        assertThat(event.getUserEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Multiple events for different users")
    void multipleEvents_forDifferentUsers() {
        // When
        UserPreDeleteEvent event1 = new UserPreDeleteEvent(eventSource, 1L, "user1@example.com");
        UserPreDeleteEvent event2 = new UserPreDeleteEvent(eventSource, 2L, "user2@example.com");

        // Then
        assertThat(event1.getUserId()).isEqualTo(1L);
        assertThat(event2.getUserId()).isEqualTo(2L);
        assertThat(event1.getUserEmail()).isEqualTo("user1@example.com");
        assertThat(event2.getUserEmail()).isEqualTo("user2@example.com");
    }

    @Test
    @DisplayName("Event timestamp is set on creation")
    void event_timestampIsSet() {
        // Given
        long beforeCreation = System.currentTimeMillis();

        // When
        UserPreDeleteEvent event = new UserPreDeleteEvent(eventSource, userId, userEmail);

        // Then
        long afterCreation = System.currentTimeMillis();
        assertThat(event.getTimestamp()).isGreaterThanOrEqualTo(beforeCreation);
        assertThat(event.getTimestamp()).isLessThanOrEqualTo(afterCreation);
    }
}
