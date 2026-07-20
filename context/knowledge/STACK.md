# Technology Stack

## Languages
- Java: compiled with toolchain `JavaLanguageVersion.of(21)` (build.gradle `java.toolchain`); `mise.toml` pins `java = "17"` for the local dev shell (min supported runtime). CI runs on Java 21 (compile) and additionally on Java 25 (`testJdk25` task). Library published for consumers on Java 21+ (Spring Boot 4.x) via `main`, and Java 17+ (Spring Boot 3.5.x) via the separate `release/3.x` maintenance branch — the `main` branch's `build.gradle` only targets Spring Boot 4.1.0/Java 21; the 3.5/Java 17 line lives on `release/3.x`, not in this branch's build file.
- Python: 3.13 (`mise.toml`), used only for release tooling (`generate_changelog.py`, `test_generate_changelog.py`), not part of the shipped library.
- HTML: Thymeleaf templates under `src/main/resources/templates/mail/` (registration/forgot-password emails).
- Properties/YAML: Spring config metadata and i18n message bundles.

## Frameworks & Libraries
### Core
- Spring Boot: 4.1.0 (`org.springframework.boot` plugin, build.gradle:2) — all Boot starters declared `compileOnly` (see below), confirming this is a library, not an app.
- Spring Dependency Management plugin: `io.spring.dependency-management` 1.1.7 — manages transitive versions via the Boot BOM.
- Spring Security: consumed via `spring-boot-starter-security` (compileOnly) plus `spring-security-webauthn` (compileOnly) for Passkey/WebAuthn support.
- Spring Data JPA / JDBC: `spring-boot-starter-data-jpa`, `spring-boot-starter-jdbc` (compileOnly) — persistence layer (`src/main/java/.../persistence`).
- Spring Mail: `spring-boot-starter-mail` (compileOnly) — used for registration/forgot-password emails (`audit`, `mail` packages).
- Spring OAuth2 Client: `spring-boot-starter-oauth2-client` (compileOnly) — social login / OIDC support (`registration`, `security` packages).
- Spring Web + Thymeleaf: `spring-boot-starter-web`, `spring-boot-starter-thymeleaf` (compileOnly), plus `thymeleaf-extras-springsecurity6:3.1.5.RELEASE` and `nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect:4.0.1` (compileOnly).
- Spring Boot Actuator: `spring-boot-starter-actuator` (compileOnly).
- Lombok: 1.18.46 (`compileOnly` + `annotationProcessor`, also for tests) — `lombok.config` enables `addLombokGeneratedAnnotation` to suppress Javadoc warnings on generated code.

**Architectural signal — `compileOnly` pattern:** every Spring Boot starter and closely-related Spring artifact is declared `compileOnly`, not `implementation`/`api`. This means the library compiles against these APIs but does not pull them in transitively for consumers — consuming Spring Boot applications must supply their own compatible starter versions at runtime. This is the clearest evidence the project is a reusable library, not a standalone application (`tasks.named('bootJar') { enabled = false }` further confirms this — no executable jar is built, only a plain library jar via `tasks.named('jar')`).

### Supporting
- Passay 2.0.0 (`org.passay:passay`) — password strength/validation rules (`implementation`).
- Apache Commons Text 1.15.0 (`implementation`) — text utilities.
- Jakarta Validation API 3.1.1 (`compileOnly`) — bean validation annotations.
- Spring Retry 2.0.13 (`compileOnly`) — retry logic (e.g., for mail sending or DB ops).
- Spring Boot Configuration Processor (`annotationProcessor`) — generates metadata for `@ConfigurationProperties`.

## Build & Dev Tools
- Gradle: wrapper version 9.6.1 (`gradle/wrapper/gradle-wrapper.properties`), using the `java-library` plugin.
- `com.github.ben-manes.versions` 0.54.0 — dependency-update reports (`dependencyUpdates` task).
- `com.vanniktech.maven.publish` 0.37.0 + `signing` + `maven-publish` — Maven Central publishing (see Publishing below).
- `net.researchgate.release` 3.1.0 — release automation; `release { git { requireBranch = 'main|release/.*' } }` allows releases from `main` or `release/*` maintenance branches, wires `generateAIChangelog` before release builds and `publishMavenCentral` after.
- No static-analysis/linting plugin is configured in `build.gradle` (no checkstyle, spotless, PMD, SonarQube, or Jacoco found).
- Custom JDK test tasks: `testJdk21` and `testJdk25` (via `registerJdkTestTask`) run the full test suite against JDK 21 and JDK 25 toolchains respectively; `testAll` runs both. Standard `test` task also enables JUnit 5 parallel execution (`junit.jupiter.execution.parallel.*` system properties, `maxParallelForks` = half of available cores).
- `.hintrc` — webhint linting config (`extends: development`), likely for the docs/site assets rather than Java code.
- Dependabot (`.github/dependabot.yml`): weekly `gradle` ecosystem updates on `/`.

## Test Frameworks & Tools
- JUnit 5 (via `spring-boot-starter-test`), run with `useJUnitPlatform()`.
- AssertJ: `org.assertj:assertj-core:3.27.7` (testImplementation).
- Spring Security Test: `spring-security-test` (testImplementation).
- ArchUnit: `com.tngtech.archunit:archunit-junit5:1.4.2` — architecture rule enforcement tests.
- Testcontainers: `org.testcontainers:testcontainers:2.0.5`, plus `testcontainers-junit-jupiter`, `testcontainers-mariadb`, `testcontainers-postgresql` (all 2.0.5) — integration tests against real MariaDB/PostgreSQL containers.
- H2 Database: `com.h2database:h2:2.4.240` (testImplementation) — fast in-memory DB for unit/slice tests.
- Hibernate Validator: `org.hibernate.validator:hibernate-validator:9.1.2.Final` (testImplementation) — validation runtime for tests.
- Jackson (legacy, `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`) — explicitly declared for test JSON utilities since Spring Boot 4 ships Jackson 3 (`tools.jackson`) by default.
- Spring Boot 4 modular test starters: `spring-boot-starter-data-jpa-test`, `spring-boot-webmvc-test`, `spring-boot-jdbc-test`.
- Mockito is pulled transitively via `spring-boot-starter-test` (not declared directly in build.gradle).
- No Awaitility dependency found in build.gradle.

## External Services
- Spring Mail (SMTP): used for account registration confirmation and forgot-password emails, rendered via Thymeleaf templates (`src/main/resources/templates/mail/registration-token.html`, `forgot-password-token.html`); consuming app supplies actual mail server config/credentials.
- OAuth2/OIDC providers: supported generically via `spring-boot-starter-oauth2-client` for social login (`registration`/`security` packages) — no specific provider (Google/GitHub/etc.) is hardcoded in build.gradle; providers are configured by the consuming application.
- WebAuthn/Passkey: `spring-security-webauthn` — passwordless/FIDO2 authentication support.
- OpenAI API: used only by the release-tooling script `generate_changelog.py` (via `openai==1.105.0` in `requirements.txt`) to generate AI-assisted changelog entries during releases — not part of the runtime library.
- GitHub Actions / Claude Code Action (`anthropics/claude-code-action@v1`): automated PR review (`claude-code-review.yml`) and `@claude`-mention-triggered assistant (`claude.yml`).
- CodeQL (`github/codeql-action`): security scanning (`security-extended` query pack) on `java-kotlin` in `build.yml`.

## Database
- No ORM is bundled as a runtime dependency (Spring Data JPA/Hibernate come from the consuming app via the `compileOnly` starters); the library's `persistence` package defines JPA entities/repositories against that consumer-supplied Hibernate/JPA implementation.
- Test-only databases: H2 2.4.240 (in-memory, fast tests) and Testcontainers-managed MariaDB/PostgreSQL (`org.mariadb.jdbc:mariadb-java-client`, `org.postgresql:postgresql` as `testRuntimeOnly`) for realistic integration testing.

## Key Configuration Files
- `build.gradle`: single-module Gradle build — plugins, dependencies (compileOnly-heavy for library pattern), test task config (parallel JUnit, multi-JDK test tasks), Maven Central publishing (`mavenPublishing` block via vanniktech plugin), Reposilite private repo publishing, and `net.researchgate.release` release automation.
- `settings.gradle`: single root project, `rootProject.name = 'ds-spring-user-framework'`.
- `gradle.properties`: current version (`5.1.1-SNAPSHOT`) and Maven Central publishing flags (`mavenCentralPublishing=true`, `mavenCentralAutomaticPublishing=true`).
- `gradle/wrapper/gradle-wrapper.properties`: Gradle 9.6.1 wrapper distribution.
- `mise.toml`: pins local toolchain versions — Java 17, Python 3.13 (mise is used for tool version management, e.g. `mise x -- python generate_changelog.py`).
- `lombok.config`: enables `@Generated` annotation on Lombok output to suppress Javadoc missing-constructor warnings.
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`: registers auto-configuration classes (`UserConfiguration`, `AuditMailAutoConfiguration`, `UserSecurityBeansAutoConfiguration`, `WebSecurityFilterChainAutoConfiguration`) — the standard Spring Boot 3+/4 auto-config discovery mechanism (replacing `spring.factories`).
- `src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports`: registers `MessageSourceEnvironmentPostProcessor` for i18n message source setup.
- `src/main/resources/META-INF/additional-spring-configuration-metadata.json`: IDE autocomplete metadata for the library's `@ConfigurationProperties`.
- `src/main/resources/config/dsspringuserconfig.properties`: default library configuration values.
- `src/main/resources/messages/dsspringusermessages.properties`: default i18n messages (see `docs`/`i18n` for locale variants if present).
- `.github/workflows/build.yml`: CI — compile+test on Java 21, runtime test on Java 25, plus a CodeQL security-scan job.
- `.github/workflows/claude.yml` / `claude-code-review.yml`: Claude Code GitHub Action integrations for automated PR review and `@claude`-triggered assistance.
- `.github/dependabot.yml`: weekly automated Gradle dependency update PRs.
- `requirements.txt` / `generate_changelog.py` / `test_generate_changelog.py`: standalone Python release tooling (OpenAI-powered changelog generation invoked via `mise x -- python generate_changelog.py` from the Gradle `release` plugin's `beforeReleaseBuild` hook) — not part of the shipped Java library.
