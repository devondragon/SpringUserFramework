# CONFIG.md

Welcome to the User Framework SpringBoot Configuration Guide! This document outlines the key configuration values you'll need to set up and customize the framework for your specific needs. Configuration values which can generally be left as defaults are not included in this document. Please review the applicaiton.yml file for more information on all the available configuration values.

## Essential Configuration

### Mail Server Settings

- **Username (`spring.mail.username`)**: Set this to your mail server's username.
- **Password (`spring.mail.password`)**: Your mail server's password goes here.
- **Host (`spring.mail.host`)**: Set this to your mail server's hostname
- **Port (`spring.mail.port`)**: Set to `587` by default. Modify if your mail server uses a different port.

### Database Configuration

- **URL (`spring.datasource.url`)**: The JDBC URL for your database.
- **Username (`spring.datasource.username`)**: Database username.
- **Password (`spring.datasource.password`)**: Database password.
- **Driver Class Name (`spring.datasource.driverClassName`)**: The JDBC driver, defaults to `org.mariadb.jdbc.Driver`.

### Hibernate Settings

- **DDL Auto (`spring.jpa.hibernate.ddl-auto`)**: Hibernate schema generation strategy, defaults to `update`.
- **Dialect (`spring.jpa.properties.hibernate.dialect`)**: Set this to the appropriate dialect for your database, defaults to `org.hibernate.dialect.MariaDBDialect`.

### Application Properties

- **Name (`spring.application.name`)**: Set your application's name, defaults to `User Framework`.

## User Settings

- **Account Deletion (`user.actuallyDeleteAccount`)**: Set to `true` to enable account deletion. Defaults to `false` where accounts are disabled instead of deleted.
- **Registration Email Verification (`user.registration.sendVerificationEmail`)**: Enable (`true`) or disable (`false`) sending verification emails post-registration.

## Audit Logging

- **Log File Path (`user.audit.logFilePath`)**: The path to the audit log file.
- **Flush on Write (`user.audit.flushOnWrite`)**: Set to `true` for immediate log flushing. Defaults to `false` for performance.

## Security Settings

- **Failed Login Attempts (`spring.security.failedLoginAttempts`)**: Number of failed login attempts before account lockout. Set to `0` to disable lockout.
- **Account Lockout Duration (`spring.security.accountLockoutDuration`)**: Duration (in minutes) for account lockout.
- **BCrypt Strength (`spring.security.bcryptStrength`)**: Adjust the bcrypt strength for password hashing. Default is `12`.

## Mail Configuration

- **From Address (`spring.mail.fromAddress`)**: The email address used as the sender in outgoing emails.

## Copyright

- **First Year (`spring.copyrightFirstYear`)**: The starting year for the copyright notice.

## Role and Privileges

- **Roles and Privileges (`spring.roles-and-privileges`)**: Map out roles to their respective privileges.
- **Role Hierarchy (`spring.role-hierarchy`)**: Define the hierarchy and inheritance of roles.

## New Relic Monitoring

- **API Key and Account ID (`management.newrelic.metrics.export`)**: Required if you're integrating with New Relic for monitoring.

## Server and Session Settings

- **Session Timeout (`server.servlet.session.timeout`)**: The session timeout period, defaults to `30m` (30 minutes).

## Logging

- **Log File Path (`logging.file.name`)**: Set the path to the application log file.

---

Remember, this guide covers the most critical settings to get you started. Depending on your specific use case, you may need to explore and adjust additional configurations. Always refer to the official SpringBoot and related libraries' documentation for more detailed information.
