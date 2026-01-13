package com.digitalsanctuary.spring.user.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.digitalsanctuary.spring.user.dto.WebAuthnCredentialInfo;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.service.WebAuthnCredentialManagementService;
import com.digitalsanctuary.spring.user.util.GenericResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * </ul>
 */
@RestController
@RequestMapping("/user/webauthn")
@RequiredArgsConstructor
@Slf4j
public class WebAuthnManagementAPI {

	private final WebAuthnCredentialManagementService credentialManagementService;
	private final UserService userService;

	/**
	 * Get user's registered passkeys.
	 *
	 * @param userDetails the authenticated user details
	 * @return ResponseEntity containing list of credential information
	 */
	@GetMapping("/credentials")
	public ResponseEntity<List<WebAuthnCredentialInfo>> getCredentials(@AuthenticationPrincipal UserDetails userDetails) {

		User user = userService.findUserByEmail(userDetails.getUsername());
		if (user == null) {
			log.error("User not found: {}", userDetails.getUsername());
			throw new RuntimeException("User not found");
		}

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

		User user = userService.findUserByEmail(userDetails.getUsername());
		if (user == null) {
			log.error("User not found: {}", userDetails.getUsername());
			throw new RuntimeException("User not found");
		}

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
	 * The label must be non-empty and no more than 255 characters.
	 * </p>
	 *
	 * @param id the credential ID to rename
	 * @param request the rename request containing the new label
	 * @param userDetails the authenticated user details
	 * @return ResponseEntity with success message or error
	 */
	@PutMapping("/credentials/{id}/label")
	public ResponseEntity<GenericResponse> renameCredential(@PathVariable String id, @RequestBody @Valid RenameCredentialRequest request,
			@AuthenticationPrincipal UserDetails userDetails) {

		try {
			User user = userService.findUserByEmail(userDetails.getUsername());
			if (user == null) {
				throw new WebAuthnException("User not found");
			}

			credentialManagementService.renameCredential(id, request.label(), user);

			return ResponseEntity.ok(new GenericResponse("Passkey renamed successfully"));

		} catch (WebAuthnException e) {
			log.error("Failed to rename credential: {}", e.getMessage());
			return ResponseEntity.badRequest().body(new GenericResponse(e.getMessage()));
		}
	}

	/**
	 * Delete a passkey.
	 *
	 * <p>
	 * Soft-deletes a passkey by marking it as disabled. Includes last-credential protection to prevent users from being 
	 * locked out of their accounts.
	 * </p>
	 *
	 * <p>
	 * If this is the user's last passkey and they have no password, the deletion will be blocked with an error message.
	 * </p>
	 *
	 * @param id the credential ID to delete
	 * @param userDetails the authenticated user details
	 * @return ResponseEntity with success message or error
	 */
	@DeleteMapping("/credentials/{id}")
	public ResponseEntity<GenericResponse> deleteCredential(@PathVariable String id, @AuthenticationPrincipal UserDetails userDetails) {

		try {
			User user = userService.findUserByEmail(userDetails.getUsername());
			if (user == null) {
				throw new WebAuthnException("User not found");
			}

			credentialManagementService.deleteCredential(id, user);

			return ResponseEntity.ok(new GenericResponse("Passkey deleted successfully"));

		} catch (WebAuthnException e) {
			log.error("Failed to delete credential: {}", e.getMessage());
			return ResponseEntity.badRequest().body(new GenericResponse(e.getMessage()));
		}
	}

	/**
	 * Request DTO for renaming credential.
	 *
	 * @param label the new label (must not be blank)
	 */
	public record RenameCredentialRequest(@NotBlank String label) {
	}
}
