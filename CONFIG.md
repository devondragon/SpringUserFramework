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

- **Log File Path (`user.audit.logFilePath`)**: The path to the audit log file. If this path is not writable, the system falls back to the system temp directory.
- **Flush on Write (`user.audit.flushOnWrite`)**: Set to `true` for immediate log flushing on every write. Defaults to `false` for performance. See **Durability** below.
- **Flush Rate (`user.audit.flushRate`)**: The interval, in milliseconds, at which the buffered audit log is flushed to disk when `flushOnWrite=false`. Defaults to `30000` (30 seconds).
- **Max Query Results (`user.audit.maxQueryResults`)**: Maximum number of audit events returned from queries. The query service streams the active log file and retains only the most-recent `maxQueryResults` matching events in a bounded ring buffer, so query memory stays bounded regardless of file size. Defaults to `10000`.
- **Max File Size (`user.audit.maxFileSizeMb`)**: Maximum size, in megabytes, of the active audit log file before it is rotated. When exceeded, the active file is renamed to `<name>.1` (shifting existing archives up to `maxFiles`) and a fresh active file is opened. **Defaults to `0`, which disables rotation — the active audit file grows unbounded.** Rotation is opt-in (rather than on by default) because audit queries used by GDPR export and investigations read only the *active* file, so once events rotate into `<name>.1`, `<name>.2`, ... they are excluded from those results (see **Query Scope** below). Enable rotation (a positive value) only alongside external log retention or a database-backed `AuditLogWriter`/`AuditLogQueryService`; when enabled, `maxFiles` bounds how many archives are retained. If unbounded growth of the active file is a concern for your deployment, enable rotation with one of those retention strategies in place.
- **Max Files (`user.audit.maxFiles`)**: Maximum number of rotated archive files to retain (e.g. `user-audit.log.1` .. `user-audit.log.5`). The oldest archive beyond this count is deleted on rotation. Defaults to `5`.

### Durability

The file audit sink uses a buffered writer. With the default `flushOnWrite=false`, audit events are written to an in-memory buffer and flushed to disk periodically on the `flushRate` schedule. On a hard crash, JVM kill (SIGKILL), or power loss, **up to one `flushRate` interval of buffered audit events (plus any un-flushed buffer contents) can be lost**.

For compliance or security-critical deployments where no audit event may be lost, set `user.audit.flushOnWrite=true`. This flushes to disk after every event, eliminating the durability window at a per-write performance cost (under heavy load). Alternatively, lowering `flushRate` narrows the window without paying the full per-write cost.

### Query Scope

Audit queries (used by GDPR export and consent history) read only the **active** log file. Rotated archive files (`<name>.1`, `<name>.2`, ...) are not included in query results. If long-range historical queries are required, use a larger `maxFileSizeMb`/`maxFiles` window or a database-backed `AuditLogWriter`/`AuditLogQueryService`.

## JPA Auditing

- **Enable JPA Auditing (`user.jpa.auditing.enabled`)**: Controls whether the library enables Spring Data JPA auditing (`@EnableJpaAuditing`) and registers an `AuditorAware` that captures the current user from the Spring Security context for `@CreatedBy`/`@LastModifiedBy` fields. Defaults to `true`. Set to `false` if your application runs its own JPA auditing or supplies its own `AuditorAware` bean, so the library does not hijack it. This property is the primary opt-out, because the library's `@EnableJpaAuditing` resolves the auditor bean by name (`auditorProvider`).

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

- **Failed Login Attempts (`user.security.failedLoginAttempts`)**: Number of failed login attempts before account lockout. Set to `0` to disable lockout. Applies to the login path and to the authenticated password-change endpoint `POST /user/updatePassword` (a locked account is rejected with `HTTP 423`, a wrong current password counts toward lockout, and a correct one resets the counter).
- **Account Lockout Duration (`user.security.accountLockoutDuration`)**: Duration (in minutes) for account lockout. `0` disables lockout; a negative value (e.g. `-1`) locks the account until an administrator unlocks it.
- **BCrypt Strength (`user.security.bcryptStrength`)**: Adjust the bcrypt strength for password hashing. Default is `12`.

### Email Link Authority (Host-header poisoning defense, CWE-640)

Password-reset and verification emails contain a link back to your application. The host in that link determines where the bearer token is sent, so it must not be derived from an attacker-controllable `Host` header. Configure at least one of the following in production.

- **App URL (`user.security.appUrl`)**: Canonical base URL for security email links (e.g. `https://app.example.com`). **Strongly recommended in production.** When set, request-derived hosts and `X-Forwarded-Host` are ignored entirely. Default: unset.
- **Trusted Hosts (`user.security.trustedHosts`)**: Comma-separated allow-list used when `appUrl` is unset. It gates **both** `X-Forwarded-Host` and the ordinary request server name (the `Host` header). A request host not in the list falls back to the first entry (treated as the canonical host) rather than being emitted into the link. Default: empty.
- **Require Canonical App URL (`user.security.requireCanonicalAppUrl`)**: When `true`, application startup fails unless `appUrl` or a non-empty `trustedHosts` is configured — a hard guarantee that email links can never derive their authority from a spoofable `Host` header. Default `false` (a startup warning is logged instead). Planned to become the default in the next major version.

When neither `appUrl` nor `trustedHosts` is set, links are built from the request host (backward-compatible behavior) and a startup warning is logged.

### Token Security

Verification and password-reset tokens are **hashed at rest**. The raw token is only ever sent to the user in the emailed link; the database stores its hash. Lookups hash the incoming token and match by hash, with a transparent fallback to plaintext lookup so that any links issued before upgrading keep working until they expire. This requires no schema migration and no action from consuming applications.

- **Token Hash Secret (`user.security.tokenHashSecret`)**: Optional secret used to key the at-rest hashing (HMAC-SHA-256) of verification and password-reset tokens. If left unset, plain SHA-256 is used, which is adequate because tokens are high-entropy random values. Setting a secret (kept outside the database) adds defense-in-depth against a database-only compromise. Default: unset.
- **Password Reset Token Lifetime (`user.security.passwordResetTokenValidityMinutes`)**: Lifetime in minutes of a password reset token before it expires. Default is `1440` (24 hours).
- **Verification Token Lifetime (`user.registration.verificationTokenValidityMinutes`)**: Lifetime in minutes of a registration verification token before it expires. Default is `1440` (24 hours).

Only one active token per user is kept for each token type: requesting a new password reset or verification email invalidates the previous one.

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
- When a user account is deleted, all associated WebAuthn credentials and user entities are automatically cleaned up via the `UserPreDeleteEvent` listener. The database schema also uses `ON DELETE CASCADE` as a safety net.

## Dev Login Settings

Provides a reusable "login as" controller for local development, so consuming applications don't need to write boilerplate dev-login controllers. **This feature is disabled by default and requires both a property flag and the `local` Spring profile to activate.**

- **Auto-Login Enabled (`user.dev.auto-login-enabled`)**: Master toggle for the dev login feature. Defaults to `false`. Must be set to `true` **and** the `local` Spring profile must be active for the endpoints to be registered.
- **Login Redirect URL (`user.dev.login-redirect-url`)**: The URL to redirect to after a successful dev login. Defaults to `/`.

**Example configuration:**
```yaml
# application-local.yml (only active with spring.profiles.active=local)
user:
  dev:
    auto-login-enabled: true
    login-redirect-url: /dashboard
```

**Endpoints** (only available when enabled with the `local` profile):

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/dev/login-as/{email}` | GET | Authenticate as the specified user and redirect |
| `/dev/users` | GET | List all enabled user emails |

**Important Notes:**
- The `local` Spring profile **must** be active. Without it, the controller and warning beans are never registered regardless of the property value.
- When enabled, `/dev/**` is automatically added to the unprotected URI list and CSRF-ignored URIs in `WebSecurityConfig`.
- A prominent WARN-level banner is logged on startup when dev login is active.
- **NEVER enable this in production.** It bypasses all password authentication.

## Mail Configuration

- **From Address (`spring.mail.fromAddress`)**: The email address used as the sender in outgoing emails.

### Mail Executor

Email is sent asynchronously (`@Async`) with retry/backoff. To prevent an SMTP outage from starving the shared application task executor that other
async features rely on, mail runs on its own dedicated, bounded executor bean named `dsMailExecutor` (core pool 2, max pool 4, queue capacity 50, with
a `CallerRunsPolicy` rejection handler that applies backpressure to the calling thread when the pool and queue are saturated). To change the sizing,
supply your own `dsMailExecutor` bean (a `ThreadPoolTaskExecutor`); the library's default backs off via `@ConditionalOnMissingBean(name = "dsMailExecutor")`.


## Role and Privileges

- **Roles and Privileges (`spring.roles-and-privileges`)**: Map out roles to their respective privileges.
- **Role Hierarchy (`spring.role-hierarchy`)**: Define the hierarchy and inheritance of roles.


## Server and Session Settings

- **Session Timeout (`server.servlet.session.timeout`)**: The session timeout period, defaults to `30m` (30 minutes).

## Logging

- **Log File Path (`logging.file.name`)**: Set the path to the application log file.

---

Remember, this guide covers the most critical settings to get you started. Depending on your specific use case, you may need to explore and adjust additional configurations. Always refer to the official SpringBoot and related libraries' documentation for more detailed information.
