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

The library now contributes its `SecurityFilterChain` through a dedicated auto-configuration with two important properties:

- **Ordered at low precedence.** The library's chain is registered with `@Order(Ordered.LOWEST_PRECEDENCE - 5)` — the same low precedence Spring Boot uses for its own default servlet security chain. This value is sourced from `SecurityFilterProperties.BASIC_AUTH_ORDER`; the constant was `SecurityProperties.BASIC_AUTH_ORDER` in Spring Boot 3.x and was relocated to `SecurityFilterProperties.BASIC_AUTH_ORDER` in Spring Boot 4.0 (still `Ordered.LOWEST_PRECEDENCE - 5`). This means any consumer-supplied chain with a lower (higher-precedence) `@Order` is consulted first by Spring Security's `FilterChainProxy`.
- **Backs off entirely if you define your own.** The library's chain is annotated `@ConditionalOnMissingBean(SecurityFilterChain.class)`. If your application defines **any** `SecurityFilterChain` bean, the library's chain is suppressed completely.

This gives you two ways to customize security:

**Option A — Replace the library's chain (you own all the rules).**

Define your own `SecurityFilterChain`. Because of `@ConditionalOnMissingBean`, the library's chain backs off entirely and **does not** apply any of its rules. You are now responsible for protecting *all* URIs, including the framework's endpoints (login, registration, password reset, profile, etc.). Use this when you want full control.

```java
@Configuration
@EnableWebSecurity
public class CustomSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                // All patterns must start with /
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // You must also permit/secure the framework's own URIs here,
                // since the library chain no longer applies:
                .requestMatchers("/user/registration", "/user/login", "/user/resetPassword").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/user/login.html")
                .permitAll()
            );
        return http.build();
    }
}
```

**Option B — Layer your own higher-precedence chain in front of the library's.**

Define your own `SecurityFilterChain` with a `securityMatcher` scoping it to a subset of requests and a higher-precedence (lower) `@Order`. Spring Security evaluates chains in order and uses the **first** chain whose matcher matches. Requests that don't match your chain fall through to the library's chain.

> Note: As soon as you define *any* `SecurityFilterChain` bean, the library's `@ConditionalOnMissingBean` causes its chain to back off. So Option B does **not** keep the library's chain active automatically — if you want both your chain and the library's behavior, you currently need to reproduce the rules you care about in your own chain(s). The `@Order` mechanism is what lets multiple consumer-defined chains coexist with predictable precedence.

For most applications that only need to *add* a few rules, the simplest path is to rely on the library's chain and the `user.security.*` properties (`protectedURIs`, `unprotectedURIs`, `defaultAction`, etc.) rather than defining your own `SecurityFilterChain`.

### Custom User Services

If you extend `UserService` or implement custom user management:

1. **Method signatures unchanged** - Core service methods remain compatible
2. **Password encoding** - Still uses BCrypt, no changes required
3. **User entity** - No schema changes required

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
