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
import com.digitalsanctuary.spring.user.test.annotations.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = "user.mfa.enabled=false")
@DisplayName("MFA Disabled Integration Tests")
class MfaFeatureDisabledIntegrationTest {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private RequestMappingHandlerMapping requestMappingHandlerMapping;

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("should not register MFA API bean when disabled")
	void shouldNotRegisterMfaApiBeanWhenDisabled() {
		assertThat(applicationContext.getBeanNamesForType(MfaAPI.class)).isEmpty();
	}

	@Test
	@DisplayName("should not expose MFA endpoint mappings when disabled")
	void shouldNotExposeMfaEndpointMappingsWhenDisabled() {
		Set<String> mappedPaths = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
				.flatMap(info -> info.getPatternValues().stream()).collect(Collectors.toSet());
		assertThat(mappedPaths).noneMatch(path -> path.startsWith("/user/mfa"));
	}

	@Test
	@DisplayName("should return 404 for MFA status endpoint when disabled")
	void shouldReturnNotFoundForMfaStatusEndpointWhenDisabled() throws Exception {
		mockMvc.perform(get("/user/mfa/status").with(user("user@test.com").roles("USER")))
				.andExpect(status().isNotFound());
	}
}
