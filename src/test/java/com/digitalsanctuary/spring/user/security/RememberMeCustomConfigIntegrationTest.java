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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

/**
 * Proves the remember-me cookie/parameter configuration properties actually bind — a silently dropped {@code @Value}
 * on {@code rememberMeParameter}, {@code rememberMeCookieName}, {@code tokenValiditySeconds}, or
 * {@code useSecureCookie} would pass every default-value test, so this class overrides all of them and asserts the
 * issued cookie reflects each override.
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@Import(BaseTestConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:remembermecustomtest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "user.security.rememberMe.enabled=true",
        "user.security.rememberMe.key=remember-me-custom-config-test-key",
        "user.security.rememberMe.tokenValiditySeconds=3600",
        "user.security.rememberMe.rememberMeParameter=keep-me-signed-in",
        "user.security.rememberMe.rememberMeCookieName=stay-signed-in",
        "user.security.rememberMe.useSecureCookie=true"
})
@DisplayName("Remember-Me Integration Tests (non-default cookie/parameter configuration)")
class RememberMeCustomConfigIntegrationTest {

    private static final String LOGIN_URL = "/user/login";
    private static final String TEST_EMAIL = "custom-remember-me-user@test.com";
    private static final String PASSWORD = "CorrectPass1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedFreshUser() {
        deleteTestUser();
        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setFirstName("Custom");
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

    @Test
    @DisplayName("should issue a cookie honoring the custom name, validity, and Secure flag when the custom parameter is posted")
    void shouldHonorCustomParameterCookieNameValidityAndSecureFlag() throws Exception {
        MvcResult result = mockMvc
                .perform(post(LOGIN_URL).param("username", TEST_EMAIL).param("password", PASSWORD)
                        .param("keep-me-signed-in", "true").with(csrf()))
                .andExpect(authenticated())
                .andReturn();

        Cookie customCookie = result.getResponse().getCookie("stay-signed-in");
        assertThat(customCookie).as("cookie should be issued under the configured custom name").isNotNull();
        assertThat(customCookie.getMaxAge()).as("cookie lifetime should honor tokenValiditySeconds").isEqualTo(3600);
        assertThat(customCookie.getSecure()).as("useSecureCookie=true should force the Secure flag even on an HTTP test request").isTrue();
        assertThat(result.getResponse().getCookie("remember-me")).as("nothing should be issued under the default cookie name").isNull();
    }

    @Test
    @DisplayName("should ignore the default remember-me parameter when a custom parameter name is configured")
    void shouldIgnoreDefaultParameterName() throws Exception {
        MvcResult result = mockMvc
                .perform(post(LOGIN_URL).param("username", TEST_EMAIL).param("password", PASSWORD)
                        .param("remember-me", "true").with(csrf()))
                .andExpect(authenticated())
                .andReturn();

        assertThat(result.getResponse().getCookie("stay-signed-in")).as("the default parameter name must not trigger issuance").isNull();
        assertThat(result.getResponse().getCookie("remember-me")).isNull();
    }
}
