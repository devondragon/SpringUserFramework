## [4.2.0] - 2026-02-21
### Features
- WebAuthn / Passkey support (opt-in, disabled by default)
  - Passwordless authentication via platform authenticators (Touch ID, Face ID, Windows Hello) and roaming security keys (YubiKey)
  - Synced passkey support (iCloud Keychain, Google Password Manager, etc.)
  - Passkey management REST API under `/user/webauthn/*` (auth required):
    - GET `/user/webauthn/credentials` — List user's registered passkeys
    - GET `/user/webauthn/has-credentials` — Check if user has any passkeys
    - PUT `/user/webauthn/credentials/{id}/label` — Rename a passkey (max 64 chars)
    - DELETE `/user/webauthn/credentials/{id}` — Delete a passkey (with last-credential protection)
  - `WebAuthnAuthenticationToken` — Custom authentication token that distinguishes passkey sessions from password-based sessions, enabling downstream code to check how a user authenticated
  - Automatic cleanup of passkey data when a user account is deleted via `WebAuthnPreDeleteEventListener` (listens for `UserPreDeleteEvent`)
  - Configuration properties under `user.webauthn.*` (enabled, rpId, rpName, allowedOrigins)
  - Database schema additions: `user_entities` and `user_credentials` tables with `ON DELETE CASCADE` for referential integrity

### Fixes
- Safety and input handling
  - Safe-parse `AuthenticatorTransport` enum values to prevent `IllegalArgumentException` on unknown transport types
  - Default passkey label to "Passkey" when no label is provided instead of leaving it blank
  - Trim passkey labels before enforcing the 64-character length limit
  - Fixed TOCTOU race condition in last-credential protection by recounting credentials inside the `@Transactional` boundary
- Data integrity
  - Added `ON DELETE CASCADE` to WebAuthn foreign keys so credential data is cleaned up at the database level when a user is deleted
  - Added `WebAuthnPreDeleteEventListener` to clean up WebAuthn user entities and credentials via JPA before user deletion, complementing the cascade
- API quality
  - `transports` field in credential responses is now `List<String>` instead of a single comma-delimited string
  - Added `@NotBlank` validation on path variable parameters in management API endpoints
  - `WebAuthnManagementAPIAdvice` is now `@ConditionalOnProperty(name = "user.webauthn.enabled")` so it is not loaded when WebAuthn is disabled
- Design improvements
  - `WebAuthnException` now extends `RuntimeException` (was checked `Exception`), simplifying error handling throughout the WebAuthn stack
  - User handle generation uses `SecureRandom` bytes instead of deterministic user ID mapping, improving privacy
  - Credential operations use `@Transactional(propagation = MANDATORY)` to enforce that callers provide a transaction context

### Breaking Changes
- None. WebAuthn is a new opt-in feature disabled by default. Existing APIs and behavior are unchanged.

### Documentation
- Added WebAuthn/Passkey sections to README (setup, features, API endpoints) and CONFIG (settings, examples, important notes)
- Updated CHANGELOG with 4.2.0 entry

### Testing
- New test suites for all WebAuthn components:
  - `WebAuthnManagementAPITest` — REST endpoint behavior, validation, error handling
  - `WebAuthnCredentialManagementServiceTest` — Service-layer logic, TOCTOU protection, transports parsing
  - `WebAuthnAuthenticationSuccessHandlerTest` — Authentication token creation, user resolution
  - `WebAuthnUserEntityBridgeTest` — User entity bridge and handle generation
  - `WebAuthnPreDeleteEventListenerTest` — Cleanup on user deletion

### Other Changes
- Version bumped to 4.2.0-SNAPSHOT in gradle.properties
- Test output noise reduced with context-aware verbosity for expected WebAuthn exceptions

## [4.1.0] - 2026-02-02
### Features
- GDPR compliance (opt‑in, disabled by default)
  - New REST API under /user/gdpr/* (auth required; 404 when disabled):
    - GET /user/gdpr/export – Full JSON export of user data (account, audit history, consents, token metadata, and custom data via contributors)
    - POST /user/gdpr/delete – Orchestrated account deletion (hard delete)
    - POST /user/gdpr/consent – Record consent grant/withdrawal (built‑in types + custom)
    - GET /user/gdpr/consent/status – Current consent state
  - Services and types:
    - GdprExportService – Aggregates export data (Article 15), includes audit events and consent history
    - GdprDeletionService – Executes deletion workflow (Article 17) and publishes lifecycle events
    - ConsentAuditService – Tracks consent changes via audit logging
    - GdprDataContributor – Extension point for apps to contribute domain data to exports and to clean up during deletion
    - DTOs and enums: GdprExportDTO, ConsentRecord, ConsentType, ConsentExtraData, ConsentRequestDto (validated)
  - Audit log query infrastructure:
    - AuditLogQueryService + FileAuditLogQueryService – Streamed, filterable file‑based audit reader for GDPR export
    - Supports filtering by user, timestamp, and action; handles pipe‑delimited format, unescaped pipes, zone‑less timestamps
  - Application events for GDPR lifecycle:
    - UserDataExportedEvent, UserDeletedEvent (post‑transaction), ConsentChangedEvent; plus existing UserPreDeleteEvent usage
  - Configuration (defaults in dsspringuserconfig.properties):
    - user.gdpr.enabled=false, user.gdpr.exportBeforeDeletion=true, user.gdpr.consentTracking=true
    - user.audit.maxQueryResults=10000 (cap for file‑based audit queries)
  - Deletion flow hardens security and consistency:
    - Invalidates all sessions across all devices before deletion
    - Publishes pre/post deletion events, deletes framework artifacts (tokens, password history), logs out current session
  - ObjectMapper is injected (respects app Jackson config) for stable JSON handling across services

### Fixes
- Security and data integrity
  - Replaced manual string JSON handling with Jackson serialization/deserialization to remove injection risks
  - Removed PII (emails) from GDPR logs; log user IDs; sanitized custom consent type names in logs (avoid leaking identifiers)
  - Added strict input validation for custom consent types in ConsentRequestDto:
    - @Size(max = 100), @Pattern allowing only alphanumeric, underscore, hyphen; regex updated to require non‑empty value
  - Invalidate all user sessions (not just current) on GDPR deletion via SessionInvalidationService
  - Preserve user identity in GDPR deletion audit event; pass user object during audit logging
  - Use getSession(false) for GDPR audit logging to avoid creating sessions during logging
- Performance and stability
  - FileAuditLogQueryService now streams file lines (Files.lines) to avoid unbounded memory usage
  - Added configurable query cap user.audit.maxQueryResults (default 10000) and enforced it in stream processing
  - Defensive parsing for unescaped pipes in audit lines; tolerant timestamp parsing:
    - Fallback from ZonedDateTime to LocalDateTime (system default zone) for zone‑less dates
- Correctness
  - When a consent is re‑granted, withdrawnAt is cleared to reflect active consent
  - Javadoc and docs fixes (see Documentation)

### Breaking Changes
- None. GDPR features are disabled by default and are only exposed when explicitly enabled. Existing APIs and behavior remain unchanged.

### Refactoring
- Code quality improvements:
  - Centralized JSON handling via Spring‑configured ObjectMapper in GdprExportService and ConsentAuditService
  - Shared client IP resolution via UserUtils.getClientIP()

### Documentation
- Comprehensive GDPR documentation added to README and CONFIG:
  - Enabling features, configuration examples, endpoint usage, consent management, export/deletion flows, events
  - Extending exports via GdprDataContributor with transaction safety guidance (perform external cleanup after commit using UserDeletedEvent)
  - Rate limiting recommendations for export/delete endpoints
- README improvements:
  - “GDPR Compliance” added to Features
  - Updated dependency coordinates to 4.1.0 in Maven/Gradle snippets for Spring Boot 4.0
  - New anchors: Spring Boot 4.0 Key Changes; Admin Password Reset; table formatting consistency
- Javadoc fix:
  - Corrected ConsentType CUSTOM reference to ConsentRecord#customType

### Testing
- New unit test suites:
  - GdprAPI – Auth flows, GDPR toggle behavior, consent validation, export and deletion endpoints
  - FileAuditLogQueryService – Streaming, filtering, timestamp parsing, cap enforcement
  - ConsentChangedEvent, UserDeletedEvent – Event publication/fields
  - ConsentAuditService – Parsing and aggregation logic
  - GdprDeletionService – Deletion orchestration and edge cases
  - GdprExportService – Export composition, consent re‑grant behavior
- Test dependency update:
  - org.assertj:assertj-core bumped 3.27.6 → 3.27.7

### Other Changes
- Build and tooling
  - Gradle Wrapper updated: 9.1.0 → 9.3.0 → 9.3.1
  - Spring Boot Gradle plugin updated: 4.0.1 → 4.0.2
  - Version bumps:
    - gradle.properties: 4.0.3 → 4.0.4-SNAPSHOT, then 4.1.0-SNAPSHOT
- Repository housekeeping
  - Added context7.json verification file (service verification; no runtime impact)

## [4.0.3] - 2026-01-26
### Features
- Internationalization resilience and defaults
  - User-facing messages now provide sensible default text when a translation is missing. All MessageSource.getMessage(...) calls in UserAPI were updated to use the overload with a default message, e.g., "Password updated successfully", "Invalid old password", "Passwords do not match", etc., ensuring users never see an empty message due to missing bundle entries.
  - GlobalMessageControllerAdvice now falls back to the messageKey itself if no translation is found when resolving messages for views, preventing errors and guaranteeing some feedback is shown.

### Fixes
- Password update and profile update reliability (detached entity bug)
  - In UserAPI.updatePassword and UserAPI.updateUserAccount, the user is re-fetched from the database via userService.findUserByEmail(...) instead of relying on the DSUserDetails’ user instance, which could be detached from the JPA session. This prevents persistence issues when saving changes.
  - If the user cannot be re-fetched (e.g., missing from DB), the API now logs the issue and returns HTTP 400 with a localized "User not found" message.
- More robust token handling and consistent message keys
  - TokenValidationResult message keys are now generated using TokenValidationResult.getValue() rather than name()/toString(). This standardizes keys to camelCase (e.g., invalidToken, expired) and aligns all code paths.
  - Reset password flow now:
    - Builds error messages using the standardized auth.message.<value> form with default fallbacks (e.g., "Invalid or expired token", "Invalid token").
    - Returns clear, localized success and error messages with appropriate HTTP 400 statuses for validation failures (password mismatch, invalid token, invalid old password).
- Improved user-facing feedback across APIs
  - Success responses for profile updates, password updates, and password resets use localized messages with safe defaults to avoid exposing raw keys.
  - Error responses consistently return HTTP 400 for validation issues with localized messaging and defaults.

### Breaking Changes
- Message key values in redirects have changed to camelCase
  - Redirects that include messageKey query parameters (e.g., in UserActionController) now use camelCase values derived from TokenValidationResult.getValue(), such as:
    - auth.message.invalidToken (was sometimes auth.message.invalid_token or auth.message.INVALID_TOKEN)
    - auth.message.expired (was auth.message.EXPIRED)
  - If front-end code or integrations depend on the exact messageKey values, update them to the new camelCase forms.

### Refactoring
- Internal consistency updates around message resolution:
  - Centralized use of MessageSource.getMessage with default messages and consistent message key construction.

### Documentation
- No documentation changes in this set.

### Testing
- UserActionControllerTest updated to expect the new camelCase message keys in redirects and model attributes:
  - invalidToken instead of invalid_token/INVALID_TOKEN
  - expired instead of EXPIRED
- UserAPIUnitTest updates:
  - Mocks updated to use the 4-argument getMessage(...) signature with a default message parameter.
  - Tests now mock userService.findUserByEmail(...) to reflect the new re-fetch pattern in updatePassword and updateUserAccount.
  - Assertions aligned to the new success/error message paths.

### Other Changes
- Build/Version
  - Bumped project version to 4.0.3-SNAPSHOT in gradle.properties.

## [4.0.2] - 2026-01-25
### Features
- Admin-initiated password reset with optional session invalidation
  - Added SessionInvalidationService to expire all active sessions for a user using Spring Security’s SessionRegistry, with a documented race-condition caveat (sessions created during the sweep may survive).
  - Extended UserEmailService with new admin reset entry points:
    - initiateAdminPasswordReset(User user, String appUrl, boolean invalidateSessions)
    - initiateAdminPasswordReset(User user) – uses configured user.admin.appUrl, defaults to invalidating sessions.
  - End-to-end flow:
    - Generates a cryptographically secure, URL-safe Base64 token (256-bit entropy) for reset links.
    - Sends password reset email with validated app URL and reset link path (/user/changePassword?token=).
    - Optionally invalidates all user sessions.
    - Publishes an audit event with action adminInitiatedPasswordReset, correlation ID, admin identity, and session counts.
  - Security hardening baked into the feature:
    - @PreAuthorize now uses hasRole('ADMIN') (Spring auto-prefixes ROLE_) on admin APIs.
    - Admin identity is derived from the SecurityContext (prevents log injection and spoofing).
    - URL validation rejects dangerous schemes (javascript:, data:) for appUrl.
    - Operation order improved to avoid accidental lockout: token creation + email send happen before session invalidation.
  - Configuration support:
    - user.admin.appUrl for default reset link base URL.
    - user.session.invalidation.warn-threshold for performance warnings on principal scans.
  - Extensive tests added covering session invalidation behavior, email content/link generation, authorization metadata, URL validation, audit contents, and token format.

### Fixes
- Security and robustness improvements in the new admin reset capability
  - Reordered initiateAdminPasswordReset steps to prevent a user lockout if token creation/email sending fails.
  - Replaced UUID.randomUUID() tokens with SecureRandom-based 32-byte tokens encoded as URL-safe Base64 (no padding).
  - Switched audit extraData from CSV-like strings to JSON for safe, predictable parsing.
  - Replaced ad-hoc JSON-escaping with Jackson ObjectMapper to safely serialize audit metadata.
  - Truncated session IDs to first 8 characters in debug logs to reduce exposure risk.
- Authorization annotation correctness
  - Changed @PreAuthorize("hasRole('ROLE_ADMIN')") to @PreAuthorize("hasRole('ADMIN')") in all admin APIs to match Spring Security’s ROLE_ auto-prefixing.
- OIDC claim safety
  - DSUserDetails.getClaims() now guards against null OIDC user info, returning an empty map instead of throwing a NullPointerException.

### Breaking Changes
- Audit event extraData format changed from ad-hoc CSV to JSON
  - If any downstream systems parse AuditEvent.extraData, update them to expect JSON (e.g., {"adminIdentifier":"...","sessionsInvalidated":3,"correlationId":"..."}).
- Token and correlation ID formats
  - Password reset and verification tokens are now URL-safe Base64 (43 chars for 32 bytes) instead of UUIDs.
  - The correlationId used in audit extraData was also moved to URL-safe Base64 in later changes. Update any format assumptions in log-processing pipelines.
- API method deprecations (not removals)
  - Admin reset overloads taking an explicit adminIdentifier are now deprecated; admin identity is derived from the SecurityContext. No functional break, but planned for removal later.

### Refactoring
- UserEmailService
  - Extracted helper methods to align with SRP:
    - URL validation (isValidAppUrl), password-reset email sending (sendPasswordResetEmail), session invalidation handling (handleSessionInvalidation), and audit emission (publishAdminPasswordResetAuditEvent).
  - Centralized JSON serialization for audit metadata via ObjectMapper.
- SessionInvalidationService
  - Added performance logging and warn threshold configuration.
  - Documented inherent SessionRegistry race condition and included safer logging for session IDs.

### Documentation
- Admin password reset documentation
  - README: Added to Features list, detailed usage examples (with/without session invalidation, using configured appUrl), security notes (role requirements, URL validation, operation ordering), and configuration snippet.
  - CONFIG.md: New Admin Settings section documenting user.admin.appUrl and user.session.invalidation.warn-threshold.
- Version and compatibility updates
  - README install snippets updated to 4.0.2; added Java 25 to supported Java badge.
- Developer guidance
  - CLAUDE.md substantially rewritten with architecture overview, package map, entry points, core services, extension points, configuration namespaces, and common commands.
- JavaDoc quality pass
  - Added/standardized class-level JavaDoc across 40+ classes; added lombok.config and Javadoc task options to suppress Lombok-generated constructor warnings; restored consistent @RequiredArgsConstructor usage where appropriate.

### Testing
- New and expanded test coverage
  - SessionInvalidationServiceTest:
    - Validates mixed principal types (User and DSUserDetails), correct counting, performance warnings, and resilience on empty/null inputs.
  - UserEmailServiceTest:
    - Verifies admin-initiated flow (email sending, token creation, session invalidation counts).
    - Ensures URL validation rejects null/empty and javascript: schemes.
    - Confirms @PreAuthorize role expressions are correct (hasRole('ADMIN')).
    - Validates audit extraData JSON content and correlation ID format.
    - Updates for token format: assertions now check URL-safe Base64 43-character tokens.
- Test dependency updates
  - Testcontainers upgraded from 2.0.2 to 2.0.3 (core and MariaDB modules).

### Other Changes
- Dependency and build updates
  - Spring Boot bumped from 4.0.0 to 4.0.1.
  - com.vanniktech.maven.publish upgraded 0.35.0 → 0.36.0.
  - Project version set to 4.0.2-SNAPSHOT post-release.
- CI/CD and workflows
  - Updated Claude Code Review and PR Assistant GitHub Actions:
    - Permissions adjusted (read vs write), token input switched to claude_code_oauth_token, documentation links updated, allowed-tools refined.
- Repository hygiene
  - Removed obsolete planning docs and boilerplate (DEMO_APP_CHANGES_REQUIRED.md, IMPLEMENTATION_PLAN_PASSWORD_FIXES.md, HELP.md).

Notes for integrators:
- If you parse audit logs or rely on token/correlation ID formats, update your parsers to expect JSON extraData and URL-safe Base64 tokens/IDs.
- Ensure the admin role mapping aligns with Spring’s ROLE_ prefixing; the correct expression is hasRole('ADMIN').
- Configure user.admin.appUrl if using the convenience admin reset method without passing an explicit app URL.

## [4.0.1] - 2025-12-15
### Features
- No user-facing features in this set of commits.

### Fixes
- No bug fixes in this set of commits.

### Breaking Changes
- No runtime breaking changes for consumers of the library/app.
- Potential test-only breaking changes due to major version upgrades:
  - Testcontainers upgraded to 2.0.2 and the MariaDB module artifact was renamed from org.testcontainers:mariadb to org.testcontainers:testcontainers-mariadb. If you maintain custom build scripts or rely on specific Testcontainers modules in other projects, update coordinates accordingly. Review upstream migration notes for any API/behavior changes that might affect test code.
  - Rest Assured upgraded to 6.0.0. Tests using Rest Assured may require minor adjustments depending on your usage; consult Rest Assured 6 release notes for API changes.
  - Hibernate Validator upgraded to 9.1.0.Final (test scope). If your tests interact directly with HV internals, verify compatibility with Jakarta Validation 3.1.1 (still in use).

### Refactoring
- No refactoring changes.

### Documentation
- No documentation changes.

### Testing
- Updated major test dependencies to current versions:
  - org.hibernate.validator:hibernate-validator: 8.0.2.Final → 9.1.0.Final (testImplementation)
  - org.testcontainers:testcontainers: 1.21.3 → 2.0.2 (testImplementation)
  - org.testcontainers:mariadb → org.testcontainers:testcontainers-mariadb, version 2.0.2 (artifact rename and version bump; testImplementation)
  - io.rest-assured:rest-assured: 5.5.6 → 6.0.0 (testImplementation)
- Scope of changes is limited to test dependencies (build.gradle only). No production code or runtime dependencies were modified.
- Rationale/impact:
  - Brings test stack up-to-date for improved stability and features in containerized integration tests and HTTP testing.
  - The MariaDB container module’s coordinate change ensures compatibility with Testcontainers 2’s artifact layout.

### Other Changes
- Version bumped to 4.0.1-SNAPSHOT (gradle.properties) to begin the next development cycle.
- Merged PR #238 “Update major test dependencies” into main.

## [4.0.0] - 2025-12-14
### Features
- Spring Boot 4.0 and Spring Security 7 enablement
  - Updated security configuration to align with Spring Security 7:
    - Removed deprecated DefaultWebSecurityExpressionHandler and SecurityExpressionHandler<FilterInvocation> bean.
    - MethodSecurityExpressionHandler is now a static bean with RoleHierarchy parameter injection (recommended Spring Security 7 pattern), ensuring method-level security honors role hierarchy.
- Profile update endpoint simplified
  - Added UserProfileUpdateDto with validation:
    - Fields: firstName and lastName only, both @NotBlank and @Size(max = 50).
  - Updated POST /user/updateUser to accept UserProfileUpdateDto (no longer requires email/password/matchingPassword), allowing users to update their names without password validation friction.

### Fixes
- Corrected test expectations for registration validation
  - Updated unit tests to expect 400 Bad Request (not 500) when required registration fields (email/password) are missing, aligning tests with validation behavior.

### Breaking Changes
- Minimum Java version is now 21
  - Gradle toolchain updated from Java 17 to Java 21 to meet Spring Boot 4 requirements. Consumers must build and run with JDK 21+.
- Spring Security 7 behavior changes
  - All security URL patterns must start with a leading slash (/) in configuration and custom security matchers (e.g., user.security.unprotectedURIs, requestMatchers()).
  - Deprecated methods removed in Security 7 (e.g., antMatchers(), authorizeRequests())—use authorizeHttpRequests() with requestMatchers().
- Security bean changes
  - Removed the webExpressionHandler bean (DefaultWebSecurityExpressionHandler). If downstream applications relied on this bean, they should migrate to the new pattern using RoleHierarchy with method security expressions.
- Test package relocations (affects consumers’ test code on Spring Boot 4)
  - Test annotations moved to new modular packages:
    - @AutoConfigureMockMvc → org.springframework.boot.webmvc.test.autoconfigure
    - @WebMvcTest → org.springframework.boot.webmvc.test.autoconfigure
    - @DataJpaTest / @AutoConfigureDataJpa → org.springframework.boot.data.jpa.test.autoconfigure
    - @AutoConfigureTestDatabase → org.springframework.boot.jdbc.test.autoconfigure
    - @EntityScan → org.springframework.boot.persistence.autoconfigure

### Refactoring
- Security configuration cleanup for Spring Security 7
  - Removed deprecated imports and beans, and updated MethodSecurityExpressionHandler bean declaration to static with RoleHierarchy injection, reducing bean wiring fragility and aligning with current best practices.

### Documentation
- Added a comprehensive Migration Guide (MIGRATION.md)
  - Covers Java 21 requirement, Spring Security 7 changes (URL patterns, API removals), test infrastructure modularization, Jackson 3 notes, API changes (profile update DTO), troubleshooting, and a compatibility matrix.
- README refresh for Spring Boot 4.0
  - New installation section for Boot 4.0 with Maven/Gradle snippets.
  - Version compatibility table (Spring Boot, framework version, Java, Spring Security).
  - Key changes section (Java 21, Security 7, Jackson 3, modular test infrastructure).
  - Required test dependencies listed for Boot 4.
  - Quick Start prerequisites updated, and links to Migration Guide added.

### Testing
- Test infrastructure updated for Spring Boot 4
  - Switched imports to new modular test annotation packages.
  - Added Spring Boot 4 modular test starters:
    - spring-boot-starter-data-jpa-test
    - spring-boot-webmvc-test
    - spring-boot-jdbc-test
- Expanded unit test coverage for profile updates
  - Updated UserAPIUnitTest to use UserProfileUpdateDto.
  - Added validation tests for blank/null fields and length constraints; verified acceptance at max valid length.
  - Added org.hibernate.validator:hibernate-validator to test scope to exercise bean validation.
  - Adjusted CSRF test expectations and commentary to reflect standalone MockMvc limitations (actual CSRF should be covered by integration tests).
- Test dependency bump
  - GreenMail test dependency updated to 2.1.8 for SMTP testing.

### Other Changes
- Dependency and build updates
  - Spring Boot upgraded to 4.0.0.
  - org.apache.commons:commons-text bumped from 1.14.0 to 1.15.0.
  - spring-retry pinned to 2.0.12 (compileOnly and test) for compatibility.
  - com.vanniktech.maven.publish plugin upgraded from 0.34.0 to 0.35.0.
  - Clarified that thymeleaf-extras-springsecurity6 is compatible with Spring Security 7; no springsecurity7 artifact exists yet.
  - Project version bumped to 4.0.0-SNAPSHOT.
- CI/Automation
  - GitHub Action for Claude Code Review now uses ANTHROPIC_API_KEY instead of CLAUDE_CODE_OAUTH_TOKEN and has proper write permissions to comment on PRs and issues.

## [3.5.1] - 2025-10-26
### Features
- New password reset endpoint: /user/savePassword
  - Implements the missing step in the password reset flow: validates a reset token and saves a new password.
  - Accepts SavePasswordDto { token, newPassword, confirmPassword } with validation.
  - Enforces full password policy (length, complexity, common password prevention, history, similarity).
  - Returns localized messages and a login redirect path on success.

### Fixes
- Hardened password management and validation
  - Update password now enforced by policy:
    - The /updatePassword endpoint validates the new password against the configured policy (history, similarity, complexity) before saving, preventing weak or reused passwords.
  - Complete password reset flow:
    - Added /user/savePassword endpoint with full validation and token checks.
    - Optimized reset token invalidation:
      - Added PasswordResetTokenRepository.deleteByToken(@Modifying @Query) to delete via a single DELETE statement.
      - UserService.deletePasswordResetToken() now uses the direct delete method and logs the deletion, reducing DB round trips.
    - Clarified password matching:
      - Documented that using String.equals() to compare two user-provided inputs (new vs confirm) is safe; constant-time comparison is needed only when comparing against stored secrets (handled by PasswordEncoder).
  - Reduced risk of race conditions during password history cleanup:
    - Added @Transactional(isolation = SERIALIZABLE) to UserService.cleanUpPasswordHistory to ensure atomic trimming of history entries during concurrent updates.
  - JPA mapping and performance improvements:
    - Adjusted PasswordHistoryEntry/User relationships for LAZY fetching and cascade delete to reduce unnecessary loads and avoid orphaned entries.
  - Internationalized messages:
    - Added message.password.mismatch and message.reset-password.success keys for clearer user feedback.

### Breaking Changes
- None in API surface. However, behavior is intentionally stricter:
  - Password updates that previously succeeded with weak/reused passwords will now be rejected per policy.
  - Applications relying on lax validation may see new 400 responses from /updatePassword and /savePassword until client-side validation and UX are aligned.

### Refactoring
- Repository-level optimization for token deletion:
  - Introduced deleteByToken() JPQL delete in PasswordResetTokenRepository and migrated service logic to use it.
- Minor code comments and documentation improvements in UserAPI for clarity and audit logging.

### Documentation
- README
  - Updated dependency examples from 3.4.1 → 3.5.0 and then to 3.5.1 for both Maven and Gradle snippets.
- DEMO_APP_CHANGES_REQUIRED.md (new)
  - Detailed steps to fix demo app password reset form:
    - Change form action to /user/savePassword.
    - Add name="confirmPassword" to the confirm field to match SavePasswordDto.
  - Notes on reusing the existing registration password strength meter in reset/update flows; reduced estimated effort (1–2 hours).
  - Optional consistency update: rename currentPassword → oldPassword in update-password.js to match backend DTO (non-breaking).
  - Comprehensive testing checklist for reset/update/registration flows.
- IMPLEMENTATION_PLAN_PASSWORD_FIXES.md (new)
  - Full analysis and plan covering:
    - Adding validation to /updatePassword.
    - Implementing /savePassword with DTO, token checks, and policy enforcement.
    - Design decision: history checks apply to existing users; registration passes user=null by design.
    - Transaction isolation choice for history cleanup and concurrent-change considerations.

### Testing
- All existing tests pass (372 tests), confirming no regressions.
- The plan documents recommended additional unit and integration tests for reset/update flows; no new test files were added in the provided diffs.

### Other Changes
- CI/CD and Developer Experience
  - Added GitHub Actions workflows:
    - .github/workflows/claude-code-review.yml: Automated PR code reviews using anthropics/claude-code-action@v1 with limited gh tool permissions and review prompts.
    - .github/workflows/claude.yml: On-demand “@claude” assistant for issues and PRs with permissions to read CI results and repository metadata.
- Release and versioning
  - Bumped project version to 3.5.1-SNAPSHOT in gradle.properties to begin the next development iteration.

Notes for integrators
- Update any client UI that performs password reset:
  - Post to /user/savePassword with fields: token, newPassword, confirmPassword.
  - Expect localized error messages and strict validation failures for policy violations.
- Consider surfacing the same password strength and requirements UX used in registration on reset/update screens to align with backend enforcement.

## [3.5.0] - 2025-10-26
### Features
- Password policy enforcement integrated into registration and password updates
  - New PasswordPolicyService enforces configurable rules:
    - Length limits (min/max), required character classes (uppercase, lowercase, digit, special)
    - Allowed special characters are fully configurable via user.security.password.special-chars
    - Common password prevention using a Passay dictionary built from bundled common_passwords.txt
    - Username/email similarity check using Levenshtein distance with a configurable similarity threshold
    - Password history reuse prevention using stored password hashes and a configurable history-count
  - UserAPI now validates passwords on registration and returns HTTP 400 with aggregated error messages if policy fails
  - Key implementation details:
    - Passay-based validation with a MessageSource-backed PasswordValidator for localized error messages
    - Dictionary initialization uses WordListDictionary and ArrayWordList; optimized sorting via ArraysSort for faster startup
    - Similarity computed with Apache Commons Text LevenshteinDistance; threshold is percentage-based
    - Password history checks query the latest N hashes via PasswordHistoryRepository.findRecentPasswordHashes(Pageable)
    - History is recorded automatically on changeUserPassword and during registration where applicable

- Password history persistence and automatic cleanup
  - New JPA entity PasswordHistoryEntry stores per-user password hashes with timestamps
  - New repository PasswordHistoryRepository offers:
    - findRecentPasswordHashes(user, pageable) for efficient reuse checks
    - findByUserOrderByEntryDateDesc(user) for cleanup
  - UserService now:
    - Saves hashed passwords to history on password changes
    - Cleans up old entries after saves to limit stored history per configured history-count

- Database-level integrity and performance for password history
  - PasswordHistoryEntry now enforces:
    - passwordHash length capped at 255 and not null (safe for bcrypt, etc.)
    - entryDate not null and mapped as entry_date
  - Indexes added:
    - idx_user_id on user_id
    - idx_entry_date on entry_date
  - Improves performance for queries like findByUserOrderByEntryDateDesc and general cleanup operations

- Build and tooling enhancements
  - Gradle wrapper updated to 9.1.0
  - Apache Commons Text upgraded to 1.14.0

### Fixes
- Correct password history cleanup off-by-one error
  - Previously kept only historyCount entries; now keeps historyCount + 1 (current + N previous) to actually prevent reuse of the Nth previous password
  - Implementation: compute maxEntries = historyCount + 1 and delete entries beyond that index

- Robust password policy configuration parsing
  - Fixed escaping for special characters in dsspringuserconfig.properties
  - Removed surrounding quotes from user.security.password.special-chars so all characters (including backslash and quotes) parse correctly

- Faster and more predictable common password dictionary initialization
  - Updated Passay WordLists.createFromReader to use new ArraysSort() for sorting, improving startup performance and consistency

- Stabilized unit tests by properly stubbing PasswordPolicyService in UserAPI tests
  - Prevented NPE by returning Collections.emptyList() from validate()

### Breaking Changes
- New required configuration properties for password policy
  - PasswordPolicyService uses @Value without defaults for multiple properties (e.g., user.security.password.enabled, min-length, max-length, require-* flags, special-chars, prevent-common-passwords, history-count, similarity-threshold).
  - If you use UserAPI (now depends on PasswordPolicyService), you must supply these properties or import a property source that defines them. Otherwise, application startup may fail.
  - Behavior change: registration and password updates are now rejected (HTTP 400) when password policy is not met.

- Database schema changes
  - New table password_history_entry with not-null constraints and indexes
  - If you manage schema outside of Hibernate auto-DDL, you must add this table, columns, and indexes in your migrations before deploying.

Migration guidance:
- Add the new properties to your application configuration (example):
  - user.security.password.enabled=true
  - user.security.password.min-length=8
  - user.security.password.max-length=128
  - user.security.password.require-uppercase=true
  - user.security.password.require-lowercase=true
  - user.security.password.require-digit=true
  - user.security.password.require-special=true
  - user.security.password.special-chars=~`!@#$%^&*()_-+={}[]|\\:;"'<>,.?/
  - user.security.password.prevent-common-passwords=true
  - user.security.password.history-count=3
  - user.security.password.similarity-threshold=80

- Apply DB migration to create password_history_entry with:
  - Columns: id (PK), user_id (FK), password_hash VARCHAR(255) NOT NULL, entry_date TIMESTAMP NOT NULL
  - Indexes: idx_user_id(user_id), idx_entry_date(entry_date)

### Refactoring
- PasswordPolicyService structure and error handling improved
  - Extracted validation into cohesive private methods:
    - buildPassayRules(), createSpecialCharacterRule()
    - checkPasswordHistory(), checkPasswordSimilarity()
    - validateWithPassay()
  - Early returns for failed history/similarity checks to avoid unnecessary work
  - More detailed debug logging and clearer severe-error logging for dictionary load failures
  - Optional used for null-safe error signaling from pre-checks

### Documentation
- Enhanced Spring configuration metadata
  - additional-spring-configuration-metadata.json updated with meaningful descriptions for the new password policy properties, improving IDE assistance and documentation

### Testing
- New and updated test coverage
  - PasswordPolicyServiceIntegrationTest ensures:
    - Properties load correctly from config file
    - Allowed special characters are correctly parsed and enforced
    - Validation behaves as expected in a real Spring context
  - PasswordPolicyServiceTest fixes
    - Corrected similarity test to pass username/email to validate(), ensuring the check is truly exercised
  - UserAPIUnitTest updates
    - Added a mock PasswordPolicyService and default stubbing to prevent NPEs
  - Broader test additions from the password policy feature:
    - New tests for PasswordPolicyService and expanded UserService tests to cover history and policy integration

### Other Changes
- Versioning and editor configuration
  - Project version bumped to 3.5.0-SNAPSHOT
  - Added VS Code Java settings to .vscode/settings.json

- Dependency updates
  - Runtime:
    - com.google.guava:guava 33.4.8-jre → 33.5.0-jre
    - org.apache.commons:commons-text 1.13.1 → 1.14.0
    - org.springframework.boot 3.5.5 → 3.5.6
    - org.projectlombok:lombok 1.18.38 → 1.18.42
  - Test:
    - com.h2database:h2 2.3.232 → 2.4.240
    - com.icegreen:greenmail 2.1.5 → 2.1.7
    - org.assertj:assertj-core 3.27.4 → 3.27.6
  - Build:
    - com.github.ben-manes.versions plugin 0.52.0 → 0.53.0
    - Removed unused dependencyUpdates config and related helper from build.gradle (cleanup)

What this means for you:
- If you consume this library’s UserAPI endpoints, define the new password policy properties and run DB migrations for password history before upgrading.
- Expect stricter password handling during registration and updates, with clear, localized error messages reported to clients when policy checks fail.
- Performance during startup when loading the common password dictionary should improve due to the new ArraysSort usage.

## [3.4.1] - 2025-09-04
### Features
- No new feature functionality introduced in these commits.

### Fixes
- Hardened audit logging to prevent NullPointerException on first-time OAuth registration when the user ID is null (Fixes #210).
  - FileAuditLogWriter:
    - Null-safe extraction of user fields. New subject resolution order for the 4th pipe-delimited field: user ID (if present) → email (if ID is null) → "unknown".
    - Catches and logs IOException and any other Exception to ensure audit failures never impact application flow.
    - Uses log.error for audit system failures.
  - AuditEventListener:
    - Wraps event handling in try/catch to suppress and log unexpected errors from configuration checks or writer failures.
  - Impact:
    - Prevents application crashes during first-time OAuth registration or other scenarios where user.getId() is null.
    - Compatibility note for log consumers: the user identifier field may now contain an email address or the literal "unknown" when no numeric ID is available. If your log processing expects a numeric ID, update parsers accordingly.

### Breaking Changes
- None.

### Refactoring
- None.

### Documentation
- Updated README dependency coordinates to version 3.4.1 for both Maven and Gradle.

### Testing
- Substantial test coverage added around audit logging for null-safety and robustness:
  - AuditEventListenerTest:
    - Ensures events with a user that has a null ID are handled and logged.
    - Verifies exceptions thrown by the writer are suppressed and do not propagate.
    - Verifies exceptions during audit configuration checks are suppressed and the writer is not called.
  - New FileAuditLogWriterTest (403 lines):
    - Null-safety cases: null user, null ID, null ID + null email, normal user with ID.
    - Error handling: IOException on write, uninitialized writer, unexpected exceptions during event access.
    - Setup and configuration: disabled logging, null config object, empty log file path.
    - Writer lifecycle: flush and cleanup behaviors.
- Overall increases resilience and confidence in the audit subsystem through 15+ targeted test cases.

### Other Changes
- Bumped project version to 3.4.1-SNAPSHOT in gradle.properties via Gradle Release Plugin (development iteration; no runtime behavior change).

## [3.4.0] - 2025-09-03
### Features
- Proxy-aware URL and IP detection
  - UserUtils.getAppUrl now builds correct external URLs when behind proxies/load balancers by honoring X-Forwarded-Proto, X-Forwarded-Host, and X-Forwarded-Port; the generated URL always includes a port for backward compatibility
  - UserUtils.getClientIP now checks multiple standard headers in priority order (X-Forwarded-For, X-Real-IP, CF-Connecting-IP, True-Client-IP) with clean fallbacks
- Remember‑me is now opt‑in and explicitly configurable
  - Disabled by default; enable only when you set both properties:
    - user.security.rememberMe.enabled=true
    - user.security.rememberMe.key=<your-static-secret-key>
- Role hierarchy applied to method security
  - Method security expressions now honor the configured hierarchy (e.g., ROLE_ADMIN > ROLE_USER) via a MethodSecurityExpressionHandler wired with the RoleHierarchy
- Stronger password validation for registration
  - New @PasswordMatches class‑level constraint and validator; registration now enforces password and matchingPassword equality, with clear validation errors
- Safer OAuth2/OIDC account creation
  - Email is validated and normalized (lowercased) for OAuth2 and OIDC providers; authentication fails early with a clear, user‑friendly message if the provider didn’t supply an email (e.g., missing scope/permission)
- Audit logging hardening and defaults
  - FileAuditLogWriter concurrency is now protected with synchronized methods
  - Default audit log location changed to ./logs with automatic creation; graceful fallback to system temp directory if not writable
  - Periodic flush scheduling only active when audit logging is enabled and flushOnWrite is false
- Password reset API refinement
  - New PasswordResetRequestDto introduced; endpoint continues to send reset emails but now cleanly models the request as { "email": "..." }

### Fixes
- Security and privacy hardening
  - Removed session IDs from debug logs to prevent sensitive data exposure
  - CustomOAuth2AuthenticationEntryPoint now returns generic user‑friendly messages and logs detailed errors internally
  - Enforced lowercase normalization for emails throughout registration and OAuth2/OIDC paths, preventing duplicate users by case variance
- Robust null/edge‑case handling
  - Fixed potential NPE in UserService.getUserByPasswordResetToken when token or token record is null
  - UserService.registerNewUserAccount now validates password matching before proceeding
- Correct URL generation in emails
  - Registration verification emails now use UserUtils.getAppUrl(request), fixing previously broken links that used only the context path
- JPA entity equality fixes
  - Role and Privilege equals/hashCode now based on id only; bidirectional relationships excluded to avoid recursion/stack overflows and to improve Set behavior in persistence contexts
- Build/packaging correctness for consumers
  - Fixed published artifact name to ds-spring-user-framework
  - Removed surprise transitive runtime dependencies from the library (devtools, database drivers) by moving them to test runtime scope
- Configuration correctness and resilience
  - Fixed CSRF property typo: user.security.disableCSRFdURIs → user.security.disableCSRFURIs
  - Hardened parsing of comma‑delimited URI properties to ignore empty/whitespace entries
- Logging and code quality
  - Replaced string concatenation in logs with parameterized logging throughout
  - Fixed JavaDoc syntax issues in JSONResponse

### Breaking Changes
- Password reset endpoint request body
  - /user/resetPassword now expects PasswordResetRequestDto instead of UserDto
  - Migration: change the request body to { "email": "user@example.com" }
- Configuration property rename
  - user.security.disableCSRFdURIs → user.security.disableCSRFURIs
  - Migration: update your application properties/yaml accordingly
- Remember‑me behavior
  - Previously could be active with an ephemeral key; now disabled by default and only enabled when both user.security.rememberMe.enabled=true and user.security.rememberMe.key are set
- MailService bean construction
  - MailService now uses constructor injection for both JavaMailSender and MailContentBuilder
  - Migration: if you construct MailService manually, pass both dependencies; Spring auto‑config will wire it automatically in typical setups
- OAuth2/OIDC email requirement
  - Authentication now fails if the provider does not return an email address; ensure the email scope/permission is granted

### Refactoring
- WebSecurityConfig
  - Simplified remember‑me configuration; created DaoAuthenticationProvider via constructor; used RoleHierarchyImpl.fromHierarchy; reduced boilerplate and improved readability
- UserUtils
  - Streamlined IP header checks and forward‑aware app URL construction; clarified JavaDoc
- General logging cleanup
  - Consistent use of parameterized logging; removed System.out and noisy concatenation

### Documentation
- Major README overhaul
  - Step‑by‑step Quick Start with prerequisites, dependencies (Thymeleaf, Mail, JPA, Security, Spring Retry), database examples (MariaDB/PostgreSQL/H2), email setup, and complete example configuration
  - Clear explanation of registration modes: auto‑enable vs email verification, with expected behavior and configuration
  - Guidance on customizing views and next steps
- Configuration metadata fixes
  - property names corrected (camelCase), types fixed (Boolean/Integer), missing properties added for better IDE assistance

### Testing
- Substantial test coverage added across critical paths
  - OAuth2/OIDC services
    - DSOAuth2UserServiceTest (≈15 tests): Google/Facebook flows, new vs existing users, provider conflicts, error handling
    - DSOidcUserServiceTest (≈14 tests): Keycloak flows, claims extraction, DSUserDetails integration, conflict scenarios
  - Security utilities and flows
    - UserUtilsTest (≈29 tests): IP extraction header priority and URL building
    - CustomOAuth2AuthenticationEntryPointTest (≈21 tests): exception handling, redirects, failure handler delegation
    - LoginHelperServiceTest (≈15 tests): last activity tracking, automatic unlock, authorities handling, edge cases
    - LogoutSuccessServiceTest (≈17 tests): audit event creation, IP extraction, URL resolution, exception scenarios
    - RolePrivilegeSetupServiceTest (≈15 tests): initialization, reuse of privileges, transactional handling, mixed existing/new entities
  - Mail
    - MailServiceTest (≈24 tests): simple/template sends, async behavior, retry/recovery, edge cases
  - Validation
    - PasswordMatchesValidatorTest: positive/negative cases for password confirmation
- Test infrastructure improvements
  - Added fixture builders for OAuth2/OIDC users (Google, Facebook, Keycloak) for realistic claims and attributes
  - Dependency updates for test stack (AssertJ, ArchUnit, Awaitility, Testcontainers, Rest‑Assured, GreenMail)

### Other Changes
- Dependency updates
  - Spring Boot 3.5.5
  - Test libraries: AssertJ 3.27.4, ArchUnit 1.4.1, Awaitility 4.3.0, Testcontainers 1.21.3, Rest‑Assured 5.5.6, GreenMail 2.1.5
  - MariaDB JDBC driver 3.5.5
- Build improvements
  - Published artifact renamed correctly to ds-spring-user-framework
  - Group/publishing coordinates aligned; dependency management standardized via Spring Boot BOM
  - Added Gradle Versions Plugin configuration to prefer stable releases
- Internal tools
  - Updated changelog generator to use a newer model (gpt‑5)
  
Migration checklist
- Update /user/resetPassword client requests to use PasswordResetRequestDto { "email": "..." }
- Rename user.security.disableCSRFdURIs to user.security.disableCSRFURIs
- If you rely on remember‑me, add:
  - user.security.rememberMe.enabled=true
  - user.security.rememberMe.key=<stable-secret>
- Ensure OAuth2/OIDC providers grant email scope; otherwise login will fail by design
- If constructing MailService manually, pass both JavaMailSender and MailContentBuilder
- Be aware that emails are now normalized to lowercase; verify database uniqueness constraints if case sensitivity was previously assumed

## [3.3.0] - 2025-07-22
# Changelog

## Features

### Comprehensive Test Infrastructure and Service Tests
- Established a comprehensive testing foundation including modular test configurations, custom annotations, test data builders, and a mock email service. This setup is crucial for facilitating both unit and integration tests within the library.
- Implemented test data builders for User, Role, and Token entities to streamline the creation of test data.
- Added testing support for OAuth2/OIDC with configured mock providers.
- Authored detailed test plans and phase-specific task lists targeting a test coverage increase from 27% to 80%+.

### Support for Parallel Test Execution
- Enabled JUnit 5's parallel execution capabilities to significantly improve test performance by leveraging multiple CPU cores.
- Expected test execution time reduced by 30-50%.

### End-to-End Testing Infrastructure
- Established a framework for end-to-end (E2E) testing which includes database setup using Testcontainers and email testing using GreenMail.
- Initiated examples of user registration and password reset journey tests which validate integration across system boundaries.

### Dependency and Version Updates
- Updated `org.mariadb.jdbc:mariadb-java-client` to version 3.5.4 to ensure compatibility with the latest database features and security patches.
- Updated the Gradle wrapper to version 8.14.3 for enhanced build performance and new functionalities.

## Fixes

### Improved Handling of Authentication Types
- Enhanced `AuthenticationEventListener` to correctly handle various authentication types, including OAuth2User and DSUserDetails, solving issues related to improper principal extraction and null user scenarios in OAuth2 authentication flows.
- Fixed deprecated API usage in `WebSecurityConfig` by replacing `RoleHierarchyImpl.fromHierarchy()` with the new constructor and `setHierarchy()` method.

### Hibernate Entity Management
- Addressed a critical issue related to Hibernate's immutable collection proxy by refactoring `User` entity's role storage from `List` to `Set`. This change prevents `UnsupportedOperationException` during entity saves in specific integration scenarios.

### Patch Vulnerabilities and Improve Compatibility
- Resolved the compilation error by aligning with the latest vanniktech maven publish plugin (0.34.0), ensuring smooth publishing to Maven Central.

## Refactoring

### Improved Readability and Consistency in Test Code
- Refactored test data builders for improved readability and consistent code style, enhancing maintainability and developer understanding.
- Refactored `UserServiceTest` to leverage centralized TestFixtures for cleaner setup and more organized test logic.

## Testing

### Extensive Test Coverage
- Added a comprehensive suite of unit and integration tests for DSUserDetailsService and AuthorityService, covering user role loading, OAuth2 flows, and role hierarchy management.
- Refined test classes to ensure appropriate usage of mocking and context initialization, leading to robust validation of expected behaviors across various scenarios.

### Test Documentation
- Authored detailed documentation outlining testing conventions, best practices, and guidelines for leveraging custom test annotations.
- Documented the new test infrastructure setup extensively to guide future test development and optimization.

## Other Changes

### File Renaming and Cleanup
- Streamlined test and source files by fixing naming discrepancies and correcting file paths where necessary for improved project organization.
- Deleted outdated and redundant documentation files such as `TESTPLAN.md`, `TESTNEXTTASKS.md`, and `FAILING_TESTS_ANALYSIS.md`, integrating relevant content into a unified test improvement document.

Overall, these changes enhance the library's testability, reliability, and developer experience, setting a robust foundation for future development and maintenance.

## [3.2.3] - 2025-06-23
### Features
- **[Gradle Release Plugin] New Version:** Updated `gradle.properties` to set the project version to `3.2.3-SNAPSHOT` from `3.2.2`. This change prepares the project for future development by marking the start of a new snapshot version.

### Other Changes
- **Dependency Updates:**
  - **Spring Boot Update to 3.5.3:** 
    - **Merged Changes and Details:**
      - Updated `springBootVersion` in `build.gradle` from `3.5.0` to `3.5.3`. This update includes improvements and bug fixes from Spring Boot's latest patch release.
      - Affected modules include `spring-boot-starter-web`, `spring-boot-configuration-processor`, and `spring-boot-starter-test`. Each of these was bumped from version `3.5.0` to `3.5.3`.
      - These updates ensure incorporation of resolved issues and performance improvements provided by the Spring Boot team. [Release notes and commits](https://github.com/spring-projects/spring-boot/releases). 
  - **Maven Publish Plugin Update to 0.33.0:**
    - **Merged Changes and Details:**
      - Updated `com.vanniktech.maven.publish` plugin in `build.gradle` from `0.32.0` to `0.33.0`. This minor version update may include new features, enhancements, and potentially minor API changes that improve the publication process.
      - This update ensures the usage of the latest features and improvements, detailed in [release notes and changelog](https://github.com/vanniktech/gradle-maven-publish-plugin/releases). 

- **Version Increment for Spring Boot to 3.5.0 from 3.4.5:**
  - Previous increment from `3.4.5` to `3.5.0` had merged code changes which similarly ensured improved stability and support by aligning with the latest minor release prior to patch updates.

These updates help make sure the project dependencies are current, which is important for taking advantage of recent bug fixes and performance improvements, as well as maintaining security standards within the project.

**Note:** All dependency updates above relate to enhancement and security, and do not introduce breaking changes as they are minor or patch updates to dependencies already in use.

## [3.2.2] - 2025-05-07
### Changelog

#### Features
- **Bump ds-spring-user-framework to 3.2.2**  
  Updated the ds-spring-user-framework version in the `README.md` to 3.2.2. This ensures that users who follow documentation instructions use the latest features and enhancements of the framework.

- **Dependency Updates: Guava**
  - Updated the Google Guava library from version `33.4.6-jre` to `33.4.8-jre` in `build.gradle`, reflecting two incremental updates. These semver-patch updates likely contain bug fixes or performance improvements without introducing new features or breaking changes.

#### Fixes
- **Conditional Logging Fix in FileAuditLogFlushScheduler**  
  Resolved an issue where the `FileAuditLogFlushScheduler` component continued to log events despite logEvents being disabled. The previous configuration used `@ConditionalOnProperty`, which was adjusted to `@ConditionalOnExpression` for better control. This change ensures that logging adheres to the intended configuration settings.

#### Breaking Changes
- None identified in this update cycle.

#### Refactoring
- **Improved Conditional Annotation Flexibility**  
  The `FileAuditLogFlushScheduler` was refactored to use `@ConditionalOnExpression` instead of `@ConditionalOnProperty`. This change provides more flexibility in how conditions for the component's activation are defined, allowing more dynamic configuration based on properties.

#### Documentation
- **Updated Version References in README**  
  Made explicit updates to version numbers in `README.md` to reflect the latest versions of the libraries, guiding users toward using the most up-to-date and secure versions.

#### Testing
- No specific changes related to testing documented.

#### Other Changes
- **Spring Boot Version Bump**  
  Updated multiple Spring Boot-related dependencies from `3.4.4` to `3.4.5` in `build.gradle` as a part of regular maintenance. These updates include `spring-boot-starter-web`, `spring-boot-configuration-processor`, and `spring-boot-starter-test` among others. This reflects minor version changes which may contain minor improvements or bug fixes without disrupting existing functionalities.

- **Version Management and Version Updates**  
  Made necessary changes to version management files like `gradle.properties` to transition from `3.2.1` to `3.2.2-SNAPSHOT`, indicating ongoing development work post the latest release. This snapshot designation is typically used for builds that are still under development.
  
This changelog captures nuanced improvements and ensures that developers and users of the project are aware of the recent enhancements and bugfixes, to maintain alignment with the latest codebase optimizations.

## [3.2.1] - 2025-04-13
# Changelog

## Features
- **User Account Deletion Improvements (80f7c474, 1c42d603):** Added comprehensive handling for user account deletions. A new event, `UserPreDeleteEvent`, is published before a user is deleted, allowing applications to clean up related data. This supports both logical disablement (setting `enabled=false`) and actual deletion from the database, controlled by the `user.actuallyDeleteAccount` configuration.

## Fixes
- **User Deletion Cascading Fix (80f7c474, 1c42d603):** Addressed the issue where deleting a user did not cascade to `BaseUserProfile` subclasses. The fix ensures that all related data can be cleaned upon user deletion, preventing orphaned data.
- **Google SSO Login Error (9a891b2a, 094a5341, b294d43d):** Resolved an error when logging in with Google SSO by updating dependencies and enhancing the logging within the authentication process for better tracking and debugging.
  
## Breaking Changes
- **Authentication Process Refactor (b294d43d):** The `DSUserDetails` class no longer implements the `OAuth2User` interface. Applications relying on `OAuth2User` from `DSUserDetails` need to update their logic accordingly.

## Refactoring
- **Improved Test Setup in `UserServiceTest` (7285da93):** Refactored imports and utilized a mock for `ApplicationEventPublisher`, improving the modularity and maintainability of tests.

## Documentation
- **Version Updates (634eb247):** Documentation in `README.md` updated to reflect latest version `3.2.1` for Maven and Gradle installations.
- **Complete Overhaul of Contribution Guides (6aaa0c72, 2a9951ec, e5d4aec0, fab0b80e):** Comprehensive updates to `CONTRIBUTING.md` and introduction of `CODE_OF_CONDUCT.md` emphasizing user management, contribution workflows, and code of conduct within the project.
- **Updated Contribution Instructions (c05ba9ff, f5421631):** Clear guidelines established for contributing to the project, including links between the main library and demo app.

## Other Changes
- **Dependency Updates:**
  - Upgraded `org.mariadb.jdbc` to version `3.5.3` for improved stability and security (f3470115, 1e771634).
  - Updated `com.google.guava` to `33.4.6-jre` (b3cf7985, fab0b80e).
  - Updated Lombok to `1.18.38` and upgraded Gradle wrapper to `8.13` (b294d43d).

The changelog is designed to provide meaningful insights into improvements, bug fixes, and potential impacts due to updates, ensuring users can easily understand the project's evolution and necessary updates on their end.

## [3.2.0] - 2025-03-23
## Changelog

### Features

- **Keycloak Authentication Support**
  - Added support for Keycloak as an SSO provider. Updated the framework to include Keycloak as a recognized authentication provider alongside existing ones like Google and Facebook. This involved modifying the database schema to support Keycloak as an authentication provider and adding configurations to enable Keycloak support in `WebSecurityConfig` and `UserPageController`. ([Commit: 3d90765f](#), [Commit: 96033640](#))

- **Spring Boot and Guava Versions Update**
  - Upgraded Spring Boot to version 3.4.4 and Guava to version 33.4.5-jre. This ensures compatibility with the latest features and improvements in these libraries. ([Commit: f29295b1](#), [Commit: b8bd58b5](#))

- **Enhanced Logging in Testing**
  - Included full exception formats and enabled the display of standard output streams during test execution for better debuggability and visibility of testing operations. ([Commit: 136457d6](#))

### Fixes

- **OAuth2 Dependency Issues**
  - Temporarily disabled several OAuth2-related tests due to unresolved dependency issues, preventing them from interfering with the build process. This included disabling entire test classes and specific test methods. ([Commit: 2908c614](#), [Commit: a5a25da4](#), [Commit: 711288c1](#))

- **Javadoc Improvements**
  - Corrected and enhanced Javadoc documentation for methods in `UserService` and `UserActionController`. Fixed inaccuracies in descriptions and added detailed commentary to key methods to clarify their function and usage. ([Commit: 1fbdaa0c](#), [Commit: 542f323d](#))

### Documentation

- **README Updates**
  - Refreshed the version in `README.md` to align with the latest release and updated the documentation to reflect the addition of Keycloak support, including setup instructions. ([Commit: 3787ee4d](#), [Commit: acf0481f](#))

- **CLAUDE.md Addition**
  - Added `CLAUDE.md`, which outlines build commands, code style guidelines, and development practices for maintaining consistency across project contributions. ([Commit: efe05a12](#))

### Other Changes

- **Merge and Version Handling**
  - Managed several merge operations to integrate changes related to Keycloak support, Spring Boot upgrades, and test stability improvements. This included handling conflicts and ensuring smooth integration of features and fixes into the main codebase. ([Commit: 2782eef9](#), [Commit: 8b51908f](#), [Commit: f29295b1](#))

- **Dependency Update**
  - Updated the Maven Publish plugin from version 0.30.0 to 0.31.0, ensuring that the build process uses the latest enhancements available in the plugin. ([Commit: a87be702](#), [Commit: 2424b728](#))

This changelog covers significant feature enhancements, critical fixes, and updates to the documentation that improve the overall robustness and usability of the software framework.

## [3.1.1] - 2025-02-24
### Features
- **Version Update to Develop Branch**: The project version has been incremented to `3.1.2-SNAPSHOT`. This is a preparatory step for future developments, ensuring that ongoing changes do not interfere with the stable release version. This change was made in the `gradle.properties` file. ([commit d24ae77c](#))

### Fixes
- **Dependency Updates**: The dependencies have been updated to ensure compatibility and incorporate the latest security and performance enhancements:
  - Spring Boot has been upgraded from version `3.4.1` to `3.4.3`.
  - The plugin `com.github.ben-manes.versions` has been updated from `0.51.0` to `0.52.0`.
  These updates were applied within the `build.gradle` file, reflecting changes in both the plugins and extension configurations. ([commit a498695e](#), [commit fb7aeb6](#))

### Other Changes
- **Merged Dependency Update**: As part of maintaining the project's core dependencies, the PR that updated Spring Boot and related plugins was successfully merged, finalizing these changes into the mainline development branch. This ensures all team members and CI/CD pipelines use the latest verified configurations. ([commit a498695e](#))

There were no breaking changes, refactorings, documentation updates, or test-related changes in this update cycle. The focus was on maintaining a secure, compatible, and up-to-date codebase.

## [3.1.0] - 2025-02-24
# Changelog

## Features
- **User Profile Management**: Added new classes for user profile management, including `BaseUserProfile`, `UserProfileService`, `BaseAuthenticationListener`, and `BaseSessionProfile`. This allows for enhanced management of user-specific data within the session context of a Spring Boot application. [commit 363013cd]
- **AuthorityService Integration in Tests**: Integrated a mock of `AuthorityService` in `UserServiceTest` to facilitate improved unit testing of service interactions. [commit 8f4ca291]
- **Role and Authority Enhancement**: Introduced `AuthorityService` and `LoginHelperService` for better management of user roles and authorities, especially for OAuth2-based users. This helps streamline authentication and authority management across the system. [commit 1e08c5c1]

## Fixes
- **Changelog Generation Enhancements**: Enhanced the changelog generation script to include commit diffs and automatic categorization of changes, which should improve the clarity and utility of generated changelogs. [commit 86999343]
- **OAuth2 User Handling**: Addressed multiple issues related to role and authority handling for OAuth2 users, ensuring proper authority assignment and role retrieval during user authentication and login processes. [commit 1e08c5c1]
- **Build and Configuration Updates**: Updated Python interpreter path in VSCode settings and added method security configuration to improve project setup and security management. [commit e50a5f22]

## Breaking Changes
- There are no outright breaking changes in this release, but the introduction of new role and authority management features requires validation against existing security configurations to ensure compatibility.

## Refactoring
- **User Configuration Improvements**: Refactored user configuration for tighter integration with Spring Boot, including the addition of an auto-configuration registrar to streamline component discovery and setup. [commit 1fce50d6]

## Documentation
- **Framework Documentation Update**: Updated README and created a new PROFILE.md document detailing the user profile extension framework, offering guidance on implementation and integration within applications. [commit 6e482fa4]

## Testing
- No explicit testing updates beyond integration of service mocks.

## Other Changes
- **Dependency Updates**: Several dependency updates managed by dependabot, including updates to the MariaDB Java client, Jakarta validation API, and Thymeleaf layout dialect, ensuring the use of latest stable libraries. [commits 6364b568, 2ef01f1e, 606e02f6]
- **Script Migration**: Migrated the user schema SQL script to a dedicated MariaDB script directory for better organization and separation from other resources. [commit 00cd0b74]
- **Gradle Release Versioning**: Updated the project version to `3.0.2-SNAPSHOT` and subsequently to `3.1.0-SNAPSHOT` as part of the release process, ensuring versioning reflects ongoing developments. [commits eb07d886, 363013cd] 

This changelog is intended to provide a comprehensive overview of recent changes with an emphasis on adding context and detail useful to developers reviewing updates and integrating them into ongoing projects.

## [3.0.1] - 2025-02-01
### Features
- The controller path mappings are now configurable.

### Fixes
- Fixed the bug where the schema.sql file was in a location that caused it to be automatically executed, it has now been migrated from the resources to the db-scripts directory.



## [3.0.0] - 2025-01-12
### Features
- Converted project from a simple framework to a Maven library with a separate demo app (#136).
- Updated dependencies to use compileOnly scope and upgraded versions.
- Massive refactoring to be a Maven Library
- Updated project version to 3.0.0-SNAPSHOT and Gradle distribution to 8.12
- Updated the README.md file.

### Fixes
- Bumped com.codeborne:selenide from 7.5.1 to 7.6.0, then from 7.6.0 to 7.6.1, and finally from 7.6.1 to 7.7.0 (#127, #128, #129, and #133).
- Bumped io.spring.dependency-management from 1.1.6 to 1.1.7 (#131).
- Bumped com.google.guava:guava from 33.3.1-jre to 33.4.0-jre (#130).
- Bumped org.thymeleaf.extras:thymeleaf-extras-springsecurity6 (#129).
- Bumped org.mariadb.jdbc:mariadb-java-client from 3.5.0 to 3.5.1 (#125).
- Updated Gradle wrapper to version 8.12.

### Breaking Changes
- Major update in the project's structure as it was converted to a Maven library.

