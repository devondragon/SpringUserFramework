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

5. **`DefaultRegistrationGuard`** — The built-in permit-all fallback. Automatically registered via `@ConditionalOnMissingBean` when no custom guard bean exists.

## Implementation Guide

Create a `@Component` that implements `RegistrationGuard`. That's it — the default guard is automatically replaced.

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

For OAuth2/OIDC denials, customize the user experience by configuring Spring Security's OAuth2 login failure handler to inspect the error code and display an appropriate message.

## Key Constraints

- **Single-bean SPI** — Only one `RegistrationGuard` bean may be active at a time. This is not a chain or filter pattern; define exactly one guard.
- **Thread safety required** — The guard may be invoked concurrently from multiple request threads. Ensure your implementation is thread-safe.
- **No configuration properties** — The guard is activated entirely by bean presence. There are no `user.*` properties involved.
- **Existing users unaffected** — The guard only runs for new registrations. Existing users logging in via OAuth2/OIDC are not evaluated.

## Troubleshooting

**Guard Not Activating**
- Ensure your guard class is annotated with `@Component` (or otherwise registered as a Spring bean)
- Verify the class is within a package that is component-scanned by your application
- Check that `DefaultRegistrationGuard` is being replaced — enable debug logging:
  ```yaml
  logging:
    level:
      com.digitalsanctuary.spring.user.registration: DEBUG
  ```

**Multiple Guards Defined**
- Only one `RegistrationGuard` bean is allowed. If multiple beans are defined, Spring will throw a `NoUniqueBeanDefinitionException` at startup.
- If you need to compose multiple rules, implement a single guard that delegates internally.

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

This SPI provides a clean extension point for controlling registration without modifying framework internals. Implement a single bean, return allow or deny, and the framework handles the rest across all registration paths.

For a complete working example, refer to the [Spring User Framework Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp).
