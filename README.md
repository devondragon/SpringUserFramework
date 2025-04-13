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

### Maven

```xml
<dependency>
    <groupId>com.digitalsanctuary</groupId>
    <artifactId>ds-spring-user-framework</artifactId>
    <version>3.2.1</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.digitalsanctuary:ds-spring-user-framework:3.2.1'
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
- [Configuration Guide](CONFIG.md)
- [Security Guide](SECURITY.md)
- [Customization Guide](CUSTOMIZATION.md)
- [Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp)

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

Created by [Devon Hillard](https://github.com/devondragon/) at [Digital Sanctuary](https://www.digitalsanctuary.com/)
