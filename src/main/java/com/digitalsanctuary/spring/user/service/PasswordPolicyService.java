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
                ArrayWordList wordList = WordLists.createFromReader(new Reader[] { reader }, false);
                WordListDictionary dictionary = new WordListDictionary(wordList);
                commonPasswordRule = new DictionaryRule(dictionary);
                log.info("Common password dictionary initialized successfully.");
            } catch (Exception e) {
                log.error("Failed to load common passwords file", e);
            }
        }
    }

    /**
     * Validate the given password against the configured policy rules.
     *
     * @param user            The user
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
            rules.add(new CharacterRule(specialCharacterData, 1));
        }

        // Common Passwords Dictionary Rule
        if (preventCommonPasswords && commonPasswordRule != null) {
            rules.add(commonPasswordRule);
        }

        // Password History Check
        if (user != null && historyCount > 0) {
            List<String> oldHashes = passwordHistoryRepository.findRecentPasswordHashes(user,
                    PageRequest.of(0, historyCount));
            for (String hash : oldHashes) {
                if (passwordEncoder.matches(password, hash)) {
                    String msg = messages.getMessage("password.error.history.reuse", new Object[] { historyCount },
                            locale);
                    return List.of(msg);
                }
            }
        }

        // Similarity Check
        if (StringUtils.hasText(usernameOrEmail) && similarityThreshold > 0) {
            int distance = LevenshteinDistance.getDefaultInstance().apply(password.toLowerCase(),
                    usernameOrEmail.toLowerCase());
            int maxLength = Math.max(password.length(), usernameOrEmail.length());

            if (maxLength > 0) {
                double similarityPercent = (100.0 * (maxLength - distance)) / maxLength;
                log.debug("Password similarity to username/email: {}%", similarityPercent);

                if (similarityPercent >= similarityThreshold) {
                    String msg = messages.getMessage("password.error.similarity",
                            new Object[] { String.format("%.2f", similarityPercent) }, locale);
                    return List.of(msg);
                }
            }
        }

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
