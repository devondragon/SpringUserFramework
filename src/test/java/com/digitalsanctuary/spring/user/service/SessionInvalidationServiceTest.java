package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
}
