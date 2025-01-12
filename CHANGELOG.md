## [3.0.0] - 2025-01-12
### Features
- Added Python interpreter path to VSCode settings and created mise.toml for Python tool configuration.
- Converted project from a simple framework to a Maven library with a separate demo app (#136).
- Updated dependencies to use compileOnly scope and upgraded versions.
- Massive refactoring to be a Maven Library
- Updated project version to 3.0.0-SNAPSHOT and Gradle distribution to 8.10.2
- Updated the README.md file.

### Fixes
- Bumped com.codeborne:selenide from 7.5.1 to 7.6.0, then from 7.6.0 to 7.6.1, and finally from 7.6.1 to 7.7.0 (#127, #128, #129, and #133).
- Bumped io.spring.dependency-management from 1.1.6 to 1.1.7 (#131).
- Bumped com.google.guava:guava from 33.3.1-jre to 33.4.0-jre (#130).
- Bumped org.thymeleaf.extras:thymeleaf-extras-springsecurity6 (#129).
- Bumped org.mariadb.jdbc:mariadb-java-client from 3.5.0 to 3.5.1 (#125).
- Updated Gradle wrapper to version 8.11.

### Breaking Changes
- Project name updated to 'ds-spring-user-framework'.
- Deleted .github/ISSUE_TEMPLATE/sweep-template.yml.
- Deleted codeql-analysis.yml and removed sweep-template.yml.
- Update in the project's structure as it was converted to a Maven library.

