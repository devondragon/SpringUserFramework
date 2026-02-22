# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring User Framework is a reusable Spring Boot library (not an application) that provides user authentication and management features built on Spring Security. It supports Spring Boot 4.0 (Java 21+) and Spring Boot 3.5 (Java 17+).

**This is a library, not an app.** All Spring Boot starters are `compileOnly` dependencies. Consuming applications provide their own database, mail server, and security configuration. Never add Spring starters as `implementation` dependencies.

## Commands

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run single test
./gradlew test --tests "com.digitalsanctuary.spring.user.service.UserServiceTest"

# Test with specific JDK
./gradlew testJdk17
./gradlew testJdk21

# Test all JDKs
./gradlew testAll

# Lint/check
./gradlew check

# Publish locally (for testing in consuming apps)
./gradlew publishLocal
```

## Architecture

### Package Structure
```
com.digitalsanctuary.spring.user
├── api/              # REST endpoints (UserAPI)
├── audit/            # Audit logging system
├── controller/       # MVC controllers for HTML pages
├── dev/              # Dev login auto-configuration (local profile only)
├── dto/              # Data transfer objects
├── event/            # Spring application events
├── exceptions/       # Custom exceptions
├── listener/         # Event listeners (auth, registration)
├── mail/             # Email service components
├── persistence/      # JPA entities and repositories
│   ├── model/        # User, Role, Privilege, tokens
│   └── repository/   # Spring Data repositories
├── profile/          # User profile extension framework
├── roles/            # Role/privilege configuration
├── security/         # Spring Security configuration
├── service/          # Business logic services
├── validation/       # Custom validators
└── web/              # Web interceptors and config
```

### Key Components

**Entry Points:**
- `UserConfiguration` - Main auto-configuration class, enables async/retry/scheduling/method security
- `WebSecurityConfig` - Spring Security filter chain configuration
- `UserAPI` - REST endpoints at `/user/*` (registration, password reset, profile update)

**Core Services:**
- `UserService` - User CRUD, password management, token handling
- `UserEmailService` - Verification and password reset emails
- `PasswordPolicyService` - Password strength validation using Passay
- `DSUserDetailsService` - Spring Security UserDetailsService implementation
- `SessionInvalidationService` - Session management for security events

**Security:**
- `DSUserDetails` - Custom UserDetails implementation wrapping User entity
- `DSOAuth2UserService` / `DSOidcUserService` - OAuth2/OIDC user services
- `LoginAttemptService` - Brute force protection with account lockout

**Extension Points:**
- `BaseUserProfile` - Extend for custom user data (see PROFILE.md)
- `UserProfileService<T>` - Interface for profile management
- `BaseSessionProfile<T>` - Session-scoped profile access
- `UserPreDeleteEvent` - Listen for user deletion to clean up related data

### Auto-Configuration

- Entry point: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` → `UserConfiguration`
- `UserAutoConfigurationRegistrar` dynamically registers the library package for entity/repository scanning
- No conditional annotations — all features load, controlled by `user.*` properties

### Startup Behavior

- `RolePrivilegeSetupService` listens for `ContextRefreshedEvent` and creates/updates Role and Privilege entities from `user.roles-and-privileges` config. Must complete before any auth requests.
- `PasswordHashTimeTester` runs async on `ApplicationStartedEvent` to benchmark bcrypt performance (controlled by `user.security.testHashTime`).

### Design Decisions to Preserve

- `UserService.createUser()` uses `@Transactional(isolation = Isolation.SERIALIZABLE)` to prevent race conditions during concurrent registration
- Event-driven architecture: `AuthenticationEventListener`, `RegistrationListener`, `AuditEventListener`, `BaseAuthenticationListener` — don't bypass events
- All Spring starters are `compileOnly` — this is intentional for library consumers

### Configuration

All configuration uses `user.*` prefix in application.yml. Key property groups:
- `user.security.*` - URIs, default action (allow/deny), bcrypt strength, lockout settings, testHashTime
- `user.registration.*` - Email verification toggle, OAuth provider toggles
- `user.mail.*` - Email sender settings (fromAddress)
- `user.audit.*` - Audit logging (logFilePath, flushOnWrite, logEvents, maxQueryResults)
- `user.roles-and-privileges` - Role-to-privilege mapping (applied on startup)
- `user.role-hierarchy` - Role inheritance (e.g., ROLE_ADMIN > ROLE_MANAGER)
- `user.gdpr.*` - GDPR features (enabled, exportBeforeDeletion, consentTracking)
- `user.dev.*` - Dev login (autoLoginEnabled, loginRedirectUrl) — requires `local` profile
- `user.purgetokens.cron.*` - Token cleanup schedule
- `user.session.invalidation.warn-threshold` - Performance warning threshold
- `user.actuallyDeleteAccount` - Hard delete vs disable

## Code Style

- **Imports**: Alphabetical, no wildcards
- **Indentation**: 4 spaces
- **Logging**: SLF4J via Lombok `@Slf4j`
- **DI**: `@RequiredArgsConstructor` with `final` fields
- **Documentation**: JavaDoc on public classes/methods
- **Assertions**: AssertJ fluent style (not JUnit assertEquals)
- **Test naming**: `should[ExpectedBehavior]When[Condition]` pattern

## Testing

Tests use H2 in-memory database with JUnit 5 parallel execution. Key dependencies: Testcontainers, WireMock, GreenMail, AssertJ, REST Assured.

### Custom Test Annotations (use these instead of raw Spring annotations)

| Annotation | Use When | Spring Context | Key Imports |
|---|---|---|---|
| `@ServiceTest` | Unit testing services with mocks | None (Mockito only) | `BaseTestConfiguration` |
| `@DatabaseTest` | Testing JPA repositories | `@DataJpaTest` slice | `DatabaseTestConfiguration` |
| `@IntegrationTest` | Full workflow testing | Full context | All 5 test configs |
| `@SecurityTest` | Testing auth/authorization | Full context + security | `SecurityTestConfiguration` |
| `@OAuth2Test` | Testing OAuth2/OIDC flows | Full context + OAuth2 | `OAuth2TestConfiguration` |

All annotations are in `com.digitalsanctuary.spring.user.test.annotations`.

### Test Configuration Classes (`test.config` package)

- `BaseTestConfiguration` - BCrypt strength 4 (fast), fixed Clock (2024-01-15 10:00 UTC), mock EventPublisher, SessionRegistry
- `SecurityTestConfiguration` - Pre-built test users: `user@test.com`, `admin@test.com`, `moderator@test.com` (password: "password"). `TestSecurityContextFactory` for custom contexts.
- `OAuth2TestConfiguration` - Mock OAuth2/OIDC providers (Google, GitHub, OIDC), test token factory
- `MockMailConfiguration` - Captures sent emails via `TestMailCapture` instead of sending
- `DatabaseTestConfiguration` - Database-specific test setup

### Test Application

Integration tests use `TestApplication` (in `test.app` package) as their Spring Boot context class.

## Related Documentation

- `TESTING.md` - Comprehensive testing guide with patterns and troubleshooting
- `PROFILE.md` - User profile extension framework
- `CONFIG.md` - Configuration reference
- `MIGRATION.md` - Version migration guide
- `CONTRIBUTING.md` - Contributor guidelines (fork/branch/PR workflow)

## Release Process

Version is in `gradle.properties`. Do not manually update version numbers. The release process (`./gradlew release`) handles versioning, changelog generation, tagging, and Maven Central publishing automatically.
