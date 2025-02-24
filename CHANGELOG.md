## [3.1.1] - 2025-02-24
### Features
- **Version Update to Develop Branch**: The project version has been incremented to `3.1.1-SNAPSHOT`. This is a preparatory step for future developments, ensuring that ongoing changes do not interfere with the stable release version. This change was made in the `gradle.properties` file. ([commit d24ae77c](#))

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

