# Codebase Knowledge

Generated: 2026-07-20

## Quick Reference

**Stack:** Java 21 (toolchain) + Spring Boot 4.1.0 (all starters `compileOnly`) — a reusable auto-configuration library, not a runnable app. A separate `release/3.x` branch maintains a Spring Boot 3.5.x / Java 17+ compatibility line.
**Architecture:** Hybrid layer-based (api/controller → service → persistence) with self-contained, property-gated feature verticals (gdpr, registration, audit, security/MFA/WebAuthn). Ships as Spring Boot auto-configuration (`AutoConfiguration.imports`), enforced with ArchUnit layering rules.
**Testing:** JUnit 5 + AssertJ (dominant) + Mockito, in `src/test/java/com/digitalsanctuary/spring/user/`, using project-specific composite annotations (`@ServiceTest`, `@DatabaseTest`, `@IntegrationTest`, `@SecurityTest`, `@OAuth2Test`) instead of raw Spring Boot test annotations.

## Key Files
- Entry point (auto-config): `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` → `UserConfiguration`, `AuditMailAutoConfiguration`, `UserSecurityBeansAutoConfiguration`, `WebSecurityFilterChainAutoConfiguration`
- Core services: `src/main/java/com/digitalsanctuary/spring/user/service/UserService.java`, `DSUserDetailsService.java`, `LoginAttemptService.java`
- Security: `src/main/java/com/digitalsanctuary/spring/user/security/WebSecurityConfig.java`
- Config: `build.gradle`, `gradle.properties`, `src/main/resources/config/dsspringuserconfig.properties`
- Tests: `src/test/java/com/digitalsanctuary/spring/user/`, with shared infra in `test/annotations/`, `test/config/`, `test/builders/`
- Architecture enforcement: `src/test/java/com/digitalsanctuary/spring/user/architecture/ArchitectureTest.java` (ArchUnit)

## Getting Started
This library auto-configures itself into any Spring Boot app that adds it as a dependency — no explicit `@ComponentScan` needed. Because every Spring starter is `compileOnly`, a consuming app must supply its own compatible Spring Boot/Security/Data JPA/Mail versions at runtime (see `../SpringUserFrameworkDemoApp` for a working consumer). Local iteration loop: `./gradlew publishLocal` → bump the SNAPSHOT dependency in the demo app → `./gradlew bootRun` there → Playwright tests. Tests run via custom annotations rather than raw `@SpringBootTest`/`@DataJpaTest` — check `test/annotations/` before reaching for vanilla Spring Boot Test annotations.

## Concerns & Tech Debt
- **No linting/static-analysis tooling configured** in `build.gradle` — no checkstyle, spotless, PMD, SonarQube, or Jacoco. Style is convention-only and enforced by reviewer discipline, not tooling.
- **Mixed indentation**: roughly 65 of 158 main source files are tab-indented (legacy: `UserService.java`, `User.java`, `WebSecurityConfig.java`, most of `persistence/`) vs. 4-space elsewhere; no `.editorconfig` exists to normalize this. New code should use 4 spaces but match the existing file when editing legacy files.
- **Minor wildcard-import exceptions** to the "no wildcards" rule: `jakarta.persistence.*` in `User.java`, `java.lang.annotation.*` in all custom test annotations, and Mockito/MockMvc static wildcards in 21 test files.
- **Inconsistent `Dto`/`DTO` casing** across the codebase (`UserDto` vs. `AuditEventDTO`/`GdprExportDTO`) — no single convention enforced.
- **Field visibility inconsistency**: `UserService.userEmailService`/`userVerificationService` are `public final` rather than `private final`, breaking the otherwise-consistent DI pattern.
- **Split Spring Boot compatibility across branches** (`main` = Boot 4.x/Java 21+, `release/3.x` = Boot 3.5.x/Java 17+, security-maintenance only) — worth remembering that this branch's `build.gradle` only reflects the 4.x line; don't assume both live in one build file.
- **Python release tooling** (`generate_changelog.py`, using OpenAI's API) is a separate concern from the Java library itself — not shipped, but does execute during `./gradlew release`.

## Detailed Documentation
- [STACK.md](./STACK.md) - Technology stack details
- [ARCHITECTURE.md](./ARCHITECTURE.md) - Code organization and patterns
- [CONVENTIONS.md](./CONVENTIONS.md) - Coding standards and idioms
