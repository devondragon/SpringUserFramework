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


## User Settings

- **Account Deletion (`user.actuallyDeleteAccount`)**: Set to `true` to enable account deletion. Defaults to `false` where accounts are disabled instead of deleted.
- **Registration Email Verification (`user.registration.sendVerificationEmail`)**: Enable (`true`) or disable (`false`) sending verification emails post-registration.

## Admin Settings

- **Admin App URL (`user.admin.appUrl`)**: Base URL for admin-initiated password reset emails. Required when using `initiateAdminPasswordReset(user)` without explicit URL. Example: `https://myapp.com`
- **Session Invalidation Warn Threshold (`user.session.invalidation.warn-threshold`)**: Number of active sessions that triggers a performance warning during session invalidation. Defaults to `1000`.

## Audit Logging

- **Log File Path (`user.audit.logFilePath`)**: The path to the audit log file.
- **Flush on Write (`user.audit.flushOnWrite`)**: Set to `true` for immediate log flushing. Defaults to `false` for performance.
- **Max Query Results (`user.audit.maxQueryResults`)**: Maximum number of audit events returned from queries. Prevents memory issues with large logs. Defaults to `10000`.

## GDPR Compliance

GDPR features are disabled by default and must be explicitly enabled.

- **Enable GDPR (`user.gdpr.enabled`)**: Master toggle for all GDPR features. When `false`, all GDPR endpoints return 404. Defaults to `false`.
- **Export Before Deletion (`user.gdpr.exportBeforeDeletion`)**: When `true`, user data is automatically exported and included in the deletion response. Defaults to `true`.
- **Consent Tracking (`user.gdpr.consentTracking`)**: Enable consent grant/withdrawal tracking via the audit system. Defaults to `true`.

**Example configuration:**
```yaml
user:
  gdpr:
    enabled: true
    exportBeforeDeletion: true
    consentTracking: true
```

**Note**: When GDPR is enabled, ensure you have a `UserPreDeleteEvent` listener configured to clean up application-specific user data before deletion. See the README for details.

## Security Settings

- **Failed Login Attempts (`spring.security.failedLoginAttempts`)**: Number of failed login attempts before account lockout. Set to `0` to disable lockout.
- **Account Lockout Duration (`spring.security.accountLockoutDuration`)**: Duration (in minutes) for account lockout.
- **BCrypt Strength (`spring.security.bcryptStrength`)**: Adjust the bcrypt strength for password hashing. Default is `12`.

## WebAuthn / Passkey Settings

Provides passwordless login using biometrics, security keys, or device authentication. **HTTPS is required** for WebAuthn to function.

- **Enabled (`user.webauthn.enabled`)**: Enable or disable WebAuthn/Passkey support. Defaults to `false`. Must be explicitly enabled along with the required database schema.
- **Relying Party ID (`user.webauthn.rpId`)**: For development, use `localhost`. For production, use your domain (e.g., `example.com`). Defaults to `localhost`.
- **Relying Party Name (`user.webauthn.rpName`)**: The display name.
- **Allowed Origins (`user.webauthn.allowedOrigins`)**: Comma-separated list of allowed origins. Defaults to `https://localhost:8443`.

**Development Example:**
```properties
user.webauthn.enabled=true
user.webauthn.rpId=localhost
user.webauthn.rpName=My Application
user.webauthn.allowedOrigins=https://localhost:8443
```

**Production Example:**
```properties
user.webauthn.enabled=true
user.webauthn.rpId=example.com
user.webauthn.rpName=My Application
user.webauthn.allowedOrigins=https://example.com
```

**Database Schema:**

WebAuthn requires two additional tables: `user_entities` and `user_credentials`. If using `ddl-auto: update`, Hibernate will create them automatically. For manual schema management, see `db-scripts/mariadb-schema.sql`.

**Important Notes:**
- WebAuthn is **disabled by default** and must be explicitly enabled along with the required database tables.
- WebAuthn requires HTTPS in production. HTTP is allowed on `localhost` for development.
- For local HTTPS development, generate a self-signed certificate: `keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 3650`
- Configure SSL in `application.properties`: `server.ssl.enabled=true`, `server.ssl.key-store=classpath:keystore.p12`
- Alternatively, use ngrok (`ngrok http 8080`) for HTTPS without certificates. Note: HTTP also works on localhost with most browsers.
- Users must be authenticated before they can register a passkey. Passkeys enhance existing authentication, not replace initial registration.
- You must add `/webauthn/authenticate/**` and `/login/webauthn` to your `unprotectedURIs` for passkey login to work.
- Passkey labels are limited to 64 characters.

## Mail Configuration

- **From Address (`spring.mail.fromAddress`)**: The email address used as the sender in outgoing emails.


## Role and Privileges

- **Roles and Privileges (`spring.roles-and-privileges`)**: Map out roles to their respective privileges.
- **Role Hierarchy (`spring.role-hierarchy`)**: Define the hierarchy and inheritance of roles.


## Server and Session Settings

- **Session Timeout (`server.servlet.session.timeout`)**: The session timeout period, defaults to `30m` (30 minutes).

## Logging

- **Log File Path (`logging.file.name`)**: Set the path to the application log file.

---

Remember, this guide covers the most critical settings to get you started. Depending on your specific use case, you may need to explore and adjust additional configurations. Always refer to the official SpringBoot and related libraries' documentation for more detailed information.
