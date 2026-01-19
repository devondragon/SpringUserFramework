# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring User Framework is a reusable Spring Boot library (not an application) that provides user authentication and management features built on Spring Security. It supports Spring Boot 4.0 (Java 21+) and Spring Boot 3.5 (Java 17+).

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
- `UserConfiguration` - Main auto-configuration class, enables async/scheduling/method security
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

### Configuration

All configuration uses `user.*` prefix in application.yml. Key properties:
- `user.security.*` - URIs, default action (allow/deny), bcrypt strength, lockout
- `user.registration.*` - Email verification toggle
- `user.mail.*` - Email sender settings
- `user.audit.*` - Audit logging configuration
- `user.roles.*` - Role/privilege definitions and hierarchy

## Code Style

- **Imports**: Alphabetical, no wildcards
- **Indentation**: 4 spaces
- **Logging**: SLF4J via Lombok `@Slf4j`
- **DI**: `@RequiredArgsConstructor` with `final` fields
- **Documentation**: JavaDoc on public classes/methods

## Testing

Tests use H2 in-memory database. The framework uses JUnit 5 with parallel execution enabled. Key test dependencies: Testcontainers, WireMock, GreenMail (email), AssertJ, REST Assured.
