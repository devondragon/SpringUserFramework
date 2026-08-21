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

`user.security.*` is bound to a typed `@ConfigurationProperties` class (`UserSecurityConfigProperties`). The **camelCase key spellings shown below are canonical** (e.g. `user.security.loginPageURI`, `user.security.registrationConfirmURI`) — relaxed binding also accepts kebab-case (`user.security.login-page-uri`), but the framework's `@GetMapping`/`@RequestMapping` placeholders resolve the exact camelCase key, so a kebab-only spelling for a URI property would move the security configuration without moving the mapped controller. The framework fails startup with the offending keys named if the two ever diverge, so this cannot happen silently. Stick to camelCase for anything under `user.security.*`.

Range and cross-field checks on these properties (bcrypt strength 4–31, password-policy `minLength <= maxLength`, and similar) are validated at startup when a Bean Validation implementation (e.g. `spring-boot-starter-validation`) is on your classpath; without one they are unenforced.

Page and action URIs configured here are also exposed to Thymeleaf templates as the `${userSecurity}` model attribute (e.g. `${userSecurity.loginPageUri}`), registered on every `@Controller` request. Disable it with `user.security.expose-uris-to-model=false` if you don't use it.

- **Failed Login Attempts (`user.security.failedLoginAttempts`)**: Number of failed login attempts before account lockout. Set to `0` to disable lockout. Applies to the login path and to the authenticated password-change endpoint `POST /user/updatePassword` (a locked account is rejected with `HTTP 423`, a wrong current password counts toward lockout, and a correct one resets the counter).
- **Account Lockout Duration (`user.security.accountLockoutDuration`)**: Duration (in minutes) for account lockout. `0` disables lockout; a negative value (e.g. `-1`) locks the account until an administrator unlocks it.
- **BCrypt Strength (`user.security.bcryptStrength`)**: Adjust the bcrypt strength for password hashing. Default is `12`.

### Email Link Authority (Host-header poisoning defense, CWE-640)

Password-reset and verification emails contain a link back to your application. The host in that link determines where the bearer token is sent, so it must not be derived from an attacker-controllable `Host` header. Configure at least one of the following in production.

- **App URL (`user.security.appUrl`)**: Canonical base URL for security email links (e.g. `https://app.example.com`). **Strongly recommended in production.** When set, request-derived hosts and `X-Forwarded-Host` are ignored entirely. Default: unset.
- **Trusted Hosts (`user.security.trustedHosts`)**: Comma-separated allow-list used when `appUrl` is unset. It gates **both** `X-Forwarded-Host` and the ordinary request server name (the `Host` header). A request host not in the list falls back to the first entry (treated as the canonical host) rather than being emitted into the link. Default: empty.
- **Require Canonical App URL (`user.security.requireCanonicalAppUrl`)**: When `true`, application startup fails unless `appUrl` or a non-empty `trustedHosts` is configured — a hard guarantee that email links can never derive their authority from a spoofable `Host` header. Default `false` (a startup warning is logged instead). Planned to become the default in the next major version.

When neither `appUrl` nor `trustedHosts` is set, links are built from the request host (backward-compatible behavior) and a startup warning is logged.

### CAPTCHA Protection (Cloudflare Turnstile)

Optional CAPTCHA verification on the framework's unauthenticated, email-sending API actions (`POST /user/registration`, `POST /user/resetPassword`, `POST /user/resendRegistrationToken`). Disabled by default: no CAPTCHA interceptor or provider beans are registered and behavior is unchanged until you opt in.

- **Enabled (`user.security.captcha.enabled`)**: Master switch. When `false` (default), no CAPTCHA interceptor or provider beans are registered and no requests are checked.
- **Provider (`user.security.captcha.provider`)**: The CAPTCHA provider. Only `turnstile` (Cloudflare Turnstile, via the optional `com.digitalsanctuary:ds-spring-cf-turnstile` dependency) is currently supported. Defaults to `turnstile`. Supply your own `CaptchaService` bean to use a different provider; it takes precedence over the built-in one.
- **Allow Unusable Provider (`user.security.captcha.allow-unusable-provider`)**: Whether to start when the provider reports it cannot verify anything (missing Turnstile secret or site key, absent service bean). Defaults to `false` — such a provider rejects every request to every protected endpoint, so startup fails rather than shipping an outage that looks healthy. Set `true` to boot anyway and take a startup ERROR banner instead.
- **Protect Registration (`user.security.captcha.protect.registration`)**: Require CAPTCHA on `POST /user/registration`. Defaults to `true`.
- **Protect Passwordless Registration (`user.security.captcha.protect.passwordless-registration`)**: Require CAPTCHA on `POST /user/registration/passwordless`. Defaults to `true`. Only reachable if you have added that path to `user.security.unprotectedURIs` and have a WebAuthn credential service, but it creates an account and sends a verification email for an unauthenticated caller just like the standard registration endpoint.
- **Protect Reset Password (`user.security.captcha.protect.reset-password`)**: Require CAPTCHA on `POST /user/resetPassword`. Defaults to `true`.
- **Protect Resend Registration Token (`user.security.captcha.protect.resend-registration-token`)**: Require CAPTCHA on `POST /user/resendRegistrationToken`. Defaults to `true`.

**Example configuration:**
```yaml
user:
  security:
    captcha:
      enabled: true
      provider: turnstile
      protect:
        registration: true
        reset-password: true
        resend-registration-token: true
```

**Client contract**: these endpoints consume JSON bodies, so the CAPTCHA token must be sent in the `X-Captcha-Token` request header (preferred) or the `cf-turnstile-response` query parameter — it cannot be added as a form field. Rejections return `HTTP 403` with a `JSONResponse` body (`code: 8`), customizable via the `message.captcha.validation-failed` message key. The site key is exposed to MVC pages as the `captchaSiteKey` model attribute. See the README's [CAPTCHA Protection](README.md#captcha-protection-cloudflare-turnstile) section for the full client-side contract, fail-closed semantics, and scope notes (login is not covered).

### Step-Up Re-Authentication (SUF-02)

Credential-altering operations on a passwordless (passkey-only) account have no current credential to verify: `POST /user/setPassword` adds an *initial* password, and passkey delete/rename change how the account authenticates. Step-up requires a recent proof of presence before those proceed.

**How it works.** Spring Security records how a user authenticated as a factor authority carrying an issue time. Step-up requires one of the configured factors to have been issued within a short window. The user refreshes it by re-running that login ceremony while already logged in — for `WEBAUTHN`, the ordinary passkey assertion at `/login/webauthn`. There is no separate step-up endpoint, challenge store, or token, and the client reuses its existing login ceremony.

```yaml
user:
  security:
    stepUp:
      enabled: false          # default
      ttlSeconds: 120         # how recently the factor must have been issued
      factors: [WEBAUTHN]     # any one is sufficient
```

- **Enabled (`user.security.stepUp.enabled`)**: Registers the framework's built-in `StepUpService`. Default: `false`, in which case nothing below applies and behavior is unchanged.
- **TTL (`user.security.stepUp.ttlSeconds`)**: How recently the factor must have been issued. Default: `120`. Keep it short: within the window, one ceremony authorizes any credential-altering operation on that session.
- **Factors (`user.security.stepUp.factors`)**: Any one satisfies step-up. Valid values: `WEBAUTHN`, `PASSWORD`, `OTT`, `AUTHORIZATION_CODE`, `SAML_RESPONSE`, `CAS`, `X509`, `BEARER`. Default: `WEBAUTHN`, the only factor whose refresh reliably proves presence — re-running an OAuth2 login typically completes with no user interaction while the identity-provider session is alive. Naming a factor your deployment never issues makes the gated operations permanently unavailable. Startup fails on an unknown name.

**Client contract.** A gated operation with no sufficiently recent factor returns `HTTP 401`: `setPassword` with `JSONResponse` code `6`, passkey delete/rename with error code `step-up-required`. The client re-runs its passkey login ceremony and retries the original call.

- **Enrollment window (`user.security.stepUp.enrollmentTtlSeconds`)**: How recently the user must have authenticated, by *any* means, to register a new passkey at `POST /webauthn/register`. Default: `600`. Applies only while step-up is enabled.

  Enrolling a passkey is what turns a stolen session into durable access: the credential outlives a password change, since session invalidation ends sessions rather than credentials, and asserting with it refreshes `FACTOR_WEBAUTHN`. Without this gate an attacker holding only a session cookie could enroll their own authenticator and use it to satisfy step-up, so the rest of the feature would protect nothing. This mirrors GitHub's sudo mode, which asks for a password before adding a security key.

  Any authentication factor counts, deliberately not the `factors` list above: with the default `[WEBAUTHN]`, requiring a configured factor would demand a passkey in order to register a first passkey. The window is longer than `ttlSeconds` because enrollment normally follows a login, a look around the settings page, and a decision, whereas a step-up ceremony immediately precedes its operation.

  **Residual risk:** within this window of a genuine login, an attacker sharing the session can still enroll. The window bounds the exposure rather than eliminating it, which is the same trade-off sudo mode makes. A `PasskeyRegistration` audit event is recorded for every enrollment.

**Enabling step-up also enables factor merging** (`setMfaEnabled(true)` on authentication processing filters), without which re-authenticating replaces the session's authorities instead of merging them. If your application registers its own `AbstractAuthenticationProcessingFilter`, see the warning in `MfaFilterMergingConfiguration`.

**Accounts with no passkey** (OAuth-only, for example) cannot satisfy `WEBAUTHN` step-up. For them `setPassword` remains governed by `allowInitialPasswordSetWithoutStepUp` below, exactly as before.

**Reserved authority names.** Do not name a role or privilege `FACTOR_*` in `user.roles-and-privileges`. Spring Security uses that prefix for factor authorities, and a plain authority with such a name is indistinguishable from a real factor by name: it satisfies MFA enforcement without the factor ever being completed, and it shadows the genuine factor in a step-up freshness check. Startup fails when such a name is configured while MFA or step-up is enabled, and logs an error otherwise.

**Custom implementations.** A `StepUpService` bean supplied by your application takes precedence over the built-in one, whatever `enabled` is set to. Implement the SPI to require TOTP, a hardware token, or any other proof.

- **Allow Without Step-Up (`user.security.allowInitialPasswordSetWithoutStepUp`)**: When no `StepUpService` bean is present at all, `setPassword` is **disabled** (`HTTP 403`) unless this is `true`, which restores the previous session-only behavior. Default: `false`.

### Token Security

Verification and password-reset tokens are **hashed at rest**. The raw token is only ever sent to the user in the emailed link; the database stores its hash. Lookups hash the incoming token and match by hash, with a transparent fallback to plaintext lookup so that any links issued before upgrading keep working until they expire. This requires no schema migration and no action from consuming applications.

- **Token Hash Secret (`user.security.tokenHashSecret`)**: Optional secret used to key the at-rest hashing (HMAC-SHA-256) of verification and password-reset tokens. If left unset, plain SHA-256 is used, which is adequate because tokens are high-entropy random values. Setting a secret (kept outside the database) adds defense-in-depth against a database-only compromise. Default: unset.
- **Password Reset Token Lifetime (`user.security.passwordResetTokenValidityMinutes`)**: Lifetime in minutes of a password reset token before it expires. Default is `1440` (24 hours).
- **Verification Token Lifetime (`user.registration.verificationTokenValidityMinutes`)**: Lifetime in minutes of a registration verification token before it expires. Default is `1440` (24 hours).

Only one active token per user is kept for each token type: requesting a new password reset or verification email invalidates the previous one.

### Remember-Me ("Stay Signed In")

Disabled by default. Two things are required to make it work, and both are on you as the consumer:

1. Set `user.security.rememberMe.enabled=true` **and** a `user.security.rememberMe.key`. Without a key the feature stays off.
2. Your login form must post the remember-me request parameter (a checkbox named `remember-me` by default). **Without the parameter, no cookie is ever issued** — enabling the properties alone does nothing visible.

```html
<input type="checkbox" name="remember-me"> Remember me
```

- **Enabled (`user.security.rememberMe.enabled`)**: Master switch. Default `false`.
- **Key (`user.security.rememberMe.key`)**: Secret used to sign remember-me tokens. Required. Keep it stable across restarts and instances — changing it invalidates every outstanding remember-me cookie.
- **Token Validity (`user.security.rememberMe.tokenValiditySeconds`)**: How long a token stays valid. Default `1209600` (14 days).
- **Parameter Name (`user.security.rememberMe.rememberMeParameter`)**: Request parameter the login form posts. Default `remember-me`.
- **Cookie Name (`user.security.rememberMe.rememberMeCookieName`)**: Default `remember-me`.
- **Secure Cookie (`user.security.rememberMe.useSecureCookie`)**: Unset by default, which means the cookie is marked `Secure` whenever the request that created it used HTTPS. **Behind a TLS-terminating reverse proxy the request reaches the app as plain HTTP**, so the default only works if forwarded-header processing is configured (e.g. `server.forward-headers-strategy=framework` or `native`). If you terminate TLS at a proxy, either configure forwarded headers or set this to `true` explicitly.
- **Persistent Tokens (`user.security.rememberMe.usePersistentTokens`)**: Default `false` (hash-based cookies). See below.

**Hash-based vs. persistent tokens.** By default remember-me uses Spring Security's hash-based `TokenBasedRememberMeServices`: the cookie is a self-contained signature and nothing is stored server-side. Setting `user.security.rememberMe.usePersistentTokens=true` switches to database-backed tokens (`JdbcTokenRepositoryImpl`), which **requires the `persistent_logins` table** — the DDL is in `db-scripts/`; the library does not create it for you. You can also supply your own `PersistentTokenRepository` bean, which takes precedence.

**Revocation semantics — read this before choosing a mode:**

- **Persistent tokens** are revoked server-side by the library: when a user's sessions are invalidated (account disable/delete, admin-initiated sign-out) and on password change, all of the user's stored tokens are removed. On a self-service password change this includes the current device's token — the current session stays alive, but the user logs in again once it ends.
- **Hash-based tokens cannot be revoked by admin action.** There is no server-side state; a cookie stays valid until it expires. A password change does invalidate them (the signature embeds the password hash). If "sign this user out everywhere, now" must also kill remember-me cookies, use persistent tokens.

Remember-me works with all of the library's authentication paths (form login, OAuth2/OIDC, passkeys) because they all converge on the same `DSUserDetails` principal. One caveat for OAuth2/OIDC consumers: on a remember-me auto-login, provider claims are not available — `getIdToken()`/`getUserInfo()` return `null` and `getAttributes()` falls back to values from the local `User` entity.

## WebAuthn / Passkey Settings

Provides passwordless login using biometrics, security keys, or device authentication. **HTTPS is required** for WebAuthn to function.

- **Enabled (`user.webauthn.enabled`)**: Enable or disable WebAuthn/Passkey support. Defaults to `false`. Must be explicitly enabled along with the required database schema.
- **Relying Party ID (`user.webauthn.rpId`)**: For development, use `localhost`. For production, use your domain (e.g., `example.com`). Defaults to `localhost`.
- **Relying Party Name (`user.webauthn.rpName`)**: The display name.
- **Allowed Origins (`user.webauthn.allowedOrigins`)**: Comma-separated list of allowed origins. Defaults to `https://localhost:8443`.
- **Registration notification (`user.webauthn.notifyOnRegistration`)**: Email the account owner when a passkey is registered on their account. Defaults to `true`. Enrolling a passkey grants a durable new way into the account that survives a password change, since session invalidation ends sessions rather than credentials, so an enrollment the owner did not perform is worth surfacing. A `PasskeyRegistration` audit event is recorded either way. Set to `false` only if your application sends its own equivalent notification.

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
