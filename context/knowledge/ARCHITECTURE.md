# Architecture

## Directory Structure
```
src/main/java/com/digitalsanctuary/spring/user/
├── UserConfiguration.java                 # @AutoConfiguration entry point (component scan + toggle configs)
├── UserAutoConfigurationRegistrar.java    # Registers library base package via AutoConfigurationPackages
├── MessageSourceEnvironmentPostProcessor.java  # Additively merges library i18n bundle into spring.messages.basename
├── api/                    # REST JSON controllers (UserAPI, GdprAPI, MfaAPI, WebAuthnManagementAPI, + Advice)
├── audit/                  # File-based audit logging (AuditEventListener, FileAuditLogWriter/QueryService, rotation)
├── controller/              # Thymeleaf MVC page/action controllers (UserPageController, UserActionController)
├── dev/                    # Opt-in local-dev passwordless auto-login (profile-gated)
├── dto/                    # Request/response DTOs (UserDto, PasswordDto, GdprExportDTO, Mfa/WebAuthn DTOs, etc.)
├── event/                  # Application events (OnRegistrationCompleteEvent, UserPreDeleteEvent, UserDeletedEvent, ...)
├── exceptions/             # Custom exceptions + GlobalValidationExceptionHandler
├── gdpr/                   # GDPR export/deletion/consent services (GdprExportService, GdprDeletionService, ConsentAuditService)
├── jobs/                   # Scheduled jobs (ExpiredTokenCleanJob)
├── listener/               # Event listeners (RegistrationListener, AuthenticationEventListener, WebAuthnPreDeleteEventListener)
├── mail/                   # MailService, MailContentBuilder, async mail executor config
├── persistence/
│   ├── model/              # JPA entities: User, Role, Privilege, VerificationToken, PasswordResetToken,
│   │                       #   PasswordHistoryEntry, WebAuthnCredential, WebAuthnUserEntity
│   └── repository/         # Spring Data repositories for the above + WebAuthn bridge/query repos
├── profile/                 # Extension points: BaseUserProfile (MappedSuperclass), UserProfileService<T>
│   └── session/            #   BaseSessionProfile<T>, SessionScopedProfile, BaseAuthenticationListener<T>
├── registration/            # Pluggable registration gating (RegistrationGuard SPI, CompositeRegistrationGuard, Decision/Context)
├── roles/                   # RolePrivilegeSetupService (bootstraps roles/privileges from config on startup)
├── security/                # WebSecurityConfig + *AutoConfiguration, OAuth2/OIDC user services, WebAuthn, MFA, HTMX entry point
├── service/                 # Core business services: UserService, UserEmailService, PasswordPolicyService,
│                            #   DSUserDetailsService/DSUserDetails, LoginAttemptService, SessionInvalidationService, etc.
├── util/                    # AppUrlResolver, PasswordHashTimeTester, JpaAuditingConfig, GenericResponse/JSONResponse
├── validation/               # PasswordMatches / PasswordMatchesValidator (bean validation)
└── web/                     # GlobalUserModelInterceptor, UserWebConfig, interceptor config, ExcludeUserFromModel

src/main/resources/
├── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│     → UserConfiguration, AuditMailAutoConfiguration, UserSecurityBeansAutoConfiguration, WebSecurityFilterChainAutoConfiguration
├── META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports → MessageSourceEnvironmentPostProcessor
├── config/dsspringuserconfig.properties     # All library defaults (security URIs, password policy, GDPR, MFA, WebAuthn, roles, audit)
├── messages/dsspringusermessages.properties # i18n bundle, merged additively (library keys last)
└── templates/mail/*.html                    # Registration/forgot-password email templates

src/test/java/com/digitalsanctuary/spring/user/
├── architecture/ArchitectureTest.java        # ArchUnit layering rules (see below)
├── test/
│   ├── app/TestApplication.java              # @SpringBootApplication importing UserConfiguration for integration tests
│   ├── annotations/                          # @DatabaseTest, @ServiceTest, @SecurityTest, @IntegrationTest, @OAuth2Test
│   ├── config/                               # SecurityTestConfiguration, DatabaseTestConfiguration, OAuth2TestConfiguration,
│   │                                          #   MockMailConfiguration, BaseTestConfiguration, StatementCountInspector(Test)
│   ├── builders/                             # UserTestDataBuilder, RoleTestDataBuilder, TokenTestDataBuilder
│   └── fixtures/TestFixtures.java
├── fixtures/                                 # OidcUserTestDataBuilder, OAuth2UserTestDataBuilder
├── config/TestConfig.java
└── api/, audit/, controller/, dev/, event/, exceptions/, gdpr/, listener/, mail/, persistence/, profile/,
    registration/, roles/, security/, service/, unit/, util/, validation/, json/  (mirrors main package layout)
```

## Organization Pattern
**Hybrid: layer-based at the top level, feature-cohesive within.** The package tree separates by architectural layer (`api`/`controller` = presentation, `service` = business logic, `persistence` = data), but several verticals (`gdpr`, `registration`, `security` MFA/WebAuthn, `audit`) are organized as self-contained feature modules that internally span DTOs, services, and config in one package. This is deliberate for a *library*: consumers pull in whole features (e.g., GDPR, MFA) that are auto-configured independently and gated by properties (`user.gdpr.enabled`, `user.mfa.enabled`, `user.webauthn.enabled`), rather than being wired manually layer-by-layer.

## Entry Points
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`: registers 4 auto-configuration classes consumers get automatically via Spring Boot's auto-config discovery (no `@ComponentScan` needed in the consuming app):
  - `com.digitalsanctuary.spring.user.UserConfiguration` — root config, component-scans `com.digitalsanctuary.spring.user`, conditionally enables `@EnableAsync`/`@EnableRetry`/`@EnableScheduling`/`@EnableMethodSecurity` (each gated by its own `user.*.enabled` property).
  - `com.digitalsanctuary.spring.user.audit.AuditMailAutoConfiguration`
  - `com.digitalsanctuary.spring.user.security.UserSecurityBeansAutoConfiguration`
  - `com.digitalsanctuary.spring.user.security.WebSecurityFilterChainAutoConfiguration` — exposes the `SecurityFilterChain` bean at low precedence, backing off if the consumer defines its own.
- `META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports` → `MessageSourceEnvironmentPostProcessor`, runs at `Ordered.LOWEST_PRECEDENCE` to append the library's i18n bundle (`messages/dsspringusermessages`) to `spring.messages.basename` without clobbering the consumer's own bundle.
- REST API (`src/main/java/.../api/`): `UserAPI` (`/user/*` — registration, password reset/update, profile update, passwordless registration, auth-methods), `GdprAPI` (`/user/gdpr/*` — export, delete, consent), `MfaAPI` (`/user/mfa/status`), `WebAuthnManagementAPI` (`/user/webauthn/*` — credential listing).
- MVC controllers (`src/main/java/.../controller/`): `UserPageController` (login/registration/forgot-password Thymeleaf pages, all URIs overridable via properties), `UserActionController` (change-password/registration-confirm GET actions).
- Security filter chain: `security/WebSecurityConfig.buildSecurityFilterChain(...)`, invoked by `WebSecurityFilterChainAutoConfiguration`'s `@Bean` method (kept off the `@Configuration` class itself so `@ConditionalOnMissingBean` semantics work correctly for auto-config ordering).

## Core Modules
### UserConfiguration / UserAutoConfigurationRegistrar
- **Location**: `src/main/java/com/digitalsanctuary/spring/user/UserConfiguration.java`, `UserAutoConfigurationRegistrar.java`
- **Responsibility**: Bootstraps the whole library into a consumer's Spring context — component scan, cross-cutting `@Enable*` toggles, and dynamic registration of the library's base package with `AutoConfigurationPackages` so JPA entity/repository scanning picks it up even though the consumer never explicitly scans `com.digitalsanctuary.spring.user`.
- **Dependencies**: none inbound; is the composition root for everything else.

### UserService
- **Location**: `src/main/java/com/digitalsanctuary/spring/user/service/UserService.java` (~1128 lines, largest class)
- **Responsibility**: Central business-logic service for registration (`registerNewUserAccount`), saving/enabling users, delete-or-disable, password-reset token lifecycle (`getPasswordResetToken`, `validateAndConsumePasswordResetToken`, `deletePasswordResetToken`), user lookup. Annotated `@Service("dsUserService")`, `@RequiredArgsConstructor`, class-level `@Transactional`.
- **Dependencies**: `UserRepository`, `VerificationTokenRepository`, `PasswordResetTokenRepository`, `PasswordEncoder`, `RoleRepository`, `SessionRegistry`, `DSUserDetailsService`, `ApplicationEventPublisher`, `PasswordHistoryRepository`, `SessionInvalidationService`, `TokenHasher`, `RegistrationGuard`. Registration is guarded exactly once inside `registerNewUserAccount` via `RegistrationGuard`, so no caller can bypass it.

### DSUserDetailsService / DSUserDetails
- **Location**: `service/DSUserDetailsService.java`, `service/DSUserDetails.java`
- **Responsibility**: `DSUserDetailsService implements UserDetailsService`, loads a `User` via `UserRepository.findWithRolesByEmail` (entity-graph fetch to avoid `LazyInitializationException`) and delegates to `LoginHelperService.userLoginHelper` to build the `DSUserDetails` principal. `DSUserDetails implements UserDetails, OidcUser`, wrapping the JPA `User` plus granted authorities and optional OIDC attributes/claims — used uniformly for form login, OAuth2, and OIDC principals.

### LoginAttemptService / SessionInvalidationService
- **Location**: `service/LoginAttemptService.java`, `service/SessionInvalidationService.java`
- **Responsibility**: `LoginAttemptService` implements brute-force lockout (`loginSucceeded`, `loginFailed`, `isLocked`, `checkIfUserShouldBeUnlocked`), driven by `user.security.failedLoginAttempts` / `user.security.accountLockoutDuration`, fed by `AuthenticationEventListener`. `SessionInvalidationService` uses Spring Security's `SessionRegistry` to force-expire a user's other sessions (e.g., on password change or account lock) via `invalidateUserSessions` / `invalidateSessionsAfterPasswordChange`, with optional session-fixation-safe regeneration of the current session.

### DSOAuth2UserService / DSOidcUserService
- **Location**: `service/DSOAuth2UserService.java`, `service/DSOidcUserService.java`
- **Responsibility**: `OAuth2UserService<OAuth2UserRequest, OAuth2User>` / `OAuth2UserService<OidcUserRequest, OidcUser>` implementations that map Google/Facebook (`getUserFromGoogleOAuth2User`, `getUserFromFacebookOAuth2User`) and Keycloak OIDC (`getUserFromKeycloakOidc2User`) provider profiles onto local `User` records, creating them on first login (`handleOAuthLoginSuccess` / `handleOidcLoginSuccess`).

### WebSecurityConfig / WebSecurityFilterChainAutoConfiguration
- **Location**: `security/WebSecurityConfig.java` (385 lines), `security/WebSecurityFilterChainAutoConfiguration.java`
- **Responsibility**: Fully declarative, property-driven `SecurityFilterChain` construction: form login, logout, remember-me, CSRF-exempt URIs, session registry + `maximumSessions(-1)` tracking, `allow`/`deny` default-action authorization model, optional OAuth2 login, optional WebAuthn (passkey) registration via `ObjectPostProcessor` injecting a custom success handler, optional MFA via `DelegatingMissingAuthorityAccessDeniedHandler` mapping missing-factor authorities to per-factor login entry points. The auto-configuration class hosts the actual `@Bean` (with `@ConditionalOnMissingBean`) so a consuming app's own `SecurityFilterChain` always wins.

### HtmxAwareAuthenticationEntryPoint
- **Location**: `security/HtmxAwareAuthenticationEntryPoint.java`, `HtmxAwareAuthenticationEntryPointConfiguration.java`
- **Responsibility**: Wraps a delegate `AuthenticationEntryPoint`; detects `HX-Request` header and returns a 401 + `HX-Redirect` header/JSON body instead of a 302 redirect, so HTMX partial-swap requests handle session expiry gracefully instead of swapping the full login page into a fragment.

### PasswordPolicyService / PasswordHashTimeTester
- **Location**: `service/PasswordPolicyService.java`, `util/PasswordHashTimeTester.java`
- **Responsibility**: `PasswordPolicyService.validate(User, password, usernameOrEmail, Locale)` enforces length, character-class, common-password (dictionary via `common_passwords.txt`), history-reuse (via `PasswordHistoryRepository`), and username/email-similarity rules, all individually toggleable via `user.security.password.*` properties. `PasswordHashTimeTester` benchmarks `PasswordEncoder.encode` 5x on `ApplicationStartedEvent` (async) and logs average time, guiding `user.security.bcryptStrength` tuning toward ~1000ms/hash.

### RolePrivilegeSetupService
- **Location**: `roles/RolePrivilegeSetupService.java`, `RolesAndPrivilegesConfig.java`
- **Responsibility**: `ApplicationListener<ContextRefreshedEvent>` that reads `user.roles.roles-and-privileges.*` and `user.roles.role-hierarchy[*]` properties and idempotently creates/updates `Role`/`Privilege` rows on startup (`getOrCreateRole`, `getOrCreatePrivilege`, `updateRolePrivileges`).

### GDPR module
- **Location**: `gdpr/` (`GdprExportService`, `GdprDeletionService`, `ConsentAuditService`, `GdprDataContributor`, `GdprConfig`), exposed via `api/GdprAPI.java`
- **Responsibility**: Opt-in (`user.gdpr.enabled=false` by default) data-subject-rights features: JSON data export (`/user/gdpr/export`), account deletion with optional pre-deletion export (`/user/gdpr/delete`), consent capture/query (`/user/gdpr/consent`). `GdprDataContributor` is itself an extension point — consumers implement it to include their own domain data in exports.

### Audit module
- **Location**: `audit/` (`AuditEventListener`, `FileAuditLogWriter`, `FileAuditLogQueryService`, `FileAuditLogFlushScheduler`, `AuditConfig`, `AuditMailAutoConfiguration`)
- **Responsibility**: Append-only file-based audit log (`AuditEvent`/`AuditEventDTO`) with configurable flush-on-write vs. scheduled flush, file rotation (`user.audit.maxFileSizeMb`/`maxFiles`), and a query service (`AuditLogQueryService`/`FileAuditLogQueryService`) used by GDPR export and admin tooling. Wired into email delivery via `AuditMailAutoConfiguration` for audit-triggered notifications.

## Data Flow

**Registration (form-based):**
`UserAPI.registerUserAccount` → validates `UserDto` → `PasswordPolicyService.validate` (no history check for new users) → `UserService.registerNewUserAccount` (enforces `RegistrationGuard`/`CompositeRegistrationGuard` exactly once inside the service, persists `User` via `UserRepository`) → `UserAPI` publishes `OnRegistrationCompleteEvent` → `RegistrationListener.onApplicationEvent` (async) skips already-enabled users (OAuth2/OIDC path) and, if `user.registration.sendVerificationEmail=true`, calls `UserEmailService.sendRegistrationVerificationEmail(userId, appUrl)` which reloads the `User` by id inside its own transaction (avoiding detached-entity hazards from the async thread) and generates a `VerificationToken` + sends mail via `MailService`/`MailContentBuilder` using the `registration-token.html` template. Anti-enumeration: `UserAlreadyExistException` and success both return an identical generic 200 response; the true outcome is recorded via `AuditEventListener`/audit log only.

**Login:**
Spring Security's form-login/OAuth2/OIDC filters authenticate against `DSUserDetailsService` (form) or `DSOAuth2UserService`/`DSOidcUserService` (social), producing a `DSUserDetails` principal. `AuthenticationEventListener` listens for `AuthenticationSuccessEvent`/`AbstractAuthenticationFailureEvent` (handling `DSUserDetails`, `OAuth2User`, and raw `String` principal shapes uniformly) and calls `LoginAttemptService.loginSucceeded`/`loginFailed`, which tracks `failedLoginAttempts`/`locked`/`lockedDate` on the `User` entity and enforces `user.security.failedLoginAttempts` / `accountLockoutDuration`. On success, `LoginSuccessService` handles redirect/session concerns and `BaseAuthenticationListener<T>` (extension point) listens for `InteractiveAuthenticationSuccessEvent` to load-or-create a profile via `UserProfileService<T>` and store it in the session-scoped `BaseSessionProfile<T>`.

**Password reset:**
`UserAPI.resetPassword` → `UserService` creates/looks up a `PasswordResetToken` (hashed via `TokenHasher`) → `UserEmailService.sendForgotPasswordVerificationEmail` sends the `forgot-password-token.html` template. On submission, `UserAPI.savePassword`/`setPassword` → `UserService.validateAndConsumePasswordResetToken` validates and single-use-consumes the token → `PasswordPolicyService.validate` (this time checking `PasswordHistoryRepository` since the user exists) → password is updated and old sessions are force-expired via `SessionInvalidationService.invalidateSessionsAfterPasswordChange` (optionally preserving the current session, per a `SessionInvalidationService` config flag — confirm exact property name when refining).

**Account deletion:**
`UserAPI`/`GdprAPI` → publishes `UserPreDeleteEvent` (carries only `userId`/`userEmail`, not a live JPA entity, to avoid cross-thread `LazyInitializationException`) → listeners such as `WebAuthnPreDeleteEventListener` clean up related data (WebAuthn credentials) → `UserService.deleteOrDisableUser` either hard-deletes (`user.actuallyDeleteAccount=true`) or disables the account → `UserDeletedEvent`/`UserDisabledEvent` published for consumer-side cleanup hooks. GDPR deletion (`GdprDeletionService`) optionally exports data first (`user.gdpr.exportBeforeDeletion=true`).

## Key Patterns
- **Auto-configuration + conditional back-off**: `@AutoConfiguration` classes registered via `AutoConfiguration.imports`; security filter chain and other beans use `@ConditionalOnMissingBean` so a consuming app's own beans always win (see comments in `WebSecurityConfig`/`WebSecurityFilterChainAutoConfiguration`).
- **Property-gated cross-cutting concerns**: every `@Enable*` (`Async`, `Retry`, `Scheduling`, `MethodSecurity`) and every optional feature (GDPR, MFA, WebAuthn, dev auto-login, rememberMe) is behind its own `user.*.enabled` property, defaulting to preserve existing behavior.
- **Event-driven decoupling**: registration, authentication, deletion, consent, and data-export flows all communicate via Spring `ApplicationEvent`s (`OnRegistrationCompleteEvent`, `UserPreDeleteEvent`, `UserDeletedEvent`, `UserDisabledEvent`, `ConsentChangedEvent`, `UserDataExportedEvent`) rather than direct service calls, letting consumers hook in via plain `@EventListener` beans without modifying library code.
- **Immutable/scalar event payloads over live entities**: `UserPreDeleteEvent` and the async registration-email path deliberately carry only `userId`/`userEmail` scalars (not the JPA `User`), because listeners run on separate threads/transactions where a detached entity would throw `LazyInitializationException`.
- **Anti-enumeration responses**: `UserAPI` registration/resend-verification endpoints always return an identical generic success response regardless of whether the email exists, is already verified, or is new — true outcome is recorded only via `AuditEventListener`.
- **Strategy/SPI pattern for registration gating**: `RegistrationGuard` interface + `CompositeRegistrationGuard` (collects all `RegistrationGuard` beans, evaluates them, returns `RegistrationDecision`) lets consumers add custom registration rules (e.g., invite-only) as ordinary `@Component` beans.
- **Generic extension classes for consumer subclassing**: `BaseUserProfile` (`@MappedSuperclass`), `UserProfileService<T extends BaseUserProfile>`, `BaseSessionProfile<T>` (`@Component @Scope(SCOPE_SESSION, proxyMode=TARGET_CLASS)`), `BaseAuthenticationListener<T>` (`ApplicationListener<InteractiveAuthenticationSuccessEvent>`) all use Java generics + Spring stereotype annotations so consumers extend them with their own concrete profile types.
- **ArchUnit-enforced layering**: `src/test/java/.../architecture/ArchitectureTest.java` asserts `persistence` never depends on `api`/`controller`/`service`, `service` never depends on `api`/`controller`, and bans `System.out`/`java.util.logging` usage anywhere in production code (SLF4J only).
- **Additive environment/message-bundle merging**: `MessageSourceEnvironmentPostProcessor` appends the library's message bundle to `spring.messages.basename` rather than overwriting it, and runs at `LOWEST_PRECEDENCE` so consumer-configured basenames are visible first.

## Extension Points
- **`BaseUserProfile`** (`profile/BaseUserProfile.java`): `@MappedSuperclass` with `id`/`user` (shared PK via `@MapsId`)/`lastAccessed`/`locale`. Consumers subclass with `@Entity @Table(...)` to add domain-specific profile fields (documented example: `CustomerProfile extends BaseUserProfile`).
- **`UserProfileService<T extends BaseUserProfile>`** (`profile/UserProfileService.java`): interface with `getOrCreateProfile(User)` / `updateProfile(T)`; consumers implement a `@Service` backed by their own profile repository.
- **`BaseSessionProfile<T>`** (`profile/session/BaseSessionProfile.java`): session-scoped holder for the current user's profile; consumers MUST re-declare `@Scope(SCOPE_SESSION, proxyMode=TARGET_CLASS)` (or use the `@SessionScopedProfile` meta-annotation) on their subclass, since Spring's `@Scope` is not inherited — the class docs explicitly warn this is a security footgun (singleton profile leak across users) if forgotten.
- **`BaseAuthenticationListener<T>`** (`profile/session/BaseAuthenticationListener.java`): abstract `ApplicationListener<InteractiveAuthenticationSuccessEvent>`; consumers subclass with a constructor wiring their concrete `BaseSessionProfile<T>` and `UserProfileService<T>`, and the base class automatically loads/creates the profile into the session on login.
- **`RegistrationGuard`** (`registration/RegistrationGuard.java`): interface for custom registration-approval logic (e.g., invite-only signups); any `@Component` implementing it is auto-collected by `CompositeRegistrationGuard` and evaluated during `UserService.registerNewUserAccount`.
- **`GdprDataContributor`** (`gdpr/GdprDataContributor.java`): interface for consumers to plug their own domain data into GDPR export/deletion flows.
- **Application events** (`event/` package): `OnRegistrationCompleteEvent`, `UserPreDeleteEvent`, `UserDeletedEvent`, `UserDisabledEvent`, `ConsentChangedEvent`, `UserDataExportedEvent` — consumers add plain `@EventListener` methods to react without touching library internals.
- **AuthenticationEntryPoint override pattern**: `HtmxAwareAuthenticationEntryPointConfiguration` supplies the default `AuthenticationEntryPoint` bean conditionally (`@ConditionalOnMissingBean`), so a consumer can define their own `AuthenticationEntryPoint` bean to fully replace the HTMX-aware behavior; `WebSecurityConfig` always wires whatever `AuthenticationEntryPoint` bean is present.
- **Property-driven URI/behavior overrides**: virtually every page/action URI (`user.security.loginPageURI`, `registrationURI`, etc.) and behavior toggle (`user.security.defaultAction`, `user.actuallyDeleteAccount`, `user.web.globalUserModelOptIn`, etc.) in `config/dsspringuserconfig.properties` is overridable by the consuming application's own `application.properties`.

## Dependency Graph
- `persistence.model` / `persistence.repository` — lowest layer; ArchUnit-enforced to have no dependency on `service`, `api`, or `controller`.
- `service` — depends on `persistence`, `mail`, `audit`, `roles`; ArchUnit-enforced to never depend on `api` or `controller`.
- `security` — depends on `service` (`DSUserDetailsService`, `DSOAuth2UserService`, `LoginSuccessService`, etc.) and `persistence` indirectly through services; provides the `SecurityFilterChain` consumed at the framework boundary.
- `api` / `controller` / `web` — top-level presentation layer; depend on `service`, `dto`, `util`, `exceptions`, but nothing depends on them (enforced by ArchUnit).
- `listener` / `event` — cross-cutting glue: `listener` classes depend on `event` + `service` (e.g., `RegistrationListener` → `UserEmailService`; `AuthenticationEventListener` → `LoginAttemptService`).
- `gdpr`, `registration`, `audit`, `roles` — feature-vertical modules that depend on `persistence`/`service`/`event` but are otherwise independently auto-configured and property-gated (`AuditMailAutoConfiguration`, `GdprConfig`, `RolesAndPrivilegesConfig`, `RegistrationGuardConfiguration`).
- `profile` / `profile.session` — extension-point package with minimal outward dependency (only on `persistence.model.User`); consumer code in the *host application* depends on these, not vice versa.
- `UserConfiguration` (root) — depends on nothing internally; is imported by `AutoConfiguration.imports` and transitively pulls in every other package via component scan.
