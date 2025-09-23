package com.digitalsanctuary.spring.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordHistoryRepository;

@ExtendWith(MockitoExtension.class)
class PasswordPolicyServiceTest {

    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MessageSource messages;

    private PasswordPolicyService service;

    private static final Locale LOCALE = Locale.ENGLISH;

    @BeforeEach
    void setUp() {
        service = new PasswordPolicyService(passwordHistoryRepository, passwordEncoder, messages);

        // Default message resolver: return the error code as-is for deterministic
        // assertions
        // when(messages.getMessage(anyString(), any(), any(Locale.class)))
        // .thenAnswer(inv -> inv.getArgument(0, String.class));

        // Baseline config (can be overridden per test)
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "minLength", 8);
        ReflectionTestUtils.setField(service, "maxLength", 128);
        ReflectionTestUtils.setField(service, "requireUppercase", true);
        ReflectionTestUtils.setField(service, "requireLowercase", true);
        ReflectionTestUtils.setField(service, "requireDigit", true);
        ReflectionTestUtils.setField(service, "requireSpecial", true);
        ReflectionTestUtils.setField(service, "specialChars", "!@#$%^&*()_-+={}[]|:;<>,.?");
        ReflectionTestUtils.setField(service, "preventCommonPasswords", false);
        ReflectionTestUtils.setField(service, "historyCount", 0);
        ReflectionTestUtils.setField(service, "similarityThreshold", 0);
    }

    @Test
    void validate_returnsEmpty_whenPolicyDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);

        List<String> errors = service.validate(null, "anything", null, LOCALE);

        assertTrue(errors.isEmpty(), "Expected no errors when policy is disabled");
        verifyNoInteractions(passwordHistoryRepository, passwordEncoder);
    }

    @Test
    void validate_enforcesMinLength() {
        when(messages.getMessage(anyString(), any(), eq(LOCALE)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));

        // Keep only length rule active (the test password already satisfies the others)
        ReflectionTestUtils.setField(service, "minLength", 8);
        ReflectionTestUtils.setField(service, "maxLength", 128);

        // "Ab1@" = 4 chars; still contains upper/lower/digit/special so only TOO_SHORT
        // should fail
        List<String> errors = service.validate(null, "Ab1@", null, LOCALE);

        assertEquals(1, errors.size());
        assertEquals("TOO_SHORT", errors.get(0));
    }

    @Test
    void validate_enforcesMaxLength() {
        when(messages.getMessage(anyString(), any(), eq(LOCALE)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));

        ReflectionTestUtils.setField(service, "maxLength", 5);

        // 8 chars => should trigger TOO_LONG; other rules satisfied
        List<String> errors = service.validate(null, "Abcdef1@", null, LOCALE);

        assertEquals(1, errors.size());
        assertEquals("TOO_LONG", errors.get(0));
    }

    @Test
    void validate_requiresUppercase() {
        when(messages.getMessage(anyString(), any(), eq(LOCALE)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        // Isolate uppercase rule
        ReflectionTestUtils.setField(service, "requireUppercase", true);
        ReflectionTestUtils.setField(service, "requireLowercase", false);
        ReflectionTestUtils.setField(service, "requireDigit", false);
        ReflectionTestUtils.setField(service, "requireSpecial", false);
        ReflectionTestUtils.setField(service, "minLength", 1);

        List<String> errors = service.validate(null, "abc123!", null, LOCALE);

        assertEquals(1, errors.size());
        assertEquals("INSUFFICIENT_UPPERCASE", errors.get(0));
    }

    @Test
    void validate_requiresLowercase() {
        when(messages.getMessage(anyString(), any(), eq(LOCALE)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));

        ReflectionTestUtils.setField(service, "requireUppercase", false);
        ReflectionTestUtils.setField(service, "requireLowercase", true);
        ReflectionTestUtils.setField(service, "requireDigit", false);
        ReflectionTestUtils.setField(service, "requireSpecial", false);
        ReflectionTestUtils.setField(service, "minLength", 1);

        List<String> errors = service.validate(null, "ABC123!", null, LOCALE);

        assertEquals(1, errors.size());
        assertEquals("INSUFFICIENT_LOWERCASE", errors.get(0));
    }

    @Test
    void validate_requiresDigit() {
        when(messages.getMessage(anyString(), any(), eq(LOCALE)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        ReflectionTestUtils.setField(service, "requireUppercase", false);
        ReflectionTestUtils.setField(service, "requireLowercase", false);
        ReflectionTestUtils.setField(service, "requireDigit", true);
        ReflectionTestUtils.setField(service, "requireSpecial", false);
        ReflectionTestUtils.setField(service, "minLength", 1);

        List<String> errors = service.validate(null, "Abcdef@", null, LOCALE);

        assertEquals(1, errors.size());
        assertEquals("INSUFFICIENT_DIGIT", errors.get(0));
    }

    @Test
    void validate_requiresAllowedSpecial_whenRequireSpecialTrue() {
        when(messages.getMessage(anyString(), any(), eq(LOCALE)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        // Only require special; restrict allowed specials to "!@#"
        ReflectionTestUtils.setField(service, "requireUppercase", false);
        ReflectionTestUtils.setField(service, "requireLowercase", false);
        ReflectionTestUtils.setField(service, "requireDigit", false);
        ReflectionTestUtils.setField(service, "requireSpecial", true);
        ReflectionTestUtils.setField(service, "specialChars", "!@#");
        ReflectionTestUtils.setField(service, "minLength", 1);

        // Uses '$' which is NOT in allowed set => INSUFFICIENT_SPECIAL
        List<String> errors = service.validate(null, "abc1$", null, LOCALE);

        assertEquals(1, errors.size());
        assertEquals("INSUFFICIENT_SPECIAL", errors.get(0));
    }

    @Test
    void validate_rejectsCommonPasswords_whenDictionaryEnabled() {
        when(messages.getMessage(anyString(), any(), eq(LOCALE)))
                .thenAnswer(inv -> inv.getArgument(0, String.class));
        // Disable other rules so we only see the dictionary error
        ReflectionTestUtils.setField(service, "requireUppercase", false);
        ReflectionTestUtils.setField(service, "requireLowercase", false);
        ReflectionTestUtils.setField(service, "requireDigit", false);
        ReflectionTestUtils.setField(service, "requireSpecial", false);
        ReflectionTestUtils.setField(service, "minLength", 1);

        ReflectionTestUtils.setField(service, "preventCommonPasswords", true);

        // Provide an in-memory dictionary and invoke @PostConstruct
        // String dict = "password\n123456\nqwerty\n";
        String dict = "password";
        ByteArrayResource resource = new ByteArrayResource(dict.getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(service, "commonPasswordsResource", resource);
        ReflectionTestUtils.invokeMethod(service, "initCommonPasswords");

        List<String> errors = service.validate(null, "password", null, LOCALE);

        assertEquals(1, errors.size());
        assertEquals("ILLEGAL_WORD", errors.get(0));
    }

    @Test
    void validate_rejectsPasswordReuse_whenInHistory() {

        ReflectionTestUtils.setField(service, "historyCount", 3);

        User user = new User();
        user.setEmail("test@example.com");

        when(passwordHistoryRepository.findRecentPasswordHashes(eq(user), eq(PageRequest.of(0, 3))))
                .thenReturn(List.of("old-hash"));
        when(passwordEncoder.matches(eq("Abcdef1@"), eq("old-hash"))).thenReturn(true);

        // The service uses this key directly for history reuse
        when(messages.getMessage(eq("password.error.history.reuse"), any(), eq(LOCALE)))
                .thenReturn("password.error.history.reuse");

        List<String> errors = service.validate(user, "Abcdef1@", null, LOCALE);

        assertEquals(1, errors.size());
        assertEquals("password.error.history.reuse", errors.get(0));
        verify(passwordHistoryRepository).findRecentPasswordHashes(eq(user), eq(PageRequest.of(0, 3)));
        verify(passwordEncoder).matches("Abcdef1@", "old-hash");
    }

    @Test
    void validate_allowsNewPassword_whenNotInHistory() {
        ReflectionTestUtils.setField(service, "historyCount", 3);

        User user = new User();
        user.setEmail("test@example.com");

        when(passwordHistoryRepository.findRecentPasswordHashes(eq(user), eq(PageRequest.of(0, 3))))
                .thenReturn(List.of("old-hash"));
        when(passwordEncoder.matches(eq("Abcdef1@"), eq("old-hash"))).thenReturn(false);

        List<String> errors = service.validate(user, "Abcdef1@", null, LOCALE);

        assertTrue(errors.isEmpty(), "Expected no errors since password not in history");
        verify(passwordHistoryRepository).findRecentPasswordHashes(eq(user), eq(PageRequest.of(0, 3)));
        verify(passwordEncoder).matches("Abcdef1@", "old-hash");
    }

    @Test
    void validate_rejectsWhenSimilarityAboveThreshold() {
        // Turn off other constraints to isolate similarity check
        ReflectionTestUtils.setField(service, "requireUppercase", false);
        ReflectionTestUtils.setField(service, "requireLowercase", false);
        ReflectionTestUtils.setField(service, "requireDigit", false);
        ReflectionTestUtils.setField(service, "requireSpecial", false);
        ReflectionTestUtils.setField(service, "minLength", 1);
        ReflectionTestUtils.setField(service, "historyCount", 0);
        ReflectionTestUtils.setField(service, "preventCommonPasswords", false);
        ReflectionTestUtils.setField(service, "similarityThreshold", 80);

        when(messages.getMessage(eq("password.error.similarity"), any(), eq(LOCALE)))
                .thenReturn("password.error.similarity");

        // Identical strings => 100% similarity >= 80 -> fail
        List<String> errors = service.validate(null, "password", "password", LOCALE);

        assertEquals(1, errors.size());
        assertEquals("password.error.similarity", errors.get(0));
    }

    @Test
    void validate_allowsPassword_whenNotSimilar() {
        // Same setup but use a very different password
        ReflectionTestUtils.setField(service, "requireUppercase", false);
        ReflectionTestUtils.setField(service, "requireLowercase", false);
        ReflectionTestUtils.setField(service, "requireDigit", false);
        ReflectionTestUtils.setField(service, "requireSpecial", false);
        ReflectionTestUtils.setField(service, "minLength", 1);
        ReflectionTestUtils.setField(service, "historyCount", 0);
        ReflectionTestUtils.setField(service, "preventCommonPasswords", false);
        ReflectionTestUtils.setField(service, "similarityThreshold", 80);

        List<String> errors = service.validate(null, "CompletelyDifferent123!", null, LOCALE);

        assertTrue(errors.isEmpty(), "Expected no errors since password is not similar");
    }

    @Test
    void validate_returnsEmpty_forValidPassword() {
        // Baseline requires all character classes, length 8..128, allowed specials
        // includes '@'
        List<String> errors = service.validate(null, "Abcdef1@", null, LOCALE);

        assertTrue(errors.isEmpty(), "Expected no errors for a valid password");
    }
}
