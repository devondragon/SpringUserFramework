package com.digitalsanctuary.spring.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import com.digitalsanctuary.spring.user.dto.WebAuthnCredentialInfo;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnException;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnUserNotFoundException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.service.WebAuthnCredentialManagementService;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.fixtures.TestFixtures;
import com.digitalsanctuary.spring.user.util.GenericResponse;

@ServiceTest
@DisplayName("WebAuthnManagementAPI Tests")
class WebAuthnManagementAPITest {

	@Mock
	private WebAuthnCredentialManagementService credentialManagementService;

	@Mock
	private UserService userService;

	@Mock
	private UserDetails userDetails;

	@InjectMocks
	private WebAuthnManagementAPI api;

	private User testUser;

	@BeforeEach
	void setUp() {
		testUser = TestFixtures.Users.standardUser();
		when(userDetails.getUsername()).thenReturn(testUser.getEmail());
		when(userService.findUserByEmail(testUser.getEmail())).thenReturn(testUser);
	}

	@Nested
	@DisplayName("GET /user/webauthn/credentials")
	class GetCredentialsTests {

		@Test
		@DisplayName("should return credentials for authenticated user")
		void shouldReturnCredentials() {
			// Given
			WebAuthnCredentialInfo cred = WebAuthnCredentialInfo.builder().id("cred-1").label("My iPhone").created(Instant.now())
					.build();

			when(credentialManagementService.getUserCredentials(testUser)).thenReturn(List.of(cred));

			// When
			ResponseEntity<List<WebAuthnCredentialInfo>> response = api.getCredentials(userDetails);

			// Then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).hasSize(1);
			assertThat(response.getBody().get(0).getLabel()).isEqualTo("My iPhone");
		}

		@Test
		@DisplayName("should return empty list when no credentials")
		void shouldReturnEmptyList() {
			// Given
			when(credentialManagementService.getUserCredentials(testUser)).thenReturn(Collections.emptyList());

			// When
			ResponseEntity<List<WebAuthnCredentialInfo>> response = api.getCredentials(userDetails);

			// Then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isEmpty();
		}

		@Test
		@DisplayName("should throw not found when user not found")
		void shouldThrowNotFoundWhenUserNotFound() {
			// Given
			when(userService.findUserByEmail(testUser.getEmail())).thenReturn(null);

			// When
			assertThatThrownBy(() -> api.getCredentials(userDetails)).isInstanceOf(WebAuthnUserNotFoundException.class)
					.hasMessageContaining("User not found");
		}
	}

	@Nested
	@DisplayName("GET /user/webauthn/has-credentials")
	class HasCredentialsTests {

		@Test
		@DisplayName("should return true when user has credentials")
		void shouldReturnTrue() {
			// Given
			when(credentialManagementService.hasCredentials(testUser)).thenReturn(true);

			// When
			ResponseEntity<Boolean> response = api.hasCredentials(userDetails);

			// Then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isTrue();
		}

		@Test
		@DisplayName("should return false when user has no credentials")
		void shouldReturnFalse() {
			// Given
			when(credentialManagementService.hasCredentials(testUser)).thenReturn(false);

			// When
			ResponseEntity<Boolean> response = api.hasCredentials(userDetails);

			// Then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody()).isFalse();
		}

		@Test
		@DisplayName("should throw not found when user not found")
		void shouldThrowNotFoundWhenUserNotFound() {
			// Given
			when(userService.findUserByEmail(testUser.getEmail())).thenReturn(null);

			// When
			assertThatThrownBy(() -> api.hasCredentials(userDetails)).isInstanceOf(WebAuthnUserNotFoundException.class)
					.hasMessageContaining("User not found");
			verify(credentialManagementService, never()).hasCredentials(any());
		}
	}

	@Nested
	@DisplayName("PUT /user/webauthn/credentials/{id}/label")
	class RenameCredentialTests {

		@Test
		@DisplayName("should rename credential successfully")
		void shouldRenameSuccessfully() {
			// Given
			WebAuthnManagementAPI.RenameCredentialRequest request = new WebAuthnManagementAPI.RenameCredentialRequest("Work Laptop");

			// When
			ResponseEntity<GenericResponse> response = api.renameCredential("cred-1", request, userDetails);

			// Then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody().getMessage()).contains("renamed successfully");
			verify(credentialManagementService).renameCredential("cred-1", "Work Laptop", testUser);
		}

		@Test
		@DisplayName("should throw when rename fails")
		void shouldThrowOnFailure() {
			// Given
			WebAuthnManagementAPI.RenameCredentialRequest request = new WebAuthnManagementAPI.RenameCredentialRequest("New Name");
			doThrow(new WebAuthnException("Credential not found or access denied")).when(credentialManagementService)
					.renameCredential(eq("cred-999"), eq("New Name"), any(User.class));

			// When
			assertThatThrownBy(() -> api.renameCredential("cred-999", request, userDetails)).isInstanceOf(WebAuthnException.class)
					.hasMessageContaining("not found");
		}

		@Test
		@DisplayName("should throw not found when user not found")
		void shouldThrowNotFoundWhenUserNotFound() {
			// Given
			WebAuthnManagementAPI.RenameCredentialRequest request = new WebAuthnManagementAPI.RenameCredentialRequest("New Name");
			when(userService.findUserByEmail(testUser.getEmail())).thenReturn(null);

			// When
			assertThatThrownBy(() -> api.renameCredential("cred-1", request, userDetails))
					.isInstanceOf(WebAuthnUserNotFoundException.class).hasMessageContaining("User not found");
			verify(credentialManagementService, never()).renameCredential(any(), any(), any());
		}
	}

	@Nested
	@DisplayName("DELETE /user/webauthn/credentials/{id}")
	class DeleteCredentialTests {

		@Test
		@DisplayName("should delete credential successfully")
		void shouldDeleteSuccessfully() {
			// When
			ResponseEntity<GenericResponse> response = api.deleteCredential("cred-1", userDetails);

			// Then
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(response.getBody().getMessage()).contains("deleted successfully");
			verify(credentialManagementService).deleteCredential("cred-1", testUser);
		}

		@Test
		@DisplayName("should throw when delete fails")
		void shouldThrowOnFailure() {
			// Given
			doThrow(new WebAuthnException("Cannot delete last passkey")).when(credentialManagementService).deleteCredential(eq("cred-1"),
					any(User.class));

			// When
			assertThatThrownBy(() -> api.deleteCredential("cred-1", userDetails)).isInstanceOf(WebAuthnException.class)
					.hasMessageContaining("Cannot delete last passkey");
		}

		@Test
		@DisplayName("should throw not found when user not found")
		void shouldThrowNotFoundWhenUserNotFound() {
			// Given
			when(userService.findUserByEmail(testUser.getEmail())).thenReturn(null);

			// When
			assertThatThrownBy(() -> api.deleteCredential("cred-1", userDetails))
					.isInstanceOf(WebAuthnUserNotFoundException.class).hasMessageContaining("User not found");
			verify(credentialManagementService, never()).deleteCredential(any(), any());
		}
	}
}
