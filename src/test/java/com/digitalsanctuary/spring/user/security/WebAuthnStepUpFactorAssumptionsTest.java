package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
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
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationProvider;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Characterization tests for the behaviour a built-in WebAuthn step-up primitive would depend on (issue #335).
 *
 * <p>
 * This framework ships no step-up primitive today: {@link StepUpService} is an SPI a consuming application implements,
 * and nothing in production code configures {@code RequiredFactor.validDuration}. The design proposed in #335 would
 * build step-up on Spring Security's own factor machinery rather than on a bespoke challenge/verify flow: a sensitive
 * operation would require a {@code FACTOR_WEBAUTHN} {@link FactorGrantedAuthority} issued within a short TTL, and the
 * user would refresh it by re-running the ordinary passkey assertion at {@code /login/webauthn}. These tests pin the
 * behaviour that design rests on, ahead of building it.
 * </p>
 * <ol>
 * <li><b>Freshness enforcement</b> &mdash; {@code RequiredFactor.validDuration} denies a stale WEBAUTHN factor, grants
 * a fresh one, and refuses a look-alike authority that is not a {@link FactorGrantedAuthority} &mdash; including when
 * that look-alike sorts ahead of a genuine one and shadows it.</li>
 * <li><b>Stamping</b> &mdash; {@code WebAuthnAuthenticationProvider} adds a {@code FACTOR_WEBAUTHN} authority whose
 * {@code issuedAt} defaults to now, on top of whatever authorities the {@code UserDetailsService} supplies. That
 * default is the freshness clock.</li>
 * <li><b>Refresh</b> &mdash; re-asserting while already authenticated merges the new factor into the existing session
 * rather than replacing it, so the fresh {@code issuedAt} wins while the session's other authorities survive. This is
 * the merging half of {@link AbstractAuthenticationProcessingFilter} that {@code setMfaEnabled(true)} activates; with
 * it off, the second authentication replaces the first and any authority the {@code UserDetailsService} does not
 * re-supply (the {@code FACTOR_PASSWORD} from the original login, say) is lost.</li>
 * </ol>
 *
 * <p>
 * Those three are Spring Security's behaviour, not this framework's, so they will fail loudly on an upgrade that
 * changes them. The last test is different in kind: it pins this framework's own
 * {@link WebAuthnAuthenticationSuccessHandler} against a regression that would silently drop the refreshed factor
 * while swapping {@link DSUserDetails} in as the principal.
 * </p>
 *
 * <p>
 * The only behaviour stubbed is {@link WebAuthnRelyingPartyOperations#authenticate}, which needs a real
 * authenticator (its request argument is a stand-in for the same reason, and is never read).
 * Everything downstream is the genuine path: the real {@code WebAuthnAuthenticationProvider} assembles the
 * authentication and stamps the factor, and the real {@code AbstractAuthenticationProcessingFilter#doFilter} performs
 * the merge. Only the credential-JSON converter is skipped, by overriding {@code attemptAuthentication} to hand the
 * authentication manager a request token directly.
 * </p>
 *
 * <p>
 * That merge lives in {@code AbstractAuthenticationProcessingFilter}, so it is inherited by the filters that extend it
 * (form login, one-time token, WebAuthn) and not by the {@code OncePerRequestFilter}-based ones. It fires only when
 * all four of {@code shouldPerformMfa}'s gates pass: {@code mfaEnabled} is set, an authenticated authentication is
 * already in the context, the result's concrete class <em>declares</em> {@code toBuilder()} (the check reflects over
 * {@code getDeclaredMethods()}, so an inherited one would not count), and {@code current.getName()} equals the new
 * result's name. The last gate is why the pre-step-up session below is a {@link WebAuthnAuthenticationToken} over
 * {@link DSUserDetails}, matching what a completed passkey login actually leaves in the context: its {@code getName()}
 * resolves to the user's email, the same value {@code WebAuthnAuthentication} takes from its
 * {@link PublicKeyCredentialUserEntity}.
 * </p>
 */
@DisplayName("WebAuthn Step-Up Factor Assumptions Tests")
class WebAuthnStepUpFactorAssumptionsTest {

	private static final String EMAIL = "passkey-user@test.com";
	private static final String ROLE_USER = "ROLE_USER";
	private static final Duration STEP_UP_TTL = Duration.ofMinutes(5);
	private static final Duration LONG_AGO = Duration.ofMinutes(30);

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
		Authentication stale = authWith(webAuthnFactor(Instant.now().minus(LONG_AGO)));
		Authentication fresh = authWith(webAuthnFactor(Instant.now()));

		assertThat(granted(freshWebAuthnRequired, stale)).as("30-minute-old WEBAUTHN factor against a 5-minute TTL").isFalse();
		assertThat(granted(freshWebAuthnRequired, fresh)).as("just-issued WEBAUTHN factor against a 5-minute TTL").isTrue();
	}

	@Test
	@DisplayName("should deny a plain authority named FACTOR_WEBAUTHN when a validDuration is required")
	void shouldDenyWhenWebAuthnAuthorityIsNotAFactorGrantedAuthority() {
		// Consumers can name any authority they like in user.roles-and-privileges, so a granted string that happens to
		// read FACTOR_WEBAUTHN must not pass a freshness check: it carries no issuedAt, and treating it as valid would
		// mean a permanently satisfied step-up gate. AllRequiredFactorsAuthorizationManager type-checks for this.
		Authentication lookAlike = authWith(new SimpleGrantedAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY));

		assertThat(granted(freshWebAuthnRequired, lookAlike)).as("an authority with no issuedAt cannot satisfy a validDuration")
				.isFalse();
	}

	@Test
	@DisplayName("should deny a freshly stamped factor when a look-alike authority precedes it")
	void shouldDenyWhenALookAlikeAuthorityPrecedesTheStampedFactor() {
		// The look-alike does not merely fail on its own, it SHADOWS a genuine one. AllRequiredFactors resolves a
		// RequiredFactor by taking the first authority whose string matches, then type-checks that one: a non-factor
		// first match is reported expired and the real, fresh FactorGrantedAuthority behind it is never considered.
		// The provider appends its stamp after the UserDetailsService's authorities, so a consumer privilege named
		// FACTOR_WEBAUTHN always sorts first and step-up can never be satisfied on that deployment. It fails closed,
		// but the symptom (a just-completed assertion that still does not satisfy the gate) is opaque, which is why a
		// built-in step-up primitive should reject FACTOR_-prefixed names in user.roles-and-privileges at startup.
		List<GrantedAuthority> withLookAlike =
				List.of(new SimpleGrantedAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY), new SimpleGrantedAuthority(ROLE_USER));

		Authentication result = provider(withLookAlike).authenticate(assertionRequestToken());

		assertThat(authorityStrings(result)).as("the look-alike is carried through and sorts ahead of the stamped factor")
				.startsWith(FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
		assertThat(webAuthnFactorsOf(result)).as("a genuine, freshly stamped factor is present all the same").hasSize(1);
		assertThat(granted(freshWebAuthnRequired, result)).as("yet the gate denies, immediately after a real assertion").isFalse();
	}

	@Test
	@DisplayName("should stamp a fresh WEBAUTHN factor onto the user's authorities when the provider authenticates")
	void shouldStampFreshWebAuthnFactorWhenProviderAuthenticates() {
		Instant beforeAssertion = Instant.now();

		Authentication result = provider(List.of(new SimpleGrantedAuthority(ROLE_USER))).authenticate(assertionRequestToken());

		assertThat(webAuthnFactorsOf(result)).as("the provider stamps exactly one WEBAUTHN factor").hasSize(1);
		assertThat(webAuthnFactorsOf(result).get(0).getIssuedAt()).as("issuedAt defaults to the moment of assertion, driving the clock")
				.isBetween(beforeAssertion, Instant.now());
		assertThat(authorityStrings(result)).as("the UserDetailsService authorities are carried across").contains(ROLE_USER);
		assertThat(granted(freshWebAuthnRequired, result)).as("a just-stamped factor satisfies the step-up gate").isTrue();
	}

	@Test
	@DisplayName("should refresh issuedAt and keep existing authorities when re-asserting while already authenticated")
	void shouldRefreshWebAuthnFactorWhenReAssertingWhileAuthenticated() throws Exception {
		Instant staleIssuedAt = Instant.now().minus(LONG_AGO);

		// The session as it stands before step-up: a passkey login whose factor has gone stale, the FACTOR_PASSWORD
		// from the original password login, and the user's role.
		Authentication preStepUp = existingSession(webAuthnFactor(staleIssuedAt), passwordFactor(staleIssuedAt),
				new SimpleGrantedAuthority(ROLE_USER));
		boolean grantedBeforeStepUp = granted(freshWebAuthnRequired, preStepUp);
		setContext(preStepUp);

		Authentication merged = runAssertion(true, List.of(new SimpleGrantedAuthority(ROLE_USER)));

		assertThat(webAuthnFactorsOf(merged)).as("exactly one WEBAUTHN factor survives the merge (deduped by authority string)").hasSize(1);
		assertThat(webAuthnFactorsOf(merged).get(0).getIssuedAt()).as("the surviving WEBAUTHN factor is the newly stamped one")
				.isAfter(staleIssuedAt);
		assertThat(authorityStrings(merged)).as("session authorities the UserDetailsService does not re-supply are carried over")
				.contains(FactorGrantedAuthority.PASSWORD_AUTHORITY, ROLE_USER);

		// The payoff: the gate that denied the pre-step-up session grants the merged one.
		assertThat(grantedBeforeStepUp).as("gate before step-up").isFalse();
		assertThat(granted(freshWebAuthnRequired, merged)).as("gate after step-up").isTrue();
	}

	@Test
	@DisplayName("should drop session-only authorities when re-asserting with factor merging disabled")
	void shouldReplaceAuthoritiesWhenMergingIsDisabled() throws Exception {
		Instant staleIssuedAt = Instant.now().minus(LONG_AGO);
		setContext(existingSession(webAuthnFactor(staleIssuedAt), passwordFactor(staleIssuedAt),
				new SimpleGrantedAuthority(ROLE_USER)));

		Authentication result = runAssertion(false, List.of(new SimpleGrantedAuthority(ROLE_USER)));

		assertThat(authorityStrings(result))
				.as("with mfaEnabled=false the result is the new authentication alone, so the original login's FACTOR_PASSWORD is lost")
				.containsExactlyInAnyOrder(FactorGrantedAuthority.WEBAUTHN_AUTHORITY, ROLE_USER);
	}

	@Test
	@DisplayName("should preserve the refreshed factor when the success handler converts the principal to DSUserDetails")
	void shouldPreserveRefreshedFactorWhenPrincipalIsConverted() throws Exception {
		Instant freshIssuedAt = Instant.now();

		// The handler must copy authorities off the incoming authentication, not off the UserDetails it loads: a
		// DB-loaded DSUserDetails carries roles only, so reading authorities from it would drop the factor entirely.
		// This stub therefore returns roles only, and the factor exists solely on the authentication passed in.
		UserDetailsService rolesOnly = username -> new DSUserDetails(user(), List.of(new SimpleGrantedAuthority(ROLE_USER)));

		CapturingSuccessHandler captor = new CapturingSuccessHandler();
		WebAuthnAuthenticationSuccessHandler handler = new WebAuthnAuthenticationSuccessHandler(rolesOnly, captor, null);

		handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), new WebAuthnAuthentication(
				userEntity(), Set.of(webAuthnFactor(freshIssuedAt), new SimpleGrantedAuthority(ROLE_USER))));

		assertThat(captor.captured.getPrincipal()).isInstanceOf(DSUserDetails.class);
		assertThat(webAuthnFactorsOf(captor.captured)).as("the factor survives the principal swap").hasSize(1);
		assertThat(webAuthnFactorsOf(captor.captured).get(0).getIssuedAt()).as("issuedAt is preserved through the principal swap")
				.isEqualTo(freshIssuedAt);
		assertThat(granted(freshWebAuthnRequired, captor.captured)).as("gate still grants after conversion").isTrue();
	}

	/**
	 * Drives one authentication-filter pass through the real {@link WebAuthnAuthenticationProvider}, returning the
	 * authentication the filter hands to its success handler (i.e. after any factor merging).
	 */
	private Authentication runAssertion(boolean mergingEnabled, Collection<GrantedAuthority> userAuthorities) throws Exception {
		CapturingSuccessHandler captor = new CapturingSuccessHandler();
		AbstractAuthenticationProcessingFilter filter = new AbstractAuthenticationProcessingFilter(request -> true) {
			@Override
			public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
				// What WebAuthnAuthenticationFilter does once its converter has parsed the credential JSON.
				return getAuthenticationManager().authenticate(assertionRequestToken());
			}
		};
		filter.setAuthenticationManager(provider(userAuthorities)::authenticate);
		filter.setAuthenticationSuccessHandler(captor);
		filter.setMfaEnabled(mergingEnabled);

		filter.doFilter(new MockHttpServletRequest("POST", "/login/webauthn"), new MockHttpServletResponse(), new MockFilterChain());
		return captor.captured;
	}

	/** The real provider, with only the assertion verification stubbed out, since that needs an authenticator. */
	private static WebAuthnAuthenticationProvider provider(Collection<GrantedAuthority> userAuthorities) {
		WebAuthnRelyingPartyOperations relyingParty = mock(WebAuthnRelyingPartyOperations.class);
		when(relyingParty.authenticate(any())).thenReturn(userEntity());
		return new WebAuthnAuthenticationProvider(relyingParty, username -> new DSUserDetails(user(), userAuthorities));
	}

	/**
	 * The token the WebAuthn filter's converter would produce. Its payload is never read here: the provider passes
	 * it straight to the stubbed {@link WebAuthnRelyingPartyOperations#authenticate}, and the real request object
	 * can only be built from an authenticator's assertion.
	 */
	private static WebAuthnAuthenticationRequestToken assertionRequestToken() {
		return new WebAuthnAuthenticationRequestToken(mock(RelyingPartyAuthenticationRequest.class));
	}

	/** The authentication a completed passkey login leaves in the context. */
	private static Authentication existingSession(GrantedAuthority... authorities) {
		return new WebAuthnAuthenticationToken(new DSUserDetails(user(), List.of(authorities)), List.of(authorities));
	}

	private static void setContext(Authentication authentication) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
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

	private static FactorGrantedAuthority passwordFactor(Instant issuedAt) {
		return FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY).issuedAt(issuedAt).build();
	}

	private static User user() {
		User user = new User();
		user.setEmail(EMAIL);
		user.setFirstName("Passkey");
		user.setLastName("User");
		return user;
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
