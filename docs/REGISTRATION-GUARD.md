# Registration Guard SPI

This guide explains how to use the Registration Guard SPI in Spring User Framework to control who can register in your application.

## Table of Contents
- [Registration Guard SPI](#registration-guard-spi)
  - [Table of Contents](#table-of-contents)
  - [Overview](#overview)
  - [When to Use](#when-to-use)
  - [Core Components](#core-components)
  - [Implementation Guide](#implementation-guide)
  - [Usage Examples](#usage-examples)
    - [Domain Restriction](#domain-restriction)
    - [Invite-Only with OAuth2 Bypass](#invite-only-with-oauth2-bypass)
    - [Beta Access / Waitlist](#beta-access--waitlist)
  - [Denial Behavior](#denial-behavior)
  - [Composing Multiple Guards](#composing-multiple-guards)
  - [Key Constraints](#key-constraints)
  - [Troubleshooting](#troubleshooting)

## Overview

The Registration Guard is a pre-registration hook that gates all four registration paths: form, passwordless, OAuth2, and OIDC. It allows you to accept or reject registration attempts before a user account is created.

The guard requires zero configuration — it activates by bean presence alone. When no custom guard is defined, a built-in permit-all default is used automatically.

## When to Use

Consider implementing a Registration Guard when you need to:

- Restrict registration to specific email domains (e.g., corporate apps)
- Implement invite-only or beta access registration
- Enforce waitlist-based onboarding
- Apply compliance or legal gates before account creation
- Allow social login but restrict form-based registration (or vice versa)

If your application allows open registration with no restrictions, you do not need to implement a guard.

## Core Components

The Registration Guard SPI consists of these types in the `com.digitalsanctuary.spring.user.registration` package:

1. **`RegistrationGuard`** — The interface you implement. Has a single method: `evaluate(RegistrationContext)` returning a `RegistrationDecision`.

2. **`RegistrationContext`** — An immutable record describing the registration attempt:
   - `email` — the email address of the user attempting to register
   - `source` — the registration path (`FORM`, `PASSWORDLESS`, `OAUTH2`, or `OIDC`)
   - `providerName` — the OAuth2/OIDC provider registration ID (e.g. `"google"`, `"keycloak"`), or `null` for form/passwordless

3. **`RegistrationDecision`** — An immutable record with the guard's verdict:
   - `allowed` — whether the registration is permitted
   - `reason` — a human-readable denial reason (may be `null` when allowed)
   - `allow()` — static factory for an allowing decision
   - `deny(String reason)` — static factory for a denying decision

4. **`RegistrationSource`** — Enum identifying the registration path: `FORM`, `PASSWORDLESS`, `OAUTH2`, `OIDC`

5. **`DefaultRegistrationGuard`** — The built-in permit-all fallback. Automatically registered via `@ConditionalOnMissingBean` when no custom guard bean exists, so the composite always has at least one delegate.

6. **`CompositeRegistrationGuard`** — The primary (`@Primary`) guard the framework injects everywhere. It wraps **all** `RegistrationGuard` beans and evaluates them in order with **first-deny-wins** semantics (see [Composing Multiple Guards](#composing-multiple-guards)). You normally never reference it directly.

7. **`RegistrationDeniedException`** — A `RuntimeException` carrying the denial `reason`. The framework throws this from the service layer when a guard denies, and translates it into the appropriate response per path (see [Denial Behavior](#denial-behavior)). Consumers rarely need to catch it.

### Where the guard runs

The guard is enforced **inside `UserService`** (and, for first-time social sign-ups, via `UserService.enforceRegistrationGuard(...)` called by the OAuth2/OIDC user services). Because enforcement lives in the service rather than the controller, **every** registration path — REST API, OAuth2, OIDC, and any direct call to `UserService.registerNewUserAccount(...)` / `registerPasswordlessAccount(...)` — is guarded exactly once and cannot be bypassed. The guard runs only for **new** registrations; existing users logging in are never evaluated.

## Implementation Guide

Create a `@Component` that implements `RegistrationGuard`. That's it — the default guard is automatically replaced, and your guard is wrapped by the composite.

```java
@Component
public class MyRegistrationGuard implements RegistrationGuard {

    @Override
    public RegistrationDecision evaluate(RegistrationContext context) {
        // Your logic here
        return RegistrationDecision.allow();
    }
}
```

No additional configuration, properties, or wiring is needed. The library detects your bean and uses it in place of the default.

## Usage Examples

### Domain Restriction

Allow only users with a specific email domain:

```java
@Component
public class DomainGuard implements RegistrationGuard {

    @Override
    public RegistrationDecision evaluate(RegistrationContext context) {
        if (context.email().endsWith("@mycompany.com")) {
            return RegistrationDecision.allow();
        }
        return RegistrationDecision.deny("Registration is restricted to @mycompany.com email addresses.");
    }
}
```

### Invite-Only with OAuth2 Bypass

Require an invite for form/passwordless registration but allow all OAuth2/OIDC users:

```java
@Component
@RequiredArgsConstructor
public class InviteOnlyGuard implements RegistrationGuard {

    private final InviteCodeRepository inviteCodeRepository;

    @Override
    public RegistrationDecision evaluate(RegistrationContext context) {
        // Allow all OAuth2/OIDC registrations
        if (context.source() == RegistrationSource.OAUTH2
                || context.source() == RegistrationSource.OIDC) {
            return RegistrationDecision.allow();
        }
        // For form/passwordless, check invite list
        if (inviteCodeRepository.existsByEmail(context.email())) {
            return RegistrationDecision.allow();
        }
        return RegistrationDecision.deny("Registration is by invitation only.");
    }
}
```

### Beta Access / Waitlist

Check a beta-users table before allowing registration:

```java
@Component
@RequiredArgsConstructor
public class BetaAccessGuard implements RegistrationGuard {

    private final BetaUserRepository betaUserRepository;

    @Override
    public RegistrationDecision evaluate(RegistrationContext context) {
        if (betaUserRepository.existsByEmail(context.email())) {
            return RegistrationDecision.allow();
        }
        return RegistrationDecision.deny("Registration is currently limited to beta users. "
                + "Please join the waitlist.");
    }
}
```

## Denial Behavior

When a guard denies a registration, the behavior depends on the registration path:

| Registration Path | Denial Response |
|---|---|
| **Form** | HTTP 403 Forbidden with JSON: `{"success": false, "code": 6, "messages": ["<reason>"]}` |
| **Passwordless** | HTTP 403 Forbidden with JSON: `{"success": false, "code": 6, "messages": ["<reason>"]}` |
| **OAuth2** | `OAuth2AuthenticationException` with error code `"registration_denied"` — handled by Spring Security's OAuth2 failure handler |
| **OIDC** | `OAuth2AuthenticationException` with error code `"registration_denied"` — handled by Spring Security's OAuth2 failure handler |

The JSON error code `6` identifies a registration guard denial specifically, distinguishing it from other registration errors (e.g., code `1` for validation failures, code `2` for duplicate accounts). Client-side code can check this code to display targeted messaging.

For OAuth2/OIDC denials, customize the user experience by configuring Spring Security's OAuth2 login failure handler to inspect the error code and display an appropriate message.

All denied registrations are logged at INFO level with the source and denial reason.

Internally, a denial surfaces from the service layer as a `RegistrationDeniedException` carrying the reason. The REST API catches it and returns the form/passwordless JSON above; the OAuth2/OIDC user services catch it and re-throw the `OAuth2AuthenticationException` shown above. The HTTP contract is identical regardless of how registration was triggered.

## Composing Multiple Guards

You may define **more than one** `RegistrationGuard` bean. The framework wraps them all in a `CompositeRegistrationGuard` (registered as `@Primary`) that evaluates them **in order, first-deny-wins**:

- Guards are consulted in `@Order` / `org.springframework.core.Ordered` order (lowest value first; unordered beans come last).
- The **first** guard to return `deny(...)` short-circuits — later guards are not consulted — and its reason is propagated.
- If every guard allows (or you define no guards at all, leaving only the permit-all default), registration proceeds.

This lets you layer independent policies — for example an invite-only guard **and** a domain-allowlist guard — where any single denial blocks the registration. Each guard stays small and single-purpose:

```java
@Component
@Order(1)
public class InviteOnlyGuard implements RegistrationGuard { /* ... */ }

@Component
@Order(2)
public class DomainAllowlistGuard implements RegistrationGuard { /* ... */ }
```

## Key Constraints

- **Composable SPI** — One or more `RegistrationGuard` beans may be active; they are composed with first-deny-wins ordering. (You can still define exactly one guard — that is just a composite of size one.)
- **Enforced in the service** — The guard runs inside `UserService`, so direct callers of the service registration methods are guarded too; the SPI cannot be bypassed by skipping the controller.
- **Thread safety required** — The guard may be invoked concurrently from multiple request threads. Ensure your implementation is thread-safe.
- **No configuration properties** — The guard is activated entirely by bean presence. There are no `user.*` properties involved.
- **Existing users unaffected** — The guard only runs for new registrations. Existing users logging in via OAuth2/OIDC are not evaluated.

## Troubleshooting

**Guard Not Activating**
- Ensure your guard class is annotated with `@Component` (or otherwise registered as a Spring bean)
- Verify the class is within a package that is component-scanned by your application
- At startup, the library logs `"No custom RegistrationGuard bean found — using DefaultRegistrationGuard (permit-all)"` at INFO level. If you see this message, your custom guard bean is not being detected.
- You can also check the active guard via `/actuator/beans` (if enabled) or your IDE's Spring tooling.

**Multiple Guards Defined**
- Multiple `RegistrationGuard` beans are fully supported — they are composed automatically with first-deny-wins ordering (see [Composing Multiple Guards](#composing-multiple-guards)). Use `@Order` to control evaluation order.
- The framework injects the `@Primary` `CompositeRegistrationGuard` everywhere, so defining several guards does **not** cause a `NoUniqueBeanDefinitionException`.

**OAuth2/OIDC Denial UX**
- By default, OAuth2/OIDC denials redirect to Spring Security's default failure URL with a generic error.
- To show a custom message, configure an `AuthenticationFailureHandler` on your OAuth2 login that checks for the `"registration_denied"` error code:
  ```java
  http.oauth2Login(oauth2 -> oauth2
      .failureHandler((request, response, exception) -> {
          if (exception instanceof OAuth2AuthenticationException oauthEx
                  && "registration_denied".equals(oauthEx.getError().getErrorCode())) {
              response.sendRedirect("/registration-denied");
          } else {
              response.sendRedirect("/login?error");
          }
      })
  );
  ```

---

This SPI provides a clean extension point for controlling registration without modifying framework internals. Implement one or more beans, return allow or deny, and the framework composes them and handles the rest across all registration paths.

For a complete working example, refer to the [Spring User Framework Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp).
