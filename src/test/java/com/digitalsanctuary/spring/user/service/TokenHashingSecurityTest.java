package com.digitalsanctuary.spring.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import com.digitalsanctuary.spring.user.mail.MailService;
import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

/**
 * Security tests for token-at-rest hashing, single-active-token enforcement, dual-read backward
 * compatibility, configurable lifetime, and atomic consume.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Token Hashing Security Tests")
class TokenHashingSecurityTest {

    private final TokenHasher tokenHasher = new TokenHasher(null);

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = UserTestDataBuilder.aUser().withId(1L).withEmail("test@example.com").enabled().build();
    }

    private Date future(int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, minutes);
        return cal.getTime();
    }

    private Date past(int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -minutes);
        return cal.getTime();
    }

    // ---------------------------------------------------------------------------------------------
    // Password Reset Token tests
    // ---------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Password Reset Token")
    class PasswordResetTokenTests {

        @Mock
        private MailService mailService;
        @Mock
        private UserVerificationService userVerificationService;
        @Mock
        private PasswordResetTokenRepository passwordTokenRepository;
        @Mock
        private ApplicationEventPublisher eventPublisher;
        @Mock
        private SessionInvalidationService sessionInvalidationService;

        private UserEmailService userEmailService;

        @BeforeEach
        void initService() {
            userEmailService = new UserEmailService(mailService, userVerificationService, passwordTokenRepository,
                    eventPublisher, sessionInvalidationService, tokenHasher);
        }

        @Test
        @DisplayName("(a) stored token value is the HASH, not the raw token")
        void shouldStoreHashedTokenNotRawToken() {
            String rawToken = "raw-reset-token-value";

            userEmailService.createPasswordResetTokenForUser(testUser, rawToken);

            ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordTokenRepository).save(captor.capture());
            String stored = captor.getValue().getToken();

            assertThat(stored).isNotEqualTo(rawToken);
            assertThat(stored).isEqualTo(tokenHasher.hash(rawToken));
        }

        @Test
        @DisplayName("(c) creating a second reset token deletes the first (single active token)")
        void shouldDeleteExistingTokenWhenCreatingNewOne() {
            userEmailService.createPasswordResetTokenForUser(testUser, "raw");

            verify(passwordTokenRepository).deleteByUser(testUser);
        }

        @Test
        @DisplayName("(e) expiry honors the configured minutes")
        void shouldHonorConfiguredLifetime() {
            ReflectionTestUtils.setField(userEmailService, "passwordResetTokenValidityMinutes", 30);
            long before = System.currentTimeMillis();

            userEmailService.createPasswordResetTokenForUser(testUser, "raw");

            ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordTokenRepository).save(captor.capture());
            long expiry = captor.getValue().getExpiryDate().getTime();
            // ~30 minutes out, well below the 1440 default
            assertThat(expiry).isBetween(before + 25L * 60 * 1000, before + 35L * 60 * 1000);
        }
    }

    @Nested
    @DisplayName("Password Reset Lookup / Consume")
    class PasswordResetLookupTests {

        @Mock
        private PasswordResetTokenRepository passwordTokenRepository;

        private UserService userService;

        @BeforeEach
        void initService() {
            userService = new UserService(null, null, passwordTokenRepository, null, null, null, null, null, null, null,
                    null, null, null, tokenHasher, null);
        }

        @Test
        @DisplayName("(b) lookup by RAW token resolves the entity stored under the HASH")
        void shouldResolveByRawTokenWhenStoredAsHash() {
            String rawToken = "raw-reset-token";
            String hashed = tokenHasher.hash(rawToken);
            PasswordResetToken entity = new PasswordResetToken();
            entity.setToken(hashed);
            entity.setUser(testUser);
            entity.setExpiryDate(future(60));
            when(passwordTokenRepository.findByToken(hashed)).thenReturn(entity);

            assertThat(userService.getUserByPasswordResetToken(rawToken)).contains(testUser);
        }

        @Test
        @DisplayName("(f) DUAL-READ: non-expired PLAINTEXT token (pre-upgrade) still resolves")
        void shouldResolvePreUpgradePlaintextTokenWhenNotExpired() {
            String rawToken = "legacy-plaintext-token";
            String hashed = tokenHasher.hash(rawToken);
            PasswordResetToken legacy = new PasswordResetToken();
            legacy.setToken(rawToken); // stored as plaintext before upgrade
            legacy.setUser(testUser);
            legacy.setExpiryDate(future(60));
            // hash lookup misses, raw lookup hits
            when(passwordTokenRepository.findByToken(hashed)).thenReturn(null);
            when(passwordTokenRepository.findByToken(rawToken)).thenReturn(legacy);

            assertThat(userService.validatePasswordResetToken(rawToken))
                    .isEqualTo(UserService.TokenValidationResult.VALID);
            assertThat(userService.getUserByPasswordResetToken(rawToken)).contains(testUser);
        }

        @Test
        @DisplayName("(g) DUAL-READ: EXPIRED plaintext token is REJECTED by validate")
        void shouldRejectExpiredPreUpgradePlaintextToken() {
            String rawToken = "legacy-expired-token";
            String hashed = tokenHasher.hash(rawToken);
            PasswordResetToken legacy = new PasswordResetToken();
            legacy.setToken(rawToken);
            legacy.setUser(testUser);
            legacy.setExpiryDate(past(60));
            when(passwordTokenRepository.findByToken(hashed)).thenReturn(null);
            when(passwordTokenRepository.findByToken(rawToken)).thenReturn(legacy);

            assertThat(userService.validatePasswordResetToken(rawToken))
                    .isEqualTo(UserService.TokenValidationResult.EXPIRED);
        }

        @Test
        @DisplayName("(d) reusing a consumed token fails (atomic consume deletes it)")
        void shouldFailWhenReusingConsumedToken() {
            String rawToken = "consume-me";
            String hashed = tokenHasher.hash(rawToken);
            PasswordResetToken entity = new PasswordResetToken();
            entity.setToken(hashed);
            entity.setUser(testUser);
            entity.setExpiryDate(future(60));

            // First consume: token found, then deleted
            when(passwordTokenRepository.findByToken(hashed)).thenReturn(entity, (PasswordResetToken) null);
            lenient().when(passwordTokenRepository.findByToken(rawToken)).thenReturn(null);

            User consumed = userService.validateAndConsumePasswordResetToken(rawToken);
            assertThat(consumed).isEqualTo(testUser);
            verify(passwordTokenRepository).delete(entity);

            // Second consume: token no longer present -> null user
            User second = userService.validateAndConsumePasswordResetToken(rawToken);
            assertThat(second).isNull();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Verification Token tests
    // ---------------------------------------------------------------------------------------------
    @Nested
    @DisplayName("Verification Token")
    class VerificationTokenTests {

        @Mock
        private UserRepository userRepository;
        @Mock
        private VerificationTokenRepository tokenRepository;

        private UserVerificationService verificationService;

        @BeforeEach
        void initService() {
            verificationService = new UserVerificationService(userRepository, tokenRepository, tokenHasher);
        }

        @Test
        @DisplayName("(a) stored verification token value is the HASH, not the raw token")
        void shouldStoreHashedVerificationToken() {
            String rawToken = "raw-verification-token";

            verificationService.createVerificationTokenForUser(testUser, rawToken);

            ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
            verify(tokenRepository).save(captor.capture());
            assertThat(captor.getValue().getToken()).isEqualTo(tokenHasher.hash(rawToken));
            assertThat(captor.getValue().getToken()).isNotEqualTo(rawToken);
        }

        @Test
        @DisplayName("(c) creating a second verification token deletes the first")
        void shouldDeleteExistingVerificationTokenWhenCreatingNewOne() {
            verificationService.createVerificationTokenForUser(testUser, "raw");

            verify(tokenRepository).deleteByUser(testUser);
        }

        @Test
        @DisplayName("(b) lookup by RAW token resolves entity stored under HASH")
        void shouldResolveVerificationByRawTokenWhenStoredAsHash() {
            String rawToken = "raw-verification";
            String hashed = tokenHasher.hash(rawToken);
            VerificationToken entity = new VerificationToken();
            entity.setToken(hashed);
            entity.setUser(testUser);
            entity.setExpiryDate(future(60));
            when(tokenRepository.findByToken(hashed)).thenReturn(entity);

            assertThat(verificationService.getUserByVerificationToken(rawToken)).isEqualTo(testUser);
        }

        @Test
        @DisplayName("(f) DUAL-READ: non-expired PLAINTEXT verification token resolves and validates")
        void shouldValidatePreUpgradePlaintextVerificationToken() {
            String rawToken = "legacy-verify-token";
            String hashed = tokenHasher.hash(rawToken);
            VerificationToken legacy = new VerificationToken();
            legacy.setToken(rawToken);
            legacy.setUser(testUser);
            legacy.setExpiryDate(future(60));
            when(tokenRepository.findByToken(hashed)).thenReturn(null);
            when(tokenRepository.findByToken(rawToken)).thenReturn(legacy);

            assertThat(verificationService.validateVerificationToken(rawToken))
                    .isEqualTo(UserService.TokenValidationResult.VALID);
        }

        @Test
        @DisplayName("(g) DUAL-READ: EXPIRED plaintext verification token is REJECTED")
        void shouldRejectExpiredPreUpgradePlaintextVerificationToken() {
            String rawToken = "legacy-verify-expired";
            String hashed = tokenHasher.hash(rawToken);
            VerificationToken legacy = new VerificationToken();
            legacy.setToken(rawToken);
            legacy.setUser(testUser);
            legacy.setExpiryDate(past(60));
            when(tokenRepository.findByToken(hashed)).thenReturn(null);
            when(tokenRepository.findByToken(rawToken)).thenReturn(legacy);

            assertThat(verificationService.validateVerificationToken(rawToken))
                    .isEqualTo(UserService.TokenValidationResult.EXPIRED);
        }

        @Test
        @DisplayName("(e) verification expiry honors the configured minutes")
        void shouldHonorConfiguredVerificationLifetime() {
            ReflectionTestUtils.setField(verificationService, "verificationTokenValidityMinutes", 45);
            long before = System.currentTimeMillis();

            verificationService.createVerificationTokenForUser(testUser, "raw");

            ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
            verify(tokenRepository).save(captor.capture());
            long expiry = captor.getValue().getExpiryDate().getTime();
            assertThat(expiry).isBetween(before + 40L * 60 * 1000, before + 50L * 60 * 1000);
        }

        @Test
        @DisplayName("(h) generateNewVerificationToken stores the HASH and exposes the RAW token via plainToken")
        void shouldStoreHashAndExposeRawTokenOnRegenerate() {
            // An existing token resolved by raw value for regeneration.
            VerificationToken existing = new VerificationToken();
            existing.setToken(tokenHasher.hash("old-raw"));
            existing.setUser(testUser);
            existing.setExpiryDate(future(10));
            when(tokenRepository.findByToken(tokenHasher.hash("old-raw"))).thenReturn(existing);
            when(tokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));

            VerificationToken regenerated = verificationService.generateNewVerificationToken("old-raw");

            // The raw token is exposed for email-link building...
            String raw = regenerated.getPlainToken();
            assertThat(raw).isNotBlank();
            // ...but the persisted column holds the HASH of that raw token, not the raw value.
            assertThat(regenerated.getToken()).isEqualTo(tokenHasher.hash(raw));
            assertThat(regenerated.getToken()).isNotEqualTo(raw);

            // And the dual-read lookup path resolves the entity from the raw token.
            when(tokenRepository.findByToken(tokenHasher.hash(raw))).thenReturn(regenerated);
            assertThat(verificationService.getUserByVerificationToken(raw)).isEqualTo(testUser);
        }

        @Test
        @DisplayName("(i) regenerated token expiry honors the configured minutes, not a hardcoded 24h")
        void shouldHonorConfiguredLifetimeOnRegenerate() {
            ReflectionTestUtils.setField(verificationService, "verificationTokenValidityMinutes", 45);
            VerificationToken existing = new VerificationToken();
            existing.setToken(tokenHasher.hash("old-raw"));
            existing.setUser(testUser);
            existing.setExpiryDate(future(10));
            when(tokenRepository.findByToken(tokenHasher.hash("old-raw"))).thenReturn(existing);
            when(tokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));
            long before = System.currentTimeMillis();

            VerificationToken regenerated = verificationService.generateNewVerificationToken("old-raw");

            long expiry = regenerated.getExpiryDate().getTime();
            // ~45 minutes out, clearly distinct from the hardcoded 24h (1440m) default.
            assertThat(expiry).isBetween(before + 40L * 60 * 1000, before + 50L * 60 * 1000);
        }

        @Test
        @DisplayName("(j) plainToken is JPA @Transient and never persisted")
        void plainTokenIsNotPersisted() throws NoSuchFieldException {
            assertThat(VerificationToken.class.getDeclaredField("plainToken")
                    .isAnnotationPresent(jakarta.persistence.Transient.class)).isTrue();
        }
    }
}
