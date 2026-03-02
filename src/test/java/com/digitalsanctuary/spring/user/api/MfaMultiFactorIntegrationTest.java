package com.digitalsanctuary.spring.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.DefaultAuthorizationManagerFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.digitalsanctuary.spring.user.security.MfaConfigProperties;
import com.digitalsanctuary.spring.user.security.MfaConfiguration;
import com.digitalsanctuary.spring.user.test.annotations.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = {
		"user.mfa.enabled=true",
		"user.mfa.factors=PASSWORD,WEBAUTHN",
		"user.webauthn.enabled=true",
		"user.webauthn.rpId=localhost",
		"user.webauthn.rpName=Test App"
})
@DisplayName("MFA Multi-Factor (PASSWORD + WEBAUTHN) Integration Tests")
class MfaMultiFactorIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MfaConfigProperties mfaConfigProperties;

	@Autowired
	private DefaultAuthorizationManagerFactory<?> mfaAuthorizationManagerFactory;

	@Test
	@DisplayName("should configure both PASSWORD and WEBAUTHN factors when both are specified")
	void shouldConfigureBothFactorsWhenBothAreSpecified() {
		assertThat(mfaConfigProperties.isEnabled()).isTrue();
		assertThat(mfaConfigProperties.getFactors()).containsExactly("PASSWORD", "WEBAUTHN");
	}

	@Test
	@DisplayName("should create MFA authorization manager factory when MFA is enabled")
	void shouldCreateMfaAuthorizationManagerFactoryWhenMfaIsEnabled() {
		assertThat(mfaAuthorizationManagerFactory).isNotNull();
	}

	@Test
	@DisplayName("should resolve both factor authorities when both factors are configured")
	void shouldResolveBothFactorAuthoritiesWhenBothFactorsAreConfigured() {
		List<String> factors = mfaConfigProperties.getFactors();
		for (String factor : factors) {
			String authority = MfaConfiguration.mapFactorToAuthority(factor);
			assertThat(authority).as("Authority for factor %s should not be null", factor).isNotNull();
		}
	}

	@Test
	@DisplayName("should return both factors as required in MFA status endpoint")
	void shouldReturnBothFactorsAsRequiredInMfaStatusEndpoint() throws Exception {
		mockMvc.perform(get("/user/mfa/status").with(user("user@test.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.mfaEnabled").value(true))
				.andExpect(jsonPath("$.data.requiredFactors").isArray())
				.andExpect(jsonPath("$.data.requiredFactors.length()").value(2))
				.andExpect(jsonPath("$.data.requiredFactors[0]").value("PASSWORD"))
				.andExpect(jsonPath("$.data.requiredFactors[1]").value("WEBAUTHN"));
	}

	@Test
	@DisplayName("should report both factors as missing when user has no factor authorities")
	void shouldReportBothFactorsAsMissingWhenUserHasNoFactorAuthorities() throws Exception {
		mockMvc.perform(get("/user/mfa/status").with(user("user@test.com").roles("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.missingFactors.length()").value(2))
				.andExpect(jsonPath("$.data.missingFactors[0]").value("PASSWORD"))
				.andExpect(jsonPath("$.data.missingFactors[1]").value("WEBAUTHN"))
				.andExpect(jsonPath("$.data.fullyAuthenticated").value(false));
	}

	@Test
	@DisplayName("should report both factors as missing for unauthenticated request")
	void shouldReportBothFactorsAsMissingForUnauthenticatedRequest() throws Exception {
		mockMvc.perform(get("/user/mfa/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.missingFactors.length()").value(2))
				.andExpect(jsonPath("$.data.fullyAuthenticated").value(false));
	}

	@Test
	@DisplayName("should report fully authenticated when user has all required factor authorities")
	void shouldReportFullyAuthenticatedWhenUserHasAllRequiredFactorAuthorities() throws Exception {
		List<GrantedAuthority> authorities = List.of(
				new SimpleGrantedAuthority("ROLE_USER"),
				FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY),
				FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY));

		mockMvc.perform(get("/user/mfa/status").with(user("user@test.com").authorities(authorities)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.fullyAuthenticated").value(true))
				.andExpect(jsonPath("$.data.missingFactors").isEmpty())
				.andExpect(jsonPath("$.data.satisfiedFactors.length()").value(2));
	}
}
