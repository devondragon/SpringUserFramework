package com.digitalsanctuary.spring.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnCredential;
import com.digitalsanctuary.spring.user.persistence.model.WebAuthnUserEntity;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnCredentialRepository;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnUserEntityRepository;
import com.digitalsanctuary.spring.user.service.WebAuthnCredentialManagementService;
import com.digitalsanctuary.spring.user.test.annotations.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = "user.webauthn.enabled=true")
@DisplayName("WebAuthn Enabled Integration Tests")
class WebAuthnFeatureEnabledIntegrationTest {

	private static final String TEST_EMAIL = "webauthn-user@test.com";

	private static final String TEST_PASSWORD = "currentPassword1!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private WebAuthnUserEntityRepository webAuthnUserEntityRepository;

	@Autowired
	private WebAuthnCredentialRepository webAuthnCredentialRepository;

	@Autowired
	private RequestMappingHandlerMapping requestMappingHandlerMapping;

	@Autowired
	private WebAuthnCredentialManagementService credentialManagementService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		webAuthnCredentialRepository.deleteAll();
		webAuthnUserEntityRepository.deleteAll();
		userRepository.deleteAll();

		User user = new User();
		user.setEmail(TEST_EMAIL);
		user.setFirstName("Web");
		user.setLastName("Authn");
		user.setEnabled(true);
		user.setPassword(passwordEncoder.encode(TEST_PASSWORD));
		User savedUser = userRepository.saveAndFlush(user);

		WebAuthnUserEntity userEntity = new WebAuthnUserEntity();
		userEntity.setId("d2ViYXV0aG4tdXNlcg");
		userEntity.setName(TEST_EMAIL);
		userEntity.setDisplayName("Web Authn");
		userEntity.setUser(savedUser);
		WebAuthnUserEntity savedUserEntity = webAuthnUserEntityRepository.saveAndFlush(userEntity);

		WebAuthnCredential credential = new WebAuthnCredential();
		credential.setCredentialId("cred-1");
		credential.setUserEntity(savedUserEntity);
		credential.setPublicKey(new byte[] {1, 2, 3});
		credential.setSignatureCount(0L);
		credential.setUvInitialized(true);
		credential.setBackupEligible(true);
		credential.setBackupState(false);
		credential.setAuthenticatorTransports("internal");
		credential.setPublicKeyCredentialType("public-key");
		credential.setCreated(Instant.now());
		credential.setLabel("My Device");
		webAuthnCredentialRepository.saveAndFlush(credential);
	}

	@Test
	@DisplayName("should register WebAuthn beans and endpoint mappings when enabled")
	void shouldRegisterWebAuthnBeansAndMappingsWhenEnabled() {
		assertThat(credentialManagementService).isNotNull();

		Set<String> mappedPaths = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
				.flatMap(info -> info.getPatternValues().stream()).collect(Collectors.toSet());
		assertThat(mappedPaths).contains("/user/webauthn/credentials");
		assertThat(mappedPaths).contains("/user/webauthn/has-credentials");
	}

	@Test
	@DisplayName("should return credentials for authenticated user")
	void shouldReturnCredentialsForAuthenticatedUser() throws Exception {
		mockMvc.perform(get("/user/webauthn/credentials").with(user(TEST_EMAIL).roles("USER"))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value("cred-1")).andExpect(jsonPath("$[0].label").value("My Device"));
	}

	@Test
	@DisplayName("should return consistent validation response for overlong label")
	void shouldReturnValidationResponseForOverlongLabel() throws Exception {
		String overlongLabel = "a".repeat(65);
		String payload = "{\"label\":\"" + overlongLabel + "\"}";

		mockMvc.perform(put("/user/webauthn/credentials/cred-1/label").with(user(TEST_EMAIL).roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Validation failed"))
				.andExpect(jsonPath("$.message", containsString("\"field\":\"label\"")));
	}

	@Test
	@DisplayName("should return business error response when service throws WebAuthnException")
	void shouldReturnBusinessErrorWhenServiceFails() throws Exception {
		String payload = "{\"currentPassword\":\"" + TEST_PASSWORD + "\"}";
		mockMvc.perform(delete("/user/webauthn/credentials/does-not-exist").with(user(TEST_EMAIL).roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Credential not found or access denied"));
	}

	@Test
	@DisplayName("should reject passkey deletion when account has password and current password missing")
	void shouldRejectDeleteWhenCurrentPasswordMissingForPasswordAccount() throws Exception {
		mockMvc.perform(delete("/user/webauthn/credentials/cred-1").with(user(TEST_EMAIL).roles("USER")).with(csrf()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("Current password is required")));

		assertThat(webAuthnCredentialRepository.findByIdWithUser("cred-1")).isPresent();
	}

	@Test
	@DisplayName("should reject passkey deletion when account has password and current password incorrect")
	void shouldRejectDeleteWhenCurrentPasswordIncorrectForPasswordAccount() throws Exception {
		String payload = "{\"currentPassword\":\"wrongPassword!\"}";
		mockMvc.perform(delete("/user/webauthn/credentials/cred-1").with(user(TEST_EMAIL).roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("Current password is incorrect")));

		assertThat(webAuthnCredentialRepository.findByIdWithUser("cred-1")).isPresent();
	}

	@Test
	@DisplayName("should reject passkey rename when account has password and current password missing")
	void shouldRejectRenameWhenCurrentPasswordMissingForPasswordAccount() throws Exception {
		String payload = "{\"label\":\"New Label\"}";
		mockMvc.perform(put("/user/webauthn/credentials/cred-1/label").with(user(TEST_EMAIL).roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("Current password is required")));

		assertThat(webAuthnCredentialRepository.findByIdWithUser("cred-1"))
				.isPresent()
				.hasValueSatisfying(c -> assertThat(c.getLabel()).isEqualTo("My Device"));
	}

	@Test
	@DisplayName("should reject passkey rename when account has password and current password incorrect")
	void shouldRejectRenameWhenCurrentPasswordIncorrectForPasswordAccount() throws Exception {
		String payload = "{\"label\":\"New Label\",\"currentPassword\":\"wrongPassword!\"}";
		mockMvc.perform(put("/user/webauthn/credentials/cred-1/label").with(user(TEST_EMAIL).roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", containsString("Current password is incorrect")));

		assertThat(webAuthnCredentialRepository.findByIdWithUser("cred-1"))
				.isPresent()
				.hasValueSatisfying(c -> assertThat(c.getLabel()).isEqualTo("My Device"));
	}
}
