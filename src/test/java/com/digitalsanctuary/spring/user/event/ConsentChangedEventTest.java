package com.digitalsanctuary.spring.user.event;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.digitalsanctuary.spring.user.gdpr.ConsentRecord;
import com.digitalsanctuary.spring.user.gdpr.ConsentType;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

@DisplayName("ConsentChangedEvent Tests")
class ConsentChangedEventTest {

    private User testUser;
    private Object eventSource;

    @BeforeEach
    void setUp() {
        testUser = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("test@example.com")
                .enabled()
                .build();
        eventSource = this;
    }

    @Test
    @DisplayName("Event creation stores user and consent record")
    void eventCreation_storesUserAndConsentRecord() {
        // Given
        ConsentRecord record = ConsentRecord.builder()
                .type(ConsentType.PRIVACY_POLICY)
                .grantedAt(Instant.now())
                .build();

        // When
        ConsentChangedEvent event = new ConsentChangedEvent(
                eventSource, testUser, record, ConsentChangedEvent.ChangeType.GRANTED);

        // Then
        assertThat(event.getUser()).isEqualTo(testUser);
        assertThat(event.getConsentRecord()).isEqualTo(record);
        assertThat(event.getSource()).isEqualTo(eventSource);
    }

    @Test
    @DisplayName("getUserId returns user's ID")
    void getUserId_returnsUserId() {
        // Given
        ConsentRecord record = ConsentRecord.builder()
                .type(ConsentType.MARKETING_EMAILS)
                .build();

        // When
        ConsentChangedEvent event = new ConsentChangedEvent(
                eventSource, testUser, record, ConsentChangedEvent.ChangeType.GRANTED);

        // Then
        assertThat(event.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getConsentType returns correct type")
    void getConsentType_returnsCorrectType() {
        // Given
        ConsentRecord record = ConsentRecord.builder()
                .type(ConsentType.ANALYTICS)
                .build();

        // When
        ConsentChangedEvent event = new ConsentChangedEvent(
                eventSource, testUser, record, ConsentChangedEvent.ChangeType.WITHDRAWN);

        // Then
        assertThat(event.getConsentType()).isEqualTo(ConsentType.ANALYTICS);
    }

    @Test
    @DisplayName("isGranted returns true for GRANTED change type")
    void isGranted_returnsTrue_forGrantedChangeType() {
        // Given
        ConsentRecord record = ConsentRecord.builder()
                .type(ConsentType.DATA_PROCESSING)
                .build();

        // When
        ConsentChangedEvent event = new ConsentChangedEvent(
                eventSource, testUser, record, ConsentChangedEvent.ChangeType.GRANTED);

        // Then
        assertThat(event.isGranted()).isTrue();
        assertThat(event.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("isWithdrawn returns true for WITHDRAWN change type")
    void isWithdrawn_returnsTrue_forWithdrawnChangeType() {
        // Given
        ConsentRecord record = ConsentRecord.builder()
                .type(ConsentType.THIRD_PARTY_SHARING)
                .build();

        // When
        ConsentChangedEvent event = new ConsentChangedEvent(
                eventSource, testUser, record, ConsentChangedEvent.ChangeType.WITHDRAWN);

        // Then
        assertThat(event.isWithdrawn()).isTrue();
        assertThat(event.isGranted()).isFalse();
    }

    @Test
    @DisplayName("getChangeType returns correct type")
    void getChangeType_returnsCorrectType() {
        // Given
        ConsentRecord record = ConsentRecord.builder()
                .type(ConsentType.TERMS_OF_SERVICE)
                .build();

        // When
        ConsentChangedEvent grantedEvent = new ConsentChangedEvent(
                eventSource, testUser, record, ConsentChangedEvent.ChangeType.GRANTED);
        ConsentChangedEvent withdrawnEvent = new ConsentChangedEvent(
                eventSource, testUser, record, ConsentChangedEvent.ChangeType.WITHDRAWN);

        // Then
        assertThat(grantedEvent.getChangeType()).isEqualTo(ConsentChangedEvent.ChangeType.GRANTED);
        assertThat(withdrawnEvent.getChangeType()).isEqualTo(ConsentChangedEvent.ChangeType.WITHDRAWN);
    }

    @Test
    @DisplayName("Event timestamp is set on creation")
    void event_timestampIsSet() {
        // Given
        ConsentRecord record = ConsentRecord.builder()
                .type(ConsentType.PRIVACY_POLICY)
                .build();
        long beforeCreation = System.currentTimeMillis();

        // When
        ConsentChangedEvent event = new ConsentChangedEvent(
                eventSource, testUser, record, ConsentChangedEvent.ChangeType.GRANTED);

        // Then
        long afterCreation = System.currentTimeMillis();
        assertThat(event.getTimestamp()).isGreaterThanOrEqualTo(beforeCreation);
        assertThat(event.getTimestamp()).isLessThanOrEqualTo(afterCreation);
    }

}
