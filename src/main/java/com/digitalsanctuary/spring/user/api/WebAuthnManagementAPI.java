package com.digitalsanctuary.spring.user.api;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.dto.WebAuthnCredentialInfo;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnException;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnUserNotFoundException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.LoginAttemptService;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.service.WebAuthnCredentialManagementService;
import com.digitalsanctuary.spring.user.util.GenericResponse;
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for WebAuthn credential management.
 *
 * <p>
 * This controller provides endpoints for managing WebAuthn credentials (passkeys). Authenticated users can list their 
 * registered passkeys, rename them for easier identification, and delete passkeys they no longer use.
 * </p>
 *
 * <p>
 * Endpoints:
 * </p>
 * <ul>
 * <li>GET /user/webauthn/credentials - List all passkeys for the authenticated user</li>
 * <li>GET /user/webauthn/has-credentials - Check if user has any passkeys</li>
 * <li>PUT /user/webauthn/credentials/{id}/label - Rename a passkey</li>
 * <li>DELETE /user/webauthn/credentials/{id} - Delete a passkey</li>
 * <li>DELETE /user/webauthn/password - Remove the account password (passkey-only)</li>
 * </ul>
 *
 * <p>
 * <strong>Re-authentication for credential-altering operations:</strong> Removing the password and deleting/renaming a
 * passkey change the account's authentication methods. When the account has a password set, these operations require the
 * caller to supply the current password ({@code currentPassword}), which is verified before any mutation. This prevents a
 * session-only actor (e.g. via a hijacked or unattended session) from silently altering how the account authenticates.
 * </p>
 *
 * <p>
 * <strong>Residual risk (passwordless accounts):</strong> For passwordless (passkey-only) accounts there is no current
 * password to verify, and this library does not yet implement a WebAuthn step-up assertion. Passkey delete/rename on such
 * accounts therefore remain session-only operations. Last-credential protection still prevents lockout, and ownership
 * (IDOR) checks remain enforced in the service layer. See MIGRATION.md for details and guidance.
 * </p>
 */
@Slf4j
@RestController("dsWebAuthnManagementAPI")
@RequestMapping("/user/webauthn")
@ConditionalOnProperty(name = "user.webauthn.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Validated
public class WebAuthnManagementAPI {

	private final WebAuthnCredentialManagementService credentialManagementService;
	private final UserService userService;
	private final ApplicationEventPublisher eventPublisher;
	private final LoginAttemptService loginAttemptService;

	/**
	 * Get user's registered passkeys.
	 *
	 * @param userDetails the authenticated user details
	 * @return ResponseEntity containing list of credential information
	 */
	@GetMapping("/credentials")
	public ResponseEntity<List<WebAuthnCredentialInfo>> getCredentials(@AuthenticationPrincipal UserDetails userDetails) {
		User user = findAuthenticatedUser(userDetails);
		List<WebAuthnCredentialInfo> credentials = credentialManagementService.getUserCredentials(user);
		return ResponseEntity.ok(credentials);
	}

	/**
	 * Check if user has any passkeys.
	 *
	 * @param userDetails the authenticated user details
	 * @return ResponseEntity containing true if user has passkeys, false otherwise
	 */
	@GetMapping("/has-credentials")
	public ResponseEntity<Boolean> hasCredentials(@AuthenticationPrincipal UserDetails userDetails) {
		User user = findAuthenticatedUser(userDetails);
		boolean hasCredentials = credentialManagementService.hasCredentials(user);
		return ResponseEntity.ok(hasCredentials);
	}

	/**
	 * Rename a passkey.
	 *
	 * <p>
	 * Updates the user-friendly label for a passkey. The label helps users identify their passkeys (e.g., "My iPhone", "Work Laptop").
	 * </p>
	 *
	 * <p>
	 * The label must be non-empty and no more than 64 characters.
	 * </p>
	 *
	 * @param id the credential ID to rename
	 * @param request the rename request containing the new label
	 * @param userDetails the authenticated user details
	 * @return ResponseEntity with success message or error
	 */
	@PutMapping("/credentials/{id}/label")
	public ResponseEntity<GenericResponse> renameCredential(@PathVariable @NotBlank @Size(max = 512) String id,
			@RequestBody @Valid RenameCredentialRequest request,
			@AuthenticationPrincipal UserDetails userDetails) {
		User user = findAuthenticatedUser(userDetails);
		requireCurrentPasswordIfSet(user, request.currentPassword());
		credentialManagementService.renameCredential(id, request.label(), user);
		return ResponseEntity.ok(new GenericResponse("Passkey renamed successfully"));
	}

	/**
	 * Delete a passkey.
	 *
	 * <p>
	 * Deletes a passkey. Includes last-credential protection to prevent users from being
	 * locked out of their accounts.
	 * </p>
	 *
	 * <p>
	 * If this is the user's last passkey and they have no password, the deletion will be blocked with an error message.
	 * </p>
	 *
	 * <p>
	 * <strong>Re-authentication:</strong> If the account has a password set, the request body must include the current
	 * {@code currentPassword}, which is verified before the passkey is deleted. This prevents a session-only actor from
	 * altering the account's authentication methods. For passwordless (passkey-only) accounts there is no current
	 * credential to verify; see the class-level note on the residual risk for passwordless credential changes.
	 * </p>
	 *
	 * @param id the credential ID to delete
	 * @param request the (optional) request body carrying the current password; may be {@code null} for passwordless accounts
	 * @param userDetails the authenticated user details
	 * @return ResponseEntity with success message or error
	 */
	@DeleteMapping("/credentials/{id}")
	public ResponseEntity<GenericResponse> deleteCredential(@PathVariable @NotBlank @Size(max = 512) String id,
			@RequestBody(required = false) CurrentPasswordRequest request,
			@AuthenticationPrincipal UserDetails userDetails) {
		User user = findAuthenticatedUser(userDetails);
		requireCurrentPasswordIfSet(user, request != null ? request.currentPassword() : null);
		credentialManagementService.deleteCredential(id, user);
		return ResponseEntity.ok(new GenericResponse("Passkey deleted successfully"));
	}

	/**
	 * Remove the user's password, making the account passwordless (passkey-only).
	 *
	 * <p>
	 * Requires the user to have at least one passkey registered. This ensures the user can still authenticate after the
	 * password is removed.
	 * </p>
	 *
	 * <p>
	 * <strong>Re-authentication:</strong> Because the account by definition has a password (it is being removed), the
	 * caller must supply {@code currentPassword} in the request body. The body is declared {@code required = false} so
	 * that a missing body does not produce a generic 415/400 from the message converter; instead, a missing or empty body
	 * is treated identically to a missing {@code currentPassword} field — both are routed through
	 * {@link #requireCurrentPasswordIfSet} and result in HTTP 400 with the message
	 * {@code "Current password is required to change authentication methods."}. A blank or incorrect
	 * {@code currentPassword} is likewise rejected with a 400 before any mutation occurs.
	 * </p>
	 *
	 * @param body the request body carrying the current password; technically optional at the HTTP layer so that a
	 *        missing body produces a clear 400 rather than a framework-level error, but functionally required
	 * @param userDetails the authenticated user details
	 * @param request the HTTP servlet request
	 * @return ResponseEntity with success message or error
	 */
	@DeleteMapping("/password")
	public ResponseEntity<GenericResponse> removePassword(@RequestBody(required = false) CurrentPasswordRequest body,
			@AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request) {
		User user = findAuthenticatedUser(userDetails);

		if (!userService.hasPassword(user)) {
			throw new WebAuthnException("User does not have a password to remove");
		}

		requireCurrentPasswordIfSet(user, body != null ? body.currentPassword() : null);

		if (!credentialManagementService.hasCredentials(user)) {
			throw new WebAuthnException("Cannot remove password. Please register a passkey first.");
		}

		userService.removeUserPassword(user);

		AuditEvent event = AuditEvent.builder().source(this).user(user).sessionId(request.getSession().getId())
				.ipAddress(UserUtils.getClientIP(request))
				.userAgent(request.getHeader("User-Agent")).action("PasswordRemoval").actionStatus("Success")
				.message("Password removed for passwordless account").build();
		eventPublisher.publishEvent(event);

		log.info("User {} removed their password", user.getEmail());

		return ResponseEntity.ok(new GenericResponse("Password removed successfully"));
	}

	private User findAuthenticatedUser(UserDetails userDetails) throws WebAuthnUserNotFoundException {
		User user = userService.findUserByEmail(userDetails.getUsername());
		if (user == null) {
			throw new WebAuthnUserNotFoundException("User not found");
		}
		return user;
	}

	/**
	 * Requires and verifies the current password for a credential-altering operation when the account has a password set.
	 *
	 * <p>
	 * If the account has a password, the supplied {@code currentPassword} must be present and valid (verified via
	 * {@link UserService#checkIfValidOldPassword(User, String)}); otherwise a {@link WebAuthnException} (HTTP 400) is
	 * thrown <em>before</em> any mutation, preventing a session-only actor from changing the account's authentication
	 * methods. If the account is passwordless (passkey-only) there is no current credential to verify, so this check is
	 * a no-op — see the residual-risk note in MIGRATION.md.
	 * </p>
	 *
	 * <p>
	 * Because this verifies the current password, it is an authentication surface and participates in the same
	 * brute-force lockout as the login path: a locked account is rejected up front, each wrong password is reported to
	 * {@link LoginAttemptService#loginFailed(String)} (which locks the account once the threshold is reached), and a
	 * correct password resets the failed-attempt counter via {@link LoginAttemptService#loginSucceeded(String)}. This
	 * stops a session-holding actor from making unlimited password guesses here.
	 * </p>
	 *
	 * @param user the authenticated user
	 * @param currentPassword the current password supplied by the client (may be {@code null})
	 * @throws WebAuthnException if the account is locked, or has a password and the supplied current password is missing or incorrect
	 */
	private void requireCurrentPasswordIfSet(User user, String currentPassword) {
		if (!userService.hasPassword(user)) {
			// Passwordless (passkey-only) account: no current credential exists to verify. See MIGRATION.md residual-risk note.
			return;
		}
		if (loginAttemptService.isLocked(user.getEmail())) {
			throw new WebAuthnException("Account is locked due to too many failed attempts. Please try again later.");
		}
		if (currentPassword == null || currentPassword.isBlank()) {
			// A missing field is a client error, not a password guess, so it does not count toward lockout.
			throw new WebAuthnException("Current password is required to change authentication methods.");
		}
		if (!userService.checkIfValidOldPassword(user, currentPassword)) {
			loginAttemptService.loginFailed(user.getEmail());
			throw new WebAuthnException("Current password is incorrect.");
		}
		// Successful re-authentication clears the failed-attempt counter, matching login semantics.
		loginAttemptService.loginSucceeded(user.getEmail());
	}

	/**
	 * Request DTO for renaming credential.
	 *
	 * @param label the new label (must not be blank, max 64 chars)
	 * @param currentPassword the current account password, required when the account has a password set (re-authentication)
	 */
	public record RenameCredentialRequest(@NotBlank @Size(max = 64) String label, String currentPassword) {
	}

	/**
	 * Request DTO carrying the current account password for credential-altering operations that re-authenticate the user.
	 *
	 * @param currentPassword the current account password, required when the account has a password set
	 */
	public record CurrentPasswordRequest(String currentPassword) {
	}
}
