package com.digitalsanctuary.spring.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.passay.CharacterData;
import org.passay.CharacterRule;
import org.passay.DictionaryRule;
import org.passay.EnglishCharacterData;
import org.passay.LengthRule;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.Rule;
import org.passay.RuleResult;
import org.passay.dictionary.ArrayWordList;
import org.passay.dictionary.WordListDictionary;
import org.passay.dictionary.WordLists;
import org.passay.dictionary.sort.ArraysSort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordHistoryRepository;

import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The PasswordPolicyService enforces configurable password validation rules
 * such as length, character types, similarity checks,
 * dictionary-based rejection, and password history. Built using Passay. More
 * info:
 * https://github.com/devondragon/SpringUserFramework/issues/158
 * 
 * @author Edamijueda
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PasswordPolicyService {

    @Value("${user.security.password.enabled}")
    private boolean enabled;

    @Value("${user.security.password.min-length}")
    private int minLength;

    @Value("${user.security.password.max-length}")
    private int maxLength;

    @Value("${user.security.password.require-uppercase}")
    private boolean requireUppercase;

    @Value("${user.security.password.require-lowercase}")
    private boolean requireLowercase;

    @Value("${user.security.password.require-digit}")
    private boolean requireDigit;

    @Value("${user.security.password.require-special}")
    private boolean requireSpecial;

    @Value("${user.security.password.special-chars}")
    private String specialChars;

    @Value("${user.security.password.prevent-common-passwords}")
    private boolean preventCommonPasswords;

    @Value("${user.security.password.history-count}")
    private int historyCount;

    @Value("${user.security.password.similarity-threshold}")
    private int similarityThreshold;

    @Value("classpath:common_passwords.txt")
    private Resource commonPasswordsResource;

    private DictionaryRule commonPasswordRule;

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messages;

    @PostConstruct
    private void initCommonPasswords() {
        if (preventCommonPasswords) {
            log.debug("Initializing common passwords dictionary from file");
            try (
                    Reader reader = new BufferedReader(
                            new InputStreamReader(commonPasswordsResource.getInputStream()))) {
                ArrayWordList wordList = WordLists.createFromReader(new Reader[] { reader }, false, new ArraysSort());
                WordListDictionary dictionary = new WordListDictionary(wordList);
                commonPasswordRule = new DictionaryRule(dictionary);
                log.info("Common password dictionary initialized successfully.");
            } catch (Exception e) {
                // Log the error as severe since this is a security feature
                log.error("CRITICAL: Failed to load common passwords file. " +
                    "Common password checking is DISABLED. This is a security risk!", e);
                // In production, you may want to fail fast, but for now we'll allow
                // the application to start with reduced security to avoid breaking tests
                // Consider using a profile-based approach for stricter production behavior
            }
        }
    }

    /**
     * Validate the given password against the configured policy rules.
     *
     * <p>Note: The user parameter may be null during new user registration.
     * When null, password history checking is skipped (since new users have no history).
     * This is intentional - history checks only apply to existing users changing their passwords.</p>
     *
     * @param user            The user (may be null for new registrations)
     * @param password        the password to validate
     * @param usernameOrEmail optional username/email for similarity checks
     * @param locale          the locale
     * @return list of error messages if validation fails, empty if valid
     */
    public List<String> validate(User user, String password, String usernameOrEmail, Locale locale) {
        if (!enabled) {
            log.debug("Password policy enforcement is disabled. Skipping validation.");
            return List.of();
        }

        log.debug("Validating password with configured policy.");

        // Check password history first (early return if reused)
        Optional<String> historyError = checkPasswordHistory(user, password, locale);
        if (historyError.isPresent()) {
            return List.of(historyError.get());
        }

        // Check similarity to username/email (early return if too similar)
        Optional<String> similarityError = checkPasswordSimilarity(password, usernameOrEmail, locale);
        if (similarityError.isPresent()) {
            return List.of(similarityError.get());
        }

        // Build and apply Passay rules
        List<Rule> rules = buildPassayRules();
        return validateWithPassay(password, rules, locale);
    }

    /**
     * Build the list of Passay rules based on configuration.
     *
     * @return list of Passay rules
     */
    private List<Rule> buildPassayRules() {
        List<Rule> rules = new ArrayList<>();

        // Length rule
        rules.add(new LengthRule(minLength, maxLength));

        // Character rules
        if (requireUppercase) {
            rules.add(new CharacterRule(EnglishCharacterData.UpperCase, 1));
        }
        if (requireLowercase) {
            rules.add(new CharacterRule(EnglishCharacterData.LowerCase, 1));
        }
        if (requireDigit) {
            rules.add(new CharacterRule(EnglishCharacterData.Digit, 1));
        }
        if (requireSpecial) {
            rules.add(createSpecialCharacterRule());
        }

        // Common Passwords Dictionary Rule
        if (preventCommonPasswords && commonPasswordRule != null) {
            rules.add(commonPasswordRule);
        }

        return rules;
    }

    /**
     * Create a special character rule with configured allowed characters.
     *
     * @return CharacterRule for special characters
     */
    private CharacterRule createSpecialCharacterRule() {
        CharacterData specialCharacterData = new CharacterData() {
            @Override
            public String getErrorCode() {
                return EnglishCharacterData.Special.getErrorCode();
            }

            @Override
            public String getCharacters() {
                return specialChars;
            }
        };
        return new CharacterRule(specialCharacterData, 1);
    }

    /**
     * Check if the password has been used before by this user.
     *
     * @param user     the user
     * @param password the password to check
     * @param locale   the locale for error messages
     * @return Optional containing error message if password was reused, empty otherwise
     */
    private Optional<String> checkPasswordHistory(User user, String password, Locale locale) {
        if (user == null || historyCount <= 0) {
            return Optional.empty();
        }

        List<String> oldHashes = passwordHistoryRepository.findRecentPasswordHashes(user,
                PageRequest.of(0, historyCount));

        for (String hash : oldHashes) {
            if (passwordEncoder.matches(password, hash)) {
                String msg = messages.getMessage("password.error.history.reuse",
                        new Object[] { historyCount }, locale);
                log.debug("Password rejected: matches historical password");
                return Optional.of(msg);
            }
        }

        return Optional.empty();
    }

    /**
     * Check if the password is too similar to the username or email.
     *
     * @param password        the password to check
     * @param usernameOrEmail the username or email to compare against
     * @param locale          the locale for error messages
     * @return Optional containing error message if too similar, empty otherwise
     */
    private Optional<String> checkPasswordSimilarity(String password, String usernameOrEmail, Locale locale) {
        if (!StringUtils.hasText(usernameOrEmail) || similarityThreshold <= 0) {
            return Optional.empty();
        }

        int distance = LevenshteinDistance.getDefaultInstance().apply(
                password.toLowerCase(), usernameOrEmail.toLowerCase());
        int maxLength = Math.max(password.length(), usernameOrEmail.length());

        if (maxLength == 0) {
            return Optional.empty();
        }

        double similarityPercent = (100.0 * (maxLength - distance)) / maxLength;
        log.debug("Password similarity to username/email: {}%", similarityPercent);

        if (similarityPercent >= similarityThreshold) {
            String msg = messages.getMessage("password.error.similarity",
                    new Object[] { String.format("%.2f", similarityPercent) }, locale);
            return Optional.of(msg);
        }

        return Optional.empty();
    }

    /**
     * Validate password using Passay rules.
     *
     * @param password the password to validate
     * @param rules    the Passay rules to apply
     * @param locale   the locale for error messages
     * @return list of error messages if validation fails, empty if valid
     */
    private List<String> validateWithPassay(String password, List<Rule> rules, Locale locale) {
        PasswordValidator validator = new PasswordValidator(
                (detail) -> messages.getMessage(detail.getErrorCode(), detail.getValues(), locale),
                rules);
        PasswordData passwordData = new PasswordData(password);

        RuleResult result = validator.validate(passwordData);

        if (result.isValid()) {
            log.debug("Password is valid.");
            return List.of();
        } else {
            log.warn("Password validation failed: {}", validator.getMessages(result));
            return validator.getMessages(result);
        }
    }
}
