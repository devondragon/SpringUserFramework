package com.digitalsanctuary.spring.user.listener;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.digitalsanctuary.spring.user.event.UserPreDeleteEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnUserEntity;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnCredentialRepository;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnUserEntityRepository;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.fixtures.TestFixtures;

@ServiceTest
@DisplayName("WebAuthnPreDeleteEventListener Tests")
class WebAuthnPreDeleteEventListenerTest {

	@Mock
	private WebAuthnCredentialRepository credentialRepository;

	@Mock
	private WebAuthnUserEntityRepository userEntityRepository;

	@InjectMocks
	private WebAuthnPreDeleteEventListener listener;

	private User testUser;

	@BeforeEach
	void setUp() {
		testUser = TestFixtures.Users.standardUser();
	}

	@Nested
	@DisplayName("User Pre-Delete Handling")
	class UserPreDeleteTests {

		@Test
		@DisplayName("should delete credentials and user entity when user has WebAuthn data")
		void shouldDeleteWebAuthnDataForUser() {
			// Given
			WebAuthnUserEntity userEntity = new WebAuthnUserEntity();
			userEntity.setId("entity-id");
			userEntity.setName(testUser.getEmail());
			userEntity.setUser(testUser);

			when(userEntityRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(userEntity));

			UserPreDeleteEvent event = new UserPreDeleteEvent(this, testUser);

			// When
			listener.onUserPreDelete(event);

			// Then
			verify(credentialRepository).deleteByUserEntity(userEntity);
			verify(userEntityRepository).delete(userEntity);
		}

		@Test
		@DisplayName("should do nothing when user has no WebAuthn data")
		void shouldDoNothingWhenNoWebAuthnData() {
			// Given
			when(userEntityRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());

			UserPreDeleteEvent event = new UserPreDeleteEvent(this, testUser);

			// When
			listener.onUserPreDelete(event);

			// Then
			verify(userEntityRepository, never()).delete(org.mockito.ArgumentMatchers.any(WebAuthnUserEntity.class));
		}
	}
}
