## [3.4.0] - 2025-09-03
### Features
- Proxy-aware URL and IP detection
  - UserUtils.getAppUrl now builds correct external URLs when behind proxies/load balancers by honoring X-Forwarded-Proto, X-Forwarded-Host, and X-Forwarded-Port; the generated URL always includes a port for backward compatibility
  - UserUtils.getClientIP now checks multiple standard headers in priority order (X-Forwarded-For, X-Real-IP, CF-Connecting-IP, True-Client-IP) with clean fallbacks
- Remember‑me is now opt‑in and explicitly configurable
  - Disabled by default; enable only when you set both properties:
    - user.security.rememberMe.enabled=true
    - user.security.rememberMe.key=<your-static-secret-key>
- Role hierarchy applied to method security
  - Method security expressions now honor the configured hierarchy (e.g., ROLE_ADMIN > ROLE_USER) via a MethodSecurityExpressionHandler wired with the RoleHierarchy
- Stronger password validation for registration
  - New @PasswordMatches class‑level constraint and validator; registration now enforces password and matchingPassword equality, with clear validation errors
- Safer OAuth2/OIDC account creation
  - Email is validated and normalized (lowercased) for OAuth2 and OIDC providers; authentication fails early with a clear, user‑friendly message if the provider didn’t supply an email (e.g., missing scope/permission)
- Audit logging hardening and defaults
  - FileAuditLogWriter concurrency is now protected with synchronized methods
  - Default audit log location changed to ./logs with automatic creation; graceful fallback to system temp directory if not writable
  - Periodic flush scheduling only active when audit logging is enabled and flushOnWrite is false
- Password reset API refinement
  - New PasswordResetRequestDto introduced; endpoint continues to send reset emails but now cleanly models the request as { "email": "..." }

### Fixes
- Security and privacy hardening
  - Removed session IDs from debug logs to prevent sensitive data exposure
  - CustomOAuth2AuthenticationEntryPoint now returns generic user‑friendly messages and logs detailed errors internally
  - Enforced lowercase normalization for emails throughout registration and OAuth2/OIDC paths, preventing duplicate users by case variance
- Robust null/edge‑case handling
  - Fixed potential NPE in UserService.getUserByPasswordResetToken when token or token record is null
  - UserService.registerNewUserAccount now validates password matching before proceeding
- Correct URL generation in emails
  - Registration verification emails now use UserUtils.getAppUrl(request), fixing previously broken links that used only the context path
- JPA entity equality fixes
  - Role and Privilege equals/hashCode now based on id only; bidirectional relationships excluded to avoid recursion/stack overflows and to improve Set behavior in persistence contexts
- Build/packaging correctness for consumers
  - Fixed published artifact name to ds-spring-user-framework
  - Removed surprise transitive runtime dependencies from the library (devtools, database drivers) by moving them to test runtime scope
- Configuration correctness and resilience
  - Fixed CSRF property typo: user.security.disableCSRFdURIs → user.security.disableCSRFURIs
  - Hardened parsing of comma‑delimited URI properties to ignore empty/whitespace entries
- Logging and code quality
  - Replaced string concatenation in logs with parameterized logging throughout
  - Fixed JavaDoc syntax issues in JSONResponse

### Breaking Changes
- Password reset endpoint request body
  - /user/resetPassword now expects PasswordResetRequestDto instead of UserDto
  - Migration: change the request body to { "email": "user@example.com" }
- Configuration property rename
  - user.security.disableCSRFdURIs → user.security.disableCSRFURIs
  - Migration: update your application properties/yaml accordingly
- Remember‑me behavior
  - Previously could be active with an ephemeral key; now disabled by default and only enabled when both user.security.rememberMe.enabled=true and user.security.rememberMe.key are set
- MailService bean construction
  - MailService now uses constructor injection for both JavaMailSender and MailContentBuilder
  - Migration: if you construct MailService manually, pass both dependencies; Spring auto‑config will wire it automatically in typical setups
- OAuth2/OIDC email requirement
  - Authentication now fails if the provider does not return an email address; ensure the email scope/permission is granted

### Refactoring
- WebSecurityConfig
  - Simplified remember‑me configuration; created DaoAuthenticationProvider via constructor; used RoleHierarchyImpl.fromHierarchy; reduced boilerplate and improved readability
- UserUtils
  - Streamlined IP header checks and forward‑aware app URL construction; clarified JavaDoc
- General logging cleanup
  - Consistent use of parameterized logging; removed System.out and noisy concatenation

### Documentation
- Major README overhaul
  - Step‑by‑step Quick Start with prerequisites, dependencies (Thymeleaf, Mail, JPA, Security, Spring Retry), database examples (MariaDB/PostgreSQL/H2), email setup, and complete example configuration
  - Clear explanation of registration modes: auto‑enable vs email verification, with expected behavior and configuration
  - Guidance on customizing views and next steps
- Configuration metadata fixes
  - property names corrected (camelCase), types fixed (Boolean/Integer), missing properties added for better IDE assistance

### Testing
- Substantial test coverage added across critical paths
  - OAuth2/OIDC services
    - DSOAuth2UserServiceTest (≈15 tests): Google/Facebook flows, new vs existing users, provider conflicts, error handling
    - DSOidcUserServiceTest (≈14 tests): Keycloak flows, claims extraction, DSUserDetails integration, conflict scenarios
  - Security utilities and flows
    - UserUtilsTest (≈29 tests): IP extraction header priority and URL building
    - CustomOAuth2AuthenticationEntryPointTest (≈21 tests): exception handling, redirects, failure handler delegation
    - LoginHelperServiceTest (≈15 tests): last activity tracking, automatic unlock, authorities handling, edge cases
    - LogoutSuccessServiceTest (≈17 tests): audit event creation, IP extraction, URL resolution, exception scenarios
    - RolePrivilegeSetupServiceTest (≈15 tests): initialization, reuse of privileges, transactional handling, mixed existing/new entities
  - Mail
    - MailServiceTest (≈24 tests): simple/template sends, async behavior, retry/recovery, edge cases
  - Validation
    - PasswordMatchesValidatorTest: positive/negative cases for password confirmation
- Test infrastructure improvements
  - Added fixture builders for OAuth2/OIDC users (Google, Facebook, Keycloak) for realistic claims and attributes
  - Dependency updates for test stack (AssertJ, ArchUnit, Awaitility, Testcontainers, Rest‑Assured, GreenMail)

### Other Changes
- Dependency updates
  - Spring Boot 3.5.5
  - Test libraries: AssertJ 3.27.4, ArchUnit 1.4.1, Awaitility 4.3.0, Testcontainers 1.21.3, Rest‑Assured 5.5.6, GreenMail 2.1.5
  - MariaDB JDBC driver 3.5.5
- Build improvements
  - Published artifact renamed correctly to ds-spring-user-framework
  - Group/publishing coordinates aligned; dependency management standardized via Spring Boot BOM
  - Added Gradle Versions Plugin configuration to prefer stable releases
- Internal tools
  - Updated changelog generator to use a newer model (gpt‑5)
  
Migration checklist
- Update /user/resetPassword client requests to use PasswordResetRequestDto { "email": "..." }
- Rename user.security.disableCSRFdURIs to user.security.disableCSRFURIs
- If you rely on remember‑me, add:
  - user.security.rememberMe.enabled=true
  - user.security.rememberMe.key=<stable-secret>
- Ensure OAuth2/OIDC providers grant email scope; otherwise login will fail by design
- If constructing MailService manually, pass both JavaMailSender and MailContentBuilder
- Be aware that emails are now normalized to lowercase; verify database uniqueness constraints if case sensitivity was previously assumed

## [3.3.0] - 2025-07-22
# Changelog

## Features

### Comprehensive Test Infrastructure and Service Tests
- Established a comprehensive testing foundation including modular test configurations, custom annotations, test data builders, and a mock email service. This setup is crucial for facilitating both unit and integration tests within the library.
- Implemented test data builders for User, Role, and Token entities to streamline the creation of test data.
- Added testing support for OAuth2/OIDC with configured mock providers.
- Authored detailed test plans and phase-specific task lists targeting a test coverage increase from 27% to 80%+.

### Support for Parallel Test Execution
- Enabled JUnit 5's parallel execution capabilities to significantly improve test performance by leveraging multiple CPU cores.
- Expected test execution time reduced by 30-50%.

### End-to-End Testing Infrastructure
- Established a framework for end-to-end (E2E) testing which includes database setup using Testcontainers and email testing using GreenMail.
- Initiated examples of user registration and password reset journey tests which validate integration across system boundaries.

### Dependency and Version Updates
- Updated `org.mariadb.jdbc:mariadb-java-client` to version 3.5.4 to ensure compatibility with the latest database features and security patches.
- Updated the Gradle wrapper to version 8.14.3 for enhanced build performance and new functionalities.

## Fixes

### Improved Handling of Authentication Types
- Enhanced `AuthenticationEventListener` to correctly handle various authentication types, including OAuth2User and DSUserDetails, solving issues related to improper principal extraction and null user scenarios in OAuth2 authentication flows.
- Fixed deprecated API usage in `WebSecurityConfig` by replacing `RoleHierarchyImpl.fromHierarchy()` with the new constructor and `setHierarchy()` method.

### Hibernate Entity Management
- Addressed a critical issue related to Hibernate's immutable collection proxy by refactoring `User` entity's role storage from `List` to `Set`. This change prevents `UnsupportedOperationException` during entity saves in specific integration scenarios.

### Patch Vulnerabilities and Improve Compatibility
- Resolved the compilation error by aligning with the latest vanniktech maven publish plugin (0.34.0), ensuring smooth publishing to Maven Central.

## Refactoring

### Improved Readability and Consistency in Test Code
- Refactored test data builders for improved readability and consistent code style, enhancing maintainability and developer understanding.
- Refactored `UserServiceTest` to leverage centralized TestFixtures for cleaner setup and more organized test logic.

## Testing

### Extensive Test Coverage
- Added a comprehensive suite of unit and integration tests for DSUserDetailsService and AuthorityService, covering user role loading, OAuth2 flows, and role hierarchy management.
- Refined test classes to ensure appropriate usage of mocking and context initialization, leading to robust validation of expected behaviors across various scenarios.

### Test Documentation
- Authored detailed documentation outlining testing conventions, best practices, and guidelines for leveraging custom test annotations.
- Documented the new test infrastructure setup extensively to guide future test development and optimization.

## Other Changes

### File Renaming and Cleanup
- Streamlined test and source files by fixing naming discrepancies and correcting file paths where necessary for improved project organization.
- Deleted outdated and redundant documentation files such as `TESTPLAN.md`, `TESTNEXTTASKS.md`, and `FAILING_TESTS_ANALYSIS.md`, integrating relevant content into a unified test improvement document.

Overall, these changes enhance the library's testability, reliability, and developer experience, setting a robust foundation for future development and maintenance.

## [3.2.3] - 2025-06-23
### Features
- **[Gradle Release Plugin] New Version:** Updated `gradle.properties` to set the project version to `3.2.3-SNAPSHOT` from `3.2.2`. This change prepares the project for future development by marking the start of a new snapshot version.

### Other Changes
- **Dependency Updates:**
  - **Spring Boot Update to 3.5.3:** 
    - **Merged Changes and Details:**
      - Updated `springBootVersion` in `build.gradle` from `3.5.0` to `3.5.3`. This update includes improvements and bug fixes from Spring Boot's latest patch release.
      - Affected modules include `spring-boot-starter-web`, `spring-boot-configuration-processor`, and `spring-boot-starter-test`. Each of these was bumped from version `3.5.0` to `3.5.3`.
      - These updates ensure incorporation of resolved issues and performance improvements provided by the Spring Boot team. [Release notes and commits](https://github.com/spring-projects/spring-boot/releases). 
  - **Maven Publish Plugin Update to 0.33.0:**
    - **Merged Changes and Details:**
      - Updated `com.vanniktech.maven.publish` plugin in `build.gradle` from `0.32.0` to `0.33.0`. This minor version update may include new features, enhancements, and potentially minor API changes that improve the publication process.
      - This update ensures the usage of the latest features and improvements, detailed in [release notes and changelog](https://github.com/vanniktech/gradle-maven-publish-plugin/releases). 

- **Version Increment for Spring Boot to 3.5.0 from 3.4.5:**
  - Previous increment from `3.4.5` to `3.5.0` had merged code changes which similarly ensured improved stability and support by aligning with the latest minor release prior to patch updates.

These updates help make sure the project dependencies are current, which is important for taking advantage of recent bug fixes and performance improvements, as well as maintaining security standards within the project.

**Note:** All dependency updates above relate to enhancement and security, and do not introduce breaking changes as they are minor or patch updates to dependencies already in use.

## [3.2.2] - 2025-05-07
### Changelog

#### Features
- **Bump ds-spring-user-framework to 3.2.2**  
  Updated the ds-spring-user-framework version in the `README.md` to 3.2.2. This ensures that users who follow documentation instructions use the latest features and enhancements of the framework.

- **Dependency Updates: Guava**
  - Updated the Google Guava library from version `33.4.6-jre` to `33.4.8-jre` in `build.gradle`, reflecting two incremental updates. These semver-patch updates likely contain bug fixes or performance improvements without introducing new features or breaking changes.

#### Fixes
- **Conditional Logging Fix in FileAuditLogFlushScheduler**  
  Resolved an issue where the `FileAuditLogFlushScheduler` component continued to log events despite logEvents being disabled. The previous configuration used `@ConditionalOnProperty`, which was adjusted to `@ConditionalOnExpression` for better control. This change ensures that logging adheres to the intended configuration settings.

#### Breaking Changes
- None identified in this update cycle.

#### Refactoring
- **Improved Conditional Annotation Flexibility**  
  The `FileAuditLogFlushScheduler` was refactored to use `@ConditionalOnExpression` instead of `@ConditionalOnProperty`. This change provides more flexibility in how conditions for the component's activation are defined, allowing more dynamic configuration based on properties.

#### Documentation
- **Updated Version References in README**  
  Made explicit updates to version numbers in `README.md` to reflect the latest versions of the libraries, guiding users toward using the most up-to-date and secure versions.

#### Testing
- No specific changes related to testing documented.

#### Other Changes
- **Spring Boot Version Bump**  
  Updated multiple Spring Boot-related dependencies from `3.4.4` to `3.4.5` in `build.gradle` as a part of regular maintenance. These updates include `spring-boot-starter-web`, `spring-boot-configuration-processor`, and `spring-boot-starter-test` among others. This reflects minor version changes which may contain minor improvements or bug fixes without disrupting existing functionalities.

- **Version Management and Version Updates**  
  Made necessary changes to version management files like `gradle.properties` to transition from `3.2.1` to `3.2.2-SNAPSHOT`, indicating ongoing development work post the latest release. This snapshot designation is typically used for builds that are still under development.
  
This changelog captures nuanced improvements and ensures that developers and users of the project are aware of the recent enhancements and bugfixes, to maintain alignment with the latest codebase optimizations.

## [3.2.1] - 2025-04-13
# Changelog

## Features
- **User Account Deletion Improvements (80f7c474, 1c42d603):** Added comprehensive handling for user account deletions. A new event, `UserPreDeleteEvent`, is published before a user is deleted, allowing applications to clean up related data. This supports both logical disablement (setting `enabled=false`) and actual deletion from the database, controlled by the `user.actuallyDeleteAccount` configuration.

## Fixes
- **User Deletion Cascading Fix (80f7c474, 1c42d603):** Addressed the issue where deleting a user did not cascade to `BaseUserProfile` subclasses. The fix ensures that all related data can be cleaned upon user deletion, preventing orphaned data.
- **Google SSO Login Error (9a891b2a, 094a5341, b294d43d):** Resolved an error when logging in with Google SSO by updating dependencies and enhancing the logging within the authentication process for better tracking and debugging.
  
## Breaking Changes
- **Authentication Process Refactor (b294d43d):** The `DSUserDetails` class no longer implements the `OAuth2User` interface. Applications relying on `OAuth2User` from `DSUserDetails` need to update their logic accordingly.

## Refactoring
- **Improved Test Setup in `UserServiceTest` (7285da93):** Refactored imports and utilized a mock for `ApplicationEventPublisher`, improving the modularity and maintainability of tests.

## Documentation
- **Version Updates (634eb247):** Documentation in `README.md` updated to reflect latest version `3.2.1` for Maven and Gradle installations.
- **Complete Overhaul of Contribution Guides (6aaa0c72, 2a9951ec, e5d4aec0, fab0b80e):** Comprehensive updates to `CONTRIBUTING.md` and introduction of `CODE_OF_CONDUCT.md` emphasizing user management, contribution workflows, and code of conduct within the project.
- **Updated Contribution Instructions (c05ba9ff, f5421631):** Clear guidelines established for contributing to the project, including links between the main library and demo app.

## Other Changes
- **Dependency Updates:**
  - Upgraded `org.mariadb.jdbc` to version `3.5.3` for improved stability and security (f3470115, 1e771634).
  - Updated `com.google.guava` to `33.4.6-jre` (b3cf7985, fab0b80e).
  - Updated Lombok to `1.18.38` and upgraded Gradle wrapper to `8.13` (b294d43d).

The changelog is designed to provide meaningful insights into improvements, bug fixes, and potential impacts due to updates, ensuring users can easily understand the project's evolution and necessary updates on their end.

## [3.2.0] - 2025-03-23
## Changelog

### Features

- **Keycloak Authentication Support**
  - Added support for Keycloak as an SSO provider. Updated the framework to include Keycloak as a recognized authentication provider alongside existing ones like Google and Facebook. This involved modifying the database schema to support Keycloak as an authentication provider and adding configurations to enable Keycloak support in `WebSecurityConfig` and `UserPageController`. ([Commit: 3d90765f](#), [Commit: 96033640](#))

- **Spring Boot and Guava Versions Update**
  - Upgraded Spring Boot to version 3.4.4 and Guava to version 33.4.5-jre. This ensures compatibility with the latest features and improvements in these libraries. ([Commit: f29295b1](#), [Commit: b8bd58b5](#))

- **Enhanced Logging in Testing**
  - Included full exception formats and enabled the display of standard output streams during test execution for better debuggability and visibility of testing operations. ([Commit: 136457d6](#))

### Fixes

- **OAuth2 Dependency Issues**
  - Temporarily disabled several OAuth2-related tests due to unresolved dependency issues, preventing them from interfering with the build process. This included disabling entire test classes and specific test methods. ([Commit: 2908c614](#), [Commit: a5a25da4](#), [Commit: 711288c1](#))

- **Javadoc Improvements**
  - Corrected and enhanced Javadoc documentation for methods in `UserService` and `UserActionController`. Fixed inaccuracies in descriptions and added detailed commentary to key methods to clarify their function and usage. ([Commit: 1fbdaa0c](#), [Commit: 542f323d](#))

### Documentation

- **README Updates**
  - Refreshed the version in `README.md` to align with the latest release and updated the documentation to reflect the addition of Keycloak support, including setup instructions. ([Commit: 3787ee4d](#), [Commit: acf0481f](#))

- **CLAUDE.md Addition**
  - Added `CLAUDE.md`, which outlines build commands, code style guidelines, and development practices for maintaining consistency across project contributions. ([Commit: efe05a12](#))

### Other Changes

- **Merge and Version Handling**
  - Managed several merge operations to integrate changes related to Keycloak support, Spring Boot upgrades, and test stability improvements. This included handling conflicts and ensuring smooth integration of features and fixes into the main codebase. ([Commit: 2782eef9](#), [Commit: 8b51908f](#), [Commit: f29295b1](#))

- **Dependency Update**
  - Updated the Maven Publish plugin from version 0.30.0 to 0.31.0, ensuring that the build process uses the latest enhancements available in the plugin. ([Commit: a87be702](#), [Commit: 2424b728](#))

This changelog covers significant feature enhancements, critical fixes, and updates to the documentation that improve the overall robustness and usability of the software framework.

## [3.1.1] - 2025-02-24
### Features
- **Version Update to Develop Branch**: The project version has been incremented to `3.1.2-SNAPSHOT`. This is a preparatory step for future developments, ensuring that ongoing changes do not interfere with the stable release version. This change was made in the `gradle.properties` file. ([commit d24ae77c](#))

### Fixes
- **Dependency Updates**: The dependencies have been updated to ensure compatibility and incorporate the latest security and performance enhancements:
  - Spring Boot has been upgraded from version `3.4.1` to `3.4.3`.
  - The plugin `com.github.ben-manes.versions` has been updated from `0.51.0` to `0.52.0`.
  These updates were applied within the `build.gradle` file, reflecting changes in both the plugins and extension configurations. ([commit a498695e](#), [commit fb7aeb6](#))

### Other Changes
- **Merged Dependency Update**: As part of maintaining the project's core dependencies, the PR that updated Spring Boot and related plugins was successfully merged, finalizing these changes into the mainline development branch. This ensures all team members and CI/CD pipelines use the latest verified configurations. ([commit a498695e](#))

There were no breaking changes, refactorings, documentation updates, or test-related changes in this update cycle. The focus was on maintaining a secure, compatible, and up-to-date codebase.

## [3.1.0] - 2025-02-24
# Changelog

## Features
- **User Profile Management**: Added new classes for user profile management, including `BaseUserProfile`, `UserProfileService`, `BaseAuthenticationListener`, and `BaseSessionProfile`. This allows for enhanced management of user-specific data within the session context of a Spring Boot application. [commit 363013cd]
- **AuthorityService Integration in Tests**: Integrated a mock of `AuthorityService` in `UserServiceTest` to facilitate improved unit testing of service interactions. [commit 8f4ca291]
- **Role and Authority Enhancement**: Introduced `AuthorityService` and `LoginHelperService` for better management of user roles and authorities, especially for OAuth2-based users. This helps streamline authentication and authority management across the system. [commit 1e08c5c1]

## Fixes
- **Changelog Generation Enhancements**: Enhanced the changelog generation script to include commit diffs and automatic categorization of changes, which should improve the clarity and utility of generated changelogs. [commit 86999343]
- **OAuth2 User Handling**: Addressed multiple issues related to role and authority handling for OAuth2 users, ensuring proper authority assignment and role retrieval during user authentication and login processes. [commit 1e08c5c1]
- **Build and Configuration Updates**: Updated Python interpreter path in VSCode settings and added method security configuration to improve project setup and security management. [commit e50a5f22]

## Breaking Changes
- There are no outright breaking changes in this release, but the introduction of new role and authority management features requires validation against existing security configurations to ensure compatibility.

## Refactoring
- **User Configuration Improvements**: Refactored user configuration for tighter integration with Spring Boot, including the addition of an auto-configuration registrar to streamline component discovery and setup. [commit 1fce50d6]

## Documentation
- **Framework Documentation Update**: Updated README and created a new PROFILE.md document detailing the user profile extension framework, offering guidance on implementation and integration within applications. [commit 6e482fa4]

## Testing
- No explicit testing updates beyond integration of service mocks.

## Other Changes
- **Dependency Updates**: Several dependency updates managed by dependabot, including updates to the MariaDB Java client, Jakarta validation API, and Thymeleaf layout dialect, ensuring the use of latest stable libraries. [commits 6364b568, 2ef01f1e, 606e02f6]
- **Script Migration**: Migrated the user schema SQL script to a dedicated MariaDB script directory for better organization and separation from other resources. [commit 00cd0b74]
- **Gradle Release Versioning**: Updated the project version to `3.0.2-SNAPSHOT` and subsequently to `3.1.0-SNAPSHOT` as part of the release process, ensuring versioning reflects ongoing developments. [commits eb07d886, 363013cd] 

This changelog is intended to provide a comprehensive overview of recent changes with an emphasis on adding context and detail useful to developers reviewing updates and integrating them into ongoing projects.

## [3.0.1] - 2025-02-01
### Features
- The controller path mappings are now configurable.

### Fixes
- Fixed the bug where the schema.sql file was in a location that caused it to be automatically executed, it has now been migrated from the resources to the db-scripts directory.



## [3.0.0] - 2025-01-12
### Features
- Converted project from a simple framework to a Maven library with a separate demo app (#136).
- Updated dependencies to use compileOnly scope and upgraded versions.
- Massive refactoring to be a Maven Library
- Updated project version to 3.0.0-SNAPSHOT and Gradle distribution to 8.12
- Updated the README.md file.

### Fixes
- Bumped com.codeborne:selenide from 7.5.1 to 7.6.0, then from 7.6.0 to 7.6.1, and finally from 7.6.1 to 7.7.0 (#127, #128, #129, and #133).
- Bumped io.spring.dependency-management from 1.1.6 to 1.1.7 (#131).
- Bumped com.google.guava:guava from 33.3.1-jre to 33.4.0-jre (#130).
- Bumped org.thymeleaf.extras:thymeleaf-extras-springsecurity6 (#129).
- Bumped org.mariadb.jdbc:mariadb-java-client from 3.5.0 to 3.5.1 (#125).
- Updated Gradle wrapper to version 8.12.

### Breaking Changes
- Major update in the project's structure as it was converted to a Maven library.

