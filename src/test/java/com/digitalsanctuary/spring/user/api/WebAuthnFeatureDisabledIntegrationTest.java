package com.digitalsanctuary.spring.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnCredentialQueryRepository;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnUserEntityBridge;
import com.digitalsanctuary.spring.user.security.WebAuthnRepositoryConfig;
import com.digitalsanctuary.spring.user.service.WebAuthnCredentialManagementService;
import com.digitalsanctuary.spring.user.test.annotations.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = "user.webauthn.enabled=false")
@DisplayName("WebAuthn Disabled Integration Tests")
class WebAuthnFeatureDisabledIntegrationTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private RequestMappingHandlerMapping requestMappingHandlerMapping;

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("should not register WebAuthn beans when disabled")
	void shouldNotRegisterWebAuthnBeansWhenDisabled() {
		assertThat(applicationContext.getBeanNamesForType(WebAuthnManagementAPI.class)).isEmpty();
		assertThat(applicationContext.getBeanNamesForType(WebAuthnCredentialManagementService.class)).isEmpty();
		assertThat(applicationContext.getBeanNamesForType(WebAuthnCredentialQueryRepository.class)).isEmpty();
		assertThat(applicationContext.getBeanNamesForType(WebAuthnUserEntityBridge.class)).isEmpty();
		assertThat(applicationContext.getBeanNamesForType(WebAuthnRepositoryConfig.class)).isEmpty();
	}

	@Test
	@DisplayName("should not expose WebAuthn management endpoint mappings when disabled")
	void shouldNotExposeWebAuthnEndpointMappingsWhenDisabled() {
		Set<String> mappedPaths = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
				.flatMap(info -> info.getPatternValues().stream()).collect(Collectors.toSet());
		assertThat(mappedPaths).noneMatch(path -> path.startsWith("/user/webauthn"));
	}

	@Test
	@DisplayName("should return 404 for management endpoint when disabled")
	void shouldReturnNotFoundForManagementEndpointWhenDisabled() throws Exception {
		mockMvc.perform(get("/user/webauthn/credentials").with(user("user@test.com").roles("USER"))).andExpect(status().isNotFound());
	}
}
