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

