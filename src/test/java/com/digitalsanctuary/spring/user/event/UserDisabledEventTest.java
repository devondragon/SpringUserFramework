package com.digitalsanctuary.spring.user.event;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserDisabledEvent Tests")
class UserDisabledEventTest {

    private Object eventSource;

    @BeforeEach
    void setUp() {
        eventSource = this;
    }

    @Test
    @DisplayName("Event creation stores user ID and email")
    void eventCreation_storesUserIdAndEmail() {
        // When
        UserDisabledEvent event = new UserDisabledEvent(eventSource, 1L, "test@example.com");

        // Then
        assertThat(event.getUserId()).isEqualTo(1L);
        assertThat(event.getUserEmail()).isEqualTo("test@example.com");
        assertThat(event.getSource()).isEqualTo(eventSource);
    }

    @Test
    @DisplayName("Event with different sources")
    void event_withDifferentSources() {
        // Given
        Object source1 = new Object();
        Object source2 = "Different Source";

        // When
        UserDisabledEvent event1 = new UserDisabledEvent(source1, 1L, "user1@example.com");
        UserDisabledEvent event2 = new UserDisabledEvent(source2, 2L, "user2@example.com");

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
        UserDisabledEvent event = new UserDisabledEvent(eventSource, 1L, "test@example.com");

        // Then
        long afterCreation = System.currentTimeMillis();
        assertThat(event.getTimestamp()).isGreaterThanOrEqualTo(beforeCreation);
        assertThat(event.getTimestamp()).isLessThanOrEqualTo(afterCreation);
    }

}
