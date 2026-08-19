package com.digitalsanctuary.spring.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetails;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnStepUpRequiredException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.security.StepUpService;
import com.digitalsanctuary.spring.user.service.LoginAttemptService;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.service.WebAuthnCredentialManagementService;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.fixtures.TestFixtures;

/**
 * Step-up enforcement on the credential-altering passkey endpoints.
 * <p>
 * These endpoints previously had no gate at all for passwordless accounts: the password check returns
 * immediately when there is no password, so a session alone authorized deleting or renaming a passkey. Step-up closes
 * that, and only when a {@code StepUpService} is configured, so deployments that have not enabled it are unaffected.
 * </p>
 */
@ServiceTest
@DisplayName("WebAuthnManagementAPI Step-Up Tests")
class WebAuthnManagementAPIStepUpTest {

    @Mock
    private WebAuthnCredentialManagementService credentialManagementService;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private UserDetails userDetails;

    @Mock
    private ObjectProvider<StepUpService> stepUpServiceProvider;

    @Mock
    private StepUpService stepUpService;

    private WebAuthnManagementAPI api;

    private User testUser;

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    void setUp() {
        testUser = TestFixtures.Users.standardUser();
        api = new WebAuthnManagementAPI(credentialManagementService, userService, eventPublisher, loginAttemptService,
                stepUpServiceProvider);
        when(userDetails.getUsername()).thenReturn(testUser.getEmail());
        when(userService.findUserByEmail(testUser.getEmail())).thenReturn(testUser);
        // Passwordless (passkey-only) account: the case step-up exists for.
        when(userService.hasPassword(testUser)).thenReturn(false);
    }

    @Nested
    @DisplayName("Step-up configured")
    class StepUpConfiguredTests {

        @BeforeEach
        void provideStepUpService() {
            when(stepUpServiceProvider.getIfAvailable()).thenReturn(stepUpService);
        }

        @Test
        @DisplayName("should reject passkey deletion when step-up is not satisfied")
        void shouldRejectDeleteWithoutStepUp() {
            when(stepUpService.isStepUpSatisfied(eq(testUser), any(), any())).thenReturn(false);

            assertThatThrownBy(() -> api.deleteCredential("cred-1", null, userDetails, request))
                    .isInstanceOf(WebAuthnStepUpRequiredException.class);
            verify(credentialManagementService, never()).deleteCredential(any(), any());
        }

        @Test
        @DisplayName("should reject passkey rename when step-up is not satisfied")
        void shouldRejectRenameWithoutStepUp() {
            when(stepUpService.isStepUpSatisfied(eq(testUser), any(), any())).thenReturn(false);

            assertThatThrownBy(() -> api.renameCredential("cred-1",
                    new WebAuthnManagementAPI.RenameCredentialRequest("Work Laptop", null), userDetails, request))
                            .isInstanceOf(WebAuthnStepUpRequiredException.class);
            verify(credentialManagementService, never()).renameCredential(any(), any(), any());
        }

        @Test
        @DisplayName("should allow passkey deletion when step-up is satisfied")
        void shouldAllowDeleteWithStepUp() {
            when(stepUpService.isStepUpSatisfied(eq(testUser), any(), any())).thenReturn(true);

            assertThat(api.deleteCredential("cred-1", null, userDetails, request).getStatusCode().is2xxSuccessful()).isTrue();
            verify(credentialManagementService).deleteCredential("cred-1", testUser);
        }

        @Test
        @DisplayName("should pass a distinct action per operation so implementations can distinguish them")
        void shouldPassPerOperationAction() {
            when(stepUpService.isStepUpSatisfied(eq(testUser), any(), any())).thenReturn(true);

            api.deleteCredential("cred-1", null, userDetails, request);
            api.renameCredential("cred-1", new WebAuthnManagementAPI.RenameCredentialRequest("Work Laptop", null), userDetails, request);

            ArgumentCaptor<String> actions = ArgumentCaptor.forClass(String.class);
            verify(stepUpService, times(2)).isStepUpSatisfied(eq(testUser), actions.capture(), any());
            assertThat(actions.getAllValues()).containsExactly("delete-passkey", "rename-passkey");
        }
    }

    @Nested
    @DisplayName("Step-up not configured")
    class StepUpAbsentTests {

        @Test
        @DisplayName("should allow passkey deletion on a passwordless account, unchanged from before step-up existed")
        void shouldAllowDeleteWhenNoStepUpServiceIsConfigured() {
            when(stepUpServiceProvider.getIfAvailable()).thenReturn(null);

            assertThat(api.deleteCredential("cred-1", null, userDetails, request).getStatusCode().is2xxSuccessful()).isTrue();
            verify(credentialManagementService).deleteCredential("cred-1", testUser);
        }
    }
}
