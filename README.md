# Spring User Framework

[![Maven Central](https://img.shields.io/maven-central/v/com.digitalsanctuary/ds-spring-user-framework.svg)](https://central.sonatype.com/artifact/com.digitalsanctuary/ds-spring-user-framework)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.5-blue)](https://spring.io/projects/spring-boot)
[![Java Version](https://img.shields.io/badge/Java-17%20|%2021-brightgreen)](https://www.oracle.com/java/technologies/downloads/)

A comprehensive Spring Boot User Management Framework that simplifies the implementation of robust user authentication and management features. Built on top of Spring Security, this library provides ready-to-use solutions for user registration, login, account management, and more.

Check out the [Spring User Framework Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp) for a complete example of how to use this library.



## Table of Contents
- [Spring User Framework](#spring-user-framework)
  - [Table of Contents](#table-of-contents)
  - [Features](#features)
  - [Installation](#installation)
    - [Spring Boot 4.0 (Latest)](#spring-boot-40-latest)
    - [Spring Boot 3.5 (Stable)](#spring-boot-35-stable)
  - [Migration Guide](#migration-guide)
  - [Quick Start](#quick-start)
    - [Prerequisites](#prerequisites)
    - [Step 1: Add Dependencies](#step-1-add-dependencies)
    - [Step 2: Database Configuration](#step-2-database-configuration)
    - [Step 3: JPA Configuration](#step-3-jpa-configuration)
    - [Step 4: Email Configuration (Optional but Recommended)](#step-4-email-configuration-optional-but-recommended)
    - [Step 5: Essential Framework Configuration](#step-5-essential-framework-configuration)
    - [Step 6: Create User Profile Extension (Optional)](#step-6-create-user-profile-extension-optional)
    - [Step 7: Start Your Application](#step-7-start-your-application)
    - [Step 8: Test Core Features](#step-8-test-core-features)
    - [Step 9: Customize Pages (Optional)](#step-9-customize-pages-optional)
    - [Complete Example Configuration](#complete-example-configuration)
    - [Next Steps](#next-steps)
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
      - [**SSO OIDC with Keycloak**](#sso-oidc-with-keycloak)
  - [Extensibility](#extensibility)
    - [Custom User Profiles](#custom-user-profiles)
    - [Handling User Account Deletion and Profile Cleanup](#handling-user-account-deletion-and-profile-cleanup)
      - [Enabling Actual Deletion](#enabling-actual-deletion)
    - [SSO OAuth2 with Google and Facebook](#sso-oauth2-with-google-and-facebook)
  - [Examples](#examples)
  - [Contributing](#contributing)
  - [Reference Documentation](#reference-documentation)
  - [License](#license)

## Features

- **User Registration and Authentication**
  - Registration, with optional email verification.
  - Login and logout functionality.
  - Forgot password flow.
  - Admin-initiated password reset with optional session invalidation.
  - Database-backed user store using Spring JPA.
  - SSO support for Google
  - SSO support for Facebook
  - SSO support for Keycloak
  - Configuration options to control anonymous access, whitelist URIs, and protect specific URIs requiring a logged-in user session.
  - CSRF protection enabled by default, with example jQuery AJAX calls passing the CSRF token from the Thymeleaf page context.
  - Audit event framework for recording and logging security events, customizable to store audit events in a database or publish them via a REST API.
  - Role and Privilege setup service to define roles, associated privileges, and role inheritance hierarchy using `application.yml`.
  - Configurable Account Lockout after too many failed login attempts

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

Choose the version that matches your Spring Boot version:

| Spring Boot Version | Framework Version | Java Version | Spring Security |
|---------------------|-------------------|--------------|-----------------|
| 4.0.x               | 4.0.x             | 21+          | 7.x             |
| 3.5.x               | 3.5.x             | 17+          | 6.x             |

### Spring Boot 4.0 (Latest)

Spring Boot 4.0 brings significant changes including Spring Security 7 and requires Java 21.

**Maven:**
```xml
<dependency>
    <groupId>com.digitalsanctuary</groupId>
    <artifactId>ds-spring-user-framework</artifactId>
    <version>4.0.1</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'com.digitalsanctuary:ds-spring-user-framework:4.0.1'
```

#### Spring Boot 4.0 Key Changes

When upgrading to Spring Boot 4.0, be aware of these important changes:

- **Java 21 Required**: Spring Boot 4.0 requires Java 21 or higher
- **Spring Security 7**: Includes breaking changes from Spring Security 6.x
  - All URL patterns in security configuration must start with `/`
  - Some deprecated APIs have been removed
- **Jackson 3**: JSON processing uses Jackson 3.x with some API changes
- **Modular Test Infrastructure**: Test annotations have moved to new packages:
  - `@AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`
  - `@DataJpaTest` → `org.springframework.boot.data.jpa.test.autoconfigure`
  - `@WebMvcTest` → `org.springframework.boot.webmvc.test.autoconfigure`

For testing, you may need these additional dependencies:
```groovy
testImplementation 'org.springframework.boot:spring-boot-data-jpa-test'
testImplementation 'org.springframework.boot:spring-boot-webmvc-test'
testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
```

**Upgrading from 3.x?** See the [Migration Guide](MIGRATION.md) for detailed upgrade instructions.

### Spring Boot 3.5 (Stable)

For projects using Spring Boot 3.5.x with Java 17+:

**Maven:**
```xml
<dependency>
    <groupId>com.digitalsanctuary</groupId>
    <artifactId>ds-spring-user-framework</artifactId>
    <version>3.5.1</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'com.digitalsanctuary:ds-spring-user-framework:3.5.1'
```

## Migration Guide

If you're upgrading from a previous version, see the **[Migration Guide](MIGRATION.md)** for:

- Step-by-step upgrade instructions
- Breaking changes and how to address them
- Spring Security 7 compatibility requirements
- Test infrastructure changes
- Guidance for developers extending the framework

## Quick Start

Follow these steps to get up and running with the Spring User Framework in your application.

### Prerequisites

- **For Spring Boot 4.0**: Java 21 or higher
- **For Spring Boot 3.5**: Java 17 or higher
- A database (MariaDB, PostgreSQL, MySQL, H2, etc.)
- SMTP server for email functionality (optional but recommended)

### Step 1: Add Dependencies

1. **Add the main framework dependency** (see [Installation](#installation) for version selection):

   **Spring Boot 4.0 (Java 21+):**
   ```groovy
   implementation 'com.digitalsanctuary:ds-spring-user-framework:4.0.1'
   ```

   **Spring Boot 3.5 (Java 17+):**
   ```groovy
   implementation 'com.digitalsanctuary:ds-spring-user-framework:3.5.1'
   ```

2. **Add required dependencies**:

   **Important**: This framework requires these additional Spring Boot starters to function properly:

   Maven:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-thymeleaf</artifactId>
   </dependency>
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-mail</artifactId>
   </dependency>
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-data-jpa</artifactId>
   </dependency>
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-security</artifactId>
   </dependency>
   <dependency>
       <groupId>org.springframework.retry</groupId>
       <artifactId>spring-retry</artifactId>
   </dependency>
   ```

   Gradle:
   ```groovy
   implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
   implementation 'org.springframework.boot:spring-boot-starter-mail'
   implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
   implementation 'org.springframework.boot:spring-boot-starter-security'
   implementation 'org.springframework.retry:spring-retry'
   ```

### Step 2: Database Configuration

Configure your database in `application.yml`. The framework supports all databases compatible with Spring Data JPA:

**MariaDB/MySQL:**
```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/yourdb?createDatabaseIfNotExist=true
    username: dbuser
    password: dbpassword
    driver-class-name: org.mariadb.jdbc.Driver
```

**PostgreSQL:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/yourdb
    username: dbuser
    password: dbpassword
    driver-class-name: org.postgresql.Driver
```

**H2 (for development/testing):**
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
```

### Step 3: JPA Configuration

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # Creates/updates tables automatically
    show-sql: false     # Set to true for SQL debugging
```

### Step 4: Email Configuration (Optional but Recommended)

For password reset and email verification features:

```yaml
spring:
  mail:
    host: smtp.gmail.com       # or your SMTP server
    port: 587
    username: your-email@gmail.com
    password: your-app-password
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

user:
  mail:
    fromAddress: noreply@yourdomain.com  # Email "from" address
```

### Step 5: Essential Framework Configuration

Add these minimal settings to get started:

```yaml
user:
  # Basic security settings
  security:
    defaultAction: deny                    # Secure by default
    bcryptStrength: 12                     # Password hashing strength
    failedLoginAttempts: 5                 # Account lockout threshold
    accountLockoutDuration: 15             # Lockout duration in minutes

  # Registration settings
  registration:
    sendVerificationEmail: false           # true = email verification required
                                          # false = auto-enable accounts
```

### Step 6: Create User Profile Extension (Optional)

If you need additional user data beyond the built-in fields, create a profile extension:

```java
@Entity
@Table(name = "app_user_profile")
public class AppUserProfile extends BaseUserProfile {
    private String department;
    private String phoneNumber;
    private LocalDate birthDate;

    // Getters and setters
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    // ... other getters and setters
}
```

### Step 7: Start Your Application

1. **Run your Spring Boot application**:
   ```bash
   mvn spring-boot:run
   # or
   ./gradlew bootRun
   ```

2. **Verify the framework is working**:
   - Navigate to `http://localhost:8080/user/login.html` to see the login page
   - Check your database - user tables should be created automatically
   - Look for framework startup messages in the console

### Step 8: Test Core Features

**Create your first user:**
- Navigate to `/user/register.html`
- Fill out the registration form
- If `sendVerificationEmail=false`, you can login immediately
- If `sendVerificationEmail=true`, check your email for verification link

**Test login:**
- Navigate to `/user/login.html`
- Use the credentials you just created

### Step 9: Customize Pages (Optional)

The framework provides default HTML templates, but you can override them:

1. **Create custom templates** in `src/main/resources/templates/user/`:
   - `login.html` - Login page
   - `register.html` - Registration page
   - `forgot-password.html` - Password reset page
   - And more...

2. **Use your own CSS** by adding stylesheets to `src/main/resources/static/css/`

### Complete Example Configuration

Here's a complete `application.yml` for a typical setup:

```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/myapp?createDatabaseIfNotExist=true
    username: appuser
    password: apppass
    driver-class-name: org.mariadb.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

  mail:
    host: smtp.gmail.com
    port: 587
    username: myapp@gmail.com
    password: myapppassword
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true

user:
  mail:
    fromAddress: noreply@myapp.com

  security:
    defaultAction: deny
    bcryptStrength: 12
    failedLoginAttempts: 3
    accountLockoutDuration: 30

  registration:
    sendVerificationEmail: true

  # Optional: Audit logging
  audit:
    logEvents: true
    logFilePath: ./logs/audit.log
```

### Next Steps

- Read the [Configuration Guide](CONFIG.md) for advanced settings
- See [Extension Examples](PROFILE.md) for custom user profiles
- Check out the [Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp) for a complete example

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

The registration flow is configurable and can operate in two modes:

**Auto-Enable Mode** (default: `user.registration.sendVerificationEmail=false`):
- Form submission validation
- Email uniqueness check
- User account is immediately enabled and can login
- No verification email is sent
- User has full access immediately after registration

**Email Verification Mode** (`user.registration.sendVerificationEmail=true`):
- Form submission validation
- Email uniqueness check
- User account is created but **disabled**
- Verification email is sent with confirmation link
- User must click verification link to enable account
- Account remains disabled until email verification is completed
- Configurable initial roles assigned after verification

**Configuration Example:**
```yaml
user:
  registration:
    sendVerificationEmail: true  # Enable email verification (default: false)
```

**Note:** When email verification is disabled, user accounts are immediately active and functional. When enabled, accounts require email confirmation before login is possible.

### Profile Management

Users can:
- Update their profile information
- Change their password
- Delete their account (configurable to either disable or fully delete)

### Admin Password Reset

Administrators can trigger password resets for users programmatically:

```java
@Autowired
private UserEmailService userEmailService;

// Reset password and invalidate all user sessions
int sessionsInvalidated = userEmailService.initiateAdminPasswordReset(user, appUrl, true);

// Reset password without invalidating sessions
userEmailService.initiateAdminPasswordReset(user, appUrl, false);

// Use configured appUrl (from user.admin.appUrl property)
userEmailService.initiateAdminPasswordReset(user);
```

**Features:**
- Requires `ROLE_ADMIN` authorization (`@PreAuthorize`)
- Optional session invalidation to force re-authentication
- Sends password reset email with secure token
- Comprehensive audit logging with correlation IDs
- Cryptographically secure tokens (256-bit entropy)

**Configuration:**
```yaml
user:
  admin:
    appUrl: https://myapp.com  # Base URL for password reset links
```

**Security Notes:**
- Admin identity is derived from `SecurityContext`, not user input
- Sessions are invalidated *after* email is sent to prevent lockout
- URL validation prevents XSS (blocks javascript:, data: schemes)

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
- Keycloak
- Custom providers

Configuration example:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: YOUR_GOOGLE_CLIENT_ID
            client-secret: YOUR_GOOGLE_CLIENT_SECRET
            redirect-uri: "{baseUrl}/login/oauth2/code/google"
          facebook:
            client-id: YOUR_FACEBOOK_CLIENT_ID
            client-secret: YOUR_FACEBOOK_CLIENT_SECRET
            redirect-uri: "{baseUrl}/login/oauth2/code/facebook"
          keycloak:
            client-id: YOUR_KEYCLOAK_CLIENT_ID
            client-secret: YOUR_KEYCLOAK_CLIENT_SECRET
            redirect-uri: "{baseUrl}/login/oauth2/code/keycloak"

```
For public OAuth you will need a public hostname and HTTPS enabled.  You can use ngrok or Cloudflare tunnels to create a public hostname and tunnel to your local machine during development.  You can then use the ngrok hostname in your Google, Facebook and Keycloak developer console configuration.


#### **SSO OIDC with Keycloak**
To enable SSO:
1. Create OIDC client in Keycloak admin console.
2. Update your `application-docker-keycloak.yml`:
   ```yaml
   spring:
     security:
       oauth2:
         client:
            registration:
              keycloak:
                client-id: ${DS_SPRING_USER_KEYCLOAK_CLIENT_ID} # Keycloak client ID for OAuth2
                client-secret: ${DS_SPRING_USER_KEYCLOAK_CLIENT_SECRET} # Keycloak client secret for OAuth2
                authorization-grant-type: authorization_code # Authorization grant type for OAuth2
                scope:
                  - email # Request email scope for OAuth2
                  - profile # Request profile scope for OAuth2
                  - openid # Request oidc scope for OAuth2
                client-name: Keycloak # Name of the OAuth2 client
                provider: keycloak
            provider:
              keycloak: # https://www.keycloak.org/securing-apps/oidc-layers
                issuer-uri: ${DS_SPRING_USER_KEYCLOAK_PROVIDER_ISSUER_URI}
                authorization-uri: ${DS_SPRING_USER_KEYCLOAK_PROVIDER_AUTHORIZATION_URI}
                token-uri: ${DS_SPRING_USER_KEYCLOAK_PROVIDER_TOKEN_URI}
                user-info-uri: ${DS_SPRING_USER_KEYCLOAK_PROVIDER_USER_INFO_URI}
                user-name-attribute: preferred_username # https://www.keycloak.org/docs-api/latest/rest-api/index.html#UserRepresentation
                jwk-set-uri: ${DS_SPRING_USER_KEYCLOAK_PROVIDER_JWK_SET_URI}
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

### Handling User Account Deletion and Profile Cleanup
By default, when a user account is "deleted" through the framework's services or APIs, the account is marked as disabled (`enabled=false`) rather than being physically removed from the database. This is controlled by the `user.actuallyDeleteAccount` configuration property, which defaults to `false`.

#### Enabling Actual Deletion

If you require user accounts to be physically deleted from the database, set the following property in your `application.properties` or `application.yml`:

```properties
user.actuallyDeleteAccount=true
```

Cleaning Up Related Data (e.g., User Profiles)
When user.actuallyDeleteAccount is set to true, the framework needs a way to ensure that related data, such as application-specific user profiles extending BaseUserProfile, is also cleaned up to avoid orphaned data or foreign key constraint violations.

To facilitate this in a decoupled manner, the framework publishes a UserPreDeleteEvent immediately before the User entity is deleted from the database. This event is published within the same transaction as the user deletion.

Consuming applications that have extended BaseUserProfile (or have other user-related data) should listen for this event and perform the necessary cleanup operations.

Event Class: com.digitalsanctuary.spring.user.event.UserPreDeleteEvent Event Data: Contains the User entity that is about to be deleted (event.getUser()).

Example Event Listener:

Here's an example of how a consuming application can implement an event listener to delete its specific user profile (DemoUserProfile in this case) when a user is deleted:

```java
package com.digitalsanctuary.spring.demo.user.profile;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.digitalsanctuary.spring.user.event.UserPreDeleteEvent;

/**
 * Listener for user profile deletion events. This class listens for UserPreDeleteEvent and deletes the associated DemoUserProfile. It is assumed that
 * the DemoUserProfile is mapped to the User entity with a one-to-one relationship.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserProfileDeletionListener {
    private final DemoUserProfileRepository demoUserProfileRepository;
    // Inject other repositories if needed (e.g., EventRegistrationRepository)

    @EventListener
    @Transactional // Joins the transaction started by UserService.deleteUserAccount
    public void handleUserPreDelete(UserPreDeleteEvent event) {
        Long userId = event.getUser().getId();
        log.info("Received UserPreDeleteEvent for userId: {}. Deleting associated DemoUserProfile...", userId);

        // Option 1: Delete profile directly (if no further cascades needed from profile)
        // Since DemoUserProfile uses @MapsId, its ID is the same as the User's ID
        demoUserProfileRepository.findById(userId).ifPresent(profile -> {
            log.debug("Found DemoUserProfile for userId: {}. Deleting...", userId);
            // If DemoUserProfile itself has relationships needing cleanup (like EventRegistrations)
            // that aren't handled by CascadeType.REMOVE or orphanRemoval=true,
            // handle them here *before* deleting the profile.
            // Example: eventRegistrationRepository.deleteByUserProfile(profile);
            demoUserProfileRepository.delete(profile);
            log.debug("DemoUserProfile deleted for userId: {}", userId);
        });

        // Option 2: If DemoUserProfile has CascadeType.REMOVE/orphanRemoval=true
        // on its collections (like eventRegistrations), deleting the profile might be enough.
        // demoUserProfileRepository.deleteById(userId);

        log.info("Finished processing UserPreDeleteEvent for userId: {}", userId);
    }
}
```

By implementing such a listener, your application ensures data integrity when the actual user account deletion feature is enabled, without requiring the core framework library to have knowledge of your specific profile entities. If you leave user.actuallyDeleteAccount as false, this event is not published, and no listener implementation is required for profile cleanup



### SSO OAuth2 with Google and Facebook
The framework supports SSO OAuth2 with Google, Facebook and Keycloak.  To enable this you need to configure the client id and secret for each provider.  This is done in the application.yml (or application.properties) file using the [Spring Security OAuth2 properties](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html). You can see the example configuration in the Demo Project's `application.yml` file.


## Examples

For complete working examples, check out the [Spring User Framework Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp).


## Contributing

We welcome contributions of all kinds! If you'd like to help improve SpringUserFramework, please read our [Contributing Guide](CONTRIBUTING.md) for details on how to get started, report issues, and submit pull requests. Let's build something great together!


## Reference Documentation

- [API Documentation](https://digitalSanctuary.github.io/SpringUserFramework/)
- [Migration Guide](MIGRATION.md)
- [Configuration Guide](CONFIG.md)
- [Security Guide](SECURITY.md)
- [Customization Guide](CUSTOMIZATION.md)
- [Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp)

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

Created by [Devon Hillard](https://github.com/devondragon/) at [Digital Sanctuary](https://www.digitalsanctuary.com/)
