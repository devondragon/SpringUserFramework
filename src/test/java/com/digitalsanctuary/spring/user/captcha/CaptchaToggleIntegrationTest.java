package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.test.app.TestApplication;
import com.digitalsanctuary.spring.user.test.config.BaseTestConfiguration;
import com.digitalsanctuary.spring.user.test.config.DatabaseTestConfiguration;
import com.digitalsanctuary.spring.user.test.config.MockMailConfiguration;
import com.digitalsanctuary.spring.user.test.config.OAuth2TestConfiguration;
import com.digitalsanctuary.spring.user.test.config.SecurityTestConfiguration;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Full-context integration test proving issue #346 acceptance criterion 4: per-action CAPTCHA
 * toggles are independent. With CAPTCHA enabled overall but the registration action's toggle turned
 * off ({@code user.security.captcha.protect.registration=false}), registration must be allowed
 * without a token while the still-protected reset-password action continues to reject.
 *
 * <p>
 * The stub {@link CaptchaService} always returns {@code false} from {@link CaptchaService#verify},
 * so no token is ever valid in this class — a passing registration request here can only be
 * explained by the toggle taking registration out of the protected set entirely, not by a token
 * happening to validate.
 * </p>
 *
 * <p>
 * Modeled on {@link CaptchaProtectionIntegrationTest} / {@code UserApiTest}: manual composite of the
 * five standard test configurations (not {@code @IntegrationTest}), its own isolated H2 database so
 * it doesn't race other integration test classes, and {@code @Execution(SAME_THREAD)} because the
 * shared {@link MockMailConfiguration.MockJavaMailSender} capture lists are class-scoped state.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:captchatoggletest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "user.security.captcha.enabled=true",
        "user.security.captcha.protect.registration=false"
})
@Import({BaseTestConfiguration.class, DatabaseTestConfiguration.class, SecurityTestConfiguration.class,
        OAuth2TestConfiguration.class, MockMailConfiguration.class,
        CaptchaToggleIntegrationTest.StubCaptchaConfiguration.class})
@DisplayName("CAPTCHA per-action toggle integration")
class CaptchaToggleIntegrationTest {

    @TestConfiguration
    static class StubCaptchaConfiguration {
        @Bean
        @Primary
        CaptchaService stubCaptchaService() {
            return new CaptchaService() {
                @Override
                public boolean verify(String token, HttpServletRequest request) {
                    // No token is ever valid in this class - the only way a protected action can
                    // succeed without one is if its toggle takes it out of the protected set.
                    return false;
                }

                @Override
                public String getSiteKey() {
                    return "stub-site-key";
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JavaMailSender mailSender;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private TransactionTemplate txTemplate;
    private String testEmail;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        // Unique email per test method; @Execution(SAME_THREAD) serializes methods, so the
        // shared mail capture can be cleared here without racing another method.
        testEmail = "captcha.toggle+" + System.nanoTime() + "@example.com";
        mockMailSender().clear();
        deleteTestUser(testEmail);
    }

    @AfterEach
    void tearDown() {
        deleteTestUser(testEmail);
    }

    private MockMailConfiguration.MockJavaMailSender mockMailSender() {
        return (MockMailConfiguration.MockJavaMailSender) mailSender;
    }

    /**
     * Hard-deletes the test user and any associated tokens (tokens first, FK order). This test is
     * not @Transactional, so cleanup runs in its own committed transaction — same pattern as
     * UserApiTest.
     */
    private void deleteTestUser(String email) {
        txTemplate.executeWithoutResult(status -> {
            User user = userRepository.findByEmail(email);
            if (user != null) {
                passwordResetTokenRepository.deleteByUser(user);
                verificationTokenRepository.deleteByUser(user);
                userRepository.delete(user);
            }
        });
    }

    private String registrationJson() {
        return objectMapper.writeValueAsString(Map.of("firstName", "Captcha", "lastName", "Toggle", "email",
                testEmail, "password", "StrongPassw0rd!x", "matchingPassword", "StrongPassw0rd!x"));
    }

    @Test
    void shouldAllowRegistrationWithoutTokenWhenRegistrationToggleOff() throws Exception {
        mockMvc.perform(post("/user/registration").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(userService.findUserByEmail(testEmail)).isNotNull();
    }

    @Test
    void shouldStillRejectResetPasswordWithoutTokenWhenOnlyRegistrationToggleOff() throws Exception {
        mockMvc.perform(post("/user/resetPassword").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", testEmail))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CaptchaValidationInterceptor.ERROR_CODE_CAPTCHA_FAILED));
    }
}
