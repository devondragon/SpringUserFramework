package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
 * Full-context integration tests proving issue #346 acceptance criteria: with CAPTCHA enabled, the
 * three unauthenticated email-sending API actions (registration, resetPassword,
 * resendRegistrationToken) reject requests that don't carry a valid CAPTCHA token, and send no email
 * while doing so, while a request carrying a valid token is allowed through unchanged.
 *
 * <p>
 * Modeled on {@link com.digitalsanctuary.spring.user.api.UserApiTest}: a manual composite of the five
 * standard test configurations (not {@code @IntegrationTest}) plus a stub {@link CaptchaService}, its
 * own isolated H2 database so it doesn't race other integration test classes' {@code deleteAll()} /
 * committed rows, and {@code @Execution(SAME_THREAD)} because the shared
 * {@link MockMailConfiguration.MockJavaMailSender} capture lists are class-scoped state and JUnit runs
 * test methods within a class concurrently by default.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:captchaprotectiontest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "user.security.captcha.enabled=true"
})
@Import({BaseTestConfiguration.class, DatabaseTestConfiguration.class, SecurityTestConfiguration.class,
        OAuth2TestConfiguration.class, MockMailConfiguration.class,
        CaptchaProtectionIntegrationTest.StubCaptchaConfiguration.class})
@DisplayName("CAPTCHA protection integration")
class CaptchaProtectionIntegrationTest {

    static final String VALID_TOKEN = "valid-captcha-token";

    @TestConfiguration
    static class StubCaptchaConfiguration {
        @Bean
        @Primary
        CaptchaService stubCaptchaService() {
            return new CaptchaService() {
                @Override
                public boolean verify(String token, HttpServletRequest request) {
                    return VALID_TOKEN.equals(token);
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

    /**
     * The bounded executor {@code MailService} dispatches {@code @Async("dsMailExecutor")} sends on
     * (e.g. the resetPassword success test's {@code sendForgotPasswordVerificationEmail} call).
     * Drained in {@link #setUp()} so a straggling send from a previous test method cannot land in
     * the shared {@link MockMailConfiguration.MockJavaMailSender} capture after it's cleared and
     * pollute a later reject-path "no email sent" assertion.
     */
    @Autowired
    @Qualifier("dsMailExecutor")
    private ThreadPoolTaskExecutor dsMailExecutor;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private TransactionTemplate txTemplate;
    private String testEmail;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        // Unique email per test method; @Execution(SAME_THREAD) serializes methods, so the
        // shared mail capture can be cleared here without racing another method.
        testEmail = "captcha.tester+" + System.nanoTime() + "@example.com";
        // Drain any in-flight/queued async send left over from a previous test method BEFORE
        // clearing the capture, so a straggler can't land after clear() and pollute this method's
        // "no email sent" assertion. junit-platform.properties randomizes method order, so a
        // preceding success-path test (e.g. shouldAllowResetPasswordWhenTokenValid) may not have
        // finished its async send by the time this method starts.
        drainMailExecutor();
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
     * Waits until {@code dsMailExecutor} has no active or queued tasks, i.e. any async
     * {@code MailService} send submitted by an earlier test method has fully completed.
     */
    private void drainMailExecutor() {
        Awaitility.await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(25))
                .until(() -> dsMailExecutor.getActiveCount() == 0 && dsMailExecutor.getThreadPoolExecutor().getQueue().isEmpty());
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

    private void assertNoEmailSent() {
        assertThat(mockMailSender().getSentMimeMessages()).isEmpty();
        assertThat(mockMailSender().getSentSimpleMessages()).isEmpty();
    }

    private String registrationJson() {
        return objectMapper.writeValueAsString(Map.of("firstName", "Captcha", "lastName", "Tester", "email",
                testEmail, "password", "StrongPassw0rd!x", "matchingPassword", "StrongPassw0rd!x"));
    }

    @Test
    void shouldRejectRegistrationWithoutTokenAndSendNoEmail() throws Exception {
        mockMvc.perform(post("/user/registration").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(CaptchaValidationInterceptor.ERROR_CODE_CAPTCHA_FAILED))
                .andExpect(jsonPath("$.messages[0]").exists());

        assertThat(userService.findUserByEmail(testEmail)).isNull();
        assertNoEmailSent();
    }

    @Test
    void shouldRejectRegistrationWithInvalidToken() throws Exception {
        mockMvc.perform(post("/user/registration").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .header(CaptchaValidationInterceptor.TOKEN_HEADER, "wrong-token").content(registrationJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CaptchaValidationInterceptor.ERROR_CODE_CAPTCHA_FAILED));

        assertThat(userService.findUserByEmail(testEmail)).isNull();
    }

    @Test
    void shouldRegisterWhenTokenValid() throws Exception {
        mockMvc.perform(post("/user/registration").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .header(CaptchaValidationInterceptor.TOKEN_HEADER, VALID_TOKEN).content(registrationJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User created = userService.findUserByEmail(testEmail);
        assertThat(created).isNotNull();
    }

    @Test
    void shouldRejectRegistrationWithMatrixParametersWithoutToken() throws Exception {
        // Regression for the matcher-mismatch bypass. Through the full stack, Spring Security's
        // default StrictHttpFirewall rejects any URL containing a semicolon before dispatch
        // (400, empty body, no resolved exception), so this variant never reaches the handler.
        // The interceptor-level defense for a consumer who relaxes the firewall (PathPattern
        // routing strips matrix parameters, so the request would then reach the handler) is
        // proven by CaptchaValidationInterceptorTest.shouldRejectWhenPathCarriesMatrixParameters,
        // which asserts the interceptor itself 403s this path.
        mockMvc.perform(post("/user/registration;jsessionid=abc").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(registrationJson()))
                .andExpect(status().isBadRequest());

        assertThat(userService.findUserByEmail(testEmail)).isNull();
        assertNoEmailSent();
    }

    @Test
    void shouldRejectRegistrationWithPercentEncodedPathWithoutToken() throws Exception {
        // Regression for the matcher-mismatch bypass: PathPattern routing URL-decodes segments, so
        // /user/%72egistration reaches the registration handler. URI.create keeps the raw encoding
        // (the String overload would re-encode the percent sign).
        mockMvc.perform(post(URI.create("/user/%72egistration")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(registrationJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CaptchaValidationInterceptor.ERROR_CODE_CAPTCHA_FAILED));

        assertThat(userService.findUserByEmail(testEmail)).isNull();
        assertNoEmailSent();
    }

    @Test
    void shouldRejectResetPasswordWithoutTokenAndSendNoEmail() throws Exception {
        mockMvc.perform(post("/user/resetPassword").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", testEmail))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CaptchaValidationInterceptor.ERROR_CODE_CAPTCHA_FAILED));
        assertNoEmailSent();
    }

    @Test
    void shouldRejectResendRegistrationTokenWithoutToken() throws Exception {
        mockMvc.perform(post("/user/resendRegistrationToken").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CaptchaValidationInterceptor.ERROR_CODE_CAPTCHA_FAILED));
    }

    @Test
    void shouldAllowResetPasswordWhenTokenValid() throws Exception {
        mockMvc.perform(post("/user/resetPassword").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .header(CaptchaValidationInterceptor.TOKEN_HEADER, VALID_TOKEN)
                .content(objectMapper.writeValueAsString(Map.of("email", testEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
