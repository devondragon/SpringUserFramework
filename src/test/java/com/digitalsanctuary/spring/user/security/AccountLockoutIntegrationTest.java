package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.service.DSUserDetailsService;
import com.digitalsanctuary.spring.user.test.app.TestApplication;
import com.digitalsanctuary.spring.user.test.config.BaseTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test proving that account lockout (brute-force protection) is enforced through the
 * <strong>real</strong> {@code formLogin} authentication path — not just at the unit level.
 *
 * <h2>What this exercises end-to-end</h2>
 *
 * <p>
 * A real {@code POST} to the form-login processing URL drives Spring Security, which fires
 * {@code AuthenticationFailureBadCredentialsEvent} / {@code AuthenticationSuccessEvent}. The library's
 * {@code AuthenticationEventListener} reacts and calls {@code LoginAttemptService.loginFailed(...)},
 * which atomically increments the persisted {@code failedLoginAttempts} counter and, at the threshold,
 * sets {@code User.locked = true} in the database. On the next authentication attempt the DB-backed
 * {@code DSUserDetailsService.loadUserByUsername} loads the now-locked user and
 * {@code LoginHelperService.assertAccountUsable} throws {@code LockedException} — so the login is
 * rejected <em>even with the correct password</em>. That correct-password-still-rejected assertion is
 * the heart of this test: it proves lockout genuinely blocks authentication.
 * </p>
 *
 * <h2>Why this test does NOT use {@code @SecurityTest} and replaces the security beans deterministically</h2>
 *
 * <p>
 * The real lockout path requires authenticating against the library's DB-backed
 * {@link DSUserDetailsService}, so that a lockout committed to the database actually blocks the next login.
 * Two obstacles make that non-trivial in the test slice:
 * </p>
 * <ol>
 * <li>{@code SecurityTestConfiguration} (imported by {@code @SecurityTest}) registers a
 * {@code @Primary InMemoryUserDetailsManager} and a {@code @Primary TestingAuthenticationProvider} (which
 * cannot authenticate username/password tokens). Even without {@code @SecurityTest}, those beans still leak
 * in because {@code UserConfiguration}'s component scan ({@code basePackages = "com.digitalsanctuary.spring.user"})
 * does not install the Spring Boot {@code TypeExcludeFilter} and therefore sweeps up that
 * {@code @TestConfiguration} from the {@code test.config} sub-package.</li>
 * <li>That leaked {@code @Primary} provider also causes Spring Security to expose its own
 * {@code @Primary AuthenticationManager}, so naively adding another {@code @Primary AuthenticationManager}
 * collides ("found 2 primary beans").</li>
 * </ol>
 * <p>
 * To remove both problems deterministically (independent of bean import/scan ordering), a
 * {@link BeanDefinitionRegistryPostProcessor} removes the leaked {@code testUserDetailsService} and
 * {@code testAuthenticationProvider} definitions and re-registers them as the real, DB-backed
 * {@link DSUserDetailsService} and a {@code DaoAuthenticationProvider} bound to it. Spring Security then
 * auto-builds a single authentication manager from that real provider, and form login authenticates against
 * the database — so the persisted lock state gates login exactly as in production.
 * {@code @AutoConfigureMockMvc(addFilters = true)} keeps the security filter chain active;
 * {@link BaseTestConfiguration} supplies the fast BCrypt(4) {@link PasswordEncoder} and an in-memory
 * {@code SessionRegistry}.
 * </p>
 *
 * <h2>Why this class uses an isolated in-memory database</h2>
 *
 * <p>
 * The {@code AuthenticationEventListener} delegates to {@code LoginAttemptService}, whose methods are
 * {@code @Transactional} and therefore <strong>commit</strong> the failed-attempt / locked mutations to the
 * user row in their own transactions. The standard {@code test} profile points every test at the shared
 * {@code jdbc:h2:mem:testdb}; JUnit runs classes in parallel, and other integration tests call
 * {@code userRepository.deleteAll()}. Those deletes would race this class's committed lock-state rows,
 * producing intermittent failures. {@link TestPropertySource} therefore overrides {@code spring.datasource.url}
 * to a dedicated database ({@code jdbc:h2:mem:lockouttest}). The URL options are copied verbatim from the
 * shared {@code application-test.properties} URL ({@code DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE}); only the
 * database name differs. The distinct datasource also gives this class its own Spring context, so its committed
 * rows live in a database no other test's {@code deleteAll()} can see. The schema is created automatically
 * because the {@code test} profile sets {@code ddl-auto=create-drop}.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@Import({BaseTestConfiguration.class, AccountLockoutIntegrationTest.DbBackedSecurityBeanConfig.class})
@TestPropertySource(properties = {
        // Isolated in-memory DB so this class's COMMITTED lock-state rows are invisible to the shared-DB
        // integration tests' deleteAll(). Options copied verbatim from the shared testdb URL
        // (application-test.properties) — only the database name (lockouttest) differs.
        "spring.datasource.url=jdbc:h2:mem:lockouttest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        // Small, known lockout threshold so N failed attempts deterministically locks the account.
        "user.security.failedLoginAttempts=3",
        // Admin-only unlock (negative duration) so the account never auto-unlocks during the test window,
        // making the correct-password-still-rejected assertion deterministic.
        "user.security.accountLockoutDuration=-1"
})
@DisplayName("Account Lockout Integration Tests (real formLogin path)")
class AccountLockoutIntegrationTest {

    /** Must match user.security.failedLoginAttempts above. */
    private static final int MAX_FAILED_ATTEMPTS = 3;

    private static final String LOGIN_URL = "/user/login";
    private static final String FAILURE_URL = "/user/login.html?error";

    private static final String TEST_EMAIL = "lockout-victim@test.com";
    private static final String CORRECT_PASSWORD = "CorrectPass1!";
    private static final String WRONG_PASSWORD = "WrongPass9!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedFreshUnlockedUser() {
        // A dedicated user with a clean lock state guarantees repeated runs start from zero, regardless
        // of any rows left committed by a previous run in this isolated database.
        User existing = userRepository.findByEmail(TEST_EMAIL);
        if (existing != null) {
            userRepository.delete(existing);
        }

        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setFirstName("Lockout");
        user.setLastName("Victim");
        user.setPassword(passwordEncoder.encode(CORRECT_PASSWORD));
        user.setEnabled(true);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
    }

    @AfterEach
    void cleanup() {
        User user = userRepository.findByEmail(TEST_EMAIL);
        if (user != null) {
            userRepository.delete(user);
        }
    }

    @Test
    @DisplayName("should reject a correct-password login once the account is locked by failed attempts")
    void shouldRejectCorrectPasswordWhenAccountLockedByFailedAttempts() throws Exception {
        // Sanity check: the correct password authenticates before any lockout, confirming the wiring.
        mockMvc.perform(formLogin(LOGIN_URL).user("username", TEST_EMAIL).password(CORRECT_PASSWORD))
                .andExpect(authenticated());
        // Reset the counter so the sanity-check success does not consume an attempt for the rest of the test.
        seedFreshUnlockedUser();

        // Perform N failed attempts with the WRONG password to trip the lockout threshold.
        for (int attempt = 1; attempt <= MAX_FAILED_ATTEMPTS; attempt++) {
            mockMvc.perform(formLogin(LOGIN_URL).user("username", TEST_EMAIL).password(WRONG_PASSWORD))
                    .andExpect(unauthenticated())
                    .andExpect(redirectedUrl(FAILURE_URL));
        }

        // The account must now be locked in the database (the listener committed the change).
        User locked = userRepository.findByEmail(TEST_EMAIL);
        assertThat(locked).isNotNull();
        assertThat(locked.isLocked()).as("account should be locked after %d failed attempts", MAX_FAILED_ATTEMPTS).isTrue();
        assertThat(locked.getFailedLoginAttempts()).isGreaterThanOrEqualTo(MAX_FAILED_ATTEMPTS);

        // KEY ASSERTION: the (N+1)th attempt with the CORRECT password is still rejected, because the
        // account is locked — the real DSUserDetailsService load path throws LockedException.
        mockMvc.perform(formLogin(LOGIN_URL).user("username", TEST_EMAIL).password(CORRECT_PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl(FAILURE_URL));
    }

    @Test
    @DisplayName("should authenticate normally before the lockout threshold is reached")
    void shouldAuthenticateWhenBelowLockoutThreshold() throws Exception {
        // One short of the threshold still allows the correct password to succeed.
        for (int attempt = 1; attempt < MAX_FAILED_ATTEMPTS; attempt++) {
            mockMvc.perform(formLogin(LOGIN_URL).user("username", TEST_EMAIL).password(WRONG_PASSWORD))
                    .andExpect(unauthenticated());
        }

        User user = userRepository.findByEmail(TEST_EMAIL);
        assertThat(user).isNotNull();
        assertThat(user.isLocked()).as("account should not be locked below the threshold").isFalse();

        mockMvc.perform(formLogin(LOGIN_URL).user("username", TEST_EMAIL).password(CORRECT_PASSWORD))
                .andExpect(authenticated());
    }

    /**
     * Replaces the leaked {@code SecurityTestConfiguration} security beans with DB-backed equivalents via a
     * {@link BeanDefinitionRegistryPostProcessor}, which runs deterministically before bean instantiation and
     * is therefore immune to bean import/scan ordering (plain {@code @Bean} overrides proved order-dependent
     * here). After this runs, the only {@code UserDetailsService} is the real {@link DSUserDetailsService} and
     * the only {@code AuthenticationProvider} is a {@code DaoAuthenticationProvider} bound to it, so Spring
     * Security builds a single, DB-backed authentication manager — no primary-bean collision, and form login
     * sees the live, committed lock state.
     */
    @TestConfiguration
    static class DbBackedSecurityBeanConfig {

        @Bean
        static BeanDefinitionRegistryPostProcessor replaceLeakedSecurityBeans() {
            return new BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    // Remove the in-memory user store and the TestingAuthenticationProvider that leak in from
                    // SecurityTestConfiguration. Once gone, the only UserDetailsService is the real
                    // DSUserDetailsService bean (which we mark primary) and the only AuthenticationProvider is
                    // the library's auto-configured DaoAuthenticationProvider (`authProvider`), already wired to
                    // the @Primary UserDetailsService and the @Primary BCrypt(4) PasswordEncoder. Spring Security
                    // then builds a single, DB-backed authentication manager — no encoder mismatch, no primary
                    // collision — and form login sees the live, committed lock state.
                    removeIfPresent(registry, "testUserDetailsService");
                    removeIfPresent(registry, "testAuthenticationProvider");
                    markPrimary(registry, "DSUserDetailsService");
                    markPrimary(registry, "dsUserDetailsService");
                }

                @Override
                public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                    // No-op: all work is done at registry time.
                }

                private void removeIfPresent(BeanDefinitionRegistry registry, String name) {
                    if (registry.containsBeanDefinition(name)) {
                        registry.removeBeanDefinition(name);
                    }
                }

                private void markPrimary(BeanDefinitionRegistry registry, String name) {
                    if (registry.containsBeanDefinition(name)) {
                        registry.getBeanDefinition(name).setPrimary(true);
                    }
                }
            };
        }
    }
}
