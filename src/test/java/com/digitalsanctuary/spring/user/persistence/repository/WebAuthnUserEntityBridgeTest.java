package com.digitalsanctuary.spring.user.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
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
	private PublicKeyCredentialUserEntity existingEntity;

	private WebAuthnUserEntityBridge bridge;

	private User testUser;

	@BeforeEach
	void setUp() {
		testUser = TestFixtures.Users.standardUser();
		bridge = new WebAuthnUserEntityBridge(jdbcTemplate, userRepository);
	}

	@Nested
	@DisplayName("Find By Username")
	class FindByUsernameTests {

		@Test
		@DisplayName("should return null for null username")
		void shouldReturnNullForNull() {
			// When
			PublicKeyCredentialUserEntity result = bridge.findByUsername(null);

			// Then
			assertThat(result).isNull();
		}

		@Test
		@DisplayName("should return null for empty username")
		void shouldReturnNullForEmpty() {
			// When
			PublicKeyCredentialUserEntity result = bridge.findByUsername("");

			// Then
			assertThat(result).isNull();
		}

		@Test
		@DisplayName("should return null for anonymousUser")
		void shouldReturnNullForAnonymousUser() {
			// When
			PublicKeyCredentialUserEntity result = bridge.findByUsername("anonymousUser");

			// Then
			assertThat(result).isNull();
		}

		@Test
		@DisplayName("should return empty when no application user found")
		void shouldReturnEmptyWhenNoUser() {
			// Given
			when(userRepository.findByEmail("unknown@test.com")).thenReturn(null);

			// When
			Optional<PublicKeyCredentialUserEntity> result = bridge.findOptionalByUsername("unknown@test.com");

			// Then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("Create User Entity")
	class CreateUserEntityTests {

		@Test
		@DisplayName("should create entity with correct name and display name")
		void shouldCreateEntityWithCorrectFields() {
			// When
			PublicKeyCredentialUserEntity entity = bridge.createUserEntity(testUser);

			// Then
			assertThat(entity.getName()).isEqualTo(testUser.getEmail());
			assertThat(entity.getDisplayName()).isEqualTo(testUser.getFullName());
			assertThat(entity.getId()).isNotNull();
		}
	}
}
