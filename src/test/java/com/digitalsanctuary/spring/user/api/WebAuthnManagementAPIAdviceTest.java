package com.digitalsanctuary.spring.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnAccountLockedException;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnException;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnReauthenticationException;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnUserNotFoundException;
import com.digitalsanctuary.spring.user.util.GenericResponse;

/**
 * Unit tests for {@link WebAuthnManagementAPIAdvice}, verifying that each WebAuthn exception type maps to the intended
 * HTTP status so credential-management clients can distinguish the failure modes (missing field, wrong password, locked
 * account, unknown user).
 */
@DisplayName("WebAuthnManagementAPIAdvice status mapping")
class WebAuthnManagementAPIAdviceTest {

    private final WebAuthnManagementAPIAdvice advice = new WebAuthnManagementAPIAdvice();

    @Test
    @DisplayName("base WebAuthnException (e.g. missing current password) -> 400 Bad Request")
    void baseExceptionMapsToBadRequest() {
        ResponseEntity<GenericResponse> response = advice.handleWebAuthnError(new WebAuthnException("Current password is required"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("incorrect current password -> 401 Unauthorized")
    void reauthenticationFailureMapsToUnauthorized() {
        ResponseEntity<GenericResponse> response =
                advice.handleReauthenticationFailure(new WebAuthnReauthenticationException("Current password is incorrect."));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("locked account -> 423 Locked")
    void accountLockedMapsToLocked() {
        ResponseEntity<GenericResponse> response = advice.handleAccountLocked(new WebAuthnAccountLockedException("Account is locked"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }

    @Test
    @DisplayName("unknown user -> 404 Not Found")
    void userNotFoundMapsToNotFound() {
        ResponseEntity<GenericResponse> response = advice.handleUserNotFound(new WebAuthnUserNotFoundException("User not found"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
