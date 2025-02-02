## Table of Contents
- [SpringUserFramework](#springuserframework)
  - [Summary](#summary)
  - [Features](#features)
  - [How To Get Started](#how-to-get-started)
    - [Refer to the Demo Project](#refer-to-the-demo-project)
    - [Configuring Your Local Environment](#configuring-your-local-environment)
    - [Database](#database)
    - [Mail Sending (SMTP)](#mail-sending-smtp)
    - [SSO OAuth2 with Google and Facebook](#sso-oauth2-with-google-and-facebook)
  - [Overriding Spring Security Messages](#overriding-spring-security-messages)
  - [Notes](#notes)


# SpringUserFramework



SpringUserFramework is a Java Spring Boot User Management Framework designed to simplify the implementation of user management features in your SpringBoot web application. It is built on top of [Spring Security](https://spring.io/projects/spring-security) and provides out-of-the-box support for registration, login, logout, and forgot password flows. It also supports SSO with Google and Facebook.

The framework includes basic example pages that are unstyled, allowing for seamless integration into your application.

## Summary

This framework aims to achieve the following goals:
- Provide an easy-to-use starting point for any Spring-based web application that requires user management features.
- Offer a local database-backed user store, with the flexibility to integrate Single Sign-On (SSO) using Spring Security.
- Design the framework around REST APIs.
- Utilize Spring Security for enhanced security features, such as two-factor authentication (2FA) and SSO integrations.
- Enable easy configuration through the use of `application.yml` whenever possible.
- Support internationalization by utilizing the messages feature for all user-facing text and messaging.
- Provide an audit event framework to facilitate the generation of security audit trails.
- Use the email address as the default username.

## Features

The framework provides support for the following features:
- Registration, with optional email verification.
- Login and logout functionality.
- Forgot password flow.
- Database-backed user store using Spring JPA.
- SSO support for Google
- SSO support for Facebook
- Configuration options to control anonymous access, whitelist URIs, and protect specific URIs requiring a logged-in user session.
- CSRF protection enabled by default, with example jQuery AJAX calls passing the CSRF token from the Thymeleaf page context.
- Audit event framework for recording and logging security events, customizable to store audit events in a database or publish them via a REST API.
- Role and Privilege setup service to define roles, associated privileges, and role inheritance hierarchy using `application.yml`.
- Configurable Account Lockout after too many failed login attempts



## How To Get Started

This Framework is now available as a library on Maven Central.  You can add it to your Gradle project by adding the following dependency to your `build.gradle` file:

```groovy
implementation 'com.digitalsanctuary:ds-spring-user-framework:3.0.1'
```

Or to your Maven project by adding it to your `pom.xml` file:

```xml
<dependency>
    <groupId>com.digitalsanctuary</groupId>
    <artifactId>ds-spring-user-framework</artifactId>
    <version>3.0.1</version>
</dependency>
```

Please check for the latest version on [Maven Central](https://central.sonatype.com/artifact/com.digitalsanctuary/ds-spring-user-framework) (this README may not always be up to date).
When upgrading to a new version, please check the [CHANGELOG](CHANGELOG.md) for any breaking changes or new features.


### Refer to the Demo Project
I have created a demo project that uses this framework.  You can find it here: [SpringUserFrameworkDemo](https://github.com/devondragon/SpringUserFrameworkDemoApp). This demo project is a full SpringBoot application that uses this framework as a library.  You can use it as a reference for how to use this framework in your own project. It demonstrates all of the configuration values and how to override them in your own `application.yml` file. It also has functioning examples for all front end pages, javascript, etc...

In addition to being a fully functional reference, you can also use the demo project as a starting point for your own project.  Just clone the repo and start building your own application on top of it.


### Configuring Your Local Environment

You can read more about the required configuration values in the [Configuration Guide](CONFIG.md).

Missing or incorrect configuration values will make this framework not work correctly.

### Database
This framework uses a database as a user store. By building on top of Spring JPA it is easy to use whichever database you like.

If you set your JPA Hibernate ddl-auto property to "create" it will create the tables for you.  If you set it to "update" it will update the tables for you.  If you set it to "none" you will need to create the tables yourself.

If you are not using automatic schema updates or Flyway, you can set up your database manually using the provided `schema.sql` file:

```bash
mysql -u username -p database_name < db-scripts/mariadb-schema.sql
```

Flyway support will be coming soon. This will allow you to automatically update your database schema as you deploy new versions of your application.


### Mail Sending (SMTP)
The framework sends emails for verification links, forgot password flow, etc... so you need to configure the outbound SMTP server and authentication information.  This is done in the `application.yml` file.  You can see the example configuration in the Demo Project's `application.yml` file. Please also refer to the [Spring Boot Mail Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/appendix-application-properties.html#mail-properties) for more information on the available properties.


### SSO OAuth2 with Google and Facebook
The framework supports SSO OAuth2 with Google and Facebook.  To enable this you need to configure the client id and secret for each provider.  This is done in the application.yml (or application.properties) file using the [Spring Security OAuth2 properties](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html). You can see the example configuration in the Demo Project's `application.yml` file.

Here is a quick example for your reference:

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
```

For public OAuth you will need a public hostname and HTTPS enabled.  You can use ngrok or Cloudflare tunnels to create a public hostname and tunnel to your local machine during development.  You can then use the ngrok hostname in your Google and Facebook developer console configuration.




## Overriding Spring Security Messages

You may want to override the default Spring Security user facing messages.  You can do this in your messages.properties file, by adding any of the message keys from Spring Security (found here: [Spring Security Messages](https://github.com/spring-projects/spring-security/blob/main/core/src/main/resources/org/springframework/security/messages.properties)) and providing your own values.



## Notes
Please note that there is no warranty or guarantee of functionality, quality, performance, or security made by the author. The code is available freely, but you assume all responsibility and liability for its usage in your application.
