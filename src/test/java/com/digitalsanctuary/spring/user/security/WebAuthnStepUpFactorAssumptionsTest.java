package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AllRequiredFactorsAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.RequiredFactor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Characterization tests for the Spring Security behaviour the WebAuthn step-up design depends on (issue #335).
 *
 * <p>
 * The built-in step-up primitive is built on Spring Security's own factor machinery rather than on a bespoke
 * challenge/verify flow: a sensitive operation requires a {@code FACTOR_WEBAUTHN}
 * {@link FactorGrantedAuthority} issued within a short TTL, and the user refreshes it by re-running the ordinary
 * passkey assertion at {@code /login/webauthn}. Three framework behaviours have to hold for that to work, and none of
 * them are this framework's code, so they are pinned here and will fail loudly on a Spring Security upgrade that
 * changes them:
 * </p>
 * <ol>
 * <li><b>Freshness enforcement</b> &mdash; {@code RequiredFactor.validDuration} denies a stale WEBAUTHN factor and
 * grants a fresh one.</li>
 * <li><b>Refresh</b> &mdash; re-running the assertion while already authenticated produces a WEBAUTHN
 * {@link FactorGrantedAuthority} with a new {@code issuedAt} while preserving the session's other authorities. This is
 * the merging half of {@link AbstractAuthenticationProcessingFilter} that {@code setMfaEnabled(true)} activates; with
 * it off, the second authentication REPLACES the first and step-up would drop the user's roles.</li>
 * <li><b>Survival</b> &mdash; the refreshed factor survives this framework's
 * {@link WebAuthnAuthenticationSuccessHandler}, which rebuilds the authentication to swap in {@code DSUserDetails}.</li>
 * </ol>
 *
 * <p>
 * The merging logic lives in {@code AbstractAuthenticationProcessingFilter#doFilter}, shared by every authentication
 * filter, and its only type-sensitive step is a reflective {@code declaresToBuilder(authenticationResult)} check. The
 * filter here therefore returns a real {@link WebAuthnAuthentication} (which does declare {@code toBuilder()}) and
 * stubs only the assertion verification, which needs an authenticator. The real
 * {@code WebAuthnAuthenticationProvider} stamps {@code FactorGrantedAuthority.fromAuthority("FACTOR_WEBAUTHN")}, whose
 * {@code issuedAt} defaults to now, so the freshness clock is driven by the genuine login path.
 * </p>
 */
@DisplayName("WebAuthn Step-Up Factor Assumptions Tests")
class WebAuthnStepUpFactorAssumptionsTest {

	private static final String EMAIL = "passkey-user@test.com";
	private static final Duration STEP_UP_TTL = Duration.ofMinutes(5);

	private final AuthorizationManager<Object> freshWebAuthnRequired = AllRequiredFactorsAuthorizationManager.builder()
			.requireFactor(RequiredFactor.withAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY).validDuration(STEP_UP_TTL).build())
			.build();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("should deny a stale WEBAUTHN factor and grant a fresh one when a validDuration is required")
	void shouldEnforceFreshnessWhenValidDurationIsRequired() {
		Authentication stale = authWith(webAuthnFactor(Instant.now().minus(Duration.ofMinutes(30))));
		Authentication fresh = authWith(webAuthnFactor(Instant.now()));

		assertThat(granted(freshWebAuthnRequired, stale)).as("30-minute-old WEBAUTHN factor against a 5-minute TTL").isFalse();
		assertThat(granted(freshWebAuthnRequired, fresh)).as("just-issued WEBAUTHN factor against a 5-minute TTL").isTrue();
	}

	@Test
	@DisplayName("should refresh issuedAt and keep existing authorities when re-asserting while already authenticated")
	void shouldRefreshWebAuthnFactorWhenReAssertingWhileAuthenticated() throws Exception {
		Instant staleIssuedAt = Instant.now().minus(Duration.ofMinutes(30));
		Instant freshIssuedAt = Instant.now();

		// The session as it stands before step-up: an old passkey login plus the user's role authorities.
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new UsernamePasswordAuthenticationToken(EMAIL, "n/a",
				List.of(webAuthnFactor(staleIssuedAt), new SimpleGrantedAuthority("ROLE_USER"))));
		SecurityContextHolder.setContext(context);

		Authentication merged = runAssertion(new WebAuthnAuthentication(userEntity(), Set.of(webAuthnFactor(freshIssuedAt))), true);

		assertThat(webAuthnFactorsOf(merged)).as("exactly one WEBAUTHN factor survives the merge (deduped by authority string)").hasSize(1);
		assertThat(webAuthnFactorsOf(merged).get(0).getIssuedAt()).as("the surviving WEBAUTHN factor is the newly issued one")
				.isEqualTo(freshIssuedAt);
		assertThat(authorityStrings(merged)).as("non-factor authorities from the existing session are carried over")
				.contains("ROLE_USER");

		// The step-up assertion itself: the same gate that denied before the ceremony now grants.
		assertThat(granted(freshWebAuthnRequired, context.getAuthentication())).as("gate before step-up").isFalse();
		assertThat(granted(freshWebAuthnRequired, merged)).as("gate after step-up").isTrue();
	}

	@Test
	@DisplayName("should replace the session authorities when re-asserting with factor merging disabled")
	void shouldReplaceAuthoritiesWhenMergingIsDisabled() throws Exception {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(new UsernamePasswordAuthenticationToken(EMAIL, "n/a",
				List.of(webAuthnFactor(Instant.now().minus(Duration.ofMinutes(30))), new SimpleGrantedAuthority("ROLE_USER"))));
		SecurityContextHolder.setContext(context);

		Authentication result = runAssertion(new WebAuthnAuthentication(userEntity(), Set.of(webAuthnFactor(Instant.now()))), false);

		assertThat(authorityStrings(result)).as("ROLE_USER is dropped when mfaEnabled=false, so merging must be switched on for step-up")
				.doesNotContain("ROLE_USER");
	}

	@Test
	@DisplayName("should preserve the refreshed factor when the success handler converts the principal to DSUserDetails")
	void shouldPreserveRefreshedFactorWhenPrincipalIsConverted() throws Exception {
		Instant freshIssuedAt = Instant.now();
		Set<GrantedAuthority> merged = Set.of(webAuthnFactor(freshIssuedAt), new SimpleGrantedAuthority("ROLE_USER"));

		User user = new User();
		user.setEmail(EMAIL);
		user.setFirstName("Passkey");
		user.setLastName("User");
		UserDetailsService userDetailsService = username -> new DSUserDetails(user, merged);

		CapturingSuccessHandler captor = new CapturingSuccessHandler();
		WebAuthnAuthenticationSuccessHandler handler = new WebAuthnAuthenticationSuccessHandler(userDetailsService, captor, null);

		handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
				new WebAuthnAuthentication(userEntity(), merged));

		assertThat(captor.captured.getPrincipal()).isInstanceOf(DSUserDetails.class);
		assertThat(webAuthnFactorsOf(captor.captured)).hasSize(1);
		assertThat(webAuthnFactorsOf(captor.captured).get(0).getIssuedAt()).as("issuedAt is preserved through the principal swap")
				.isEqualTo(freshIssuedAt);
		assertThat(granted(freshWebAuthnRequired, captor.captured)).as("gate still grants after conversion").isTrue();
	}

	/**
	 * Drives one authentication-filter pass with a stubbed assertion result, returning the authentication the filter
	 * hands to its success handler (i.e. after any factor merging).
	 */
	private Authentication runAssertion(WebAuthnAuthentication result, boolean mergingEnabled) throws Exception {
		CapturingSuccessHandler captor = new CapturingSuccessHandler();
		AbstractAuthenticationProcessingFilter filter = new AbstractAuthenticationProcessingFilter(request -> true) {
			@Override
			public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
				return result;
			}
		};
		filter.setAuthenticationManager(authentication -> authentication);
		filter.setAuthenticationSuccessHandler(captor);
		filter.setMfaEnabled(mergingEnabled);

		filter.doFilter(new MockHttpServletRequest("POST", "/login/webauthn"), new MockHttpServletResponse(), new MockFilterChain());
		return captor.captured;
	}

	private static boolean granted(AuthorizationManager<Object> manager, Authentication authentication) {
		AuthorizationResult result = manager.authorize(() -> authentication, new Object());
		return result != null && result.isGranted();
	}

	private static Authentication authWith(GrantedAuthority... authorities) {
		return new TestingAuthenticationToken(EMAIL, "n/a", List.of(authorities));
	}

	private static FactorGrantedAuthority webAuthnFactor(Instant issuedAt) {
		return FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY).issuedAt(issuedAt).build();
	}

	private static List<String> authorityStrings(Authentication authentication) {
		return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
	}

	private static List<FactorGrantedAuthority> webAuthnFactorsOf(Authentication authentication) {
		return authentication.getAuthorities().stream().filter(FactorGrantedAuthority.class::isInstance)
				.map(FactorGrantedAuthority.class::cast)
				.filter(factor -> FactorGrantedAuthority.WEBAUTHN_AUTHORITY.equals(factor.getAuthority())).toList();
	}

	private static PublicKeyCredentialUserEntity userEntity() {
		return ImmutablePublicKeyCredentialUserEntity.builder().name(EMAIL).id(new Bytes(new byte[] {1, 2, 3})).displayName("Passkey User")
				.build();
	}

	private static final class CapturingSuccessHandler implements AuthenticationSuccessHandler {
		private Authentication captured;

		@Override
		public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
			this.captured = authentication;
		}
	}
}
