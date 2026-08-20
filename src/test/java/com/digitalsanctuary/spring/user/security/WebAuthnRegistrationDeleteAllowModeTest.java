package com.digitalsanctuary.spring.user.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.digitalsanctuary.spring.user.test.annotations.SecurityTest;

/**
 * Regression test for GHSA-3cv9-vgqh-jwpm under {@code user.security.defaultAction=allow}.
 * <p>
 * {@link WebAuthnFeatureEnabledIntegrationTest} already locks in the deny under the test profile's default
 * {@code defaultAction=deny}. That mode alone would not have exposed the vulnerability: under {@code deny}, an
 * unlisted URI merely requires authentication, so an authenticated attacker could still have reached the endpoint.
 * The scenario the fix specifically targets is {@code allow} mode, where {@code anyRequest().permitAll()} would
 * otherwise expose the built-in {@code DELETE /webauthn/register/{id}} endpoint to anonymous callers entirely. This
 * test proves the deny rule is registered ahead of both {@code defaultAction} branches, not just the {@code deny}
 * one.
 * </p>
 */
@SecurityTest
@TestPropertySource(properties = {"user.security.defaultAction=allow", "user.webauthn.enabled=true"})
@DisplayName("WebAuthn built-in delete endpoint - defaultAction=allow (GHSA-3cv9-vgqh-jwpm)")
class WebAuthnRegistrationDeleteAllowModeTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("should deny an anonymous DELETE to the built-in WebAuthn registration endpoint when defaultAction is allow")
	void shouldDenyAnonymousDeleteWhenAllow() throws Exception {
		// Under allow mode, anyRequest().permitAll() is the fallback for anything not explicitly matched. Without
		// the fix, an unlisted DELETE /webauthn/register/{id} would fall through to that permitAll() and be
		// reachable by anyone. denyAll() still produces an AccessDeniedException for an anonymous principal, but
		// ExceptionTranslationFilter routes an unauthenticated caller to the login entry point (302) rather than a
		// bare 403 -- matching WebSecurityAuthorizationAllowTest's anonymous-denied assertions. Either way the
		// delete never executes.
		mockMvc.perform(delete("/webauthn/register/cred-1").with(csrf())).andExpect(status().is3xxRedirection());
	}

	@Test
	@DisplayName("should deny an authenticated DELETE to the built-in WebAuthn registration endpoint when defaultAction is allow")
	void shouldDenyAuthenticatedDeleteWhenAllow() throws Exception {
		mockMvc.perform(delete("/webauthn/register/cred-1").with(user("user@test.com").roles("USER")).with(csrf()))
				.andExpect(status().isForbidden());
	}
}
