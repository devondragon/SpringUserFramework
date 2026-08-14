# Typed `user.security.*` `@ConfigurationProperties` Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the `user.security.*` config namespace from ~40 scattered `@Value` injections into a cohesive family of typed `@ConfigurationProperties`, migrate all internal consumers, generate config metadata, and expose a secret-free template view object — with zero config-key changes for consumers.

**Architecture:** Three `@ConfigurationProperties` classes (`UserSecurityConfigProperties`, `PasswordPolicyConfigProperties`, `RememberMeConfigProperties`) bound to the existing keys via relaxed binding, enabled on `UserSecurityBeansAutoConfiguration`. Internal `@Value` field injections are replaced by constructor-injected beans. A `@ControllerAdvice` exposes a narrow immutable `UserSecurityUriView` record (URIs + `copyrightFirstYear`) as `${userSecurity}` so consuming templates stop using `${@environment.getProperty(...)}`.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Lombok, `spring-boot-configuration-processor`, JUnit 5 + AssertJ, `ApplicationContextRunner`, MockMvc standalone, ArchUnit.

**Spec:** `docs/design/2026-08-13-user-security-config-properties-design.md`

## Global Constraints

- **Additive only:** no config key renamed, moved, or removed. New Java fields use lowerCamel with lowercased acronyms (`loginPageUri`); relaxed binding still binds the existing camelCase keys (`user.security.loginPageURI`).
- **Do not modify** `src/main/resources/config/dsspringuserconfig.properties` — it is `@PropertySource`-loaded (Environment-visible API). Field initializers mirror it; they do not replace it.
- **Effective defaults win:** field initializers use the shipped-file values, not inline `@Value` fallbacks — `bcryptStrength=12` (not 10), `password.history-count=3` (not 0), `appUrl=""` (not null).
- **Never expose the raw properties bean to templates.** `@ToString.Exclude` on `tokenHashSecret` and remember-me `key`.
- No `@Validated`/JSR-380; no constructor-binding/records for the CP classes — mutable Lombok `@Data`, matching `MfaConfigProperties`/`CaptchaConfigProperties`.
- Conventions (`context/conventions.md`): 4-space indent, alphabetical non-wildcard imports, JavaDoc on public classes/methods, `@RequiredArgsConstructor` + `final` fields, `@Slf4j`, test names `should[Behavior]When[Condition]`, Conventional Commits, do not hand-edit versions.

---

### Task 1: `PasswordPolicyConfigProperties`

**Files:**
- Create: `src/main/java/com/digitalsanctuary/spring/user/security/PasswordPolicyConfigProperties.java`
- Test: `src/test/java/com/digitalsanctuary/spring/user/security/PasswordPolicyConfigPropertiesTest.java`

**Interfaces:**
- Produces: `PasswordPolicyConfigProperties` with getters `isEnabled()`, `getMinLength()`, `getMaxLength()`, `isRequireUppercase()`, `isRequireLowercase()`, `isRequireDigit()`, `isRequireSpecial()`, `getSpecialChars()`, `isPreventCommonPasswords()`, `getHistoryCount()`, `getSimilarityThreshold()`.

Defaults from `dsspringuserconfig.properties` lines 165-185.

- [ ] **Step 1: Write the failing binding test**

```java
package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("PasswordPolicyConfigProperties binding")
class PasswordPolicyConfigPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PasswordPolicyConfigProperties.class)
    static class TestConfig {
    }

    @Test
    void shouldApplyShippedDefaultsWhenUnset() {
        contextRunner.run(context -> {
            PasswordPolicyConfigProperties p = context.getBean(PasswordPolicyConfigProperties.class);
            assertThat(p.isEnabled()).isTrue();
            assertThat(p.getMinLength()).isEqualTo(8);
            assertThat(p.getMaxLength()).isEqualTo(128);
            assertThat(p.getHistoryCount()).isEqualTo(3);
            assertThat(p.getSimilarityThreshold()).isEqualTo(70);
        });
    }

    @Test
    void shouldBindKebabKeysWhenConfigured() {
        contextRunner.withPropertyValues("user.security.password.min-length=12",
                "user.security.password.require-special=false",
                "user.security.password.history-count=5").run(context -> {
            PasswordPolicyConfigProperties p = context.getBean(PasswordPolicyConfigProperties.class);
            assertThat(p.getMinLength()).isEqualTo(12);
            assertThat(p.isRequireSpecial()).isFalse();
            assertThat(p.getHistoryCount()).isEqualTo(5);
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.digitalsanctuary.spring.user.security.PasswordPolicyConfigPropertiesTest"`
Expected: FAIL — `PasswordPolicyConfigProperties` does not exist (compilation error).

- [ ] **Step 3: Create the class**

```java
package com.digitalsanctuary.spring.user.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Configuration properties for the password policy enforced by
 * {@link com.digitalsanctuary.spring.user.service.PasswordPolicyService}.
 *
 * <p>Bound from {@code user.security.password.*}. Defaults mirror the shipped
 * {@code config/dsspringuserconfig.properties} values.</p>
 */
@Data
@ConfigurationProperties(prefix = "user.security.password")
public class PasswordPolicyConfigProperties {

    /** Whether password-policy enforcement is active. */
    private boolean enabled = true;

    /** Minimum password length. */
    private int minLength = 8;

    /** Maximum password length. */
    private int maxLength = 128;

    /** Whether at least one uppercase character is required. */
    private boolean requireUppercase = true;

    /** Whether at least one lowercase character is required. */
    private boolean requireLowercase = true;

    /** Whether at least one digit is required. */
    private boolean requireDigit = true;

    /** Whether at least one special character is required. */
    private boolean requireSpecial = true;

    /** The set of characters treated as "special". */
    private String specialChars = "~`!@#$%^&*()_-+={}[]|\\:;\"'<>,.?/";

    /** Whether passwords are checked against the common-passwords dictionary. */
    private boolean preventCommonPasswords = true;

    /** Number of previous passwords retained and rejected on reuse. */
    private int historyCount = 3;

    /** Levenshtein similarity threshold (0-100) against username/email. */
    private int similarityThreshold = 70;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.digitalsanctuary.spring.user.security.PasswordPolicyConfigPropertiesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/digitalsanctuary/spring/user/security/PasswordPolicyConfigProperties.java \
        src/test/java/com/digitalsanctuary/spring/user/security/PasswordPolicyConfigPropertiesTest.java
git commit -m "feat: add PasswordPolicyConfigProperties bound to user.security.password.*"
```

---

### Task 2: `RememberMeConfigProperties`

**Files:**
- Create: `src/main/java/com/digitalsanctuary/spring/user/security/RememberMeConfigProperties.java`
- Test: `src/test/java/com/digitalsanctuary/spring/user/security/RememberMeConfigPropertiesTest.java`

**Interfaces:**
- Produces: `RememberMeConfigProperties` with `isEnabled()`, `getKey()`, `getTokenValiditySeconds()`, `getRememberMeParameter()`, `getRememberMeCookieName()`, `getUseSecureCookie()` (returns `Boolean`, may be null), `isUsePersistentTokens()`.

Prefix MUST be kebab (`user.security.remember-me`); the camel form is an invalid `@ConfigurationProperties` prefix and fails at startup. Relaxed binding still binds the existing `user.security.rememberMe.*` keys.

- [ ] **Step 1: Write the failing binding test**

```java
package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("RememberMeConfigProperties binding")
class RememberMeConfigPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RememberMeConfigProperties.class)
    static class TestConfig {
    }

    @Test
    void shouldApplyDefaultsWhenUnset() {
        contextRunner.run(context -> {
            RememberMeConfigProperties p = context.getBean(RememberMeConfigProperties.class);
            assertThat(p.isEnabled()).isFalse();
            assertThat(p.getKey()).isNull();
            assertThat(p.getTokenValiditySeconds()).isEqualTo(1209600);
            assertThat(p.getRememberMeParameter()).isEqualTo("remember-me");
            assertThat(p.getRememberMeCookieName()).isEqualTo("remember-me");
            assertThat(p.getUseSecureCookie()).isNull();
            assertThat(p.isUsePersistentTokens()).isFalse();
        });
    }

    @Test
    void shouldBindLegacyCamelCaseKeysViaRelaxedBinding() {
        contextRunner.withPropertyValues("user.security.rememberMe.enabled=true",
                "user.security.rememberMe.tokenValiditySeconds=60",
                "user.security.rememberMe.useSecureCookie=true").run(context -> {
            RememberMeConfigProperties p = context.getBean(RememberMeConfigProperties.class);
            assertThat(p.isEnabled()).isTrue();
            assertThat(p.getTokenValiditySeconds()).isEqualTo(60);
            assertThat(p.getUseSecureCookie()).isTrue();
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.digitalsanctuary.spring.user.security.RememberMeConfigPropertiesTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the class**

```java
package com.digitalsanctuary.spring.user.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import lombok.ToString;

/**
 * Configuration properties for Spring Security "remember-me". Bound from
 * {@code user.security.remember-me.*} (relaxed binding also accepts the legacy
 * {@code user.security.rememberMe.*} spelling). Defaults mirror the shipped
 * {@code config/dsspringuserconfig.properties} values.
 */
@Data
@ConfigurationProperties(prefix = "user.security.remember-me")
public class RememberMeConfigProperties {

    /** Whether remember-me is enabled. */
    private boolean enabled = false;

    /**
     * The remember-me signing key. Excluded from {@code toString} so the secret never leaks through bean logging.
     * When null, Spring Security generates an ephemeral key at startup.
     */
    @ToString.Exclude
    private String key;

    /** Token validity in seconds. */
    private int tokenValiditySeconds = 1209600;

    /** Name of the remember-me request parameter. */
    private String rememberMeParameter = "remember-me";

    /** Name of the remember-me cookie. */
    private String rememberMeCookieName = "remember-me";

    /**
     * Whether the remember-me cookie is marked {@code Secure}. Left null (unset) by default so Spring Security's own
     * behavior applies: the cookie is secure whenever the request that created it was made over HTTPS.
     */
    private Boolean useSecureCookie;

    /** Whether persistent (database-backed) remember-me tokens are used instead of the hash-based scheme. */
    private boolean usePersistentTokens = false;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.digitalsanctuary.spring.user.security.RememberMeConfigPropertiesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/digitalsanctuary/spring/user/security/RememberMeConfigProperties.java \
        src/test/java/com/digitalsanctuary/spring/user/security/RememberMeConfigPropertiesTest.java
git commit -m "feat: add RememberMeConfigProperties bound to user.security.remember-me.*"
```

---

### Task 3: `UserSecurityConfigProperties`

**Files:**
- Create: `src/main/java/com/digitalsanctuary/spring/user/security/UserSecurityConfigProperties.java`
- Test: `src/test/java/com/digitalsanctuary/spring/user/security/UserSecurityConfigPropertiesTest.java`

**Interfaces:**
- Produces: `UserSecurityConfigProperties` with getters for every field below. Getters `getProtectedUris()`, `getUnprotectedUris()`, `getDisableCsrfUris()` return the **filtered** list (blank segments dropped), matching the old `WebSecurityConfig.splitAndFilterProperty` behavior.

Field defaults from `dsspringuserconfig.properties` (URIs lines 129-163, scalars 70-93, 135; `bcryptStrength=12` per §6). `tokenHashSecret` and `appUrl` have no shipped scalar default beyond empty; `appUrl=""`, `tokenHashSecret=null`.

- [ ] **Step 1: Write the failing test (defaults + list filtering)**

```java
package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("UserSecurityConfigProperties binding")
class UserSecurityConfigPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UserSecurityConfigProperties.class)
    static class TestConfig {
    }

    @Test
    void shouldApplyShippedDefaultsWhenUnset() {
        contextRunner.run(context -> {
            UserSecurityConfigProperties p = context.getBean(UserSecurityConfigProperties.class);
            assertThat(p.getLoginPageUri()).isEqualTo("/user/login.html");
            assertThat(p.getRegistrationUri()).isEqualTo("/user/register.html");
            assertThat(p.getDefaultAction()).isEqualTo("deny");
            assertThat(p.getBcryptStrength()).isEqualTo(12);
            assertThat(p.getAppUrl()).isEqualTo("");
            assertThat(p.getTokenHashSecret()).isNull();
        });
    }

    @Test
    void shouldBindLegacyCamelCaseUriKeys() {
        contextRunner.withPropertyValues("user.security.loginPageURI=/custom/login").run(context -> {
            assertThat(context.getBean(UserSecurityConfigProperties.class).getLoginPageUri())
                    .isEqualTo("/custom/login");
        });
    }

    @Test
    void shouldDropBlankSegmentsFromUriLists() {
        contextRunner.withPropertyValues("user.security.unprotectedURIs=/a,,/b,").run(context -> {
            assertThat(context.getBean(UserSecurityConfigProperties.class).getUnprotectedUris())
                    .containsExactly("/a", "/b");
        });
    }

    @Test
    void shouldReturnEmptyListForBlankUriListProperty() {
        contextRunner.withPropertyValues("user.security.disableCSRFURIs=").run(context -> {
            assertThat(context.getBean(UserSecurityConfigProperties.class).getDisableCsrfUris()).isEmpty();
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.digitalsanctuary.spring.user.security.UserSecurityConfigPropertiesTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the class**

Use `@Data` for the mutable getters/setters, but override the three list getters to filter blanks. Store the raw bound list in a private field; the getter returns the filtered copy.

```java
package com.digitalsanctuary.spring.user.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import lombok.ToString;

/**
 * Configuration properties for the flat {@code user.security.*} namespace: page/action URIs, URI lists, and
 * security scalars. Password policy and remember-me live in their own classes
 * ({@link PasswordPolicyConfigProperties}, {@link RememberMeConfigProperties}).
 *
 * <p>Defaults mirror the shipped {@code config/dsspringuserconfig.properties}. The camelCase key spellings
 * (e.g. {@code user.security.loginPageURI}) are canonical; relaxed binding also accepts kebab-case.</p>
 */
@Data
@ConfigurationProperties(prefix = "user.security")
public class UserSecurityConfigProperties {

    // --- Access control ---
    /** Default filter-chain action for URIs not otherwise matched: {@code deny} or {@code allow}. */
    private String defaultAction = "deny";
    private List<String> protectedUris = new ArrayList<>();
    private List<String> unprotectedUris = new ArrayList<>();
    private List<String> disableCsrfUris = new ArrayList<>();

    // --- Page / action URIs ---
    private String loginPageUri = "/user/login.html";
    private String loginActionUri = "/user/login";
    private String loginSuccessUri = "/index.html?messageKey=message.login.success";
    private String logoutActionUri = "/user/logout";
    private String logoutSuccessUri = "/index.html?messageKey=message.logout.success";
    private boolean alwaysUseDefaultTargetUrl = false;
    private String registrationUri = "/user/register.html";
    private String registrationPendingUri = "/user/registration-pending-verification.html";
    private String registrationSuccessUri = "/user/registration-complete.html";
    private String registrationNewVerificationUri = "/user/request-new-verification-email.html";
    private String registrationConfirmUri = "/user/registrationConfirm";
    private String forgotPasswordUri = "/user/forgot-password.html";
    private String forgotPasswordPendingUri = "/user/forgot-password-pending-verification.html";
    private String forgotPasswordChangeUri = "/user/forgot-password-change.html";
    private String updateUserUri = "/user/update-user.html";
    private String updatePasswordUri = "/user/update-password.html";
    private String deleteAccountUri = "/user/delete-account.html";
    private String changePasswordUri = "/user/changePassword";

    // --- Security scalars ---
    private int bcryptStrength = 12;
    private int failedLoginAttempts = 10;
    private int accountLockoutDuration = 30;
    private int passwordResetTokenValidityMinutes = 1440;
    private boolean requireCanonicalAppUrl = false;
    private boolean testHashTime = true;
    private boolean allowInitialPasswordSetWithoutStepUp = false;
    private String appUrl = "";
    private List<String> trustedHosts = new ArrayList<>();

    /** HMAC secret used to hash password-reset tokens at rest. Excluded from {@code toString}. */
    @ToString.Exclude
    private String tokenHashSecret;

    public List<String> getProtectedUris() {
        return filterBlank(protectedUris);
    }

    public List<String> getUnprotectedUris() {
        return filterBlank(unprotectedUris);
    }

    public List<String> getDisableCsrfUris() {
        return filterBlank(disableCsrfUris);
    }

    private static List<String> filterBlank(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        List<String> filtered = new ArrayList<>(values.size());
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                filtered.add(value.trim());
            }
        }
        return filtered;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.digitalsanctuary.spring.user.security.UserSecurityConfigPropertiesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/digitalsanctuary/spring/user/security/UserSecurityConfigProperties.java \
        src/test/java/com/digitalsanctuary/spring/user/security/UserSecurityConfigPropertiesTest.java
git commit -m "feat: add UserSecurityConfigProperties bound to flat user.security.* keys"
```

---

### Task 4: Enable the beans + effective-defaults regression test

**Files:**
- Modify: `src/main/java/com/digitalsanctuary/spring/user/security/UserSecurityBeansAutoConfiguration.java:69-72` (add `@EnableConfigurationProperties`)
- Test: `src/test/java/com/digitalsanctuary/spring/user/security/UserSecurityDefaultsParityTest.java`

**Interfaces:**
- Consumes: the three classes from Tasks 1-3.
- Produces: the three beans registered in the framework context (later migration tasks inject them).

- [ ] **Step 1: Add the enablement annotation**

On `UserSecurityBeansAutoConfiguration`, above the class declaration (line 70-72), add:

```java
@EnableConfigurationProperties({UserSecurityConfigProperties.class, PasswordPolicyConfigProperties.class,
        RememberMeConfigProperties.class})
```

Add the import `org.springframework.boot.context.properties.EnableConfigurationProperties;` in alphabetical order.

- [ ] **Step 2: Write the effective-defaults parity test**

This asserts each field initializer equals the value shipped in `dsspringuserconfig.properties`, so the initializers can never drift from the file we intentionally leave intact.

```java
package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("user.security defaults parity with shipped properties file")
class UserSecurityDefaultsParityTest {

    private Properties shipped() throws Exception {
        ResourcePropertySource source =
                new ResourcePropertySource(new ClassPathResource("config/dsspringuserconfig.properties"));
        Properties props = new Properties();
        source.getSource().forEach(props::put);
        return props;
    }

    @Test
    void flatFieldInitializersMatchShippedFile() throws Exception {
        Properties p = shipped();
        UserSecurityConfigProperties bean = new UserSecurityConfigProperties();
        assertThat(bean.getLoginPageUri()).isEqualTo(p.getProperty("user.security.loginPageURI"));
        assertThat(bean.getRegistrationUri()).isEqualTo(p.getProperty("user.security.registrationURI"));
        assertThat(bean.getChangePasswordUri()).isEqualTo(p.getProperty("user.security.changePasswordURI"));
        assertThat(bean.getDefaultAction()).isEqualTo(p.getProperty("user.security.defaultAction"));
        assertThat(String.valueOf(bean.getBcryptStrength())).isEqualTo(p.getProperty("user.security.bcryptStrength"));
        assertThat(String.valueOf(bean.getFailedLoginAttempts()))
                .isEqualTo(p.getProperty("user.security.failedLoginAttempts"));
    }

    @Test
    void passwordFieldInitializersMatchShippedFile() throws Exception {
        Properties p = shipped();
        PasswordPolicyConfigProperties bean = new PasswordPolicyConfigProperties();
        assertThat(String.valueOf(bean.getMinLength())).isEqualTo(p.getProperty("user.security.password.min-length"));
        assertThat(String.valueOf(bean.getHistoryCount()))
                .isEqualTo(p.getProperty("user.security.password.history-count"));
        assertThat(String.valueOf(bean.getSimilarityThreshold()))
                .isEqualTo(p.getProperty("user.security.password.similarity-threshold"));
    }

    @Test
    void bindingTheShippedFileYieldsTheSameValuesAsTheInitializers() throws Exception {
        MockEnvironment env = new MockEnvironment();
        new ResourcePropertySource(new ClassPathResource("config/dsspringuserconfig.properties")).getSource()
                .forEach((k, v) -> env.setProperty(k, String.valueOf(v)));
        UserSecurityConfigProperties bound = Binder.get(env)
                .bind("user.security", UserSecurityConfigProperties.class).get();
        assertThat(bound.getLoginPageUri()).isEqualTo(new UserSecurityConfigProperties().getLoginPageUri());
        assertThat(bound.getBcryptStrength()).isEqualTo(new UserSecurityConfigProperties().getBcryptStrength());
    }
}
```

- [ ] **Step 3: Run the test**

Run: `./gradlew test --tests "com.digitalsanctuary.spring.user.security.UserSecurityDefaultsParityTest"`
Expected: PASS. If any assertion fails, fix the field initializer to match the shipped file (do NOT change the file).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/digitalsanctuary/spring/user/security/UserSecurityBeansAutoConfiguration.java \
        src/test/java/com/digitalsanctuary/spring/user/security/UserSecurityDefaultsParityTest.java
git commit -m "feat: register user.security config properties beans; add defaults parity test"
```

---

### Task 5: Migrate password-policy consumers

**Files:**
- Modify: `src/main/java/com/digitalsanctuary/spring/user/service/PasswordPolicyService.java:60-91,98-100`
- Modify: `src/main/java/com/digitalsanctuary/spring/user/service/UserService.java:280`

**Interfaces:**
- Consumes: `PasswordPolicyConfigProperties` (Task 1).

- [ ] **Step 1: Migrate `PasswordPolicyService`**

Delete the 11 `@Value` fields (lines 60-91). Add a final bean field to the injected group (near line 98-100):

```java
    private final PasswordPolicyConfigProperties passwordPolicy;
```

Replace every internal read of the removed fields with the bean getter: `enabled` → `passwordPolicy.isEnabled()`, `minLength` → `passwordPolicy.getMinLength()`, `maxLength` → `passwordPolicy.getMaxLength()`, `requireUppercase/Lowercase/Digit/Special` → `passwordPolicy.isRequire...()`, `specialChars` → `passwordPolicy.getSpecialChars()`, `preventCommonPasswords` → `passwordPolicy.isPreventCommonPasswords()`, `historyCount` → `passwordPolicy.getHistoryCount()`, `similarityThreshold` → `passwordPolicy.getSimilarityThreshold()`. Keep the non-`user.security` `@Value("classpath:common_passwords.txt")` field. Remove the now-unused `org.springframework.beans.factory.annotation.Value` import if no `@Value` remains — it still does (common_passwords), so keep it.

- [ ] **Step 2: Migrate `UserService`**

Replace the `@Value("${user.security.password.history-count:0}")` field (line 280) with the injected `PasswordPolicyConfigProperties` bean (add `private final PasswordPolicyConfigProperties passwordPolicy;` to its constructor group) and read `passwordPolicy.getHistoryCount()` at the use site. Remove the `@Value` field. Add the import for `PasswordPolicyConfigProperties`.

- [ ] **Step 3: Run the affected suites**

Run: `./gradlew test --tests "*PasswordPolicyService*" --tests "*UserService*"`
Expected: PASS (existing password and user-service tests exercise these paths).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/digitalsanctuary/spring/user/service/PasswordPolicyService.java \
        src/main/java/com/digitalsanctuary/spring/user/service/UserService.java
git commit -m "refactor: inject PasswordPolicyConfigProperties into password consumers"
```

---

### Task 6: Migrate `WebSecurityConfig`

**Files:**
- Modify: `src/main/java/com/digitalsanctuary/spring/user/security/WebSecurityConfig.java:53-126` and all internal references.

**Interfaces:**
- Consumes: `UserSecurityConfigProperties`, `RememberMeConfigProperties`.
- Produces: preserved public getters `getLoginPageURI()`, `getProtectedURIsProperty()`-equivalents, etc. (see Step 3) so existing callers/tests keep compiling.

This is the largest migration. `WebSecurityConfig` is `@Data @Configuration @RequiredArgsConstructor`; removing the `@Value` fields also removes their Lombok getters, an API break. Preserve the public getter surface with explicit delegating methods.

- [ ] **Step 1: Inject the beans**

Add to the final-field group (after line ~135, alongside the other `private final` collaborators):

```java
    private final UserSecurityConfigProperties userSecurityConfig;
    private final RememberMeConfigProperties rememberMeConfig;
```

- [ ] **Step 2: Remove the migrated `@Value` fields and repoint reads**

Delete lines 53-99 (`defaultAction` through `registrationNewVerificationURI`) and 104-126 (the `rememberMe.*` block). Keep the non-`user.security` fields (`oauth2Enabled` line 101-102, `devAutoLoginEnabled` line 128-129). Repoint every internal use:
- `defaultAction` → `userSecurityConfig.getDefaultAction()`
- `protectedURIsProperty`/`unprotectedURIsProperty`/`disableCSRFURIsProperty` fed through `splitAndFilterProperty(...)` → use `userSecurityConfig.getProtectedUris()` / `getUnprotectedUris()` / `getDisableCsrfUris()` directly (they are already filtered — delete the now-redundant `splitAndFilterProperty` calls for these three; if `splitAndFilterProperty` has no other callers, remove it too).
- Each `xxxURI` field → `userSecurityConfig.getXxxUri()`.
- `rememberMeEnabled` → `rememberMeConfig.isEnabled()`, `rememberMeKey` → `rememberMeConfig.getKey()`, `rememberMeTokenValiditySeconds` → `rememberMeConfig.getTokenValiditySeconds()`, `rememberMeParameter` → `rememberMeConfig.getRememberMeParameter()`, `rememberMeCookieName` → `rememberMeConfig.getRememberMeCookieName()`, `rememberMeUseSecureCookie` → `rememberMeConfig.getUseSecureCookie()`.

- [ ] **Step 3: Preserve the public getter API**

Add explicit delegating getters for anything previously exposed by `@Data` that external callers/tests may use (keep the historical camelCase names):

```java
    public String getLoginPageURI() {
        return userSecurityConfig.getLoginPageUri();
    }
    // ...one per previously-public URI getter that has external callers
```

Search for external references first: `grep -rn "getLoginPageURI\|getRegistrationURI\|getDefaultAction\|getProtectedURIsProperty" src/test src/main` and add a delegate for each name still referenced. If a name has zero references outside the class, no delegate is needed.

- [ ] **Step 4: Run the security suites**

Run: `./gradlew test --tests "*WebSecurityConfig*" --tests "*SecurityConfiguration*" --tests "*Security*"`
Expected: PASS. The filter-chain integration tests are the safety net for this migration.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/digitalsanctuary/spring/user/security/WebSecurityConfig.java
git commit -m "refactor: inject user.security config properties into WebSecurityConfig"
```

---

### Task 7: Migrate `UserSecurityBeansAutoConfiguration`

**Files:**
- Modify: `src/main/java/com/digitalsanctuary/spring/user/security/UserSecurityBeansAutoConfiguration.java:77-78,313-315`

**Interfaces:**
- Consumes: `UserSecurityConfigProperties`.

- [ ] **Step 1: Migrate `bcryptStrength`**

Add `private final UserSecurityConfigProperties userSecurityConfig;` to the final-field group (lines 74-75). Delete the `@Value("${user.security.bcryptStrength:10}")` field (77-78). In `encoder()` use `new BCryptPasswordEncoder(userSecurityConfig.getBcryptStrength())`.

- [ ] **Step 2: Migrate `appUrlResolver`**

Change the bean method to read from the injected bean instead of method-parameter `@Value`s:

```java
    @Bean
    public AppUrlResolver appUrlResolver() {
        return new AppUrlResolver(userSecurityConfig.getAppUrl(), userSecurityConfig.getTrustedHosts(),
                userSecurityConfig.isRequireCanonicalAppUrl());
    }
```

Confirm `AppUrlResolver`'s constructor signature is `(String, List<String>, boolean)` before editing; keep the exact order.

- [ ] **Step 3: Leave the persistent-token condition unchanged**

`@ConditionalOnProperty(name = "user.security.rememberMe.usePersistentTokens", havingValue = "true")` (line 205) stays as-is — conditions read the raw Environment key, not the bean. Do not change it.

- [ ] **Step 4: Run the suite**

Run: `./gradlew test --tests "*UserSecurityBeansAutoConfiguration*" --tests "*AppUrlResolver*" --tests "*Encoder*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/digitalsanctuary/spring/user/security/UserSecurityBeansAutoConfiguration.java
git commit -m "refactor: inject UserSecurityConfigProperties into UserSecurityBeansAutoConfiguration"
```

---

### Task 8: Migrate remaining service consumers

**Files:**
- Modify: `service/LoginSuccessService.java:58-62`, `service/LogoutSuccessService.java:38`, `service/LoginAttemptService.java:36,43`, `service/UserEmailService.java:92`, `service/TokenHasher.java:59`, `util/PasswordHashTimeTester.java:30`

**Interfaces:**
- Consumes: `UserSecurityConfigProperties`.

For each file: add `private final UserSecurityConfigProperties userSecurityConfig;` to the `@RequiredArgsConstructor` group (or add a constructor parameter where the class uses an explicit constructor, e.g. `TokenHasher`), delete the `user.security.*` `@Value`, and repoint reads:
- `LoginSuccessService`: `loginSuccessURI` → `userSecurityConfig.getLoginSuccessUri()`; `alwaysUseDefaultTargetUrl` → `userSecurityConfig.isAlwaysUseDefaultTargetUrl()`.
- `LogoutSuccessService`: `logoutSuccessURI` → `userSecurityConfig.getLogoutSuccessUri()`.
- `LoginAttemptService`: `failedLoginAttempts` → `userSecurityConfig.getFailedLoginAttempts()`; `accountLockoutDuration` → `userSecurityConfig.getAccountLockoutDuration()`.
- `UserEmailService`: `passwordResetTokenValidityMinutes` → `userSecurityConfig.getPasswordResetTokenValidityMinutes()`.
- `TokenHasher`: replace the constructor param `@Value("${user.security.tokenHashSecret:#{null}}") final String tokenHashSecret` with injecting `UserSecurityConfigProperties` and reading `userSecurityConfig.getTokenHashSecret()` (preserve the existing null-handling logic exactly).
- `PasswordHashTimeTester`: `testHashTime` → `userSecurityConfig.isTestHashTime()`.

- [ ] **Step 1: Apply the six migrations above.**

- [ ] **Step 2: Run the suites**

Run: `./gradlew test --tests "*LoginSuccessService*" --tests "*LogoutSuccessService*" --tests "*LoginAttemptService*" --tests "*UserEmailService*" --tests "*TokenHasher*" --tests "*PasswordHashTime*"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/digitalsanctuary/spring/user/service/ src/main/java/com/digitalsanctuary/spring/user/util/PasswordHashTimeTester.java
git commit -m "refactor: inject UserSecurityConfigProperties into remaining service consumers"
```

---

### Task 9: Migrate controller / web-config consumers

**Files:**
- Modify: `controller/UserActionController.java:52-68`, `api/UserAPI.java:100-114`, `security/HtmxAwareAuthenticationEntryPointConfiguration.java:31`, `web/WebInterceptorConfig.java:27,31`

**Interfaces:**
- Consumes: `UserSecurityConfigProperties`.

For each, inject `UserSecurityConfigProperties` and repoint the injected `@Value` **fields**. Leave any `@GetMapping("${user.security.*}")` / `@RequestMapping` **annotation placeholders** untouched — they resolve against the Environment, not the bean.
- `UserActionController`: `registrationPendingURI`→`getRegistrationPendingUri()`, `registrationSuccessURI`→`getRegistrationSuccessUri()`, `registrationNewVerificationURI`→`getRegistrationNewVerificationUri()`, `forgotPasswordPendingURI`→`getForgotPasswordPendingUri()`, `forgotPasswordChangeURI`→`getForgotPasswordChangeUri()`.
- `UserAPI`: `registrationPendingURI`, `registrationSuccessURI`, `forgotPasswordPendingURI` → corresponding getters; `allowInitialPasswordSetWithoutStepUp`→`isAllowInitialPasswordSetWithoutStepUp()`.
- `HtmxAwareAuthenticationEntryPointConfiguration`: `loginPageURI`→`getLoginPageUri()`.
- `WebInterceptorConfig`: `changePasswordURI`→`getChangePasswordUri()`, `forgotPasswordChangeURI`→`getForgotPasswordChangeUri()`.

- [ ] **Step 1: Apply the four migrations.**

- [ ] **Step 2: Run the suites**

Run: `./gradlew test --tests "*UserActionController*" --tests "*UserAPI*" --tests "*Htmx*" --tests "*WebInterceptor*"`
Expected: PASS.

- [ ] **Step 3: Verify no `user.security.*` field injections remain**

Run: `grep -rnE '@Value\("\$\{user\.security\.' src/main/java`
Expected: only annotation-placeholder usages on `@GetMapping`/`@RequestMapping`/`@ConditionalOnProperty` remain; zero injected-field `@Value`s.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/digitalsanctuary/spring/user/controller/UserActionController.java \
        src/main/java/com/digitalsanctuary/spring/user/api/UserAPI.java \
        src/main/java/com/digitalsanctuary/spring/user/security/HtmxAwareAuthenticationEntryPointConfiguration.java \
        src/main/java/com/digitalsanctuary/spring/user/web/WebInterceptorConfig.java
git commit -m "refactor: inject UserSecurityConfigProperties into controller/web consumers"
```

---

### Task 10: Replace hand-maintained metadata with generated metadata

**Files:**
- Modify: `src/main/resources/META-INF/additional-spring-configuration-metadata.json` (delete the migrated `user.security.*` entries)
- Test: `src/test/java/com/digitalsanctuary/spring/user/security/UserSecurityMetadataCoverageTest.java`
- Test resource: `src/test/resources/metadata/legacy-user-security-keys.json` (copy of the deleted entries, retained for the coverage assertion)

**Interfaces:**
- Consumes: the generated `META-INF/spring-configuration-metadata.json` produced by `spring-boot-configuration-processor` from Tasks 1-3.

- [ ] **Step 1: Snapshot the keys being removed**

Before editing, capture the property names that the new classes now own:

Run: `grep -oE '"name": "user\.security\.[^"]+"' src/main/resources/META-INF/additional-spring-configuration-metadata.json`

Save the exact list into `src/test/resources/metadata/legacy-user-security-keys.json` as a JSON array of the name strings. Exclude any `user.security.captcha.*` entries (Captcha is already `@ConfigurationProperties` and generates its own metadata) and any `user.security.mfa`/`webauthn` entries if present.

- [ ] **Step 2: Write the failing coverage test**

```java
package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("Generated metadata covers the retired hand-maintained user.security keys")
class UserSecurityMetadataCoverageTest {

    private static String canonical(String name) {
        return name.toLowerCase().replace("-", "");
    }

    @Test
    void generatedMetadataContainsEveryRetiredKey() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> legacy = mapper.readValue(
                new ClassPathResource("metadata/legacy-user-security-keys.json").getInputStream(),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));

        JsonNode generated = mapper.readTree(
                new ClassPathResource("META-INF/spring-configuration-metadata.json").getInputStream());
        Set<String> generatedNames = generated.get("properties").findValuesAsText("name").stream()
                .map(UserSecurityMetadataCoverageTest::canonical).collect(Collectors.toSet());

        assertThat(legacy.stream().map(UserSecurityMetadataCoverageTest::canonical))
                .allMatch(generatedNames::contains);
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew test --tests "*UserSecurityMetadataCoverageTest*"`
Expected: FAIL initially if `spring-configuration-metadata.json` is stale — run `./gradlew classes` first to generate it. If a legacy key is genuinely absent from the generated file, add the missing field/JavaDoc to the relevant class in Tasks 1-3.

- [ ] **Step 4: Delete the migrated entries from the additional-metadata file**

Remove exactly the property entries whose `name` is in `legacy-user-security-keys.json`. Leave all `user.audit.*`, `user.registration.*`, `user.copyrightFirstYear`, `spring.*`, and `user.security.captcha.*` entries intact.

- [ ] **Step 5: Rebuild metadata and run the test**

Run: `./gradlew clean classes && ./gradlew test --tests "*UserSecurityMetadataCoverageTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/META-INF/additional-spring-configuration-metadata.json \
        src/test/resources/metadata/legacy-user-security-keys.json \
        src/test/java/com/digitalsanctuary/spring/user/security/UserSecurityMetadataCoverageTest.java
git commit -m "chore: generate user.security config metadata; drop hand-maintained entries"
```

---

### Task 11: Template view object + `@ControllerAdvice`

**Files:**
- Create: `src/main/java/com/digitalsanctuary/spring/user/web/UserSecurityUriView.java`
- Create: `src/main/java/com/digitalsanctuary/spring/user/web/UserSecurityUriControllerAdvice.java`
- Modify: `security/UserSecurityBeansAutoConfiguration.java` (register the advice bean) OR annotate the advice as a `@ControllerAdvice` component picked up by scanning — match how `CaptchaSiteKeyControllerAdvice` is registered.
- Test: `src/test/java/com/digitalsanctuary/spring/user/web/UserSecurityUriControllerAdviceTest.java`

**Interfaces:**
- Consumes: `UserSecurityConfigProperties`, and `user.copyrightFirstYear` (read via injected `@Value`, since it is outside `user.security`).
- Produces: model attribute `userSecurity` of type `UserSecurityUriView`.

- [ ] **Step 1: Write the failing advice test**

```java
package com.digitalsanctuary.spring.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import com.digitalsanctuary.spring.user.security.UserSecurityConfigProperties;

@DisplayName("UserSecurityUriControllerAdvice")
class UserSecurityUriControllerAdviceTest {

    @Controller
    static class TestPageController {
        @GetMapping("/user-security-advice-test-page")
        public String page() {
            return "test";
        }
    }

    @Test
    void shouldExposeUserSecurityViewWithUrisAndCopyrightYear() throws Exception {
        UserSecurityConfigProperties props = new UserSecurityConfigProperties();
        UserSecurityUriControllerAdvice advice = new UserSecurityUriControllerAdvice(props, "2020");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestPageController())
                .setControllerAdvice(advice).build();

        mockMvc.perform(get("/user-security-advice-test-page")).andExpect(status().isOk())
                .andExpect(model().attributeExists("userSecurity"));

        UserSecurityUriView view = advice.userSecurity();
        assertThat(view.loginPageUri()).isEqualTo("/user/login.html");
        assertThat(view.copyrightFirstYear()).isEqualTo("2020");
    }

    @Test
    void viewMustNotExposeTheTokenHashSecret() {
        // The view is a fixed record of URIs + copyright; it has no accessor for secrets.
        for (var component : UserSecurityUriView.class.getRecordComponents()) {
            assertThat(component.getName()).doesNotContainIgnoringCase("secret");
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "*UserSecurityUriControllerAdviceTest*"`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Create the view record**

Fields: the page/action URIs consuming templates use, plus `copyrightFirstYear`. Include the full URI set so downstream templates have everything.

```java
package com.digitalsanctuary.spring.user.web;

/**
 * Immutable, secret-free view of the {@code user.security.*} URIs (plus the copyright first year) for templates.
 * Exposed as the {@code userSecurity} model attribute so views reference e.g. {@code ${userSecurity.loginPageUri}}
 * instead of SpEL bean access, which Thymeleaf 3.1.5 forbids in restricted (layout-decorated) contexts.
 */
public record UserSecurityUriView(String loginPageUri, String loginActionUri, String loginSuccessUri,
        String logoutActionUri, String logoutSuccessUri, String registrationUri, String registrationPendingUri,
        String registrationSuccessUri, String registrationNewVerificationUri, String registrationConfirmUri,
        String forgotPasswordUri, String forgotPasswordPendingUri, String forgotPasswordChangeUri,
        String updateUserUri, String updatePasswordUri, String deleteAccountUri, String changePasswordUri,
        String copyrightFirstYear) {
}
```

- [ ] **Step 4: Create the advice**

```java
package com.digitalsanctuary.spring.user.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.digitalsanctuary.spring.user.security.UserSecurityConfigProperties;

/**
 * Exposes {@link UserSecurityUriView} as the {@code userSecurity} model attribute on every {@code @Controller}
 * request, so consuming templates read framework URIs without SpEL bean access. Registered by default; opt out
 * with {@code user.security.expose-uris-to-model=false}. {@code userSecurity} is a reserved model-attribute name.
 */
@ConditionalOnProperty(name = "user.security.expose-uris-to-model", havingValue = "true", matchIfMissing = true)
@ControllerAdvice(annotations = Controller.class)
public class UserSecurityUriControllerAdvice {

    private final UserSecurityConfigProperties config;
    private final String copyrightFirstYear;

    public UserSecurityUriControllerAdvice(UserSecurityConfigProperties config,
            @Value("${user.copyrightFirstYear:}") String copyrightFirstYear) {
        this.config = config;
        this.copyrightFirstYear = copyrightFirstYear;
    }

    /**
     * @return the immutable URI view exposed to templates as {@code userSecurity}
     */
    @ModelAttribute("userSecurity")
    public UserSecurityUriView userSecurity() {
        return new UserSecurityUriView(config.getLoginPageUri(), config.getLoginActionUri(),
                config.getLoginSuccessUri(), config.getLogoutActionUri(), config.getLogoutSuccessUri(),
                config.getRegistrationUri(), config.getRegistrationPendingUri(), config.getRegistrationSuccessUri(),
                config.getRegistrationNewVerificationUri(), config.getRegistrationConfirmUri(),
                config.getForgotPasswordUri(), config.getForgotPasswordPendingUri(),
                config.getForgotPasswordChangeUri(), config.getUpdateUserUri(), config.getUpdatePasswordUri(),
                config.getDeleteAccountUri(), config.getChangePasswordUri(), copyrightFirstYear);
    }
}
```

- [ ] **Step 5: Register the advice** the same way `CaptchaSiteKeyControllerAdvice` is registered (component scan or an explicit `@Bean` in the auto-configuration). Verify by checking how the captcha advice becomes a bean and mirror it. Add the `expose-uris-to-model` key to `additional-spring-configuration-metadata.json` with a description (it has no field on a CP class).

- [ ] **Step 6: Run to verify it passes**

Run: `./gradlew test --tests "*UserSecurityUriControllerAdviceTest*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/digitalsanctuary/spring/user/web/UserSecurityUriView.java \
        src/main/java/com/digitalsanctuary/spring/user/web/UserSecurityUriControllerAdvice.java \
        src/main/resources/META-INF/additional-spring-configuration-metadata.json \
        src/test/java/com/digitalsanctuary/spring/user/web/UserSecurityUriControllerAdviceTest.java
git commit -m "feat: expose secret-free UserSecurityUriView to templates as \${userSecurity}"
```

---

### Task 12: Placeholder/bean parity guard + docs

**Files:**
- Test: `src/test/java/com/digitalsanctuary/spring/user/security/UriPlaceholderParityTest.java`
- Modify: `CONFIG.md`, `CHANGELOG.md`, `MIGRATION.md`

**Interfaces:**
- Consumes: `UserSecurityConfigProperties`, the running framework context.

- [ ] **Step 1: Write the parity test**

The 14 `@GetMapping`/`@RequestMapping` placeholders resolve the exact camelCase key; the generated metadata advertises kebab. This test fails fast if the effective Environment value for a mapping-relevant key ever diverges from the bean getter (e.g. a consumer sets only the kebab spelling).

```java
package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@DisplayName("Mapping-placeholder keys stay in sync with the bound bean")
class UriPlaceholderParityTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UserSecurityConfigProperties.class)
    static class TestConfig {
    }

    @Test
    void placeholderKeyValuesEqualBeanGetters() {
        // Keys used as @GetMapping/@RequestMapping/@ConditionalOnProperty placeholders elsewhere in the framework.
        Map<String, java.util.function.Function<UserSecurityConfigProperties, String>> mappingKeys = Map.of(
                "user.security.loginPageURI", UserSecurityConfigProperties::getLoginPageUri,
                "user.security.registrationURI", UserSecurityConfigProperties::getRegistrationUri,
                "user.security.changePasswordURI", UserSecurityConfigProperties::getChangePasswordUri,
                "user.security.forgotPasswordChangeURI", UserSecurityConfigProperties::getForgotPasswordChangeUri,
                "user.security.registrationConfirmURI", UserSecurityConfigProperties::getRegistrationConfirmUri);

        new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
                .withPropertyValues("user.security.loginPageURI=/user/login.html",
                        "user.security.registrationURI=/user/register.html",
                        "user.security.changePasswordURI=/user/changePassword",
                        "user.security.forgotPasswordChangeURI=/user/forgot-password-change.html",
                        "user.security.registrationConfirmURI=/user/registrationConfirm")
                .run(context -> {
                    Environment env = context.getEnvironment();
                    UserSecurityConfigProperties bean = context.getBean(UserSecurityConfigProperties.class);
                    mappingKeys.forEach((key, getter) -> assertThat(getter.apply(bean))
                            .as("bean value for %s must equal the placeholder-resolved Environment value", key)
                            .isEqualTo(env.getProperty(key)));
                });
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "*UriPlaceholderParityTest*"`
Expected: PASS.

- [ ] **Step 3: Update docs**

- `CONFIG.md`: add a short note that `user.security.*` is now typed configuration and that the **camelCase key spellings are canonical** (kebab is accepted via relaxed binding but the request-mapping placeholders resolve camelCase); mention the `${userSecurity}` model attribute and the `user.security.expose-uris-to-model` opt-out.
- `CHANGELOG.md`: add an entry under the unreleased section — internal refactor to typed `@ConfigurationProperties`; keys unchanged; new `${userSecurity}` model attribute; note that `WebSecurityConfig`'s URI getters are preserved as delegates.
- `MIGRATION.md`: add a "no action required" note — keys unchanged; new template attribute available; consumers using `${@environment.getProperty('user.security.*')}` in templates can switch to `${userSecurity.*}` (required on Spring Boot 4.1.0+ where Thymeleaf 3.1.5 rejects the bean-access form in layout-decorated templates).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/digitalsanctuary/spring/user/security/UriPlaceholderParityTest.java CONFIG.md CHANGELOG.md MIGRATION.md
git commit -m "test: guard uri placeholder/bean parity; docs: typed user.security config"
```

---

### Task 13: Full verification

- [ ] **Step 1: Full build + all tests**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, all tests pass (incl. ArchUnit, integration, security).

- [ ] **Step 2: Confirm no injected `user.security` `@Value` fields remain**

Run: `grep -rnE '@Value\("\$\{user\.security\.' src/main/java`
Expected: only `@GetMapping`/`@RequestMapping`/`@ConditionalOnProperty`/`@Value` annotation-placeholder usages that intentionally read the Environment; no injected-field `@Value`s on service/config classes.

- [ ] **Step 3: Confirm the shipped defaults file is untouched**

Run: `git diff --stat main -- src/main/resources/config/dsspringuserconfig.properties`
Expected: no output (file unchanged).

## Self-Review Notes

- **Spec coverage:** §4.1 classes → Tasks 1-3; §4.2 enablement → Task 4; §4.3 migration → Tasks 5-9; §4.4 defaults/metadata → Tasks 4 & 10; §4.5 template access → Task 11; §5 list filtering → Task 3; §6 effective defaults → Tasks 3-4; §7 parity guard → Task 12; §8 tests → per-task + Task 13; §9 docs → Task 12; §10 sequencing → out of scope (demo follow-up).
- **`historyCount` double-read** (UserService + PasswordPolicyService) both handled in Task 5.
- **Getter preservation** for `WebSecurityConfig` in Task 6 Step 3 avoids the `@Data` API break Fable flagged.
