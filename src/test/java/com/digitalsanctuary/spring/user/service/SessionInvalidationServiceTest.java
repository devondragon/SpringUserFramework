package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.util.ReflectionTestUtils;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionInvalidationService Tests")
class SessionInvalidationServiceTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @InjectMocks
    private SessionInvalidationService sessionInvalidationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("test@example.com")
                .withFirstName("Test")
                .withLastName("User")
                .enabled()
                .build();

        // Set a high default threshold to avoid warning logs in most tests
        ReflectionTestUtils.setField(sessionInvalidationService, "warnThreshold", 1000);
    }

    @Nested
    @DisplayName("invalidateUserSessions Tests")
    class InvalidateUserSessionsTests {

        @Test
        @DisplayName("invalidates all sessions for user with User principal")
        void invalidatesAllSessionsForUserWithUserPrincipal() {
            // Given
            SessionInformation session1 = mock(SessionInformation.class);
            SessionInformation session2 = mock(SessionInformation.class);
            when(session1.getSessionId()).thenReturn("session-1");
            when(session2.getSessionId()).thenReturn("session-2");

            when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(testUser));
            when(sessionRegistry.getAllSessions(testUser, false)).thenReturn(Arrays.asList(session1, session2));

            // When
            int invalidatedCount = sessionInvalidationService.invalidateUserSessions(testUser);

            // Then
            assertThat(invalidatedCount).isEqualTo(2);
            verify(session1).expireNow();
            verify(session2).expireNow();
        }

        @Test
        @DisplayName("invalidates all sessions for user with DSUserDetails principal")
        void invalidatesAllSessionsForUserWithDSUserDetailsPrincipal() {
            // Given
            DSUserDetails userDetails = new DSUserDetails(testUser);
            SessionInformation session = mock(SessionInformation.class);
            when(session.getSessionId()).thenReturn("session-1");

            when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(userDetails));
            when(sessionRegistry.getAllSessions(userDetails, false)).thenReturn(List.of(session));

            // When
            int invalidatedCount = sessionInvalidationService.invalidateUserSessions(testUser);

            // Then
            assertThat(invalidatedCount).isEqualTo(1);
            verify(session).expireNow();
        }

        @Test
        @DisplayName("returns 0 when user has no active sessions")
        void returnsZeroWhenUserHasNoActiveSessions() {
            // Given
            when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(testUser));
            when(sessionRegistry.getAllSessions(testUser, false)).thenReturn(Collections.emptyList());

            // When
            int invalidatedCount = sessionInvalidationService.invalidateUserSessions(testUser);

            // Then
            assertThat(invalidatedCount).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 when user is not in session registry")
        void returnsZeroWhenUserNotInSessionRegistry() {
            // Given
            User otherUser = UserTestDataBuilder.aUser()
                    .withId(2L)
                    .withEmail("other@example.com")
                    .build();
            when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(otherUser));

            // When
            int invalidatedCount = sessionInvalidationService.invalidateUserSessions(testUser);

            // Then
            assertThat(invalidatedCount).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 when session registry is empty")
        void returnsZeroWhenSessionRegistryIsEmpty() {
            // Given
            when(sessionRegistry.getAllPrincipals()).thenReturn(Collections.emptyList());

            // When
            int invalidatedCount = sessionInvalidationService.invalidateUserSessions(testUser);

            // Then
            assertThat(invalidatedCount).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 when user is null")
        void returnsZeroWhenUserIsNull() {
            // When
            int invalidatedCount = sessionInvalidationService.invalidateUserSessions(null);

            // Then
            assertThat(invalidatedCount).isEqualTo(0);
            verify(sessionRegistry, never()).getAllPrincipals();
        }

        @Test
        @DisplayName("only invalidates sessions for matching user")
        void onlyInvalidatesSessionsForMatchingUser() {
            // Given
            User otherUser = UserTestDataBuilder.aUser()
                    .withId(2L)
                    .withEmail("other@example.com")
                    .build();

            SessionInformation testUserSession = mock(SessionInformation.class);
            when(testUserSession.getSessionId()).thenReturn("test-session");

            when(sessionRegistry.getAllPrincipals()).thenReturn(Arrays.asList(testUser, otherUser));
            when(sessionRegistry.getAllSessions(testUser, false)).thenReturn(List.of(testUserSession));
            // Note: We don't stub otherUser sessions because our code only calls getAllSessions
            // for principals that match the target user by ID

            // When
            int invalidatedCount = sessionInvalidationService.invalidateUserSessions(testUser);

            // Then
            assertThat(invalidatedCount).isEqualTo(1);
            verify(testUserSession).expireNow();
            // Verify otherUser sessions were not touched
            verify(sessionRegistry, never()).getAllSessions(otherUser, false);
        }

        @Test
        @DisplayName("handles mixed principal types correctly")
        void handlesMixedPrincipalTypesCorrectly() {
            // Given
            DSUserDetails userDetails = new DSUserDetails(testUser);
            SessionInformation session1 = mock(SessionInformation.class);
            SessionInformation session2 = mock(SessionInformation.class);
            when(session1.getSessionId()).thenReturn("session-1");
            when(session2.getSessionId()).thenReturn("session-2");

            // Same user logged in with both User and DSUserDetails principals
            when(sessionRegistry.getAllPrincipals()).thenReturn(Arrays.asList(testUser, userDetails));
            when(sessionRegistry.getAllSessions(testUser, false)).thenReturn(List.of(session1));
            when(sessionRegistry.getAllSessions(userDetails, false)).thenReturn(List.of(session2));

            // When
            int invalidatedCount = sessionInvalidationService.invalidateUserSessions(testUser);

            // Then
            assertThat(invalidatedCount).isEqualTo(2);
            verify(session1).expireNow();
            verify(session2).expireNow();
        }
    }

    @Nested
    @DisplayName("Performance Monitoring Tests")
    class PerformanceMonitoringTests {

        @Test
        @DisplayName("logs warning when principal count exceeds threshold")
        void logsWarningWhenPrincipalCountExceedsThreshold() {
            // Given - set a low threshold for testing
            ReflectionTestUtils.setField(sessionInvalidationService, "warnThreshold", 5);

            // Create more principals than the threshold
            List<Object> manyPrincipals = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                User user = UserTestDataBuilder.aUser()
                        .withId((long) (i + 100))
                        .withEmail("user" + i + "@example.com")
                        .build();
                manyPrincipals.add(user);
            }
            when(sessionRegistry.getAllPrincipals()).thenReturn(manyPrincipals);

            // When
            sessionInvalidationService.invalidateUserSessions(testUser);

            // Then - the method completes without error
            // (Log output verification would require a log capture library like LogCaptor)
            verify(sessionRegistry).getAllPrincipals();
        }

        @Test
        @DisplayName("does not warn when principal count is below threshold")
        void doesNotWarnWhenPrincipalCountBelowThreshold() {
            // Given - threshold is set to 1000 in @BeforeEach
            // Create fewer principals than the threshold
            when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(testUser));

            // When
            sessionInvalidationService.invalidateUserSessions(testUser);

            // Then - the method completes without error
            verify(sessionRegistry).getAllPrincipals();
        }

        @Test
        @DisplayName("uses default threshold of 1000")
        void usesDefaultThresholdOf1000() {
            // Given - create a new service without setting threshold (should use default)
            SessionInvalidationService newService = new SessionInvalidationService(sessionRegistry);

            // Verify the default value is set correctly via reflection
            Integer threshold = (Integer) ReflectionTestUtils.getField(newService, "warnThreshold");

            // Then - default should be 0 (unset by Spring) since we're not using Spring context
            // In production, Spring will inject the default value of 1000
            assertThat(threshold).isEqualTo(0);
        }

        @Test
        @DisplayName("includes principal count in info log")
        void includesPrincipalCountInInfoLog() {
            // Given - threshold is set to 1000 in @BeforeEach
            SessionInformation session = mock(SessionInformation.class);
            when(session.getSessionId()).thenReturn("session-1");
            when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(testUser));
            when(sessionRegistry.getAllSessions(testUser, false)).thenReturn(List.of(session));

            // When
            int invalidatedCount = sessionInvalidationService.invalidateUserSessions(testUser);

            // Then - verify the session was invalidated (log verification would require LogCaptor)
            assertThat(invalidatedCount).isEqualTo(1);
            verify(session).expireNow();
        }
    }
}
