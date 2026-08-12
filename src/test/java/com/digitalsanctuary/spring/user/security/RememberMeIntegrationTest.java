package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.test.app.TestApplication;
import com.digitalsanctuary.spring.user.test.config.BaseTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;

/**
 * Integration tests for hash-based remember-me (the default mode, Spring's {@code TokenBasedRememberMeServices})
 * through the <strong>real</strong> form-login path.
 *
 * <p>
 * What this proves end-to-end: a login that posts the {@code remember-me} parameter is issued a remember-me cookie
 * (and one that omits the parameter is not — the reason a consumer's login form MUST include the checkbox); a
 * session-less request bearing only that cookie is auto-authenticated with a {@link RememberMeAuthenticationToken}
 * whose principal is a {@link DSUserDetails} (so {@code @AuthenticationPrincipal DSUserDetails} controller signatures
 * work on remember-me logins); the auto-login publishes {@link InteractiveAuthenticationSuccessEvent} (which
 * {@code BaseAuthenticationListener} relies on to populate session profiles); and a password change invalidates the
 * cookie inherently, because the hash-based cookie signature embeds the password hash.
 * </p>
 *
 * <p>
 * Like {@link AccountLockoutIntegrationTest}, this class avoids {@code @SecurityTest} (whose {@code @Primary}
 * in-memory user details manager would bypass the DB-backed {@code DSUserDetailsService} that remember-me
 * re-authentication must exercise) and uses an isolated in-memory database so its committed rows cannot race other
 * test classes' {@code deleteAll()} calls.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@Import({BaseTestConfiguration.class, RememberMeIntegrationTest.EventCaptureConfiguration.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remembermetest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "user.security.rememberMe.enabled=true",
        "user.security.rememberMe.key=remember-me-integration-test-key"
})
@DisplayName("Remember-Me Integration Tests (hash-based mode, real formLogin path)")
class RememberMeIntegrationTest {

    private static final String LOGIN_URL = "/user/login";
    /** Protected under the test profile's defaultAction=deny (not in unprotectedURIs). */
    private static final String PROTECTED_URL = "/protected.html";
    private static final String REMEMBER_ME_COOKIE = "remember-me";
    private static final String REMEMBER_ME_PARAMETER = "remember-me";

    private static final String TEST_EMAIL = "remember-me-user@test.com";
    private static final String PASSWORD = "CorrectPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CapturedEvents capturedEvents;

    @BeforeEach
    void seedFreshUser() {
        deleteTestUser();
        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setFirstName("Remember");
        user.setLastName("Me");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        user.setLocked(false);
        userRepository.save(user);
        capturedEvents.interactiveAuthenticationSuccessEvents.clear();
    }

    @AfterEach
    void cleanup() {
        deleteTestUser();
    }

    private void deleteTestUser() {
        User existing = userRepository.findByEmail(TEST_EMAIL);
        if (existing != null) {
            userRepository.delete(existing);
        }
    }

    @Test
    @DisplayName("should issue a remember-me cookie when the login form posts the remember-me parameter")
    void shouldIssueCookieWhenLoginPostsRememberMeParameter() throws Exception {
        Cookie cookie = loginWithRememberMe();

        assertThat(cookie).as("remember-me cookie should be issued").isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.getMaxAge()).as("cookie lifetime should be the configured 14-day default").isEqualTo(1209600);
    }

    @Test
    @DisplayName("should NOT issue a remember-me cookie when the login form omits the remember-me parameter")
    void shouldNotIssueCookieWhenParameterOmitted() throws Exception {
        Cookie cookie = mockMvc
                .perform(post(LOGIN_URL).param("username", TEST_EMAIL).param("password", PASSWORD).with(csrf()))
                .andExpect(authenticated())
                .andReturn().getResponse().getCookie(REMEMBER_ME_COOKIE);

        // This is why enabling the properties alone is not enough: the consumer's login form must post the parameter.
        assertThat(cookie).as("no remember-me cookie without the request parameter").isNull();
    }

    @Test
    @DisplayName("should auto-authenticate a session-less request from the cookie with a DSUserDetails principal")
    void shouldAutoAuthenticateFromCookieWithDSUserDetailsPrincipal() throws Exception {
        Cookie cookie = loginWithRememberMe();

        mockMvc.perform(get(PROTECTED_URL).cookie(cookie))
                .andExpect(authenticated().withAuthentication(auth -> {
                    assertThat(auth).isInstanceOf(RememberMeAuthenticationToken.class);
                    assertThat(auth.getPrincipal()).isInstanceOf(DSUserDetails.class);
                    assertThat(((DSUserDetails) auth.getPrincipal()).getUsername()).isEqualTo(TEST_EMAIL);
                }));
    }

    @Test
    @DisplayName("should publish InteractiveAuthenticationSuccessEvent on remember-me auto-login")
    void shouldPublishInteractiveAuthenticationSuccessEventOnAutoLogin() throws Exception {
        Cookie cookie = loginWithRememberMe();
        capturedEvents.interactiveAuthenticationSuccessEvents.clear();

        mockMvc.perform(get(PROTECTED_URL).cookie(cookie)).andExpect(authenticated());

        // BaseAuthenticationListener populates session profiles from this event, so remember-me logins must fire it.
        assertThat(capturedEvents.interactiveAuthenticationSuccessEvents)
                .as("remember-me auto-login should publish InteractiveAuthenticationSuccessEvent")
                .anySatisfy(event -> assertThat(event.getAuthentication()).isInstanceOf(RememberMeAuthenticationToken.class));
    }

    @Test
    @DisplayName("should reject the remember-me cookie after a password change (signature embeds the password hash)")
    void shouldRejectCookieAfterPasswordChange() throws Exception {
        Cookie cookie = loginWithRememberMe();

        User user = userRepository.findByEmail(TEST_EMAIL);
        user.setPassword(passwordEncoder.encode("CompletelyNewPass2!"));
        userRepository.save(user);

        // The hash-based cookie signature is computed over the password hash, so the old cookie no longer verifies.
        // This inherent protection is the only revocation hash-based mode has (there is no server-side state to
        // remove) — admin-initiated "sign out everywhere" cannot kill these cookies; that requires persistent mode.
        mockMvc.perform(get(PROTECTED_URL).cookie(cookie)).andExpect(unauthenticated());
    }

    private Cookie loginWithRememberMe() throws Exception {
        Cookie cookie = mockMvc
                .perform(post(LOGIN_URL).param("username", TEST_EMAIL).param("password", PASSWORD)
                        .param(REMEMBER_ME_PARAMETER, "true").with(csrf()))
                .andExpect(authenticated())
                .andReturn().getResponse().getCookie(REMEMBER_ME_COOKIE);
        assertThat(cookie).as("login with remember-me parameter should issue the cookie").isNotNull();
        return cookie;
    }

    /** Captures InteractiveAuthenticationSuccessEvent publications so tests can assert remember-me auto-login fires it. */
    static class CapturedEvents {
        final List<InteractiveAuthenticationSuccessEvent> interactiveAuthenticationSuccessEvents = new CopyOnWriteArrayList<>();

        @EventListener
        void onInteractiveAuthenticationSuccess(InteractiveAuthenticationSuccessEvent event) {
            interactiveAuthenticationSuccessEvents.add(event);
        }
    }

    @TestConfiguration
    static class EventCaptureConfiguration {
        @Bean
        CapturedEvents capturedEvents() {
            return new CapturedEvents();
        }
    }
}
