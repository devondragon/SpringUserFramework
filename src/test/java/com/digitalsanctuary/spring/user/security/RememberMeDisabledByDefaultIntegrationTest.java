package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Proves the shipped default — remember-me disabled — stays inert. Every consuming application upgrades with
 * {@code user.security.rememberMe.enabled=false} (and no key), so the guard in
 * {@code WebSecurityConfig.buildSecurityFilterChain} must keep the feature fully off: a login that posts the
 * {@code remember-me} parameter anyway succeeds normally but is issued no remember-me cookie, and no
 * {@link PersistentTokenRepository} bean exists. A regression that dropped or inverted that guard would silently
 * start issuing 14-day persistent-auth cookies to every consumer on upgrade; the other remember-me test classes all
 * set {@code enabled=true}, so only this class would catch it.
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@Import(BaseTestConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remembermedisabledtest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        // Deliberately NO user.security.rememberMe.* properties: this class tests the shipped defaults.
})
@DisplayName("Remember-Me Integration Tests (disabled by default)")
class RememberMeDisabledByDefaultIntegrationTest {

    private static final String LOGIN_URL = "/user/login";
    private static final String TEST_EMAIL = "no-remember-me-user@test.com";
    private static final String PASSWORD = "CorrectPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private PersistentTokenRepository persistentTokenRepository;

    @BeforeEach
    void seedFreshUser() {
        deleteTestUser();
        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setFirstName("NoRemember");
        user.setLastName("Me");
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

    @Test
    @DisplayName("should log in normally but issue NO remember-me cookie when the feature is left at its disabled default")
    void shouldNotIssueCookieWithDefaultConfigEvenWhenParameterPosted() throws Exception {
        MvcResult result = mockMvc
                .perform(post(LOGIN_URL).param("username", TEST_EMAIL).param("password", PASSWORD)
                        .param("remember-me", "true").with(csrf()))
                .andExpect(authenticated())
                .andReturn();

        assertThat(result.getResponse().getCookie("remember-me"))
                .as("default config (enabled=false, no key) must never issue a remember-me cookie").isNull();
    }

    @Test
    @DisplayName("should not create a PersistentTokenRepository bean under default configuration")
    void shouldNotCreatePersistentTokenRepositoryByDefault() {
        assertThat(persistentTokenRepository).as("no token repository bean without usePersistentTokens opt-in").isNull();
    }
}
