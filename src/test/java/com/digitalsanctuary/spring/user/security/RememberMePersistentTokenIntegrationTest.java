package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.SessionInvalidationService;
import com.digitalsanctuary.spring.user.test.app.TestApplication;
import com.digitalsanctuary.spring.user.test.config.BaseTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.Cookie;

/**
 * Integration tests for persistent-token remember-me ({@code user.security.rememberMe.usePersistentTokens=true}).
 *
 * <p>
 * What this proves end-to-end: the opt-in property auto-configures a {@link JdbcTokenRepositoryImpl} backed by the
 * application {@code DataSource}; a remember-me login stores a token row in {@code persistent_logins}; the cookie
 * auto-authenticates a session-less request with a {@link DSUserDetails} principal; and — the reason the token store
 * and revocation ship together — {@link SessionInvalidationService} removes the stored tokens on both invalidation
 * paths, after which the old cookie is rejected. Persistent tokens do not embed the password hash, so without that
 * revocation they would survive a password change.
 * </p>
 *
 * <p>
 * The {@code persistent_logins} table is created in {@code @BeforeEach} because it is not a JPA entity (Hibernate's
 * {@code ddl-auto} does not know it); in production the DDL ships in {@code db-scripts/}.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@Import(BaseTestConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remembermepersistenttest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "user.security.rememberMe.enabled=true",
        "user.security.rememberMe.key=remember-me-persistent-test-key",
        "user.security.rememberMe.usePersistentTokens=true"
})
@DisplayName("Remember-Me Integration Tests (persistent-token mode)")
class RememberMePersistentTokenIntegrationTest {

    private static final String LOGIN_URL = "/user/login";
    private static final String PROTECTED_URL = "/protected.html";
    private static final String REMEMBER_ME_COOKIE = "remember-me";

    private static final String TEST_EMAIL = "persistent-remember-me-user@test.com";
    private static final String PASSWORD = "CorrectPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PersistentTokenRepository persistentTokenRepository;

    @Autowired
    private SessionInvalidationService sessionInvalidationService;

    @BeforeEach
    void setUp() {
        // Mirrors db-scripts DDL; not a JPA entity, so Hibernate ddl-auto cannot create it.
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS persistent_logins ("
                + "username VARCHAR(255) NOT NULL, series VARCHAR(64) PRIMARY KEY, "
                + "token VARCHAR(64) NOT NULL, last_used TIMESTAMP NOT NULL)");
        jdbcTemplate.execute("DELETE FROM persistent_logins");

        deleteTestUser();
        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setFirstName("Persistent");
        user.setLastName("RememberMe");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        user.setLocked(false);
        userRepository.save(user);
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

    private int storedTokenCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM persistent_logins WHERE username = ?", Integer.class, TEST_EMAIL);
        return count != null ? count : 0;
    }

    @Test
    @DisplayName("should auto-configure JdbcTokenRepositoryImpl when usePersistentTokens is enabled")
    void shouldAutoConfigureJdbcTokenRepository() {
        assertThat(persistentTokenRepository).isInstanceOf(JdbcTokenRepositoryImpl.class);
    }

    @Test
    @DisplayName("should store a token row on remember-me login and auto-authenticate from the cookie")
    void shouldStoreTokenAndAutoAuthenticateFromCookie() throws Exception {
        Cookie cookie = loginWithRememberMe();

        assertThat(storedTokenCount()).as("a persistent token row should be stored, keyed by email").isEqualTo(1);

        mockMvc.perform(get(PROTECTED_URL).cookie(cookie))
                .andExpect(authenticated().withAuthentication(auth -> {
                    assertThat(auth).isInstanceOf(RememberMeAuthenticationToken.class);
                    assertThat(auth.getPrincipal()).isInstanceOf(DSUserDetails.class);
                    assertThat(((DSUserDetails) auth.getPrincipal()).getUsername()).isEqualTo(TEST_EMAIL);
                }));
    }

    @Test
    @DisplayName("should revoke stored tokens on invalidateUserSessions and reject the old cookie")
    void shouldRevokeTokensOnInvalidateUserSessions() throws Exception {
        Cookie cookie = loginWithRememberMe();
        assertThat(storedTokenCount()).isEqualTo(1);

        // Admin-initiated "sign this user out everywhere": without token revocation the remember-me cookie would
        // silently re-authenticate the user on the next request, defeating the invalidation.
        sessionInvalidationService.invalidateUserSessions(userRepository.findByEmail(TEST_EMAIL));

        assertThat(storedTokenCount()).as("invalidateUserSessions should remove the user's persistent tokens").isZero();
        mockMvc.perform(get(PROTECTED_URL).cookie(cookie)).andExpect(unauthenticated());
    }

    @Test
    @DisplayName("should revoke stored tokens on invalidateSessionsAfterPasswordChange and reject the old cookie")
    void shouldRevokeTokensOnPasswordChange() throws Exception {
        Cookie cookie = loginWithRememberMe();
        assertThat(storedTokenCount()).isEqualTo(1);

        // Persistent tokens do NOT embed the password hash (unlike hash-based cookies), so this explicit revocation
        // is what keeps a password change meaningful in persistent mode — the reason store + revocation ship together.
        sessionInvalidationService.invalidateSessionsAfterPasswordChange(userRepository.findByEmail(TEST_EMAIL));

        assertThat(storedTokenCount()).as("password change should remove the user's persistent tokens").isZero();
        mockMvc.perform(get(PROTECTED_URL).cookie(cookie)).andExpect(unauthenticated());
    }

    private Cookie loginWithRememberMe() throws Exception {
        Cookie cookie = mockMvc
                .perform(post(LOGIN_URL).param("username", TEST_EMAIL).param("password", PASSWORD)
                        .param("remember-me", "true").with(csrf()))
                .andExpect(authenticated())
                .andReturn().getResponse().getCookie(REMEMBER_ME_COOKIE);
        assertThat(cookie).as("login with remember-me parameter should issue the cookie").isNotNull();
        return cookie;
    }
}
