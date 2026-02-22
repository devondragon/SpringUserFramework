package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.fixtures.TestFixtures;

@ServiceTest
@DisplayName("WebAuthnAuthenticationSuccessHandler Tests")
class WebAuthnAuthenticationSuccessHandlerTest {

	@Mock
	private UserDetailsService userDetailsService;

	@Mock
	private AuthenticationSuccessHandler delegate;

	private WebAuthnAuthenticationSuccessHandler handler;

	private MockHttpServletRequest request;
	private MockHttpServletResponse response;
	private User testUser;

	@BeforeEach
	void setUp() {
		testUser = TestFixtures.Users.standardUser();
		handler = new WebAuthnAuthenticationSuccessHandler(userDetailsService, delegate);
		request = new MockHttpServletRequest();
		response = new MockHttpServletResponse();
		SecurityContextHolder.clearContext();
	}

	@Nested
	@DisplayName("WebAuthn Authentication Conversion")
	class WebAuthnConversionTests {

		@Test
		@DisplayName("should convert WebAuthn principal to DSUserDetails")
		void shouldConvertWebAuthnPrincipalToDSUserDetails() throws Exception {
			// Given
			Collection<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"));
			PublicKeyCredentialUserEntity userEntity = ImmutablePublicKeyCredentialUserEntity.builder()
					.name(testUser.getEmail()).id(new Bytes(new byte[] {1, 2, 3})).displayName(testUser.getFullName()).build();

			WebAuthnAuthentication webAuthnAuth = new WebAuthnAuthentication(userEntity, authorities);

			DSUserDetails dsUserDetails = new DSUserDetails(testUser, authorities);
			when(userDetailsService.loadUserByUsername(testUser.getEmail())).thenReturn(dsUserDetails);

			// When
			handler.onAuthenticationSuccess(request, response, webAuthnAuth);

			// Then - delegate should be called with converted authentication
			ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
			verify(delegate).onAuthenticationSuccess(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.eq(response),
					authCaptor.capture());

			Authentication convertedAuth = authCaptor.getValue();
			assertThat(convertedAuth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
			assertThat(convertedAuth.getPrincipal()).isInstanceOf(DSUserDetails.class);
			assertThat(((DSUserDetails) convertedAuth.getPrincipal()).getUser()).isEqualTo(testUser);
		}

		@Test
		@DisplayName("should update SecurityContext with converted authentication")
		void shouldUpdateSecurityContext() throws Exception {
			// Given
			Collection<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"));
			PublicKeyCredentialUserEntity userEntity = ImmutablePublicKeyCredentialUserEntity.builder()
					.name(testUser.getEmail()).id(new Bytes(new byte[] {1, 2, 3})).displayName(testUser.getFullName()).build();

			WebAuthnAuthentication webAuthnAuth = new WebAuthnAuthentication(userEntity, authorities);

			DSUserDetails dsUserDetails = new DSUserDetails(testUser, authorities);
			when(userDetailsService.loadUserByUsername(testUser.getEmail())).thenReturn(dsUserDetails);

			// When
			handler.onAuthenticationSuccess(request, response, webAuthnAuth);

			// Then - SecurityContext should have DSUserDetails as principal
			Authentication contextAuth = SecurityContextHolder.getContext().getAuthentication();
			assertThat(contextAuth.getPrincipal()).isInstanceOf(DSUserDetails.class);
			assertThat(((DSUserDetails) contextAuth.getPrincipal()).getUser().getEmail()).isEqualTo(testUser.getEmail());
		}

		@Test
		@DisplayName("should preserve authorities from WebAuthn authentication")
		void shouldPreserveAuthorities() throws Exception {
			// Given
			Collection<GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_USER"),
					new SimpleGrantedAuthority("ROLE_ADMIN"));
			PublicKeyCredentialUserEntity userEntity = ImmutablePublicKeyCredentialUserEntity.builder()
					.name(testUser.getEmail()).id(new Bytes(new byte[] {1, 2, 3})).displayName(testUser.getFullName()).build();

			WebAuthnAuthentication webAuthnAuth = new WebAuthnAuthentication(userEntity, authorities);

			DSUserDetails dsUserDetails = new DSUserDetails(testUser, authorities);
			when(userDetailsService.loadUserByUsername(testUser.getEmail())).thenReturn(dsUserDetails);

			// When
			handler.onAuthenticationSuccess(request, response, webAuthnAuth);

			// Then
			ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
			verify(delegate).onAuthenticationSuccess(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), authCaptor.capture());

			Authentication convertedAuth = authCaptor.getValue();
			assertThat(convertedAuth.getAuthorities()).hasSize(authorities.size());
			assertThat(convertedAuth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
					.containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
		}
	}

	@Nested
	@DisplayName("Non-WebAuthn Authentication")
	class NonWebAuthnTests {

		@Test
		@DisplayName("should pass through non-WebAuthn authentication unchanged")
		void shouldPassThroughNonWebAuthnAuth() throws Exception {
			// Given
			DSUserDetails dsUserDetails = new DSUserDetails(testUser);
			UsernamePasswordAuthenticationToken formAuth = UsernamePasswordAuthenticationToken.authenticated(dsUserDetails, null,
					Set.of(new SimpleGrantedAuthority("ROLE_USER")));

			// When
			handler.onAuthenticationSuccess(request, response, formAuth);

			// Then - delegate should be called with original authentication
			verify(delegate).onAuthenticationSuccess(request, response, formAuth);
		}
	}
}
