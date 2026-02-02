package com.digitalsanctuary.spring.user.event;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserDeletedEvent Tests")
class UserDeletedEventTest {

    private Object eventSource;

    @BeforeEach
    void setUp() {
        eventSource = this;
    }

    @Test
    @DisplayName("Event creation stores user ID and email")
    void eventCreation_storesUserIdAndEmail() {
        // When
        UserDeletedEvent event = new UserDeletedEvent(eventSource, 1L, "test@example.com");

        // Then
        assertThat(event.getUserId()).isEqualTo(1L);
        assertThat(event.getUserEmail()).isEqualTo("test@example.com");
        assertThat(event.getSource()).isEqualTo(eventSource);
    }

    @Test
    @DisplayName("Event with export flag true")
    void eventWithExportFlag_true() {
        // When
        UserDeletedEvent event = new UserDeletedEvent(eventSource, 1L, "test@example.com", true);

        // Then
        assertThat(event.isDataExported()).isTrue();
    }

    @Test
    @DisplayName("Event with export flag false")
    void eventWithExportFlag_false() {
        // When
        UserDeletedEvent event = new UserDeletedEvent(eventSource, 1L, "test@example.com", false);

        // Then
        assertThat(event.isDataExported()).isFalse();
    }

    @Test
    @DisplayName("Event without export flag defaults to false")
    void eventWithoutExportFlag_defaultsToFalse() {
        // When
        UserDeletedEvent event = new UserDeletedEvent(eventSource, 1L, "test@example.com");

        // Then
        assertThat(event.isDataExported()).isFalse();
    }

    @Test
    @DisplayName("Event with different sources")
    void event_withDifferentSources() {
        // Given
        Object source1 = new Object();
        Object source2 = "Different Source";

        // When
        UserDeletedEvent event1 = new UserDeletedEvent(source1, 1L, "user1@example.com");
        UserDeletedEvent event2 = new UserDeletedEvent(source2, 2L, "user2@example.com");

        // Then
        assertThat(event1.getSource()).isEqualTo(source1);
        assertThat(event2.getSource()).isEqualTo(source2);
        assertThat(event1.getUserId()).isNotEqualTo(event2.getUserId());
    }

    @Test
    @DisplayName("Event timestamp is set on creation")
    void event_timestampIsSet() {
        // Given
        long beforeCreation = System.currentTimeMillis();

        // When
        UserDeletedEvent event = new UserDeletedEvent(eventSource, 1L, "test@example.com");

        // Then
        long afterCreation = System.currentTimeMillis();
        assertThat(event.getTimestamp()).isGreaterThanOrEqualTo(beforeCreation);
        assertThat(event.getTimestamp()).isLessThanOrEqualTo(afterCreation);
    }

}
