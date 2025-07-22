package com.digitalsanctuary.spring.user.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserPreDeleteEvent Tests")
class UserPreDeleteEventTest {

    private User testUser;
    private Object eventSource;

    @BeforeEach
    void setUp() {
        testUser = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("test@example.com")
                .withFirstName("Test")
                .withLastName("User")
                .enabled()
                .build();
        eventSource = this;
    }

    @Test
    @DisplayName("Event creation stores user and source")
    void eventCreation_storesUserAndSource() {
        // When
        UserPreDeleteEvent event = new UserPreDeleteEvent(eventSource, testUser);

        // Then
        assertThat(event.getUser()).isEqualTo(testUser);
        assertThat(event.getSource()).isEqualTo(eventSource);
    }

    @Test
    @DisplayName("getUserId returns user's ID")
    void getUserId_returnsUserId() {
        // When
        UserPreDeleteEvent event = new UserPreDeleteEvent(eventSource, testUser);

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
        UserPreDeleteEvent event1 = new UserPreDeleteEvent(source1, testUser);
        UserPreDeleteEvent event2 = new UserPreDeleteEvent(source2, testUser);

        // Then
        assertThat(event1.getSource()).isEqualTo(source1);
        assertThat(event2.getSource()).isEqualTo(source2);
        assertThat(event1.getUser()).isEqualTo(event2.getUser());
    }

    @Test
    @DisplayName("Event preserves user information")
    void event_preservesUserInformation() {
        // When
        UserPreDeleteEvent event = new UserPreDeleteEvent(eventSource, testUser);

        // Then
        User eventUser = event.getUser();
        assertThat(eventUser.getEmail()).isEqualTo("test@example.com");
        assertThat(eventUser.getFirstName()).isEqualTo("Test");
        assertThat(eventUser.getLastName()).isEqualTo("User");
        assertThat(eventUser.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Multiple events for different users")
    void multipleEvents_forDifferentUsers() {
        // Given
        User user1 = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("user1@example.com")
                .build();
        User user2 = UserTestDataBuilder.aUser()
                .withId(2L)
                .withEmail("user2@example.com")
                .build();

        // When
        UserPreDeleteEvent event1 = new UserPreDeleteEvent(eventSource, user1);
        UserPreDeleteEvent event2 = new UserPreDeleteEvent(eventSource, user2);

        // Then
        assertThat(event1.getUser()).isNotEqualTo(event2.getUser());
        assertThat(event1.getUserId()).isEqualTo(1L);
        assertThat(event2.getUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Event timestamp is set on creation")
    void event_timestampIsSet() {
        // Given
        long beforeCreation = System.currentTimeMillis();

        // When
        UserPreDeleteEvent event = new UserPreDeleteEvent(eventSource, testUser);

        // Then
        long afterCreation = System.currentTimeMillis();
        assertThat(event.getTimestamp()).isGreaterThanOrEqualTo(beforeCreation);
        assertThat(event.getTimestamp()).isLessThanOrEqualTo(afterCreation);
    }
}