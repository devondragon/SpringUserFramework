# Migration Guide

This guide covers migrating applications using the Spring User Framework between major versions.

## Table of Contents

- [Migration Guide](#migration-guide)
  - [Table of Contents](#table-of-contents)
  - [Migrating to 4.0.x (Spring Boot 4.0)](#migrating-to-40x-spring-boot-40)
    - [Prerequisites](#prerequisites)
    - [Step 1: Update Java Version](#step-1-update-java-version)
    - [Step 2: Update Dependencies](#step-2-update-dependencies)
    - [Step 3: Spring Security 7 Changes](#step-3-spring-security-7-changes)
      - [URL Pattern Requirements](#url-pattern-requirements)
      - [Security Configuration Changes](#security-configuration-changes)
    - [Step 4: Update Test Infrastructure](#step-4-update-test-infrastructure)
      - [Test Dependency Changes](#test-dependency-changes)
      - [Test Annotation Import Changes](#test-annotation-import-changes)
    - [Step 5: Jackson 3 Changes](#step-5-jackson-3-changes)
    - [Step 6: API Changes](#step-6-api-changes)
      - [Profile Update Endpoint](#profile-update-endpoint)
    - [Step 7: Configuration Changes](#step-7-configuration-changes)
  - [For Developers Extending the Framework](#for-developers-extending-the-framework)
    - [Extending Security Configuration](#extending-security-configuration)
    - [Custom User Services](#custom-user-services)
    - [Custom Controllers](#custom-controllers)
    - [Event Listeners](#event-listeners)
  - [Troubleshooting](#troubleshooting)
    - [Common Issues](#common-issues)
  - [Version Compatibility Matrix](#version-compatibility-matrix)

## Migrating to 5.0.x

This section covers migrating from Spring User Framework 4.4.x to 5.0.x. Version 5.0.0 is a **major release** containing breaking changes. Spring Boot compatibility is unchanged from 4.4.x (Spring Boot 4.0 on Java 21+, and Spring Boot 3.5 on Java 17+); the major-version bump reflects this library's own API/contract changes, not a Spring Boot major change.

> **Note on versioning:** This library follows Semantic Versioning for its own API. Its major version is deliberately **not** tied to Spring Boot's major version — a single release line supports more than one Spring Boot major. See the Version Compatibility Matrix for supported Spring Boot versions.

### ⚠️ ACTION REQUIRED: Reverse-proxy deployments must configure a canonical app URL

Password-reset and email-verification links are now built from a configured canonical base URL instead of the inbound request's `Host` / `X-Forwarded-Host` header (CWE-640, host-header / reset poisoning). By default `X-Forwarded-Host` is **ignored** unless the host is explicitly allow-listed.

**If your application runs behind a reverse proxy or load balancer, you must take action or your reset/verification links will break:**

- **Recommended:** set the canonical base URL:
  ```properties
  user.security.appUrl=https://app.example.com
  ```
  When set, `X-Forwarded-Host` is ignored entirely and all email links use this URL.
- **Alternative:** allow-list the trusted forwarded host(s):
  ```properties
  user.security.trustedHosts=app.example.com,www.example.com
  ```
  `X-Forwarded-Host` is then honored only for hosts in this list; all others fall back to the container's own server name.

Local development with no proxy needs no change. `UserUtils.getAppUrl(HttpServletRequest)` is deprecated in favor of `AppUrlResolver`.

### Database schema: unique token constraint

The `token` column on both `password_reset_token` and `verification_token` now carries a **UNIQUE + NOT NULL** constraint. The stored value is always a fixed-length hash (introduced in 4.4.0), so the column length is predictable and the index is safe.

**Applications using `spring.jpa.hibernate.ddl-auto=update` (or `create`/`create-drop`):** Hibernate will add the unique index automatically on startup — no manual action required.

**Applications managing schema manually (Flyway, Liquibase, or `ddl-auto=validate`/`none`):** apply the following DDL before upgrading and before starting the application:

```sql
-- Ensure no existing null or duplicate token values exist first.
-- (4.4.0 already enforces one active token per user, so duplicates are unlikely.)
ALTER TABLE password_reset_token ALTER COLUMN token SET NOT NULL;
ALTER TABLE verification_token ALTER COLUMN token SET NOT NULL;
CREATE UNIQUE INDEX ux_password_reset_token_token ON password_reset_token (token);
CREATE UNIQUE INDEX ux_verification_token_token ON verification_token (token);
```

> **Note:** The DDL syntax above is standard SQL (compatible with PostgreSQL and MariaDB/MySQL). For MySQL/MariaDB, `ALTER COLUMN token SET NOT NULL` may need to include the full column definition, e.g. `MODIFY COLUMN token VARCHAR(255) NOT NULL`.

If your database contains rows with a `null` token value (possible only if tokens were created before 4.4.0 without the hash path), delete or back-fill those rows before applying the NOT NULL constraint.

<!-- Additional 5.0.x migration notes are appended below as tasks land. -->

## Migrating to 4.0.x (Spring Boot 4.0)

This section covers migrating from Spring User Framework 3.x (Spring Boot 3.x) to 4.x (Spring Boot 4.0).

### Prerequisites

Before starting the migration:

1. Ensure your application is running on the latest 3.5.x version
2. Review your custom security configurations
3. Audit any code that extends framework classes
4. Back up your database (schema changes are minimal but recommended)

### Step 1: Update Java Version

**Spring Boot 4.0 requires Java 21 or higher.**

Update your build configuration:

**Gradle:**
```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

**Maven:**
```xml
<properties>
    <java.version>21</java.version>
</properties>
```

Ensure your CI/CD pipelines and deployment environments support Java 21.

### Step 2: Update Dependencies

Update the framework dependency version:

**Gradle:**
```groovy
implementation 'com.digitalsanctuary:ds-spring-user-framework:4.0.0'
```

**Maven:**
```xml
<dependency>
    <groupId>com.digitalsanctuary</groupId>
    <artifactId>ds-spring-user-framework</artifactId>
    <version>4.0.0</version>
</dependency>
```

Update Spring Boot:
```groovy
plugins {
    id 'org.springframework.boot' version '4.0.0'
}
```

#### Passay upgraded to 2.0.0

The framework's transitive Passay dependency was upgraded from 1.x to **2.0.0**, which **relocated
several packages** (e.g. `org.passay.CharacterData` → `org.passay.data.CharacterData`,
`org.passay.CharacterRule` → `org.passay.rule.CharacterRule`). This only affects you if your
application **uses Passay directly** (e.g. for custom password rules):

- If you declared your own `org.passay:passay` dependency at a 1.x version, **remove the explicit
  pin** (let it inherit 2.0.0 transitively) or bump it to `2.0.0`. Pinning an older version forces a
  conflicting downgrade that breaks the framework's `PasswordPolicyService` at runtime
  (`ClassNotFoundException: org.passay.data.CharacterData`).
- Update your own Passay imports to the new 2.0.0 package names.

Applications that do not use Passay directly need no changes.

### Step 3: Spring Security 7 Changes

Spring Boot 4.0 includes Spring Security 7, which has breaking changes from Spring Security 6.x.

#### URL Pattern Requirements

**All URL patterns must now start with `/`.**

This affects:
- `user.security.unprotectedURIs` configuration
- `user.security.protectedURIs` configuration
- Any custom security matchers in your code

**Before (3.x):**
```yaml
user:
  security:
    unprotectedURIs: /,/index.html,/css/**,/js/**,error,error.html
```

**After (4.x):**
```yaml
user:
  security:
    unprotectedURIs: /,/index.html,/css/**,/js/**,/error,/error.html
```

Note the `/error` and `/error.html` now have leading slashes.

#### Security Configuration Changes

If you have custom security configuration extending or working with the framework:

**Deprecated methods removed:**
- `authorizeRequests()` → use `authorizeHttpRequests()`
- `antMatchers()` → use `requestMatchers()`
- `mvcMatchers()` → use `requestMatchers()`

**Example migration:**

```java
// Before (3.x)
http.authorizeRequests()
    .antMatchers("/public/**").permitAll()
    .anyRequest().authenticated();

// After (4.x)
http.authorizeHttpRequests(authz -> authz
    .requestMatchers("/public/**").permitAll()
    .anyRequest().authenticated());
```

### Step 4: Update Test Infrastructure

Spring Boot 4.0 introduces modular test packages.

#### Test Dependency Changes

Add the new modular test starters:

**Gradle:**
```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.springframework.boot:spring-boot-data-jpa-test'
testImplementation 'org.springframework.boot:spring-boot-webmvc-test'
testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
testImplementation 'org.springframework.security:spring-security-test'
```

**Maven:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
```

#### Test Annotation Import Changes

Update imports for test annotations:

| Annotation | Old Package (3.x) | New Package (4.x) |
|------------|-------------------|-------------------|
| `@AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@DataJpaTest` | `org.springframework.boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.test.autoconfigure.jdbc` | `org.springframework.boot.jdbc.test.autoconfigure` |

**Example:**
```java
// Before (3.x)
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

// After (4.x)
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
```

### Step 5: Jackson 3 Changes

Spring Boot 4.0 uses Jackson 3.x for JSON processing.

**ObjectMapper instantiation:**
```java
// Before (Jackson 2.x)
ObjectMapper mapper = new ObjectMapper();

// After (Jackson 3.x)
ObjectMapper mapper = JsonMapper.builder().build();
```

**Package changes:**
- Some classes moved from `com.fasterxml.jackson` to new packages
- Check any custom serializers/deserializers

### Step 6: API Changes

#### Profile Update Endpoint

The `/user/updateUser` endpoint now accepts `UserProfileUpdateDto` instead of `UserDto`.

**Before (3.x):**
```json
POST /user/updateUser
{
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "password": "...",
    "matchingPassword": "..."
}
```

**After (4.x):**
```json
POST /user/updateUser
{
    "firstName": "John",
    "lastName": "Doe"
}
```

This change improves security by not requiring password fields for profile updates.

**Update your frontend code** if you're calling this endpoint directly.

### Step 7: Configuration Changes

Review your `application.yml` for any deprecated properties:

| Deprecated Property | Replacement |
|---------------------|-------------|
| (none currently) | - |

Most configuration properties remain unchanged between 3.x and 4.x.

## For Developers Extending the Framework

If you've extended framework classes or implemented custom functionality, review these sections carefully.

### Extending Security Configuration

If you have a custom `WebSecurityConfig` or extend the framework's security configuration:

1. **Ensure all URL patterns start with `/`**
2. **Update to lambda DSL style** (required in Spring Security 7)
3. **Review method security annotations** - `@PreAuthorize`, `@PostAuthorize` unchanged

#### SecurityFilterChain override model (4.x)

The library contributes its `SecurityFilterChain` through a dedicated auto-configuration with two important properties:

- **Ordered at low precedence.** The library's chain is registered with `@Order(Ordered.LOWEST_PRECEDENCE - 5)` — the same low precedence Spring Boot uses for its own default servlet security chain. This value is sourced from `SecurityFilterProperties.BASIC_AUTH_ORDER`; the constant was `SecurityProperties.BASIC_AUTH_ORDER` in Spring Boot 3.x and was relocated to `SecurityFilterProperties.BASIC_AUTH_ORDER` in Spring Boot 4.0 (still `Ordered.LOWEST_PRECEDENCE - 5`). The library's chain has **no `securityMatcher`**, so it is the catch-all: any consumer-supplied chain with a `securityMatcher` and a lower (higher-precedence) `@Order` is consulted first by Spring Security's `FilterChainProxy`, and unmatched requests fall through to the library's chain.
- **Backs off only on a same-named replacement.** The library's chain bean is named `securityFilterChain` and is annotated `@ConditionalOnMissingBean(name = "securityFilterChain")`. It backs off **only** when you define a `SecurityFilterChain` bean **named `securityFilterChain`** (an explicit full replacement). Defining additional, differently-named chains does **not** suppress it.

> **Behavior change vs. earlier 4.x pre-releases:** an earlier iteration used `@ConditionalOnMissingBean(SecurityFilterChain.class)` (type-based), which suppressed the entire library chain as soon as you defined *any* `SecurityFilterChain` — even a narrow one (e.g. a test-API or actuator chain). That silently left the library's URIs unprotected. The conditional is now **name-based** so the standard Spring Security multi-chain `@Order` layering pattern works as expected.

This gives you two ways to customize security:

**Option A — Add additional, narrower chains alongside the library's (recommended for most layering).**

Define your own `SecurityFilterChain` with a `securityMatcher` scoping it to a subset of requests and a higher-precedence (lower) `@Order`. Give it **any name other than `securityFilterChain`**. Both chains coexist: your chain handles its matched requests, and the library's catch-all chain keeps protecting everything else (login, registration, password reset, profile, etc.).

```java
@Configuration
@EnableWebSecurity
public class ApiSecurityConfig {

    @Bean
    @Order(1) // higher precedence than the library's catch-all chain
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")          // scopes this chain to /api/**
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().hasRole("ADMIN"))
            .csrf(csrf -> csrf.disable());
        return http.build();                     // bean name is "apiSecurityFilterChain" -> library chain stays active
    }
}
```

**Option B — Fully replace the library's chain (you own all the rules).**

Define your own `SecurityFilterChain` bean **named `securityFilterChain`**. The library's chain backs off entirely and does **not** apply any of its rules; you are now responsible for protecting *all* URIs, including the framework's endpoints.

```java
@Configuration
@EnableWebSecurity
public class CustomSecurityConfig {

    @Bean // bean name MUST be "securityFilterChain" to replace the library's chain
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // You must also permit/secure the framework's own URIs here,
                // since the library chain no longer applies:
                .requestMatchers("/user/registration", "/user/login", "/user/resetPassword").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/user/login.html").permitAll());
        return http.build();
    }
}
```

For most applications that only need to *add* a few rules, the simplest path is to rely on the library's chain and the `user.security.*` properties (`protectedURIs`, `unprotectedURIs`, `defaultAction`, etc.) rather than defining your own `SecurityFilterChain` at all.

### Custom User Services

If you extend `UserService` or implement custom user management:

1. **Method signatures unchanged** - Core service methods remain compatible
2. **Password encoding** - Still uses BCrypt, no changes required
3. **User entity** - No schema changes required

#### Password hashing moved outside the transaction (perf)

To avoid holding a pooled DB connection during the deliberately slow bcrypt hash, password
hashing now runs *outside* the database transaction. As a result `registerNewUserAccount`,
`changeUserPassword`, and `setInitialPassword` are annotated `Propagation.NOT_SUPPORTED` and
delegate the actual DB write to short, separate transactions of their own.

**Consumer-facing behavior change:** these three methods no longer participate in a caller's
transaction. If you previously called one of them from inside your own `@Transactional`, that
outer transaction is now suspended for the call and the registration / password change commits
independently — **an outer rollback will not roll back the registration or password change.**

Most consumers call these methods from controllers (which are not transactional) and are
unaffected. If you depend on enlisting these operations in a surrounding transaction, you will
need to restructure that flow.

#### `UserEmailService` constructor gained a `TokenHasher` parameter (breaking for subclasses)

Verification and password-reset tokens are now hashed at rest. `UserEmailService` therefore takes
an additional `TokenHasher` constructor parameter. **If you subclass `UserEmailService`**, your
subclass constructor must accept and pass through the new parameter:

```java
public CustomUserEmailService(
        MailService mailService,
        UserVerificationService userVerificationService,
        PasswordResetTokenRepository passwordTokenRepository,
        ApplicationEventPublisher eventPublisher,
        SessionInvalidationService sessionInvalidationService,
        TokenHasher tokenHasher) {                    // <-- new parameter
    super(mailService, userVerificationService, passwordTokenRepository,
            eventPublisher, sessionInvalidationService, tokenHasher);
}
```

`TokenHasher` is a framework `@Component`, so it is available for injection. Consumers that do not
subclass `UserEmailService` are unaffected. The hashing is backward compatible at runtime: tokens
issued before the upgrade (stored in plaintext) are still resolved via a dual-read lookup and remain
usable until they expire.

#### Sessions on password change: current session is now preserved (OWASP)

A self-service password change (and removing a password to go passwordless) invalidates the user's
**other** sessions but, by default, now **preserves and regenerates the current session** rather than
logging the user out of the device they just used. This follows OWASP guidance (regenerate the
current session id, invalidate the rest) and is a friendlier default.

To restore the previous "invalidate every session, including the current one" behavior, set:

```yaml
user:
  session:
    invalidation:
      keep-current-session-on-password-change: false
```

Token-based password **resets** (the forgot-password flow) are unaffected: there is no authenticated
current session to preserve, so all of the user's sessions are invalidated as before.

### Custom Controllers

If you have controllers that extend or work alongside framework controllers:

1. **DTOs** - Update any code using `UserDto` for profile updates to use `UserProfileUpdateDto`
2. **Validation** - Bean validation works the same way
3. **Response format** - `JSONResponse` unchanged

### Event Listeners

Event handling remains unchanged:

- `OnRegistrationCompleteEvent`
- `UserPreDeleteEvent`
- `AuditEvent`

All events fire as before with the same payload structures.

## Troubleshooting

### Common Issues

**Issue: `pattern must start with a /`**

This error occurs when URL patterns in security configuration don't start with `/`.

**Solution:** Review all entries in:
- `user.security.unprotectedURIs`
- `user.security.protectedURIs`
- Any custom `requestMatchers()` calls

Ensure every pattern starts with `/`.

---

**Issue: `ClassNotFoundException` for test annotations**

Spring Boot 4.0 moved test annotations to new packages.

**Solution:**
1. Add the modular test dependencies (see [Step 4](#step-4-update-test-infrastructure))
2. Update imports to new package locations

---

**Issue: `NoClassDefFoundError: com/fasterxml/jackson/...`**

Jackson 3 has different package structures.

**Solution:** Update ObjectMapper instantiation and check custom serializers.

---

**Issue: Profile update returns validation error for password**

The `/user/updateUser` endpoint now uses `UserProfileUpdateDto`.

**Solution:** Update your frontend to only send `firstName` and `lastName` fields.

---

**Issue: `user_credentials` table not created on MariaDB/MySQL (WebAuthn)**

With `ddl-auto: update` or `create`, Hibernate previously mapped the `attestationObject` and
`attestationClientDataJson` columns to `VARBINARY(65535)`. Two such columns exceed MariaDB's
InnoDB 65,535-byte row-size limit, causing silent table creation failure. Symptoms include 500
errors on `/user/auth-methods` or `/user/webauthn/credentials`.

**Solution (upgrading from a version prior to this fix):**

If the `user_credentials` table was never created, it will be created automatically on next
startup with `ddl-auto: update` once you upgrade to this version.

If the table exists with `VARBINARY` columns (created on a non-MariaDB database), run:

```sql
ALTER TABLE user_credentials
    MODIFY COLUMN public_key              LONGBLOB NOT NULL,
    MODIFY COLUMN attestation_object      LONGBLOB,
    MODIFY COLUMN attestation_client_data_json LONGBLOB;
```

With `ddl-auto: update`, Hibernate will handle this automatically on MariaDB/MySQL. On
PostgreSQL no schema change is needed — the columns map to `bytea` in both old and new versions.

---

**Issue: Java version incompatibility**

Spring Boot 4.0 requires Java 21.

**Solution:**
1. Update your JDK to 21+
2. Update build configuration
3. Update CI/CD pipelines
4. Update deployment environments

## Version Compatibility Matrix

| Framework Version | Spring Boot | Spring Security | Java | Status |
|-------------------|-------------|-----------------|------|--------|
| 4.0.x | 4.0.x | 7.x | 21+ | Current |
| 3.5.x | 3.5.x | 6.x | 17+ | Maintained |
| 3.4.x | 3.4.x | 6.x | 17+ | Security fixes only |
| < 3.4 | < 3.4 | < 6 | 17+ | End of life |

---

For additional help, see:
- [README](README.md) - Main documentation
- [Configuration Guide](CONFIG.md) - All configuration options
- [Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp) - Working example
- [GitHub Issues](https://github.com/devondragon/SpringUserFramework/issues) - Report problems
