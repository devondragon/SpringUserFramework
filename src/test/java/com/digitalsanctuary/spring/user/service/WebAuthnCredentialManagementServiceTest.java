package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.digitalsanctuary.spring.user.dto.WebAuthnCredentialInfo;
import com.digitalsanctuary.spring.user.exceptions.WebAuthnException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnCredentialQueryRepository;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.fixtures.TestFixtures;

@ServiceTest
@DisplayName("WebAuthnCredentialManagementService Tests")
class WebAuthnCredentialManagementServiceTest {

	@Mock
	private WebAuthnCredentialQueryRepository credentialQueryRepository;

	@InjectMocks
	private WebAuthnCredentialManagementService service;

	private User testUser;

	@BeforeEach
	void setUp() {
		testUser = TestFixtures.Users.standardUser();
	}

	@Nested
	@DisplayName("Get User Credentials")
	class GetUserCredentialsTests {

		@Test
		@DisplayName("should return credentials for user")
		void shouldReturnCredentialsForUser() {
			// Given
			WebAuthnCredentialInfo cred = WebAuthnCredentialInfo.builder().id("cred-123").label("My iPhone")
					.created(Instant.now()).build();

			when(credentialQueryRepository.findCredentialsByUserId(testUser.getId())).thenReturn(List.of(cred));

			// When
			List<WebAuthnCredentialInfo> credentials = service.getUserCredentials(testUser);

			// Then
			assertThat(credentials).hasSize(1);
			assertThat(credentials.get(0).getLabel()).isEqualTo("My iPhone");
			assertThat(credentials.get(0).getId()).isEqualTo("cred-123");
			verify(credentialQueryRepository).findCredentialsByUserId(testUser.getId());
		}

		@Test
		@DisplayName("should return empty list when user has no credentials")
		void shouldReturnEmptyListWhenNoCredentials() {
			// Given
			when(credentialQueryRepository.findCredentialsByUserId(testUser.getId())).thenReturn(Collections.emptyList());

			// When
			List<WebAuthnCredentialInfo> credentials = service.getUserCredentials(testUser);

			// Then
			assertThat(credentials).isEmpty();
		}
	}

	@Nested
	@DisplayName("Has Credentials")
	class HasCredentialsTests {

		@Test
		@DisplayName("should return true when user has credentials")
		void shouldReturnTrueWhenHasCredentials() {
			// Given
			when(credentialQueryRepository.hasCredentials(testUser.getId())).thenReturn(true);

			// When
			boolean result = service.hasCredentials(testUser);

			// Then
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("should return false when user has no credentials")
		void shouldReturnFalseWhenNoCredentials() {
			// Given
			when(credentialQueryRepository.hasCredentials(testUser.getId())).thenReturn(false);

			// When
			boolean result = service.hasCredentials(testUser);

			// Then
			assertThat(result).isFalse();
		}
	}

	@Nested
	@DisplayName("Rename Credential")
	class RenameCredentialTests {

		@Test
		@DisplayName("should rename credential successfully")
		void shouldRenameCredentialSuccessfully() throws WebAuthnException {
			// Given
			when(credentialQueryRepository.renameCredential("cred-123", "Work Laptop", testUser.getId())).thenReturn(1);

			// When
			service.renameCredential("cred-123", "Work Laptop", testUser);

			// Then
			verify(credentialQueryRepository).renameCredential("cred-123", "Work Laptop", testUser.getId());
		}

		@Test
		@DisplayName("should throw when credential not found")
		void shouldThrowWhenCredentialNotFound() {
			// Given
			when(credentialQueryRepository.renameCredential("cred-999", "New Name", testUser.getId())).thenReturn(0);

			// When/Then
			assertThatThrownBy(() -> service.renameCredential("cred-999", "New Name", testUser)).isInstanceOf(WebAuthnException.class)
					.hasMessageContaining("not found");
		}

		@Test
		@DisplayName("should throw when label is null")
		void shouldThrowWhenLabelIsNull() {
			// When/Then
			assertThatThrownBy(() -> service.renameCredential("cred-123", null, testUser)).isInstanceOf(WebAuthnException.class)
					.hasMessageContaining("cannot be empty");

			verify(credentialQueryRepository, never()).renameCredential(anyString(), anyString(), anyLong());
		}

		@Test
		@DisplayName("should throw when label is empty")
		void shouldThrowWhenLabelIsEmpty() {
			// When/Then
			assertThatThrownBy(() -> service.renameCredential("cred-123", "", testUser)).isInstanceOf(WebAuthnException.class)
					.hasMessageContaining("cannot be empty");

			verify(credentialQueryRepository, never()).renameCredential(anyString(), anyString(), anyLong());
		}

		@Test
		@DisplayName("should throw when label is blank")
		void shouldThrowWhenLabelIsBlank() {
			// When/Then
			assertThatThrownBy(() -> service.renameCredential("cred-123", "   ", testUser)).isInstanceOf(WebAuthnException.class)
					.hasMessageContaining("cannot be empty");
		}

		@Test
		@DisplayName("should throw when label exceeds 64 characters")
		void shouldThrowWhenLabelTooLong() {
			// Given
			String longLabel = "a".repeat(65);

			// When/Then
			assertThatThrownBy(() -> service.renameCredential("cred-123", longLabel, testUser)).isInstanceOf(WebAuthnException.class)
					.hasMessageContaining("too long");

			verify(credentialQueryRepository, never()).renameCredential(anyString(), anyString(), anyLong());
		}
	}

	@Nested
	@DisplayName("Delete Credential")
	class DeleteCredentialTests {

			@Test
			@DisplayName("should delete credential when user has multiple passkeys")
			void shouldDeleteWhenMultiplePasskeys() throws WebAuthnException {
				// Given
				when(credentialQueryRepository.lockAndCountCredentials(testUser.getId())).thenReturn(2L);
				when(credentialQueryRepository.deleteCredential("cred-123", testUser.getId())).thenReturn(1);

			// When
			service.deleteCredential("cred-123", testUser);

			// Then
			verify(credentialQueryRepository).deleteCredential("cred-123", testUser.getId());
		}

			@Test
			@DisplayName("should delete last credential when user has a password")
			void shouldDeleteLastCredentialWhenUserHasPassword() throws WebAuthnException {
				// Given - user has password set (from TestFixtures)
				when(credentialQueryRepository.lockAndCountCredentials(testUser.getId())).thenReturn(1L);
				when(credentialQueryRepository.deleteCredential("cred-123", testUser.getId())).thenReturn(1);

			// When
			service.deleteCredential("cred-123", testUser);

			// Then
			verify(credentialQueryRepository).deleteCredential("cred-123", testUser.getId());
		}

			@Test
			@DisplayName("should block deletion of last passkey when user has no password")
			void shouldBlockDeletionOfLastPasskeyWithoutPassword() {
				// Given
				testUser.setPassword(null);
				when(credentialQueryRepository.lockAndCountCredentials(testUser.getId())).thenReturn(1L);

			// When/Then
			assertThatThrownBy(() -> service.deleteCredential("cred-123", testUser)).isInstanceOf(WebAuthnException.class)
					.hasMessageContaining("Cannot delete last passkey");

			verify(credentialQueryRepository, never()).deleteCredential(anyString(), anyLong());
		}

			@Test
			@DisplayName("should block deletion of last passkey when user has empty password")
			void shouldBlockDeletionOfLastPasskeyWithEmptyPassword() {
				// Given
				testUser.setPassword("");
				when(credentialQueryRepository.lockAndCountCredentials(testUser.getId())).thenReturn(1L);

			// When/Then
			assertThatThrownBy(() -> service.deleteCredential("cred-123", testUser)).isInstanceOf(WebAuthnException.class)
					.hasMessageContaining("Cannot delete last passkey");

			verify(credentialQueryRepository, never()).deleteCredential(anyString(), anyLong());
		}

			@Test
			@DisplayName("should throw when credential not found")
			void shouldThrowWhenCredentialNotFound() {
				// Given
				when(credentialQueryRepository.lockAndCountCredentials(testUser.getId())).thenReturn(2L);
				when(credentialQueryRepository.deleteCredential("cred-999", testUser.getId())).thenReturn(0);

			// When/Then
			assertThatThrownBy(() -> service.deleteCredential("cred-999", testUser)).isInstanceOf(WebAuthnException.class)
					.hasMessageContaining("not found");
		}
	}
}
