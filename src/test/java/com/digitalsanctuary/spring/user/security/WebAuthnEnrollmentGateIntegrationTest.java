package com.digitalsanctuary.spring.user.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.digitalsanctuary.spring.user.test.annotations.SecurityTest;

/**
 * Proves the enrollment gate is actually wired into the filter chain, not merely that
 * {@link FreshFactorAuthorizationManager} works in isolation.
 * <p>
 * This is the assumption the whole feature rests on: {@code WebAuthnRegistrationFilter} is registered after
 * {@code AuthorizationFilter}, so an {@code authorizeHttpRequests} rule is evaluated before the endpoint runs. If
 * that ordering were wrong the gate would silently do nothing while every unit test still passed.
 * </p>
 */
@SecurityTest
@TestPropertySource(properties = {"user.webauthn.enabled=true", "user.security.stepUp.enabled=true",
		"user.security.stepUp.enrollmentTtlSeconds=600"})
@DisplayName("WebAuthn Enrollment Gate Integration Tests")
class WebAuthnEnrollmentGateIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private static Authentication withFactor(Instant issuedAt) {
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"),
				FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY).issuedAt(issuedAt).build());
		return new TestingAuthenticationToken("user@test.com", "n/a", authorities);
	}

	@Test
	@DisplayName("should reject passkey enrollment when the session carries no authentication factor")
	void shouldRejectEnrollmentWithoutFactor() throws Exception {
		// The attack this closes: a stolen session cookie enrolls an attacker-controlled passkey, asserts with it to
		// mint a fresh FACTOR_WEBAUTHN, and thereby satisfies every step-up gate. The denial returns the same
		// 401 + step-up-required contract as the credential-management endpoints, not a bare 403.
		mockMvc.perform(post("/webauthn/register").with(user("user@test.com").roles("USER")).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("step-up-required"));
	}

	@Test
	@DisplayName("should reject passkey enrollment when the only factor is older than the window")
	void shouldRejectEnrollmentWithStaleFactor() throws Exception {
		mockMvc.perform(post("/webauthn/register").with(authentication(withFactor(Instant.now().minusSeconds(601))))
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("step-up-required"));
	}

	@Test
	@DisplayName("should not reject passkey enrollment when a factor was issued inside the window")
	void shouldAllowEnrollmentWithFreshFactor() throws Exception {
		// The request body is not a real attestation, so the endpoint itself fails it. What matters here is that the
		// gate let it through: neither the 401 the gate now returns on denial nor a 403 proves authorization passed
		// and the filter ran.
		mockMvc.perform(post("/webauthn/register").with(authentication(withFactor(Instant.now().minusSeconds(5))))
				.with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().is(not(401))).andExpect(status().is(not(403)));
	}

	@Test
	@DisplayName("should deny a non-enrollment path with a bare 403, not the step-up contract, while the gate is active")
	void shouldNotApplyStepUpContractToNonEnrollmentDenials() throws Exception {
		// Regression guard for the access-denied handler scoping (the reason StepUpEnrollmentAccessDeniedHandler is
		// composed into a RequestMatcherDelegatingAccessDeniedHandler rather than installed via a lone
		// defaultAccessDeniedHandlerFor mapping, which Spring silently unscopes). DELETE /webauthn/register/** is
		// denyAll, so an authenticated user is denied there; that denial is an AuthorizationDeniedException too, and it
		// must stay a bare 403 without the step-up-required body. If the step-up handler leaked to the app-wide default
		// this would return 401 step-up-required instead.
		mockMvc.perform(delete("/webauthn/register/some-credential-id").with(user("user@test.com").roles("USER")).with(csrf()))
				.andExpect(status().isForbidden())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("step-up-required"))));
	}

	@Test
	@DisplayName("should return a bare 403 for a CSRF failure on the enrollment path, not the step-up contract")
	void shouldDelegateCsrfFailureOnEnrollmentPath() throws Exception {
		// A CSRF failure is an AccessDeniedException but not an AuthorizationDeniedException, so the enrollment handler
		// must delegate it and keep the normal 403 rather than telling the client to re-run a login ceremony that
		// cannot fix a missing CSRF token. Posting without csrf() triggers the CSRF filter before the authorization gate.
		mockMvc.perform(post("/webauthn/register").with(user("user@test.com").roles("USER"))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("step-up-required"))));
	}

	private static org.hamcrest.Matcher<Integer> not(int status) {
		return org.hamcrest.Matchers.not(org.hamcrest.Matchers.is(status));
	}
}
