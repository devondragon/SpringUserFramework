# Migration Guide

This guide covers migrating applications using the Spring User Framework between major versions.

## Table of Contents

- [Migration Guide](#migration-guide)
  - [Table of Contents](#table-of-contents)
  - [Migrating to 5.0.x](#migrating-to-50x)
    - [⚠️ ACTION REQUIRED: Reverse-proxy deployments must configure a canonical app URL](#-action-required-reverse-proxy-deployments-must-configure-a-canonical-app-url)
    - [Database schema: unique token constraint](#database-schema-unique-token-constraint)
    - [Registration & resend responses are now uniform (anti-enumeration)](#registration--resend-responses-are-now-uniform-anti-enumeration)
    - [Re-authentication required for credential changes](#re-authentication-required-for-credential-changes)
    - [Database schema: unique role/privilege names](#database-schema-unique-roleprivilege-names)
    - [Lazy fetching of roles and privileges](#lazy-fetching-of-roles-and-privileges)
    - [Entity equals/hashCode now identity-based](#entity-equalshashcode-now-identity-based)
    - [Events carry ids/DTOs instead of entities](#events-carry-idsdtos-instead-of-entities)
    - [Validation exception handler is now library-scoped](#validation-exception-handler-is-now-library-scoped)
    - [Package consolidation](#package-consolidation)
    - [Message bundle no longer overridden; library beans renamed](#message-bundle-no-longer-overridden-library-beans-renamed)
    - [Auto-configuration entry point and toggleable cross-cutting features](#auto-configuration-entry-point-and-toggleable-cross-cutting-features)
  - [Migrating to 4.0.x (Spring Boot 4.0)](#migrating-to-40x-spring-boot-40)
    - [Prerequisites](#prerequisites)
    - [Step 1: Update Java Version](#step-1-update-java-version)
    - [Step 2: Update Dependencies](#step-2-update-dependencies)
    - [Step 3: Spring Security 7 Changes](#step-3-spring-security-7-changes)
      - [URL Pattern Requirements](#url-pattern-requirements)
      - [Security Configuration Changes](#security-configuration-changes)
    - [Step 4: Update Test Infrastructure](#step-4-update-test-infrastructure)
      - [Test Dependency Changes](#test-dependency-changes)
      - [Test Annotation Import Changes](#test-annotation-import-changes)
    - [Step 5: Jackson 3 Changes](#step-5-jackson-3-changes)
    - [Step 6: API Changes](#step-6-api-changes)
      - [Profile Update Endpoint](#profile-update-endpoint)
    - [Step 7: Configuration Changes](#step-7-configuration-changes)
  - [For Developers Extending the Framework](#for-developers-extending-the-framework)
    - [Extending Security Configuration](#extending-security-configuration)
    - [Custom User Services](#custom-user-services)
    - [Custom Controllers](#custom-controllers)
    - [Event Listeners](#event-listeners)
  - [Troubleshooting](#troubleshooting)
    - [Common Issues](#common-issues)
  - [Version Compatibility Matrix](#version-compatibility-matrix)

## Migrating to 5.0.x

This section covers migrating from Spring User Framework 4.4.x to 5.0.x. Version 5.0.0 is a **major release** containing breaking changes. Spring Boot compatibility is unchanged from 4.4.x (Spring Boot 4.0 on Java 21+, and Spring Boot 3.5 on Java 17+); the major-version bump reflects this library's own API/contract changes, not a Spring Boot major change.

> **Note on versioning:** This library follows Semantic Versioning for its own API. Its major version is deliberately **not** tied to Spring Boot's major version — a single release line supports more than one Spring Boot major. See the Version Compatibility Matrix for supported Spring Boot versions.

### ⚠️ ACTION REQUIRED: Reverse-proxy deployments must configure a canonical app URL

Password-reset and email-verification links are now built from a configured canonical base URL instead of the inbound request's `Host` / `X-Forwarded-Host` header (CWE-640, host-header / reset poisoning). By default `X-Forwarded-Host` is **ignored** unless the host is explicitly allow-listed.

**If your application runs behind a reverse proxy or load balancer, you must take action or your reset/verification links will break:**

- **Recommended:** set the canonical base URL:
  ```properties
  user.security.appUrl=https://app.example.com
  ```
  When set, `X-Forwarded-Host` is ignored entirely and all email links use this URL.
- **Alternative:** allow-list the trusted forwarded host(s):
  ```properties
  user.security.trustedHosts=app.example.com,www.example.com
  ```
  `X-Forwarded-Host` is then honored only for hosts in this list; all others fall back to the container's own server name.

Local development with no proxy needs no change. `UserUtils.getAppUrl(HttpServletRequest)` is deprecated in favor of `AppUrlResolver`.

### Database schema: unique token constraint

The `token` column on both `password_reset_token` and `verification_token` now carries a **UNIQUE + NOT NULL** constraint. The stored value is always a fixed-length hash (introduced in 4.4.0), so the column length is predictable and the index is safe.

**Applications using `spring.jpa.hibernate.ddl-auto=update` (or `create`/`create-drop`):** Hibernate will add the unique index automatically on startup — no manual action required.

**Applications managing schema manually (Flyway, Liquibase, or `ddl-auto=validate`/`none`):** apply the following DDL before upgrading and before starting the application:

```sql
-- Ensure no existing null or duplicate token values exist first.
-- (4.4.0 already enforces one active token per user, so duplicates are unlikely.)
ALTER TABLE password_reset_token ALTER COLUMN token SET NOT NULL;
ALTER TABLE verification_token ALTER COLUMN token SET NOT NULL;
CREATE UNIQUE INDEX ux_password_reset_token_token ON password_reset_token (token);
CREATE UNIQUE INDEX ux_verification_token_token ON verification_token (token);
```

> **Note:** The DDL syntax above is standard SQL (compatible with PostgreSQL and MariaDB/MySQL). For MySQL/MariaDB, `ALTER COLUMN token SET NOT NULL` may need to include the full column definition, e.g. `MODIFY COLUMN token VARCHAR(255) NOT NULL`.

If your database contains rows with a `null` token value (possible only if tokens were created before 4.4.0 without the hash path), delete or back-fill those rows before applying the NOT NULL constraint.

### Registration & resend responses are now uniform (anti-enumeration)

The `/user/registration`, `/user/registration/passwordless`, and `/user/resendRegistrationToken` endpoints now return the **same generic, success-shaped HTTP 200 response** regardless of whether the email is already registered or already verified. This prevents attackers from using these endpoints to enumerate which email addresses have accounts and which are verified (CWE-204).

**Old behavior:**

| Endpoint | Case | Old status | Old body message |
|---|---|---|---|
| `/user/registration` | New email | 200 | `Registration Successful!` |
| `/user/registration` | Email already exists | 409 Conflict | `An account already exists for the email address` (code 2) |
| `/user/registration/passwordless` | New email | 200 | `Registration Successful!` |
| `/user/registration/passwordless` | Email already exists | 409 Conflict | `An account already exists for the email address` (code 2) |
| `/user/resendRegistrationToken` | Unverified account | 200 | `Verification Email Resent Successfully!` |
| `/user/resendRegistrationToken` | Already-verified account | 409 Conflict | `Account is already verified.` (code 1) |
| `/user/resendRegistrationToken` | Unknown email | 500 Internal Server Error | `System Error!` (code 2) |

**New behavior:**

| Endpoint | All cases | New status | New body message |
|---|---|---|---|
| `/user/registration` | New email **or** already exists | 200 | `If your email address is eligible, you will receive a verification email shortly.` (success, code 0) |
| `/user/registration/passwordless` | New email **or** already exists | 200 | `Registration Successful!` (success, code 0) |
| `/user/resendRegistrationToken` | Unverified, already-verified, **or** unknown email | 200 | `If your account requires verification, a new verification email has been sent.` (success, code 0) |

Internally the framework still does the correct thing — a brand-new registration creates the account and sends verification, an existing email creates nothing, and resend sends an email only when the account exists and is unverified — and the true outcome is still recorded in the audit log. Only the externally observable response is now uniform.

**Action required:** Clients (web UIs, mobile apps, integrations) must no longer rely on the `409` status (existing/verified account) or the `500` status (unknown email on resend) to detect account existence or verification state. Branch only on the `success` flag for these three endpoints, and present the generic message to end users.

### Re-authentication required for credential changes

Operations that change *how an account authenticates* now require the user to prove knowledge of the current password **when the account has one**. This closes a gap where a session-only actor (e.g. an unattended or hijacked session) could silently alter the account's authentication methods without re-authenticating (CWE-620 / weak re-authentication).

Affected endpoints (all require `user.webauthn.enabled=true` except where noted):

| Endpoint | Method | What changed | How to send the current password |
|---|---|---|---|
| `/user/webauthn/password` | `DELETE` | Removing the password (converting to passkey-only) now requires the current password. | JSON body `{"currentPassword": "..."}` |
| `/user/webauthn/credentials/{id}` | `DELETE` | Deleting a passkey requires the current password **when the account has a password**. | JSON body `{"currentPassword": "..."}` |
| `/user/webauthn/credentials/{id}/label` | `PUT` | Renaming a passkey requires the current password **when the account has a password**. The existing body gains a `currentPassword` field. | JSON body `{"label": "...", "currentPassword": "..."}` |

**Behavior when the account has a password** (status codes refined in **5.0.1** — see note below):
- Missing/blank `currentPassword` → `HTTP 400 Bad Request`, message *"Current password is required to change authentication methods."* — nothing is mutated.
- Incorrect `currentPassword` → `HTTP 401 Unauthorized`, message *"Current password is incorrect."* — nothing is mutated.
- Account locked (too many failed attempts) → `HTTP 423 Locked`, message *"Account is locked due to too many failed attempts…"* — nothing is mutated.
- Correct `currentPassword` → the operation proceeds as before.

> **5.0.0 → 5.0.1 note:** In 5.0.0 all three failure cases returned `HTTP 400`. As of 5.0.1 they return distinct statuses (400 missing / 401 incorrect / 423 locked) so clients can tell them apart. If you wrote a client against 5.0.0 that treats any `4xx` as "re-auth failed", no change is needed; only update it if you branched specifically on `400`.

`/user/updatePassword` is unchanged: it already required and verified `oldPassword`.

**Action required:** Update any client that calls the three endpoints above so that it collects the user's current password and sends it in the request body. `DELETE /user/webauthn/credentials/{id}` and `DELETE /user/webauthn/password`, which previously had no request body, now accept (and for password-holding accounts require) a JSON body carrying `currentPassword`. Existing IDOR/ownership checks and last-credential lockout protection are unchanged.

**Passwordless (passkey-only) accounts — residual risk:** For accounts with no password set, there is no current credential to verify, and this library does not yet implement a WebAuthn step-up assertion (a feasible recent-authentication signal does not currently exist in the framework). As a result:
- Deleting or renaming a passkey on a passwordless account remains a session-only operation (last-credential lockout protection and ownership checks still apply).
- Setting an *initial* password via `POST /user/setPassword` on a passwordless account also cannot require a current password (there is none); this endpoint still rejects accounts that already have a password.

This is a deliberate, documented limitation rather than a half-measure: implementing a true WebAuthn step-up assertion would require significant new challenge/response infrastructure. Consuming applications that need stronger guarantees for passwordless accounts can front these endpoints with their own step-up (e.g. require a fresh passkey assertion) before allowing the call. This will be revisited if/when a step-up mechanism is added to the framework.

### Database schema: unique role/privilege names

The `name` column on both the `role` and `privilege` tables now carries a **UNIQUE + NOT NULL** constraint. Role and privilege names were always intended to be unique identifiers (the framework looks them up by name), so this enforces an existing invariant at the schema level. It also makes the startup role/privilege setup safe under concurrent multi-node startup: if two nodes start simultaneously and both try to create the same role/privilege, the unique constraint guarantees only one row is created, and the framework re-reads the winning row instead of failing (first-writer-wins).

**Applications using `spring.jpa.hibernate.ddl-auto=update` (or `create`/`create-drop`):** Hibernate will add the unique index automatically on startup — no manual action required.

**Applications managing schema manually (Flyway, Liquibase, or `ddl-auto=validate`/`none`):** **de-duplicate any existing duplicate role/privilege names first**, then apply the following DDL before upgrading and before starting the application:

```sql
-- 1. Find duplicates before applying the constraint (must return zero rows):
SELECT name, COUNT(*) FROM role GROUP BY name HAVING COUNT(*) > 1;
SELECT name, COUNT(*) FROM privilege GROUP BY name HAVING COUNT(*) > 1;
-- Resolve any duplicates manually (merge/repoint references, delete extras) before continuing.

-- 2. Apply NOT NULL + UNIQUE:
ALTER TABLE role ALTER COLUMN name SET NOT NULL;
ALTER TABLE privilege ALTER COLUMN name SET NOT NULL;
CREATE UNIQUE INDEX ux_role_name ON role (name);
CREATE UNIQUE INDEX ux_privilege_name ON privilege (name);
```

> **Note:** The table names above (`role`, `privilege`) and column name (`name`) are Hibernate's defaults for the `Role` and `Privilege` entities (no `@Table` override). If you have customized Hibernate's physical naming strategy, adjust the identifiers accordingly. The DDL syntax is standard SQL (PostgreSQL / MariaDB / MySQL); for MySQL/MariaDB, `ALTER COLUMN name SET NOT NULL` may need the full column definition, e.g. `MODIFY COLUMN name VARCHAR(255) NOT NULL`.

### Lazy fetching of roles and privileges

**What changed:** The `roles` collection on `User` was previously `FetchType.EAGER` and is now `FetchType.LAZY`. The authentication path (`DSUserDetailsService.loadUserByUsername`) loads the full `User` &rarr; `roles` &rarr; `privileges` graph via a new `@EntityGraph` repository finder, `UserRepository.findWithRolesByEmail(String email)`, which fetches everything in a **single round trip** (a bounded, typically single query — the exact statement count can vary by JPA provider/version).

> **Note (5.0.0 → 5.0.1):** `Role.privileges` was also switched to `LAZY` in 5.0.0, but that was reverted to `EAGER` in **5.0.1** because it made `role.getPrivileges()` throw outside a transaction for marginal benefit. If you are on 5.0.1 or later, only `User.roles` is lazy — `role.getPrivileges()` is safe everywhere. The guidance below is written for 5.0.1+; on 5.0.0 specifically, the privilege caveats also apply.

**Why:** The old eager fetch loaded every role (and, transitively, every privilege) on *every* `User` load — even for operations that never touch authorities (token lookups, lockout-counter updates, existence checks) — and caused an N+1 query pattern across user loads. Making `User.roles` lazy and loading it explicitly only where it is needed removes that overhead while keeping authentication behavior identical. `Role.privileges` stays eager because privileges are small, static reference data and there is no path that bulk-loads `Role`s.

**Impact / risk:** Because `User.roles` is now lazy, **any code that accesses `user.getRoles()` on a detached entity — i.e. outside an open Hibernate session/transaction — will throw `LazyInitializationException`.** Code that accesses it *within* an active transaction (the common case for service methods) is unaffected. Once the roles collection is loaded, `role.getPrivileges()` works regardless of transaction state (privileges are eager, as of 5.0.1). The framework's own authentication, OAuth2/OIDC, and GDPR-export paths have been updated to initialize the graph correctly.

**Remediation patterns for consumers** that traverse a user's roles on a `User` they obtained outside a transaction:

- **Load through the authentication path or the entity-graph finder.** Use `UserRepository.findWithRolesByEmail(email)` (it initializes roles and privileges in a single round trip) instead of the plain `findByEmail(email)` when you need authorities.
- **Access the collections inside a transaction.** Annotate the method that reads `user.getRoles()` with `@Transactional` so the persistence session is still open when the lazy collection is first touched.
- **Use a DTO projection.** Map the roles/privileges you need into a DTO while still inside the session, then pass the DTO around the detached boundary.
- **Initialize before detaching.** If you must hand a `User` to detached code, call `Hibernate.initialize(user.getRolesAsSet())` while the session is open (privileges come along eagerly once the roles are loaded).

The plain `UserRepository.findByEmail(String)` finder is retained unchanged for callers that do not need the authority graph (token lookups, existence checks, lockout counters); it intentionally leaves the user's `roles` collection uninitialized.

### Entity equals/hashCode now identity-based

**What changed:** The JPA entities `User`, `PasswordResetToken`, `VerificationToken`, `PasswordHistoryEntry`, `WebAuthnCredential`, and `WebAuthnUserEntity` previously used Lombok `@Data`, which generates `equals`/`hashCode` over **all** fields. They now use an identity-based implementation that includes only the primary key (`@EqualsAndHashCode(onlyExplicitlyIncluded = true)` with `@EqualsAndHashCode.Include` on the id), matching the pattern already used by `Role` and `Privilege`. Their `toString` also no longer renders collections, associations, passwords, or token/credential secrets.

Note: `WebAuthnCredential` and `WebAuthnUserEntity` use their **assigned natural String keys** (`credentialId` and `id` respectively, both Base64url-encoded values assigned before persistence) rather than a database-generated surrogate id. Equality is therefore defined by that String key.

**Why:** All-fields `equals`/`hashCode` on JPA entities is unsafe: it changes as mutable fields change (breaking `Set`/`Map` membership), can force lazy collections to load, and a generated `toString` can trigger `LazyInitializationException` or leak secrets (password hashes, raw/hashed token values, key material) into logs. Identity-based equality is the standard JPA recommendation.

**Impact / risk:**

- **Two entities are now equal only when they share a non-null id.** If you compared entities field-by-field (relying on `@Data`'s all-fields equality), that behavior is gone — equality is now purely by primary key.
- **Standard JPA caveat for transient (unsaved) entities:** two newly-constructed entities that have not yet been persisted both have `id == null` and are therefore **not** considered equal to each other (and an unsaved entity is not equal to its persisted counterpart until the id is assigned). Do not use transient, id-less entities as `Set`/`Map` keys and then expect lookups to match after persistence assigns the id. If you need value-equality for unsaved instances, compare the relevant fields explicitly rather than relying on `equals`.
- **`toString` output changed:** collections, associations, and secret fields are excluded. Any code (or log assertion) that depended on those values appearing in `toString()` must be updated.

No remediation is required for the common cases (comparing managed/persisted entities by identity, or using them in collections after they have ids). This change only affects code that depended on field-by-field entity equality or on the old `toString` format.

### Events carry ids/DTOs instead of entities

**What changed:** Three application events no longer carry a live JPA `User` entity. They now expose immutable scalar data (ids/emails) captured at publish time, while the entity is still attached to a persistence context:

| Event | Old accessor(s) | New accessor(s) |
|---|---|---|
| `OnRegistrationCompleteEvent` | `getUser()` (live `User`) | `getUserId()`, `getUserEmail()`, `isUserEnabled()` — plus the unchanged `getLocale()` / `getAppUrl()` |
| `UserPreDeleteEvent` | `getUser()` (live `User`); `getUserId()` | `getUserId()` (unchanged), `getUserEmail()` (new); `getUser()` removed |
| `ConsentChangedEvent` | `getUser()` (live `User`); `getUserId()` | `getUserId()` (unchanged), `getUserEmail()` (new); `getUser()` removed. The `ConsentRecord` (a plain DTO, not a JPA entity) and `ChangeType` are unchanged |

The constructors changed accordingly:

- `OnRegistrationCompleteEvent`: now built from `userId`, `userEmail`, `userEnabled`, `locale`, `appUrl` (the Lombok `@Builder` is retained; the `.user(...)` builder method is gone, replaced by `.userId(...)`, `.userEmail(...)`, `.userEnabled(...)`).
- `UserPreDeleteEvent(Object source, Long userId, String userEmail)` replaces `UserPreDeleteEvent(Object source, User user)`.
- `ConsentChangedEvent(Object source, Long userId, String userEmail, ConsentRecord record, ChangeType changeType)` replaces `ConsentChangedEvent(Object source, User user, ConsentRecord record, ChangeType changeType)`.

**Why:** These events are (or can be) consumed by `@Async` listeners that run on a different thread from the publisher. Handing a live JPA entity across that boundary is unsafe: the persistence session that loaded it is typically closed by the time the listener runs, so touching a lazy association (or even a basic field on a proxy) throws `LazyInitializationException`, and the entity may be detached or concurrently mutated. The framework's own `UserDeletedEvent`/`UserDisabledEvent` already followed the id-only pattern; this change brings the remaining events in line.

**Remediation for consumer listeners:**

- If your listener only needed the id or email, switch from `event.getUser().getId()` / `event.getUser().getEmail()` to `event.getUserId()` / `event.getUserEmail()`.
- If your listener needs the full `User`, **load it by id from `UserRepository` inside your listener's own transaction** (e.g. annotate the listener method `@Transactional`, or call a `@Transactional` service method). Do not retain or pass around the entity beyond that transaction.
- For `OnRegistrationCompleteEvent` specifically, the framework's built-in `RegistrationListener` now passes `event.getUserId()` to `UserEmailService.sendRegistrationVerificationEmail(Long userId, String appUrl)`, which reloads the `User` in its own transaction before creating the verification token and rendering the email. The verification-email content (recipient, token, confirmation URL, template) is unchanged.

### Validation exception handler is now library-scoped

**What changed:** `GlobalValidationExceptionHandler` (the `@ControllerAdvice` that formats validation errors into a structured JSON 400 body) is now **scoped to the library's own controllers** via `assignableTypes`:

```java
@ControllerAdvice(assignableTypes = {UserAPI.class, GdprAPI.class, MfaAPI.class, UserActionController.class, UserPageController.class})
```

Previously it carried a bare `@ControllerAdvice`, which made it apply **application-wide** — including the consuming application's own controllers.

**Why:** A bare `@ControllerAdvice` from a library is global and silently hijacks the consumer's validation handling: any `MethodArgumentNotValidException` (or, in some cases, `ConstraintViolationException`) thrown by *your* controllers was being caught and reformatted into the library's response shape, overriding whatever error contract your application intended. Scoping the advice to the library's own controllers keeps the library out of your controllers' exception handling.

`WebAuthnManagementAPI` is intentionally **not** in this list: it has its own dedicated advice (`WebAuthnManagementAPIAdvice`) that already handles validation. Adding it here would create two advices targeting the same controller with overlapping handlers.

**Also fixed (400 for `@PasswordMatches`):** The handler now collects **global (class-level) binding errors** in addition to field errors. The class-level `@PasswordMatches` constraint on `UserDto` produces a *global* error (not a field error); the previous handler blindly cast every error to `FieldError`, throwing a `ClassCastException` inside the `@ExceptionHandler` and returning an unhelpful HTTP 500. A mismatched password/confirmation on registration now returns a structured **HTTP 400**. A dedicated `@ExceptionHandler(ConstraintViolationException.class)` was also added (for constraints triggered outside method-argument binding, e.g. `@Validated` on method parameters), also returning a structured 400.

**Remediation:** If your application relied on this library formatting validation errors for **your own** controllers, that no longer happens. Provide your own `@ControllerAdvice` (or `@RestControllerAdvice`) to format `MethodArgumentNotValidException` / `ConstraintViolationException` for your controllers. The library's response shape (for reference) is `{ "success": false, "code": 400, "message": "Validation failed", "errors": { <field-or-object>: <message> } }`.

### Package consolidation

The duplicate `com.digitalsanctuary.spring.user.exception` package (singular) has been merged into the canonical `com.digitalsanctuary.spring.user.exceptions` package (plural). The only class that moved is `GlobalValidationExceptionHandler`.

**Impact:** Only affects code that imports `GlobalValidationExceptionHandler` by its fully-qualified name or via an explicit import statement.

**Remediation:** Update any import referencing the old package:

```java
// Before (5.0.x prior to this change)
import com.digitalsanctuary.spring.user.exception.GlobalValidationExceptionHandler;

// After
import com.digitalsanctuary.spring.user.exceptions.GlobalValidationExceptionHandler;
```

The empty, unused `com.digitalsanctuary.spring.user.api.data` package has also been removed. This package contained only a placeholder `Response.java` with no content and was not referenced anywhere. No remediation required.

### Message bundle no longer overridden; library beans renamed

Two related changes make the library a better citizen inside a consuming application:

**1. The library no longer overrides your `spring.messages.basename`.**

Previously the library shipped a hardcoded `spring.messages.basename=messages/messages,messages/dsspringusermessages` default property. Because this was a library default, it OVERRODE the consuming application's own `spring.messages.basename`, clobbering any custom message bundle configuration.

The library now registers its own bundle (`messages/dsspringusermessages`) **additively** via a Spring Boot `EnvironmentPostProcessor` (`MessageSourceEnvironmentPostProcessor`). It reads your existing `spring.messages.basename` (or Spring Boot's conventional default of `messages` if you have not set one) and appends the library bundle to the end of the list, de-duplicated. The library bundle is placed last so YOUR message keys win on collisions.

**Impact:** None for most consumers — your message bundle is now preserved automatically.

**Remediation:** If you had previously worked around the old behavior by manually merging the library basename into your own `spring.messages.basename` (e.g. setting `spring.messages.basename=messages,messages/dsspringusermessages` yourself), you can simplify back to just your own value (e.g. `spring.messages.basename=messages`); the library appends its bundle for you. Leaving the explicit merge in place is harmless — it is de-duplicated.

**2. Library bean names are now namespaced with a `ds` prefix.**

High-collision library beans now have explicit, namespaced bean names so they no longer conflict with a consumer bean of the same default name:

| Class | Old default bean name | New bean name |
|---|---|---|
| `UserService` | `userService` | `dsUserService` |
| `MailService` | `mailService` | `dsMailService` |
| `UserEmailService` | `userEmailService` | `dsUserEmailService` |
| `DSUserDetailsService` | `dSUserDetailsService` | `dsUserDetailsService` |
| `LoginAttemptService` | `loginAttemptService` | `dsLoginAttemptService` |
| `SessionInvalidationService` | `sessionInvalidationService` | `dsSessionInvalidationService` |
| `PasswordPolicyService` | `passwordPolicyService` | `dsPasswordPolicyService` |
| `AuthorityService` | `authorityService` | `dsAuthorityService` |
| `RolePrivilegeSetupService` | `rolePrivilegeSetupService` | `dsRolePrivilegeSetupService` |
| `MailContentBuilder` | `mailContentBuilder` | `dsMailContentBuilder` |
| `UserAPI` | `userAPI` | `dsUserAPI` |
| `GdprAPI` | `gdprAPI` | `dsGdprAPI` |
| `MfaAPI` | `mfaAPI` | `dsMfaAPI` |
| `WebAuthnManagementAPI` | `webAuthnManagementAPI` | `dsWebAuthnManagementAPI` |
| `UserActionController` | `userActionController` | `dsUserActionController` |
| `UserPageController` | `userPageController` | `dsUserPageController` |

**Impact:** Only affects code that references these beans **by name** rather than by type. By-type injection (the common case) is unaffected.

**Remediation:** Update any by-name reference — `@Qualifier("userService")`, `@Resource(name = "userService")`, `@DependsOn("userService")`, or `applicationContext.getBean("userService", ...)` — to the new `ds`-prefixed name (e.g. `@Qualifier("dsUserService")`). Injection by type (e.g. `@Autowired UserService userService;`) requires no change.

### Auto-configuration entry point and toggleable cross-cutting features

The library's entry point, `UserConfiguration`, is now a proper Spring Boot `@AutoConfiguration` instead of a plain `@Configuration`. It is still registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, so it continues to load automatically — but it now runs in the auto-configuration phase (after your application's own beans), which is the correct lifecycle for a library entry point.

**Impact:** None for normal usage. The library still discovers and registers all of its components, and all existing behavior is preserved.

**Remediation:** Only required in the uncommon case that you imported the entry point yourself. If you have `@Import(UserConfiguration.class)` anywhere, or you deliberately arranged for your application's own `@ComponentScan` to pick up `com.digitalsanctuary.spring.user`, remove that — the library configures itself via auto-configuration and doing it twice is unnecessary.

**New opt-out toggles for cross-cutting features.** The library enables four cross-cutting Spring features. Each is now gated behind its own property, all defaulting to `true`, so **no action is needed** to keep current behavior:

| Property (default `true`) | Enables |
|---|---|
| `user.async.enabled` | `@EnableAsync` |
| `user.retry.enabled` | `@EnableRetry` |
| `user.scheduling.enabled` | `@EnableScheduling` |
| `user.method-security.enabled` | `@EnableMethodSecurity` |

**Use case:** If your application already enables one of these globally (for example you have your own `@EnableScheduling` or a global `@EnableMethodSecurity` with custom settings), you can disable the library's copy to avoid double-activation conflicts:

```yaml
user:
  scheduling:
    enabled: false   # you run your own @EnableScheduling
  method-security:
    enabled: false   # you run your own @EnableMethodSecurity
```

Leave them unset (or `true`) to keep the library managing these for you, exactly as before.

> **Note on async/retry interaction:** These toggles are independent. If you disable `user.async.enabled=false` while retry remains enabled (the default), any `@Retryable` methods in the library will still be registered for retry — but if those methods were previously executing on an async thread pool, they will now run synchronously on the caller's thread. In practice the library's `@Retryable` methods are not themselves `@Async`, so the common case is unaffected; however, consumers that wrap library calls in their own async boundaries should verify the combined behavior when selectively disabling these features.

<!-- Additional 5.0.x migration notes are appended below as tasks land. -->

## Migrating to 4.0.x (Spring Boot 4.0)

This section covers migrating from Spring User Framework 3.x (Spring Boot 3.x) to 4.x (Spring Boot 4.0).

### Prerequisites

Before starting the migration:

1. Ensure your application is running on the latest 3.5.x version
2. Review your custom security configurations
3. Audit any code that extends framework classes
4. Back up your database (schema changes are minimal but recommended)

### Step 1: Update Java Version

**Spring Boot 4.0 requires Java 21 or higher.**

Update your build configuration:

**Gradle:**
```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

**Maven:**
```xml
<properties>
    <java.version>21</java.version>
</properties>
```

Ensure your CI/CD pipelines and deployment environments support Java 21.

### Step 2: Update Dependencies

Update the framework dependency version:

**Gradle:**
```groovy
implementation 'com.digitalsanctuary:ds-spring-user-framework:4.0.0'
```

**Maven:**
```xml
<dependency>
    <groupId>com.digitalsanctuary</groupId>
    <artifactId>ds-spring-user-framework</artifactId>
    <version>4.0.0</version>
</dependency>
```

Update Spring Boot:
```groovy
plugins {
    id 'org.springframework.boot' version '4.0.0'
}
```

#### Passay upgraded to 2.0.0

The framework's transitive Passay dependency was upgraded from 1.x to **2.0.0**, which **relocated
several packages** (e.g. `org.passay.CharacterData` → `org.passay.data.CharacterData`,
`org.passay.CharacterRule` → `org.passay.rule.CharacterRule`). This only affects you if your
application **uses Passay directly** (e.g. for custom password rules):

- If you declared your own `org.passay:passay` dependency at a 1.x version, **remove the explicit
  pin** (let it inherit 2.0.0 transitively) or bump it to `2.0.0`. Pinning an older version forces a
  conflicting downgrade that breaks the framework's `PasswordPolicyService` at runtime
  (`ClassNotFoundException: org.passay.data.CharacterData`).
- Update your own Passay imports to the new 2.0.0 package names.

Applications that do not use Passay directly need no changes.

### Step 3: Spring Security 7 Changes

Spring Boot 4.0 includes Spring Security 7, which has breaking changes from Spring Security 6.x.

#### URL Pattern Requirements

**All URL patterns must now start with `/`.**

This affects:
- `user.security.unprotectedURIs` configuration
- `user.security.protectedURIs` configuration
- Any custom security matchers in your code

**Before (3.x):**
```yaml
user:
  security:
    unprotectedURIs: /,/index.html,/css/**,/js/**,error,error.html
```

**After (4.x):**
```yaml
user:
  security:
    unprotectedURIs: /,/index.html,/css/**,/js/**,/error,/error.html
```

Note the `/error` and `/error.html` now have leading slashes.

#### Security Configuration Changes

If you have custom security configuration extending or working with the framework:

**Deprecated methods removed:**
- `authorizeRequests()` → use `authorizeHttpRequests()`
- `antMatchers()` → use `requestMatchers()`
- `mvcMatchers()` → use `requestMatchers()`

**Example migration:**

```java
// Before (3.x)
http.authorizeRequests()
    .antMatchers("/public/**").permitAll()
    .anyRequest().authenticated();

// After (4.x)
http.authorizeHttpRequests(authz -> authz
    .requestMatchers("/public/**").permitAll()
    .anyRequest().authenticated());
```

### Step 4: Update Test Infrastructure

Spring Boot 4.0 introduces modular test packages.

#### Test Dependency Changes

Add the new modular test starters:

**Gradle:**
```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.springframework.boot:spring-boot-data-jpa-test'
testImplementation 'org.springframework.boot:spring-boot-webmvc-test'
testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
testImplementation 'org.springframework.security:spring-security-test'
```

**Maven:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
```

#### Test Annotation Import Changes

Update imports for test annotations:

| Annotation | Old Package (3.x) | New Package (4.x) |
|------------|-------------------|-------------------|
| `@AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@DataJpaTest` | `org.springframework.boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureTestDatabase` | `org.springframework.boot.test.autoconfigure.jdbc` | `org.springframework.boot.jdbc.test.autoconfigure` |

**Example:**
```java
// Before (3.x)
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

// After (4.x)
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
```

### Step 5: Jackson 3 Changes

Spring Boot 4.0 uses Jackson 3.x for JSON processing.

**ObjectMapper instantiation:**
```java
// Before (Jackson 2.x)
ObjectMapper mapper = new ObjectMapper();

// After (Jackson 3.x)
ObjectMapper mapper = JsonMapper.builder().build();
```

**Package changes:**
- Some classes moved from `com.fasterxml.jackson` to new packages
- Check any custom serializers/deserializers

### Step 6: API Changes

#### Profile Update Endpoint

The `/user/updateUser` endpoint now accepts `UserProfileUpdateDto` instead of `UserDto`.

**Before (3.x):**
```json
POST /user/updateUser
{
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "password": "...",
    "matchingPassword": "..."
}
```

**After (4.x):**
```json
POST /user/updateUser
{
    "firstName": "John",
    "lastName": "Doe"
}
```

This change improves security by not requiring password fields for profile updates.

**Update your frontend code** if you're calling this endpoint directly.

### Step 7: Configuration Changes

Review your `application.yml` for any deprecated properties:

| Deprecated Property | Replacement |
|---------------------|-------------|
| (none currently) | - |

Most configuration properties remain unchanged between 3.x and 4.x.

## For Developers Extending the Framework

If you've extended framework classes or implemented custom functionality, review these sections carefully.

### Extending Security Configuration

If you have a custom `WebSecurityConfig` or extend the framework's security configuration:

1. **Ensure all URL patterns start with `/`**
2. **Update to lambda DSL style** (required in Spring Security 7)
3. **Review method security annotations** - `@PreAuthorize`, `@PostAuthorize` unchanged

#### SecurityFilterChain override model (4.x)

The library contributes its `SecurityFilterChain` through a dedicated auto-configuration with two important properties:

- **Ordered at low precedence.** The library's chain is registered with `@Order(Ordered.LOWEST_PRECEDENCE - 5)` — the same low precedence Spring Boot uses for its own default servlet security chain. This value is sourced from `SecurityFilterProperties.BASIC_AUTH_ORDER`; the constant was `SecurityProperties.BASIC_AUTH_ORDER` in Spring Boot 3.x and was relocated to `SecurityFilterProperties.BASIC_AUTH_ORDER` in Spring Boot 4.0 (still `Ordered.LOWEST_PRECEDENCE - 5`). The library's chain has **no `securityMatcher`**, so it is the catch-all: any consumer-supplied chain with a `securityMatcher` and a lower (higher-precedence) `@Order` is consulted first by Spring Security's `FilterChainProxy`, and unmatched requests fall through to the library's chain.
- **Backs off only on a same-named replacement.** The library's chain bean is named `securityFilterChain` and is annotated `@ConditionalOnMissingBean(name = "securityFilterChain")`. It backs off **only** when you define a `SecurityFilterChain` bean **named `securityFilterChain`** (an explicit full replacement). Defining additional, differently-named chains does **not** suppress it.

> **Behavior change vs. earlier 4.x pre-releases:** an earlier iteration used `@ConditionalOnMissingBean(SecurityFilterChain.class)` (type-based), which suppressed the entire library chain as soon as you defined *any* `SecurityFilterChain` — even a narrow one (e.g. a test-API or actuator chain). That silently left the library's URIs unprotected. The conditional is now **name-based** so the standard Spring Security multi-chain `@Order` layering pattern works as expected.

This gives you two ways to customize security:

**Option A — Add additional, narrower chains alongside the library's (recommended for most layering).**

Define your own `SecurityFilterChain` with a `securityMatcher` scoping it to a subset of requests and a higher-precedence (lower) `@Order`. Give it **any name other than `securityFilterChain`**. Both chains coexist: your chain handles its matched requests, and the library's catch-all chain keeps protecting everything else (login, registration, password reset, profile, etc.).

```java
@Configuration
@EnableWebSecurity
public class ApiSecurityConfig {

    @Bean
    @Order(1) // higher precedence than the library's catch-all chain
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")          // scopes this chain to /api/**
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().hasRole("ADMIN"))
            .csrf(csrf -> csrf.disable());
        return http.build();                     // bean name is "apiSecurityFilterChain" -> library chain stays active
    }
}
```

**Option B — Fully replace the library's chain (you own all the rules).**

Define your own `SecurityFilterChain` bean **named `securityFilterChain`**. The library's chain backs off entirely and does **not** apply any of its rules; you are now responsible for protecting *all* URIs, including the framework's endpoints.

```java
@Configuration
@EnableWebSecurity
public class CustomSecurityConfig {

    @Bean // bean name MUST be "securityFilterChain" to replace the library's chain
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // You must also permit/secure the framework's own URIs here,
                // since the library chain no longer applies:
                .requestMatchers("/user/registration", "/user/login", "/user/resetPassword").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/user/login.html").permitAll());
        return http.build();
    }
}
```

For most applications that only need to *add* a few rules, the simplest path is to rely on the library's chain and the `user.security.*` properties (`protectedURIs`, `unprotectedURIs`, `defaultAction`, etc.) rather than defining your own `SecurityFilterChain` at all.

### Custom User Services

If you extend `UserService` or implement custom user management:

1. **Method signatures unchanged** - Core service methods remain compatible
2. **Password encoding** - Still uses BCrypt, no changes required
3. **User entity** - No schema changes required

#### Password hashing moved outside the transaction (perf)

To avoid holding a pooled DB connection during the deliberately slow bcrypt hash, password
hashing now runs *outside* the database transaction. As a result `registerNewUserAccount`,
`changeUserPassword`, and `setInitialPassword` are annotated `Propagation.NOT_SUPPORTED` and
delegate the actual DB write to short, separate transactions of their own.

**Consumer-facing behavior change:** these three methods no longer participate in a caller's
transaction. If you previously called one of them from inside your own `@Transactional`, that
outer transaction is now suspended for the call and the registration / password change commits
independently — **an outer rollback will not roll back the registration or password change.**

Most consumers call these methods from controllers (which are not transactional) and are
unaffected. If you depend on enlisting these operations in a surrounding transaction, you will
need to restructure that flow.

#### `UserEmailService` constructor gained a `TokenHasher` parameter (breaking for subclasses)

Verification and password-reset tokens are now hashed at rest. `UserEmailService` therefore takes
an additional `TokenHasher` constructor parameter. **If you subclass `UserEmailService`**, your
subclass constructor must accept and pass through the new parameter:

```java
public CustomUserEmailService(
        MailService mailService,
        UserVerificationService userVerificationService,
        PasswordResetTokenRepository passwordTokenRepository,
        ApplicationEventPublisher eventPublisher,
        SessionInvalidationService sessionInvalidationService,
        TokenHasher tokenHasher) {                    // <-- new parameter
    super(mailService, userVerificationService, passwordTokenRepository,
            eventPublisher, sessionInvalidationService, tokenHasher);
}
```

`TokenHasher` is a framework `@Component`, so it is available for injection. Consumers that do not
subclass `UserEmailService` are unaffected. The hashing is backward compatible at runtime: tokens
issued before the upgrade (stored in plaintext) are still resolved via a dual-read lookup and remain
usable until they expire.

#### Sessions on password change: current session is now preserved (OWASP)

A self-service password change (and removing a password to go passwordless) invalidates the user's
**other** sessions but, by default, now **preserves and regenerates the current session** rather than
logging the user out of the device they just used. This follows OWASP guidance (regenerate the
current session id, invalidate the rest) and is a friendlier default.

To restore the previous "invalidate every session, including the current one" behavior, set:

```yaml
user:
  session:
    invalidation:
      keep-current-session-on-password-change: false
```

Token-based password **resets** (the forgot-password flow) are unaffected: there is no authenticated
current session to preserve, so all of the user's sessions are invalidated as before.

### Custom Controllers

If you have controllers that extend or work alongside framework controllers:

1. **DTOs** - Update any code using `UserDto` for profile updates to use `UserProfileUpdateDto`
2. **Validation** - Bean validation works the same way
3. **Response format** - `JSONResponse` unchanged

### Event Listeners

Event handling remains unchanged:

- `OnRegistrationCompleteEvent`
- `UserPreDeleteEvent`
- `AuditEvent`

All events fire as before with the same payload structures.

## Troubleshooting

### Common Issues

**Issue: `pattern must start with a /`**

This error occurs when URL patterns in security configuration don't start with `/`.

**Solution:** Review all entries in:
- `user.security.unprotectedURIs`
- `user.security.protectedURIs`
- Any custom `requestMatchers()` calls

Ensure every pattern starts with `/`.

---

**Issue: `ClassNotFoundException` for test annotations**

Spring Boot 4.0 moved test annotations to new packages.

**Solution:**
1. Add the modular test dependencies (see [Step 4](#step-4-update-test-infrastructure))
2. Update imports to new package locations

---

**Issue: `NoClassDefFoundError: com/fasterxml/jackson/...`**

Jackson 3 has different package structures.

**Solution:** Update ObjectMapper instantiation and check custom serializers.

---

**Issue: Profile update returns validation error for password**

The `/user/updateUser` endpoint now uses `UserProfileUpdateDto`.

**Solution:** Update your frontend to only send `firstName` and `lastName` fields.

---

**Issue: `user_credentials` table not created on MariaDB/MySQL (WebAuthn)**

With `ddl-auto: update` or `create`, Hibernate previously mapped the `attestationObject` and
`attestationClientDataJson` columns to `VARBINARY(65535)`. Two such columns exceed MariaDB's
InnoDB 65,535-byte row-size limit, causing silent table creation failure. Symptoms include 500
errors on `/user/auth-methods` or `/user/webauthn/credentials`.

**Solution (upgrading from a version prior to this fix):**

If the `user_credentials` table was never created, it will be created automatically on next
startup with `ddl-auto: update` once you upgrade to this version.

If the table exists with `VARBINARY` columns (created on a non-MariaDB database), run:

```sql
ALTER TABLE user_credentials
    MODIFY COLUMN public_key              LONGBLOB NOT NULL,
    MODIFY COLUMN attestation_object      LONGBLOB,
    MODIFY COLUMN attestation_client_data_json LONGBLOB;
```

With `ddl-auto: update`, Hibernate will handle this automatically on MariaDB/MySQL. On
PostgreSQL no schema change is needed — the columns map to `bytea` in both old and new versions.

---

**Issue: Java version incompatibility**

Spring Boot 4.0 requires Java 21.

**Solution:**
1. Update your JDK to 21+
2. Update build configuration
3. Update CI/CD pipelines
4. Update deployment environments

## Version Compatibility Matrix

| Framework Version | Spring Boot | Spring Security | Java | Status |
|-------------------|-------------|-----------------|------|--------|
| 4.0.x | 4.0.x | 7.x | 21+ | Current |
| 3.5.x | 3.5.x | 6.x | 17+ | Maintained |
| 3.4.x | 3.4.x | 6.x | 17+ | Security fixes only |
| < 3.4 | < 3.4 | < 6 | 17+ | End of life |

---

For additional help, see:
- [README](README.md) - Main documentation
- [Configuration Guide](CONFIG.md) - All configuration options
- [Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp) - Working example
- [GitHub Issues](https://github.com/devondragon/SpringUserFramework/issues) - Report problems
