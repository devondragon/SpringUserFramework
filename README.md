# Spring User Framework

[![Maven Central](https://img.shields.io/maven-central/v/com.digitalsanctuary/ds-spring-user-framework.svg)](https://central.sonatype.com/artifact/com.digitalsanctuary/ds-spring-user-framework)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java Version](https://img.shields.io/badge/Java-17%2B-brightgreen)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)

A comprehensive Spring Boot User Management Framework that simplifies the implementation of robust user authentication and management features. Built on top of Spring Security, this library provides ready-to-use solutions for user registration, login, account management, and more.

Check out the [Spring User Framework Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp) for a complete example of how to use this library.



## Table of Contents
- [Spring User Framework](#spring-user-framework)
  - [Table of Contents](#table-of-contents)
  - [Features](#features)
  - [Installation](#installation)
    - [Maven](#maven)
    - [Gradle](#gradle)
  - [Quick Start](#quick-start)
  - [Configuration](#configuration)
  - [Security Features](#security-features)
    - [Role-Based Access Control](#role-based-access-control)
    - [Account Lockout](#account-lockout)
    - [Audit Logging](#audit-logging)
  - [User Management](#user-management)
    - [Registration](#registration)
    - [Profile Management](#profile-management)
  - [Email Verification](#email-verification)
  - [Authentication](#authentication)
    - [Local Authentication](#local-authentication)
    - [OAuth2/SSO](#oauth2sso)
  - [Extensibility](#extensibility)
    - [Custom User Profiles](#custom-user-profiles)
  - [Examples](#examples)
  - [Reference Documentation](#reference-documentation)
  - [License](#license)

## Features

- **User Registration and Authentication**
  - Local username/password authentication
  - OAuth2/SSO with Google, Facebook, and more
  - Email verification workflow
  - Password reset functionality
  - Account management (update profile, change password)

- **Advanced Security**
  - Role and privilege-based authorization
  - Configurable password policies
  - Account lockout after failed login attempts
  - Audit logging for security events
  - CSRF protection out of the box

- **Extensible Architecture**
  - Easily extend user profiles with custom data
  - Override default behaviors where needed
  - Integration with Spring ecosystem
  - Customizable UI templates

- **Developer-Friendly**
  - Minimal boilerplate code to get started
  - Configuration-driven features
  - Comprehensive documentation
  - Demo application for reference

## Installation

### Maven

```xml
<dependency>
    <groupId>com.digitalsanctuary</groupId>
    <artifactId>ds-spring-user-framework</artifactId>
    <version>3.1.1</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.digitalsanctuary:ds-spring-user-framework:3.1.1'
```

## Quick Start

1. **Add the dependency** as shown above

2. **Set essential configuration** in your `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/yourdb
    username: dbuser
    password: dbpassword
    driver-class-name: org.mariadb.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
  mail:
    host: smtp.example.com
    port: 587
    username: your-username
    password: your-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

user:
  mail:
    fromAddress: noreply@yourdomain.com
  security:
    defaultAction: deny
    bcryptStrength: 12
    failedLoginAttempts: 5
    accountLockoutDuration: 15
```

3. **Create a UserProfile extension** for your application-specific user data:

```java
@Entity
@Table(name = "app_user_profile")
public class AppUserProfile extends BaseUserProfile {
    // Add your application-specific fields
    private String preferredLanguage;
    private boolean receiveNewsletter;

    // Getters and setters
}
```

4. **Run your application** and navigate to `/user/login.html` to see the login page.

## Configuration

The framework uses a configuration-first approach to customize behavior. See the [Configuration Guide](CONFIG.md) for detailed documentation of all configuration options.

Key configuration categories:

- **Security**: Access control, password policies, CSRF protection
- **Mail**: Email server settings for verification and notification emails
- **User Registration**: Self-registration options, verification requirements
- **Authentication**: Local and OAuth2 provider configuration
- **UI**: Paths to customized templates and views

## Security Features

### Role-Based Access Control

Define roles and privileges with hierarchical inheritance:

```yaml
user:
  roles:
    roles-and-privileges:
      "[ROLE_ADMIN]":
        - ADMIN_PRIVILEGE
        - USER_MANAGEMENT_PRIVILEGE
      "[ROLE_USER]":
        - LOGIN_PRIVILEGE
        - SELF_SERVICE_PRIVILEGE
    role-hierarchy:
      - ROLE_ADMIN > ROLE_USER
```

### Account Lockout

Prevent brute force attacks with configurable lockout policies:

```yaml
user:
  security:
    failedLoginAttempts: 5
    accountLockoutDuration: 30  # minutes
```

### Audit Logging

Track security-relevant events with built-in audit logging:

```yaml
user:
  audit:
    logEvents: true
    logFilePath: /path/to/audit/log
    flushOnWrite: false
    flushRate: 10000
```

## User Management

### Registration

Default registration flow includes:
- Form submission validation
- Email uniqueness check
- Email verification (optional)
- Welcome email
- Configurable initial roles

### Profile Management

Users can:
- Update their profile information
- Change their password
- Delete their account (configurable to either disable or fully delete)

## Email Verification

The framework includes a complete email verification system:
- Token generation and verification
- Customizable email templates
- Token expiration and renewal
- Automatic account activation

## Authentication

### Local Authentication

Username/password authentication with:
- Secure password hashing (bcrypt)
- Account lockout protection
- Remember-me functionality

### OAuth2/SSO

Support for social login providers:
- Google
- Facebook
- Apple
- Custom providers

Configuration example:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: your-client-id
            client-secret: your-client-secret
            scope: profile,email
```

## Extensibility

The framework is designed to be extended without modifying the core code.

### Custom User Profiles

Extend the `BaseUserProfile` to add your application-specific user data:

```java
@Service
public class CustomUserProfileService implements UserProfileService<CustomUserProfile> {
    @Override
    public CustomUserProfile getOrCreateProfile(User user) {
        // Implementation
    }

    @Override
    public CustomUserProfile updateProfile(CustomUserProfile profile) {
        // Implementation
    }
}
```
Read more in the [Profile Guide](PROFILE.md).

## Examples

For complete working examples, check out the [Spring User Framework Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp).

## Reference Documentation

- [API Documentation](https://digitalSanctuary.github.io/SpringUserFramework/)
- [Configuration Guide](CONFIG.md)
- [Security Guide](SECURITY.md)
- [Customization Guide](CUSTOMIZATION.md)
- [Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp)

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

Created by [Devon Hillard](https://github.com/devondragon/) at [Digital Sanctuary](https://www.digitalsanctuary.com/)
