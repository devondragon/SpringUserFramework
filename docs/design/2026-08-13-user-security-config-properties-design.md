# Design: Typed `@ConfigurationProperties` for `user.security.*`

**Date:** 2026-08-13
**Repo:** `SpringUserFramework` (library)
**Status:** Approved design — pending implementation plan
**Tracking issue:** `#355`
**Motivating issue:** `SpringUserFrameworkDemoApp#82` (Boot 4.1.0 / Thymeleaf 3.1.5 breaks the demo's `${@environment.getProperty('user.security.*')}` template idiom)

## 1. Problem

The `user.security.*` configuration namespace is the only framework config area still wired as ~40 scattered `@Value` injections across 14 classes. Every other area — MFA, WebAuthn, Captcha, GDPR, Audit, DevLogin, Roles — is already a typed `@ConfigurationProperties` class. Consequences of the `@Value` approach:

- Defaults are split three ways and already drift: the shipped `config/dsspringuserconfig.properties`, inline `@Value(":default")` fragments, and 48 hand-maintained entries in `META-INF/additional-spring-configuration-metadata.json`. Two live disagreements exist today (see §6).
- Config metadata (IDE completion, docs) is hand-maintained instead of generated.
- Consumers cannot read these values in templates without SpEL bean access (`${@environment.getProperty(...)}`), which Thymeleaf 3.1.5 (Spring Boot 4.1.0) evaluates in a restricted context during `thymeleaf-layout-dialect` decoration and rejects with *"access to static classes or parameters is forbidden."* This is what breaks `DemoApp#82`.

The framework ships only email templates; the affected page templates live in consuming apps. So the library's role is to (a) model this config properly and (b) offer a first-class, non-bean-access way for consumers to read the URIs in templates.

## 2. Goals / non-goals

**Goals**
- Model `user.security.*` as a small family of typed `@ConfigurationProperties` classes.
- Migrate all 14 internal `@Value` consumers to inject the typed beans.
- Replace the 48 hand-maintained metadata entries with generated metadata.
- Ship a template-facing, secret-free view object so consumers can drop `${@environment...}`.
- Zero behavior change; zero config-key change for consumers.

**Non-goals (explicit out-of-scope)**
- Any renaming/regrouping of config keys (reserved for a future major version).
- `@Validated`/JSR-380 startup validation (behavior change; contradicts `defaultAction`'s deliberate runtime degrade — see §7).
- Password-policy / passay redesign.
- The demo app changes (Boot 4.1.0 adoption + template switch closing `#82`) — a separate follow-up PR that consumes the snapshot this PR publishes.
- Trimming the shipped `dsspringuserconfig.properties` (see §4 — this would break compatibility).

## 3. Decisions (settled with maintainer)

1. **Additive, zero key changes** — bind the exact existing keys; no renames.
2. **Full internal migration** — convert every `user.security.*` `@Value` field site to the beans in this PR.
3. **Cohesive class family** — not one mega-class.
4. Template view object also carries `copyrightFirstYear`.
5. Placeholder/bean divergence guarded by **docs + a fail-fast test**.

## 4. Architecture

### 4.1 Three `@ConfigurationProperties` classes

Lombok `@Data`, JavaDoc on every field (drives generated metadata), matching the existing `*ConfigProperties` house style.

- **`UserSecurityConfigProperties(prefix = "user.security")`** — the flat keys:
  - URIs/action paths (Java fields in lowerCamel with lowercase acronym, e.g. `loginPageUri`, so generated metadata is clean `login-page-uri`; relaxed binding still binds the existing `user.security.loginPageURI` keys): `loginPageUri`, `loginActionUri`, `loginSuccessUri`, `logoutActionUri`, `logoutSuccessUri`, `forgotPasswordUri`, `forgotPasswordChangeUri`, `forgotPasswordPendingUri`, `registrationUri`, `registrationPendingUri`, `registrationSuccessUri`, `registrationNewVerificationUri`, `registrationConfirmUri`, `updateUserUri`, `updatePasswordUri`, `deleteAccountUri`, `changePasswordUri`.
  - URI lists as `List<String>` (fields `protectedUris`, `unprotectedUris`, `disableCsrfUris`; relaxed binding still binds the existing `user.security.protectedURIs`/`unprotectedURIs`/`disableCSRFURIs` keys) — **must preserve empty-segment filtering** (see §5).
  - Scalars: `defaultAction`, `appUrl`, `trustedHosts`, `requireCanonicalAppUrl`, `bcryptStrength`, `failedLoginAttempts`, `accountLockoutDuration`, `passwordResetTokenValidityMinutes`, `tokenHashSecret` (**`@ToString.Exclude`**), `testHashTime`, `alwaysUseDefaultTargetUrl`, `allowInitialPasswordSetWithoutStepUp`.

- **`PasswordPolicyConfigProperties(prefix = "user.security.password")`** — 11 fields: `enabled`, `minLength`, `maxLength`, `requireUppercase`, `requireLowercase`, `requireDigit`, `requireSpecial`, `specialChars`, `preventCommonPasswords`, `historyCount`, `similarityThreshold`.

- **`RememberMeConfigProperties(prefix = "user.security.remember-me")`** — 7 fields (prefix **must** be kebab; camel `rememberMe` is an invalid `@ConfigurationProperties` prefix and fails at startup): `enabled`, `key` (**`@ToString.Exclude`**), `tokenValiditySeconds`, `rememberMeParameter`, `rememberMeCookieName`, `useSecureCookie` (**`Boolean`**, tri-state — null means "Spring default"), `usePersistentTokens`.

### 4.2 Enablement

`@EnableConfigurationProperties({UserSecurityConfigProperties.class, PasswordPolicyConfigProperties.class, RememberMeConfigProperties.class})` on `UserSecurityBeansAutoConfiguration` (the natural host for this area).

### 4.3 Internal migration (14 files)

Convert injected `@Value` **fields** to `@RequiredArgsConstructor` injection of the beans:
`WebSecurityConfig`, `UserSecurityBeansAutoConfiguration`, `HtmxAwareAuthenticationEntryPointConfiguration`, `WebInterceptorConfig`, `UserActionController`, `UserAPI`, `UserService`, `PasswordPolicyService` (+`UserService` read `password.*`), `TokenHasher`, `LoginAttemptService`, `LoginSuccessService`, `LogoutSuccessService`, `UserEmailService`, `PasswordHashTimeTester`. `rememberMe.*` is consumed by `WebSecurityConfig`.

`@GetMapping("${user.security.changePasswordURI:/user/changePassword}")`-style **annotation placeholders stay as placeholders** — they are request-mapping metadata, not injectable state. Only injected `@Value` fields migrate. `usePersistentTokens` stays a `@ConditionalOnProperty` condition on `UserSecurityBeansAutoConfiguration`; the field exists on the bean so its metadata survives the JSON deletion.

Preserve `WebSecurityConfig`'s existing public getters by delegating to the beans, to avoid a source break for anyone calling e.g. `getLoginPageURI()`. Note in the changelog either way.

### 4.4 Defaults — single documented source, **file left intact**

Canonical defaults become **field initializers** matching today's *effective* (shipped-file) values. The shipped `config/dsspringuserconfig.properties` is loaded into the Spring `Environment` via `@PropertySource` on multiple config classes, which makes its keys **observable API**: 14 `@GetMapping` placeholder mappings resolve against it, several framework `@Value`s have no inline fallback and rely on it, and consumers legitimately read the same keys from the Environment (the demo's `@environment.getProperty(...)` templates are exactly this). Field initializers on a `@ConfigurationProperties` bean **never reach the Environment**, so removing file entries would break those resolutions at startup for consumers who never touched the keys.

Therefore: **do not trim the properties file in this PR.** Keep it byte-for-byte. Add a test asserting, for every `user.security.*` key, that the shipped-file value equals the bean field initializer (equality both directions) — this closes the drift risk without shrinking the Environment surface. Any trim, and the announced `requireCanonicalAppUrl` default flip, ride a future major version.

Delete the 48 `user.security.*` entries from `additional-spring-configuration-metadata.json` (generated metadata replaces them), guarded by a test that parses the retained old JSON (as a test resource) and asserts every `user.security.*` key it declares appears in the generated `spring-configuration-metadata.json` under dash/case-insensitive compare. Do **not** touch the JSON's non-`user.security` entries (`user.audit.*`, `user.registration.*`, `user.copyrightFirstYear`, `spring.*` passthroughs). The JSON has no `hints` section, so none are lost.

### 4.5 Template access (the `#82` enabler)

A `@ControllerAdvice(annotations = Controller.class)` — mirroring `CaptchaSiteKeyControllerAdvice`, registered from the auto-configuration — exposes a **narrow, immutable view record** (built once from the beans) as `@ModelAttribute("userSecurity")`. The record carries only the page/action URIs plus `copyrightFirstYear`. It **must not** expose the `UserSecurityConfigProperties` bean itself, which holds `tokenHashSecret` and would otherwise render into any template model dump or error page. Registration is gated by `@ConditionalOnProperty(matchIfMissing = true)` so consumers can opt out; `userSecurity` is now a reserved model-attribute name and is documented as such.

Templates then use `${userSecurity.loginPageUri}` — an ordinary model variable, not subject to Thymeleaf's restricted-expression rules.

## 5. Data / binding semantics

`List<String>` binding for `protectedURIs`/`unprotectedURIs`/`disableCSRFURIs`: Boot's delimited-string conversion trims but **does not drop empty segments**, whereas the current `WebSecurityConfig.splitAndFilterProperty` drops them. A consumer override with a trailing comma (`unprotectedURIs=/a,/b,`) must not start producing `requestMatchers("")`. Preserve empty-filtering at the consumption site (or via a defensive getter). Binding tests cover `"a,,b,"`, trailing comma, and empty string (`disableCSRFURIs=` → empty list).

## 6. Effective-value defaults to encode (file wins over inline fallback)

The field initializers use the **file** values, which are the effective runtime values today:
- `bcryptStrength` → **12** (file), not 10 (inline `@Value` fallback in `UserSecurityBeansAutoConfiguration`).
- `password.historyCount` → **3** (file), not 0 (inline fallback in `UserService`).
- `appUrl` → **`""`** (file), not `null` (`@Value` `#{null}` fallback). `AppUrlResolver` treats blank and null alike, but the equality test needs one canonical form; keep `""`.

## 7. Error handling / behavior preservation

- `defaultAction` keeps its runtime degrade-to-`denyAll` on an invalid value (`WebSecurityConfig`); no startup validation is added.
- `requireCanonicalAppUrl` keeps its existing opt-in fail-fast.
- `useSecureCookie` stays tri-state `Boolean` so null continues to mean "use Spring's default."
- Placeholder/bean divergence: the generated metadata advertises kebab spellings while the 14 `@GetMapping`/`@Value` placeholders resolve the exact camelCase key only. Guard with (a) CONFIG.md + metadata descriptions stating camelCase spellings are canonical, and (b) a fail-fast test/startup check asserting each mapping-relevant placeholder value equals the corresponding bean getter, with a message naming any kebab/camel mismatch.

## 8. Testing

- Per-class binding tests: defaults, override, relaxed binding (incl. an env-var form `USER_SECURITY_REMEMBERME_*` → `remember-me` prefix to pin the binder equivalence).
- Effective-defaults equality test: shipped-file value == field initializer for every `user.security.*` key (both directions).
- Metadata coverage test: every `user.security.*` key in the retained old JSON appears in generated metadata.
- List-binding edge tests: `"a,,b,"`, trailing comma, empty string.
- ControllerAdvice test: `userSecurity` present with populated URIs + `copyrightFirstYear`, and **absent** when opted out; assert `tokenHashSecret` is not reachable from the exposed object.
- Placeholder/bean parity test (§7b).
- Existing ArchUnit / integration / security suites as the migration safety net.
- Naming `should[Behavior]When[Condition]`; use custom annotations (`@ServiceTest`, `@SecurityTest`, etc.) per `context/conventions.md`.

## 9. Documentation

- `CONFIG.md`: note the typed properties and that camelCase key spellings are canonical.
- `CHANGELOG.md`: internal refactor; new `${userSecurity}` model attribute; any `WebSecurityConfig` getter note.
- `MIGRATION.md`: "no action required — keys unchanged; `${userSecurity.*}` now available to templates."

## 10. Sequencing

1. This library PR → merge → publish `5.2.x`-SNAPSHOT.
2. Follow-up demo PR: adopt Boot 4.1.0, replace `${@environment.getProperty('user.security.*')}` and `copyrightFirstYear` with `${userSecurity.*}`, and handle the two remaining non-`user.security` template expressions (`@environment.acceptsProfiles('dev','local')` stays a demo-side computed model attribute; it is not a config value). Closes `DemoApp#82`.
