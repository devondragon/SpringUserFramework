package com.digitalsanctuary.spring.user.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.fixtures.TestFixtures;

@ServiceTest
@DisplayName("WebAuthnUserEntityBridge Tests")
class WebAuthnUserEntityBridgeTest {

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Mock
	private UserRepository userRepository;

	@Mock
	private PublicKeyCredentialUserEntityRepository baseRepository;

	@Mock
	private PublicKeyCredentialUserEntity existingEntity;

	@InjectMocks
	private WebAuthnUserEntityBridge bridge;

	private User testUser;

	@BeforeEach
	void setUp() {
		testUser = TestFixtures.Users.standardUser();
	}

	@Nested
	@DisplayName("Find By Username")
	class FindByUsernameTests {

		@Test
		@DisplayName("should return empty for null username")
		void shouldReturnEmptyForNull() {
			// When
			Optional<PublicKeyCredentialUserEntity> result = bridge.findByUsername(null);

			// Then
			assertThat(result).isEmpty();
			verify(baseRepository, never()).findByUsername(anyString());
		}

		@Test
		@DisplayName("should return empty for empty username")
		void shouldReturnEmptyForEmpty() {
			// When
			Optional<PublicKeyCredentialUserEntity> result = bridge.findByUsername("");

			// Then
			assertThat(result).isEmpty();
			verify(baseRepository, never()).findByUsername(anyString());
		}

		@Test
		@DisplayName("should return empty for anonymousUser")
		void shouldReturnEmptyForAnonymousUser() {
			// When
			Optional<PublicKeyCredentialUserEntity> result = bridge.findByUsername("anonymousUser");

			// Then
			assertThat(result).isEmpty();
			verify(baseRepository, never()).findByUsername(anyString());
		}

		@Test
		@DisplayName("should return existing entity from base repository")
		void shouldReturnExistingEntity() {
			// Given
			when(baseRepository.findByUsername(testUser.getEmail())).thenReturn(existingEntity);

			// When
			Optional<PublicKeyCredentialUserEntity> result = bridge.findByUsername(testUser.getEmail());

			// Then
			assertThat(result).isPresent();
			assertThat(result.get()).isEqualTo(existingEntity);
		}

		@Test
		@DisplayName("should return empty when no entity and no application user")
		void shouldReturnEmptyWhenNoEntityAndNoUser() {
			// Given
			when(baseRepository.findByUsername("unknown@test.com")).thenReturn(null);
			when(userRepository.findByEmail("unknown@test.com")).thenReturn(null);

			// When
			Optional<PublicKeyCredentialUserEntity> result = bridge.findByUsername("unknown@test.com");

			// Then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("should create entity for existing application user")
		void shouldCreateEntityForExistingUser() {
			// Given
			when(baseRepository.findByUsername(testUser.getEmail())).thenReturn(null);
			when(userRepository.findByEmail(testUser.getEmail())).thenReturn(testUser);
			when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);

			// When
			Optional<PublicKeyCredentialUserEntity> result = bridge.findByUsername(testUser.getEmail());

			// Then
			assertThat(result).isPresent();
			verify(baseRepository).save(any(PublicKeyCredentialUserEntity.class));
		}
	}

	@Nested
	@DisplayName("Create User Entity")
	class CreateUserEntityTests {

		@Test
		@DisplayName("should create entity with correct name and display name")
		void shouldCreateEntityWithCorrectFields() {
			// Given
			when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);

			// When
			PublicKeyCredentialUserEntity entity = bridge.createUserEntity(testUser);

			// Then
			assertThat(entity.getName()).isEqualTo(testUser.getEmail());
			assertThat(entity.getDisplayName()).isEqualTo(testUser.getFullName());
			assertThat(entity.getId()).isNotNull();
		}

		@Test
		@DisplayName("should persist entity with user_account_id link")
		void shouldPersistWithUserAccountId() {
			// Given
			when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);

			// When
			bridge.createUserEntity(testUser);

			// Then
			ArgumentCaptor<Object> argsCaptor = ArgumentCaptor.forClass(Object.class);
			verify(jdbcTemplate).update(anyString(), argsCaptor.capture(), argsCaptor.capture(), argsCaptor.capture(), argsCaptor.capture());

			// The 4th argument should be the user's ID (user_account_id)
			assertThat(argsCaptor.getAllValues().get(3)).isEqualTo(testUser.getId());
		}
	}
}
