package com.digitalsanctuary.spring.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.TokenHasher;
import com.digitalsanctuary.spring.user.service.UserEmailService;
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
 * Integration tests for {@link UserAPI}.
 *
 * <h2>Why this test was previously disabled</h2>
 *
 * <p>
 * The original version relied on a custom {@code jdbc.Jdbc} helper whose {@code ConnectionManager}
 * opens a <em>hardcoded</em> MariaDB connection ({@code jdbc:mariadb://127.0.0.1:3306/springuser}).
 * That fought the test infrastructure, which runs against an in-memory H2 database: the Jdbc-based
 * {@code @AfterAll} cleanup could not connect to (or see) the test slice's H2 data, so the class was
 * disabled. It also POSTed {@code application/x-www-form-urlencoded} bodies, but the API now consumes
 * JSON ({@code @RequestBody}).
 * </p>
 *
 * <h2>How it was ported to the standard infrastructure</h2>
 *
 * <p>
 * This test boots the same full context the {@code @IntegrationTest} composite annotation provides
 * (the five standard test configurations against H2 + {@link MockMvc}), but <strong>intentionally
 * does NOT use {@code @Transactional}</strong>. The password-management service methods
 * ({@link UserService#registerNewUserAccount} and {@link UserService#changeUserPassword}) are
 * declared {@code @Transactional(propagation = NOT_SUPPORTED)} and commit their work in short,
 * independent transactions. Under an ambient test transaction those independent commits interleave
 * with the test's suspended persistence context and the password change is masked — a test-only
 * artifact, not a production bug (verified: the same flow persists correctly with no ambient
 * transaction). Running without {@code @Transactional} mirrors production exactly.
 * </p>
 *
 * <p>
 * Because nothing rolls back automatically, the test data is cleaned up explicitly via
 * repository-based deletes (replacing the old {@code Jdbc} helper). Each test uses a unique email and
 * cleanup runs both before and after every test for isolation.
 * </p>
 *
 * <h2>Why this class uses an isolated in-memory database</h2>
 *
 * <p>
 * The standard {@code test} profile points every test at the <em>shared</em> in-memory database
 * {@code jdbc:h2:mem:testdb}. Because this class is intentionally non-{@code @Transactional} (see
 * above), its registration / password rows are <strong>committed</strong> into that shared database.
 * JUnit runs test classes in parallel, and other integration tests (e.g.
 * {@code WebAuthnFeatureEnabledIntegrationTest}) call {@code userRepository.deleteAll()}; that delete
 * races with this class's committed-but-not-yet-cleaned rows, producing intermittent FK /
 * optimistic-lock failures. To remove the race entirely, {@link TestPropertySource} below overrides
 * {@code spring.datasource.url} to a <strong>dedicated</strong> in-memory database
 * ({@code jdbc:h2:mem:userapitest}). The URL options are copied verbatim from the shared
 * {@code application-test.properties} URL ({@code DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE}); only the
 * database name differs. Distinct datasource properties also give this class its own Spring context,
 * so its committed rows live in a database no other test's {@code deleteAll()} can see. The schema is
 * created automatically because the {@code test} profile sets {@code ddl-auto=create-drop}, which
 * applies to whatever datasource URL the context boots.
 * </p>
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Isolated in-memory DB so this non-@Transactional class's COMMITTED rows are invisible to the
        // shared-DB integration tests' deleteAll(). Options copied verbatim from the shared testdb URL
        // (application-test.properties) — only the database name (userapitest) differs.
        "spring.datasource.url=jdbc:h2:mem:userapitest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@Import({
        BaseTestConfiguration.class,
        DatabaseTestConfiguration.class,
        SecurityTestConfiguration.class,
        OAuth2TestConfiguration.class,
        MockMailConfiguration.class
})
@DisplayName("UserAPI Integration Tests")
class UserApiTest {

    private static final String URL = "/user";

    /**
     * Passwords that satisfy the default password policy (upper, lower, digit, special, min length
     * 8) so registration and password changes succeed.
     */
    private static final String VALID_PASSWORD = "ValidPass1!";
    private static final String NEW_VALID_PASSWORD = "NewValidPass2!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserEmailService userEmailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private TransactionTemplate txTemplate;
    private String testEmail;
    private UserDto baseTestUser;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        // Unique email per test method so a committed registration in one test never collides with
        // another. The nanoTime suffix is sufficient for sequential per-method isolation.
        testEmail = "api.tester+" + System.nanoTime() + "@example.com";

        baseTestUser = new UserDto();
        baseTestUser.setFirstName("Api");
        baseTestUser.setLastName("Tester");
        baseTestUser.setEmail(testEmail);
        baseTestUser.setPassword(VALID_PASSWORD);
        baseTestUser.setMatchingPassword(VALID_PASSWORD);

        deleteTestUser(testEmail);
    }

    @AfterEach
    void tearDown() {
        deleteTestUser(testEmail);
    }

    /**
     * Hard-deletes the test user and any associated tokens. Tokens are deleted before the user to
     * satisfy the FK from the token tables to {@code user_account}. This replaces the custom
     * {@code Jdbc} helper the disabled version used.
     */
    private void deleteTestUser(String email) {
        // This test is not @Transactional, so cleanup must run in its own committed transaction.
        txTemplate.executeWithoutResult(status -> {
            User user = userRepository.findByEmail(email);
            if (user != null) {
                passwordResetTokenRepository.deleteByUser(user);
                verificationTokenRepository.deleteByUser(user);
                userRepository.delete(user);
            }
        });
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("Should register a brand new user account")
        void shouldRegisterNewUser() throws Exception {
            mockMvc.perform(post(URL + "/registration")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(baseTestUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.messages[0]")
                            .value("If your email address is eligible, you will receive a verification email shortly."));

            assertThat(userService.findUserByEmail(testEmail)).isNotNull();
        }

        @Test
        @DisplayName("Should return the same uniform 200 body for an existing email (anti-enumeration) and create no duplicate")
        void shouldReturnUniformResponseForExistingUser() throws Exception {
            // Register once.
            userService.registerNewUserAccount(baseTestUser);
            Long existingId = userService.findUserByEmail(testEmail).getId();

            // Register again with the same email - response must be indistinguishable from a new registration.
            mockMvc.perform(post(URL + "/registration")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(baseTestUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.messages[0]")
                            .value("If your email address is eligible, you will receive a verification email shortly."));

            // No duplicate account was created - the existing account is untouched.
            assertThat(userService.findUserByEmail(testEmail).getId()).isEqualTo(existingId);
        }

        @Test
        @DisplayName("Should return 400 Bad Request for an invalid (empty) registration")
        void shouldRejectInvalidUser() throws Exception {
            // An empty DTO fails bean validation (@NotBlank firstName/lastName/email/password) before
            // reaching the controller body, so the framework returns a 400.
            mockMvc.perform(post(URL + "/registration")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UserDto())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 with a structured error body when password and matchingPassword mismatch (class-level @PasswordMatches)")
        void shouldReturnBadRequestWhenPasswordsDoNotMatch() throws Exception {
            // The class-level @PasswordMatches constraint produces a GLOBAL (not field) binding error.
            // The library's validation advice must surface this as a structured 400 - not a 500.
            UserDto mismatched = new UserDto();
            mismatched.setFirstName("Api");
            mismatched.setLastName("Tester");
            mismatched.setEmail(testEmail);
            mismatched.setPassword(VALID_PASSWORD);
            mismatched.setMatchingPassword(NEW_VALID_PASSWORD);

            mockMvc.perform(post(URL + "/registration")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(mismatched)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.errors").isNotEmpty());

            // The mismatched registration must not have created an account.
            assertThat(userService.findUserByEmail(testEmail)).isNull();
        }
    }

    @Nested
    @DisplayName("Reset Password Request")
    class ResetPasswordRequest {

        @Test
        @DisplayName("Should accept a reset-password request and return the pending page")
        void shouldAcceptResetPasswordRequest() throws Exception {
            userService.registerNewUserAccount(baseTestUser);

            Map<String, String> body = Map.of("email", testEmail);

            mockMvc.perform(post(URL + "/resetPassword")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.redirectUrl").value("/user/forgot-password-pending-verification.html"))
                    .andExpect(jsonPath("$.messages[0]").value("If account exists, password reset email has been sent!"));
        }
    }

    @Nested
    @DisplayName("Update Password (authenticated)")
    class UpdatePassword {

        @Test
        @DisplayName("Should update the password with a valid old password")
        void shouldUpdatePasswordWhenOldPasswordValid() throws Exception {
            User user = userService.registerNewUserAccount(baseTestUser);

            Map<String, String> body = Map.of(
                    "oldPassword", VALID_PASSWORD,
                    "newPassword", NEW_VALID_PASSWORD);

            mockMvc.perform(post(URL + "/updatePassword")
                            .with(user(new DSUserDetails(user)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // The stored hash must now verify against the new password.
            User reloaded = userService.findUserByEmail(testEmail);
            assertThat(userService.checkIfValidOldPassword(reloaded, NEW_VALID_PASSWORD)).isTrue();
        }

        @Test
        @DisplayName("Should return 400 when the old password is incorrect")
        void shouldRejectUpdateWhenOldPasswordInvalid() throws Exception {
            User user = userService.registerNewUserAccount(baseTestUser);

            Map<String, String> body = Map.of(
                    "oldPassword", "WrongOldPass9!",
                    "newPassword", NEW_VALID_PASSWORD);

            mockMvc.perform(post(URL + "/updatePassword")
                            .with(user(new DSUserDetails(user)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("Save Password (password-reset completion)")
    class SavePassword {

        /**
         * Creates a password reset token for the user the way production code does (hashed at rest via
         * {@link UserEmailService#createPasswordResetTokenForUser}) and returns the raw token, which is
         * what the dual-read lookup supports.
         */
        private String createResetTokenForUser(User user) {
            String rawToken = "raw-reset-token-" + System.nanoTime();
            userEmailService.createPasswordResetTokenForUser(user, rawToken);
            return rawToken;
        }

        private Map<String, String> savePasswordBody(String token, String newPassword, String confirmPassword) {
            return Map.of(
                    "token", token,
                    "newPassword", newPassword,
                    "confirmPassword", confirmPassword);
        }

        @Test
        @DisplayName("Should change the password and consume the token for a valid reset token")
        void shouldChangePasswordWithValidToken() throws Exception {
            User user = userService.registerNewUserAccount(baseTestUser);
            String rawToken = createResetTokenForUser(user);

            // Sanity: the token is stored hashed, not raw.
            assertThat(passwordResetTokenRepository.findByToken(tokenHasher.hash(rawToken))).isNotNull();

            mockMvc.perform(post(URL + "/savePassword")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(savePasswordBody(rawToken, NEW_VALID_PASSWORD, NEW_VALID_PASSWORD))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Password was actually changed (the new password now verifies / the stored hash changed).
            User reloaded = userService.findUserByEmail(testEmail);
            assertThat(userService.checkIfValidOldPassword(reloaded, NEW_VALID_PASSWORD)).isTrue();

            // Token was consumed/deleted (neither hashed nor raw form remains).
            assertThat(passwordResetTokenRepository.findByToken(tokenHasher.hash(rawToken))).isNull();
            assertThat(passwordResetTokenRepository.findByToken(rawToken)).isNull();
        }

        @Test
        @DisplayName("Should reject a reused token on the second attempt")
        void shouldRejectReusedToken() throws Exception {
            User user = userService.registerNewUserAccount(baseTestUser);
            String rawToken = createResetTokenForUser(user);

            // First use succeeds.
            mockMvc.perform(post(URL + "/savePassword")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(savePasswordBody(rawToken, NEW_VALID_PASSWORD, NEW_VALID_PASSWORD))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Second use of the same (now consumed) token is rejected.
            mockMvc.perform(post(URL + "/savePassword")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(savePasswordBody(rawToken, "AnotherPass3!", "AnotherPass3!"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Should reject an expired token")
        void shouldRejectExpiredToken() throws Exception {
            User user = userService.registerNewUserAccount(baseTestUser);
            String rawToken = "raw-expired-token-" + System.nanoTime();

            // Persist a token whose stored value matches the dual-read hash lookup but is already
            // expired. Wrapped in its own transaction since this test is not @Transactional.
            txTemplate.executeWithoutResult(status -> {
                PasswordResetToken expired = new PasswordResetToken(tokenHasher.hash(rawToken), user);
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DATE, -1);
                expired.setExpiryDate(new Date(cal.getTimeInMillis()));
                passwordResetTokenRepository.save(expired);
            });

            mockMvc.perform(post(URL + "/savePassword")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(savePasswordBody(rawToken, NEW_VALID_PASSWORD, NEW_VALID_PASSWORD))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("Should return 400 when the new passwords do not match")
        void shouldRejectMismatchedPasswords() throws Exception {
            User user = userService.registerNewUserAccount(baseTestUser);
            String rawToken = createResetTokenForUser(user);

            mockMvc.perform(post(URL + "/savePassword")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(savePasswordBody(rawToken, NEW_VALID_PASSWORD, "DifferentPass4!"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(1));

            // The token must NOT have been consumed by a failed (mismatched) attempt.
            assertThat(passwordResetTokenRepository.findByToken(tokenHasher.hash(rawToken))).isNotNull();
        }
    }
}
