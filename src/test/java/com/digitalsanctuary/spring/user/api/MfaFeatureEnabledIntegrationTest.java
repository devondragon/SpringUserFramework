package com.digitalsanctuary.spring.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.digitalsanctuary.spring.user.security.MfaConfigProperties;
import com.digitalsanctuary.spring.user.test.annotations.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = {
		"user.mfa.enabled=true",
		"user.mfa.factors=PASSWORD",
		"user.webauthn.enabled=false"
})
@DisplayName("MFA Enabled Integration Tests")
class MfaFeatureEnabledIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RequestMappingHandlerMapping requestMappingHandlerMapping;

	@Autowired
	private MfaConfigProperties mfaConfigProperties;

	@Test
	@DisplayName("should register MFA beans and endpoint mappings when enabled")
	void shouldRegisterMfaBeansAndMappingsWhenEnabled() {
		assertThat(mfaConfigProperties).isNotNull();
		assertThat(mfaConfigProperties.isEnabled()).isTrue();

		Set<String> mappedPaths = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
				.flatMap(info -> info.getPatternValues().stream()).collect(Collectors.toSet());
		assertThat(mappedPaths).contains("/user/mfa/status");
	}

	@Test
	@DisplayName("should return MFA status for authenticated user")
	void shouldReturnMfaStatusWhenUserIsAuthenticated() throws Exception {
		mockMvc.perform(get("/user/mfa/status").with(user("user@test.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.mfaEnabled").value(true))
				.andExpect(jsonPath("$.data.requiredFactors").isArray())
				.andExpect(jsonPath("$.data.requiredFactors[0]").value("PASSWORD"));
	}

	@Test
	@DisplayName("should return MFA status for unauthenticated request")
	void shouldReturnMfaStatusWhenRequestIsUnauthenticated() throws Exception {
		// The MFA status endpoint should be accessible without full authentication
		// since it's added to unprotected URIs
		mockMvc.perform(get("/user/mfa/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.mfaEnabled").value(true))
				.andExpect(jsonPath("$.data.missingFactors").isArray());
	}

	@Test
	@DisplayName("should report PASSWORD as missing factor when user has no password authority")
	void shouldReportPasswordAsMissingFactorWhenUserLacksAuthority() throws Exception {
		// A standard user login via MockMvc's .with(user()) does not add FactorGrantedAuthority,
		// so PASSWORD should be reported as missing
		mockMvc.perform(get("/user/mfa/status").with(user("user@test.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.missingFactors[0]").value("PASSWORD"))
				.andExpect(jsonPath("$.data.fullyAuthenticated").value(false));
	}

	@Test
	@DisplayName("should redirect to password entry point when user is missing password factor authority")
	void shouldRedirectToPasswordEntryPointWhenMissingFactorAuthority() throws Exception {
		// A user with ROLE_USER but no FactorGrantedAuthority hits a protected endpoint.
		// The DelegatingMissingAuthorityAccessDeniedHandler should redirect to the
		// password entry-point URI since the PASSWORD factor is not satisfied.
		mockMvc.perform(get("/protected.html").with(user("user@test.com").roles("USER")))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern(mfaConfigProperties.getPasswordEntryPointUri() + "**"));
	}
}
