package com.digitalsanctuary.spring.user.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

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
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
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
        "user.security.captcha.enabled=true",
        // Required for the "sends no email" assertions to mean anything: this defaults to false
        // (RegistrationListener), so without it the registration path sends no verification email
        // whether or not CAPTCHA rejects, and every assertNoEmailSent() would pass vacuously.
        "user.registration.sendVerificationEmail=true",
        // Lets StubCaptchaConfiguration replace BaseTestConfiguration's no-op event publisher; see
        // realEventPublisher below.
        "spring.main.allow-bean-definition-overriding=true",
        // The passwordless registration endpoint is only reachable once a consumer opens it (its
        // own javadoc says so), which is exactly the configuration in which it needs CAPTCHA. Set
        // here rather than in the shared application.properties so no other test class's security
        // posture changes.
        // /user/savePassword is listed so shouldNotInterceptUnprotectedEndpointWhenCaptchaEnabled
        // reaches the MVC layer; it is deliberately NOT CAPTCHA-protected (token-gated, sends no
        // email), which is exactly what that test pins.
        // /user/nonexistent is opened so shouldReturnNotFoundForUnknownUserPathWhenCaptchaEnabled
        // reaches the DispatcherServlet (otherwise Spring Security 302s it to login first).
        "user.security.unprotectedURIs=/,/index.html,/css/*,/js/*,/img/*,/register.html,/user/registration,"
                + "/user/registration/passwordless,/user/resendRegistrationToken,/user/resetPassword,"
                + "/user/savePassword,/user/nonexistent,/user/login"
})
@Import({BaseTestConfiguration.class, DatabaseTestConfiguration.class, SecurityTestConfiguration.class,
        OAuth2TestConfiguration.class, MockMailConfiguration.class,
        CaptchaProtectionIntegrationTest.StubCaptchaConfiguration.class})
@DisplayName("CAPTCHA protection integration")
class CaptchaProtectionIntegrationTest {

    static final String VALID_TOKEN = "valid-captcha-token";

    @TestConfiguration
    static class StubCaptchaConfiguration {

        /**
         * Replaces {@link BaseTestConfiguration}'s {@code Mockito.spy(ApplicationEventPublisher.class)},
         * which silently swallows every published event. With that no-op publisher in place
         * {@code RegistrationListener} never runs, so no verification email is ever dispatched and
         * this class's "sends no email" assertions would hold no matter what the interceptor did.
         * Overrides by bean name (hence {@code allow-bean-definition-overriding} above); the real
         * publisher is the {@link ApplicationContext} itself.
         */
        @Bean
        @Primary
        ApplicationEventPublisher testEventPublisher(ApplicationContext applicationContext) {
            return applicationContext;
        }

        @Bean
        @Primary
        CaptchaService stubCaptchaService() {
            return new CaptchaService() {
                @Override
                public CaptchaVerification verify(CaptchaContext context) {
                    return VALID_TOKEN.equals(context.token()) ? CaptchaVerification.verified()
                            : CaptchaVerification.rejected("stub rejected token");
                }

                @Override
                public Optional<String> siteKey() {
                    return Optional.of("stub-site-key");
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

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
     * (in this class, the registration verification email sent by
     * {@link #shouldRegisterWhenTokenValid()}). Drained in {@link #setUp()} so a straggling send
     * from a previous test method cannot land in the shared
     * {@link MockMailConfiguration.MockJavaMailSender} capture after it's cleared and pollute a
     * later reject-path "no email sent" assertion.
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
        // preceding success-path test (shouldRegisterWhenTokenValid) may not have finished its
        // async send by the time this method starts.
        drainMailExecutor();
        mockMailSender().clear();
        deleteTestUser(testEmail);
    }

    @AfterEach
    void tearDown() {
        // Registration dispatches the verification email asynchronously, and that async work
        // creates a VerificationToken row. Draining first prevents a token being inserted between
        // deleteByUser and the user delete, which would fail cleanup on the FK constraint.
        drainMailExecutor();
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

    /**
     * Asserts no email was dispatched. {@code getSentPreparators()} is the load-bearing check:
     * {@code MailService} sends exclusively via {@code send(MimeMessagePreparator)}, so the MIME and
     * simple lists are never populated by production code and asserting only those would pass no
     * matter what. {@link #shouldRegisterWhenTokenValid()} is the positive control proving this
     * capture actually observes a real send.
     */
    private void assertNoEmailSent() {
        assertThat(mockMailSender().getSentPreparators()).isEmpty();
        assertThat(mockMailSender().getSentMimeMessages()).isEmpty();
        assertThat(mockMailSender().getSentSimpleMessages()).isEmpty();
    }

    /**
     * Waits for exactly one dispatched email, then asserts no more arrive. Sends are
     * {@code @Async}, so the assertion has to await rather than read the capture immediately.
     */
    private void assertOneEmailSent() {
        Awaitility.await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(25))
                .until(() -> mockMailSender().getSentPreparators().size() == 1);
        drainMailExecutor();
        assertThat(mockMailSender().getSentPreparators()).hasSize(1);
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
        assertNoEmailSent();
    }

    @Test
    void shouldRegisterWhenTokenValid() throws Exception {
        mockMvc.perform(post("/user/registration").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .header(CaptchaValidationInterceptor.TOKEN_HEADER, VALID_TOKEN).content(registrationJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User created = userService.findUserByEmail(testEmail);
        assertThat(created).isNotNull();
        // Positive control for the whole class: proves the mail capture observes a real send, so
        // the assertNoEmailSent() calls on the reject paths are meaningful rather than vacuous.
        assertOneEmailSent();
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
    void shouldAcceptTokenFromQueryParameterThroughFullStack() throws Exception {
        // The cf-turnstile-response query parameter is a documented transport, but a unit test
        // using MockHttpServletRequest.setParameter cannot distinguish query string from form body.
        // This proves it works through real parameter parsing on a JSON POST.
        mockMvc.perform(post("/user/registration?" + CaptchaValidationInterceptor.TOKEN_PARAMETER + "=" + VALID_TOKEN)
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(registrationJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(userService.findUserByEmail(testEmail)).isNotNull();
    }

    @Test
    void shouldRejectPasswordlessRegistrationWithoutTokenAndSendNoEmail() throws Exception {
        // This endpoint also creates an account and sends a verification email for an
        // unauthenticated caller, so leaving it uncovered would give an abuser a cheaper path
        // (no password in the payload) to the exact abuse CAPTCHA is here to stop.
        mockMvc.perform(post("/user/registration/passwordless").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", testEmail, "firstName", "Captcha",
                        "lastName", "Tester"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CaptchaValidationInterceptor.ERROR_CODE_CAPTCHA_FAILED));

        assertThat(userService.findUserByEmail(testEmail)).isNull();
        assertNoEmailSent();
    }

    @Test
    void shouldRejectResendRegistrationTokenWithoutToken() throws Exception {
        mockMvc.perform(post("/user/resendRegistrationToken").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(CaptchaValidationInterceptor.ERROR_CODE_CAPTCHA_FAILED));
        assertNoEmailSent();
    }

    @Test
    void shouldAllowResetPasswordWhenTokenValid() throws Exception {
        mockMvc.perform(post("/user/resetPassword").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .header(CaptchaValidationInterceptor.TOKEN_HEADER, VALID_TOKEN)
                .content(objectMapper.writeValueAsString(Map.of("email", testEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldNotInterceptUnprotectedEndpointWhenCaptchaEnabled() throws Exception {
        // Pins the interceptor's registration scope: /user/savePassword is unauthenticated and
        // deliberately not CAPTCHA-protected (it is gated by the emailed reset token, and sends no
        // email). If registration were ever broadened (e.g. to /user/**), this tokenless POST
        // would get the CAPTCHA 403 instead of reaching the handler's bean validation (400).
        mockMvc.perform(post("/user/savePassword").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundForUnknownUserPathWhenCaptchaEnabled() throws Exception {
        // The interceptor's unmatched-path branch fails closed (403) when invoked, so if the
        // registration patterns ever matched more than the CaptchaAction paths, this would turn
        // from a plain 404 into a CAPTCHA rejection.
        mockMvc.perform(post("/user/nonexistent").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRegisterSiteKeyControllerAdviceWhenCaptchaEnabled() {
        // The advice is registered by component scan plus @ConditionalOnProperty, not by
        // CaptchaAutoConfiguration, so no context-runner test can see it; this full context is
        // the only place its wiring is real. If the class moved out of the scanned package or the
        // condition changed, consumers' templates would silently lose the captchaSiteKey attribute.
        CaptchaSiteKeyControllerAdvice advice = applicationContext.getBean(CaptchaSiteKeyControllerAdvice.class);
        assertThat(advice.captchaSiteKey()).isEqualTo("stub-site-key");
    }
}
