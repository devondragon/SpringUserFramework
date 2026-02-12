# Passkey (WebAuthn) Implementation Plan
## Spring User Framework - Spring Boot 3.5.7 / Spring Security 6.5

**Version:** 2.1 (Corrected - Native Spring Security Implementation)
**Date:** 2025-11-30
**Status:** Planning Phase
**Target Platform:** Spring Boot 3.5.7 / Spring Security 6.5

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Critical Requirements & Limitations](#critical-requirements--limitations)
3. [Spring Security Native WebAuthn Overview](#spring-security-native-webauthn-overview)
4. [Architecture Design](#architecture-design)
5. [Database Schema](#database-schema)
6. [Domain Model](#domain-model)
7. [Repository Layer](#repository-layer)
8. [Service Layer](#service-layer)
9. [Security Configuration](#security-configuration)
10. [API Endpoints](#api-endpoints)
11. [Frontend Integration](#frontend-integration)
12. [Dependencies](#dependencies)
13. [Configuration Properties](#configuration-properties)
14. [Testing Strategy](#testing-strategy)
15. [Migration & Rollout Plan](#migration--rollout-plan)
16. [Security Considerations](#security-considerations)
17. [Spring Boot 4.0 Migration Path](#spring-boot-40-migration-path)
18. [Future Enhancements](#future-enhancements)

---

## Executive Summary

This document outlines the implementation plan for adding **Passkey (WebAuthn)** support to the Spring User Framework using **Spring Security 6.5's native WebAuthn support**. This approach leverages Spring Security's built-in passkey features introduced in version 6.4, providing a simpler, more maintainable implementation than custom library integration.

### Key Benefits

- ✅ **Built-in Support**: Spring Security 6.5 includes native WebAuthn/Passkey support
- ✅ **Simplified Implementation**: No custom authentication filters required
- ✅ **Official Spring Support**: Long-term maintenance and updates guaranteed
- ✅ **Faster Development**: ~6-7 weeks vs 10+ weeks with custom integration
- ✅ **Production-Ready**: Used by Spring Security team and community
- ✅ **Database Persistence**: JDBC-backed credential storage out of the box

### Implementation Scope

- ⚠️ **Passkey registration for AUTHENTICATED users** (pre-authentication required)
- Passkey-based authentication (login)
- Multiple credential management per user
- Credential revocation and lifecycle management
- JDBC persistence (PostgreSQL, MySQL, H2)
- Resident key (discoverable credential) support
- Backward compatible with existing password/OAuth2 authentication

### Why Native Spring Security?

**Spring Security 6.4+ includes WebAuthn support** using the WebAuthn4J library internally. This eliminates the need for:
- ❌ Custom authentication filters
- ❌ Third-party library integration (Yubico)
- ❌ Manual challenge management
- ❌ Complex credential repository implementations

Instead, you get:
- ✅ `.webAuthn()` DSL configuration
- ✅ Default endpoints (`/webauthn/register/*`, `/login/webauthn`)
- ✅ `UserCredentialRepository` interface for database integration
- ✅ Automatic session management
- ✅ Built-in security best practices

---

## Critical Requirements & Limitations

### ⚠️ Pre-Authentication Required for Registration

**IMPORTANT:** Spring Security WebAuthn requires users to be **already authenticated** before they can register a passkey. This is a fundamental limitation of the current implementation.

**Impact on User Flow:**

```
❌ CANNOT DO:
New User → Register Account with Passkey → Account Created

✅ MUST DO:
New User → Register with Password/OAuth2 → Login → Add Passkey → Logout → Login with Passkey
```

**Workarounds:**

1. **Option A:** Require password during registration, allow passkey addition after login
2. **Option B:** Use OAuth2/SSO for initial registration, add passkey after
3. **Option C:** Accept the limitation - passkeys as a "second factor" enhancement

**Recommendation:** Implement Option A initially. Document clearly that passkey is an *enhancement* to existing authentication, not a replacement for initial registration.

### ✅ HTTPS Requirement

**WebAuthn requires HTTPS.** Browsers enforce this (except for localhost in development).

**Development:**
- Use `https://localhost:8443` with self-signed certificate
- Generate with: `keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 3650`

**Production:**
- Use proper SSL certificate (Let's Encrypt, commercial CA)
- Configure TLS 1.2+ with strong ciphers
- Enable HSTS headers

### ✅ Browser Compatibility

| Browser | Version | Support |
|---------|---------|---------|
| Chrome/Edge | 67+ | ✅ Full |
| Safari (macOS) | 13+ | ✅ Full |
| Safari (iOS) | 14+ | ✅ Full |
| Firefox | 60+ | ✅ Full |

---

## Spring Security Native WebAuthn Overview

### How Spring Security WebAuthn Works

Spring Security 6.5 provides first-class WebAuthn support through a simple configuration API:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .webAuthn(webAuthn -> webAuthn
            .rpName("Spring User Framework")
            .rpId("example.com")
            .allowedOrigins("https://example.com")
        );
    return http.build();
}
```

### Built-in Components

Spring Security provides:

1. **Default Endpoints:**
   - `POST /webauthn/register/options` - Get registration challenge
   - `POST /webauthn/register` - Complete passkey registration
   - `POST /webauthn/authenticate/options` - Get authentication challenge
   - `POST /login/webauthn` - Complete passkey authentication
   - `GET /login` - Default login page with passkey support

2. **Repository Interfaces:**
   - `UserCredentialRepository` - Store and retrieve credentials
   - `PublicKeyCredentialUserEntityRepository` - Manage user entities

3. **JDBC Implementations:**
   - `JdbcUserCredentialRepository` - Database-backed credential storage
   - `JdbcPublicKeyCredentialUserEntityRepository` - Database-backed user entities

4. **Challenge Management:**
   - Automatic challenge generation and validation
   - Built-in replay attack prevention
   - Configurable challenge timeout

### Registration Flow

```
User logs in with password/OAuth2
    ↓
User navigates to "Add Passkey" in account settings
    ↓
Frontend POST to /webauthn/register/options
    ↓
Spring Security generates challenge
    ↓
Frontend calls navigator.credentials.create()
    ↓
Browser/authenticator generates key pair
    ↓
Public key + attestation sent to /webauthn/register
    ↓
Spring Security validates attestation
    ↓
UserCredentialRepository.save() called
    ↓
Credential stored in database
```

### Authentication Flow

```
User initiates login
    ↓
Frontend POST to /webauthn/authenticate/options
    ↓
Spring Security generates challenge
    ↓
Frontend calls navigator.credentials.get()
    ↓
Authenticator signs challenge
    ↓
Assertion sent to /login/webauthn
    ↓
Spring Security validates signature
    ↓
Signature counter checked (anti-cloning)
    ↓
User authenticated and session created
```

---

## Architecture Design

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend Layer                          │
│  (JavaScript WebAuthn API, Registration/Login UI)              │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 │ HTTPS/JSON
                 │
┌────────────────┴────────────────────────────────────────────────┐
│              Spring Security WebAuthn Layer                     │
│  • /webauthn/register/options (Built-in)                       │
│  • /webauthn/register (Built-in)                               │
│  • /webauthn/authenticate/options (Built-in)                   │
│  • /login/webauthn (Built-in)                                  │
│  • Default challenge management                                │
│  • Automatic credential validation                             │
└────────────────┬────────────────────────────────────────────────┘
                 │
┌────────────────┴────────────────────────────────────────────────┐
│                    Custom Service Layer                         │
│  • WebAuthnCredentialManagementService                         │
│  • WebAuthnUserService (bridges User entity)                   │
└────────────────┬────────────────────────────────────────────────┘
                 │
┌────────────────┴────────────────────────────────────────────────┐
│                   Repository Implementations                    │
│  • JdbcUserCredentialRepository (Spring Security built-in)     │
│  • JdbcPublicKeyCredentialUserEntityRepository (built-in)      │
│  • WebAuthnCredentialQueryRepository (custom management)       │
└────────────────┬────────────────────────────────────────────────┘
                 │
┌────────────────┴────────────────────────────────────────────────┐
│                       Database Layer                            │
│  • user_account (existing)                                     │
│  • webauthn_user_credential (new - Spring Security schema)     │
│  • webauthn_user_entity (new - Spring Security schema)         │
└─────────────────────────────────────────────────────────────────┘
```

### Integration with Existing Authentication

Passkey authentication integrates seamlessly with existing mechanisms:

```
Security Filter Chain
├── Form Login Filter (password-based) [Existing]
├── OAuth2 Login Filter (OAuth2/SSO) [Existing]
└── WebAuthn Filter (passkey-based) [New - Auto-configured]
        │
        └── Uses JdbcUserCredentialRepository
            └── Stores in webauthn_user_credential table
```

### Key Design Principles

1. **Database-First**: Use JDBC persistence from day one (not in-memory)
2. **User-Centric**: Credentials linked to existing User entities via user_account_id FK
3. **Additive**: Passkeys supplement, don't replace, existing auth methods
4. **Spring-Native**: Leverage built-in features, minimize custom code
5. **Testable**: Use Spring Security's testing support for WebAuthn

---

## Database Schema

### Overview

Spring Security provides recommended schema for WebAuthn tables. We'll adapt these to integrate with your existing `user_account` table.

### Spring Security Default Schema

Spring Security expects these tables for JDBC persistence:

#### 1. `webauthn_user_entity`

Maps Spring Security's user entity to your application users.

```sql
CREATE TABLE webauthn_user_entity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    user_id BLOB NOT NULL,
    display_name VARCHAR(255) NOT NULL,

    CONSTRAINT uk_webauthn_user_entity_name UNIQUE (name),
    CONSTRAINT uk_webauthn_user_entity_user_id UNIQUE (user_id)
);

CREATE INDEX idx_webauthn_user_entity_name ON webauthn_user_entity(name);
```

**Field Mapping:**
- `name`: User's email (username)
- `user_id`: Base64-encoded User ID from user_account table
- `display_name`: User's full name

#### 2. `webauthn_user_credential`

Stores WebAuthn credentials (public keys).

```sql
CREATE TABLE webauthn_user_credential (
    id VARCHAR(255) PRIMARY KEY,
    user_entity_id BIGINT NOT NULL,
    credential_id BLOB NOT NULL,
    public_key BLOB NOT NULL,
    signature_count BIGINT NOT NULL DEFAULT 0,
    uv_initialized BOOLEAN NOT NULL DEFAULT FALSE,
    transports VARCHAR(255),
    backup_eligible BOOLEAN DEFAULT FALSE,
    backup_state BOOLEAN DEFAULT FALSE,
    attestation_object BLOB,
    attestation_client_data_json BLOB,
    label VARCHAR(255),
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used TIMESTAMP,

    CONSTRAINT fk_webauthn_credential_user_entity
        FOREIGN KEY (user_entity_id)
        REFERENCES webauthn_user_entity(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_webauthn_credential_id UNIQUE (credential_id)
);

CREATE INDEX idx_webauthn_credential_user_entity ON webauthn_user_credential(user_entity_id);
CREATE INDEX idx_webauthn_credential_id ON webauthn_user_credential(credential_id);
```

**Field Descriptions:**
- `id`: Primary key (UUID string)
- `credential_id`: Base64-encoded credential ID (from authenticator)
- `public_key`: COSE-encoded public key
- `signature_count`: Counter to detect cloned authenticators (auto-updated by Spring Security)
- `uv_initialized`: User verification was performed during registration
- `transports`: Supported transports (usb, nfc, ble, internal)
- `backup_eligible`: Credential can be synced (iCloud Keychain, etc.)
- `backup_state`: Credential is currently backed up
- `label`: User-friendly name ("My iPhone", "YubiKey")

### Enhanced Schema for Integration

Add custom fields to link with existing User entity:

```sql
-- Add user_account_id to webauthn_user_entity for efficient lookup
ALTER TABLE webauthn_user_entity
ADD COLUMN user_account_id BIGINT;

ALTER TABLE webauthn_user_entity
ADD CONSTRAINT fk_webauthn_user_account
    FOREIGN KEY (user_account_id)
    REFERENCES user_account(id)
    ON DELETE CASCADE;

CREATE INDEX idx_webauthn_user_account_id ON webauthn_user_entity(user_account_id);

-- Add enabled flag to credentials (soft delete)
ALTER TABLE webauthn_user_credential
ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_webauthn_credential_enabled ON webauthn_user_credential(enabled);
```

### Complete Migration Script

```sql
-- File: src/main/resources/db/migration/V1_1__add_webauthn_support.sql

-- =====================================================
-- Spring Security WebAuthn Schema
-- =====================================================

-- User entity table (maps to Spring Security's WebAuthn user entity)
CREATE TABLE webauthn_user_entity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    user_id BLOB NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    user_account_id BIGINT,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_webauthn_user_entity_name UNIQUE (name),
    CONSTRAINT uk_webauthn_user_entity_user_id UNIQUE (user_id),
    CONSTRAINT fk_webauthn_user_account FOREIGN KEY (user_account_id)
        REFERENCES user_account(id) ON DELETE CASCADE
);

CREATE INDEX idx_webauthn_user_entity_name ON webauthn_user_entity(name);
CREATE INDEX idx_webauthn_user_account_id ON webauthn_user_entity(user_account_id);

-- Credential table (stores public keys)
CREATE TABLE webauthn_user_credential (
    id VARCHAR(255) PRIMARY KEY,
    user_entity_id BIGINT NOT NULL,
    credential_id BLOB NOT NULL,
    public_key BLOB NOT NULL,
    signature_count BIGINT NOT NULL DEFAULT 0,
    uv_initialized BOOLEAN NOT NULL DEFAULT FALSE,
    transports VARCHAR(255),
    backup_eligible BOOLEAN DEFAULT FALSE,
    backup_state BOOLEAN DEFAULT FALSE,
    attestation_object BLOB,
    attestation_client_data_json BLOB,
    label VARCHAR(255),
    created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_webauthn_credential_user_entity
        FOREIGN KEY (user_entity_id)
        REFERENCES webauthn_user_entity(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_webauthn_credential_id UNIQUE (credential_id)
);

CREATE INDEX idx_webauthn_credential_user_entity ON webauthn_user_credential(user_entity_id);
CREATE INDEX idx_webauthn_credential_id ON webauthn_user_credential(credential_id);
CREATE INDEX idx_webauthn_credential_enabled ON webauthn_user_credential(enabled);
CREATE INDEX idx_webauthn_credential_last_used ON webauthn_user_credential(last_used);
CREATE INDEX idx_webauthn_credential_created ON webauthn_user_credential(created);

-- Add audit events for WebAuthn operations
INSERT INTO audit_event_type (name, description) VALUES
    ('WEBAUTHN_REGISTRATION_INITIATED', 'User initiated passkey registration'),
    ('WEBAUTHN_REGISTRATION_COMPLETED', 'User completed passkey registration'),
    ('WEBAUTHN_REGISTRATION_FAILED', 'Passkey registration failed'),
    ('WEBAUTHN_AUTHENTICATION_SUCCESS', 'User authenticated with passkey'),
    ('WEBAUTHN_AUTHENTICATION_FAILED', 'Passkey authentication failed'),
    ('WEBAUTHN_CREDENTIAL_DELETED', 'User deleted a passkey'),
    ('WEBAUTHN_CREDENTIAL_RENAMED', 'User renamed a passkey');
```

---

## Domain Model

### Approach

Spring Security manages credentials internally via `JdbcUserCredentialRepository`, but we'll create lightweight DTOs for custom credential management operations (listing, renaming, deleting).

### WebAuthnCredentialInfo.java

Read-only view of credentials for user management.

```java
package com.digitalsanctuary.spring.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for WebAuthn credential information displayed to users.
 * Does not contain sensitive data (public keys, credential IDs).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnCredentialInfo {

    /**
     * Credential ID (internal identifier).
     */
    private String id;

    /**
     * User-friendly label for the credential.
     */
    private String label;

    /**
     * Credential creation date.
     */
    private Instant created;

    /**
     * Last authentication date.
     */
    private Instant lastUsed;

    /**
     * Supported transports (usb, nfc, ble, internal).
     */
    private String transports;

    /**
     * Whether credential is backup-eligible (synced passkey).
     */
    private Boolean backupEligible;

    /**
     * Whether credential is currently backed up.
     */
    private Boolean backupState;

    /**
     * Whether credential is enabled.
     */
    private Boolean enabled;
}
```

### Update User.java

Add helper methods to check passkey availability:

```java
// Add to User.java

/**
 * Check if user has any registered passkeys.
 * Queries the WebAuthn credential repository.
 */
@Transient
public boolean hasPasskeys() {
    // Implementation delegated to service layer
    return false;  // Placeholder
}

/**
 * Check if user can login without password.
 */
@Transient
public boolean isPasswordlessEnabled() {
    return hasPasskeys();
}
```

---

## Repository Layer

### Hybrid Approach

**Use Spring Security's built-in JDBC repositories** for core credential management, and add custom repository for user-facing operations.

#### 1. Spring Security Built-in Repositories (Configuration)

```java
package com.digitalsanctuary.spring.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.webauthn.management.UserCredentialRepository;

/**
 * Configuration for Spring Security's built-in WebAuthn repositories.
 */
@Configuration
public class WebAuthnRepositoryConfig {

    /**
     * Built-in Spring Security credential repository.
     * Handles save, findByCredentialId, findByUserId, delete operations.
     */
    @Bean
    public UserCredentialRepository userCredentialRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcUserCredentialRepository(jdbcTemplate);
    }

    /**
     * Built-in Spring Security user entity repository.
     * Handles user entity creation and lookup.
     */
    @Bean
    public PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(
            JdbcTemplate jdbcTemplate) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbcTemplate);
    }
}
```

**Note:** Spring Security's built-in repositories automatically:
- ✅ Update `signature_count` after each authentication
- ✅ Handle challenge validation
- ✅ Manage credential lifecycle
- ✅ Prevent replay attacks

#### 2. Custom Repository Bridge for User Integration

```java
package com.digitalsanctuary.spring.user.persistence.repository;

import com.digitalsanctuary.spring.user.persistence.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Bridge between Spring Security's WebAuthn user entities and framework User entities.
 * Handles edge cases like anonymousUser and null usernames.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class WebAuthnUserEntityBridge {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final PublicKeyCredentialUserEntityRepository baseRepository;

    /**
     * Find user entity by username with null/anonymousUser handling.
     */
    public Optional<PublicKeyCredentialUserEntity> findByUsername(String username) {
        // Handle edge cases that can occur during login
        if (username == null || username.isEmpty() || "anonymousUser".equals(username)) {
            log.debug("Ignoring invalid username: {}", username);
            return Optional.empty();
        }

        // Check if user entity already exists
        Optional<PublicKeyCredentialUserEntity> existing = baseRepository.findByUsername(username);
        if (existing.isPresent()) {
            return existing;
        }

        // User entity doesn't exist yet - check if application user exists
        Optional<User> userOpt = userRepository.findByEmail(username);
        if (userOpt.isEmpty()) {
            log.debug("No application user found for username: {}", username);
            return Optional.empty();
        }

        // Create WebAuthn user entity for this application user
        User user = userOpt.get();
        PublicKeyCredentialUserEntity entity = createUserEntity(user);
        baseRepository.save(entity);

        return Optional.of(entity);
    }

    /**
     * Create user entity from User model with user_account_id link.
     */
    @Transactional
    public PublicKeyCredentialUserEntity createUserEntity(User user) {
        byte[] userId = longToBytes(user.getId());
        String displayName = user.getFirstName() + " " + user.getLastName();

        PublicKeyCredentialUserEntity entity = ImmutablePublicKeyCredentialUserEntity.builder()
            .name(user.getEmail())
            .id(userId)
            .displayName(displayName)
            .build();

        // Save with user_account_id link
        String insertSql = """
            INSERT INTO webauthn_user_entity
            (name, user_id, display_name, user_account_id)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE display_name = VALUES(display_name)
            """;

        jdbcTemplate.update(insertSql,
            entity.getName(),
            entity.getId(),
            entity.getDisplayName(),
            user.getId()
        );

        log.info("Created WebAuthn user entity for user: {}", user.getEmail());
        return entity;
    }

    /**
     * Convert Long ID to byte array.
     */
    private byte[] longToBytes(Long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }
}
```

#### 3. WebAuthnCredentialQueryRepository.java

Custom repository for credential management operations.

```java
package com.digitalsanctuary.spring.user.persistence.repository;

import com.digitalsanctuary.spring.user.dto.WebAuthnCredentialInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Custom repository for WebAuthn credential queries and management.
 * Complements Spring Security's built-in repositories.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class WebAuthnCredentialQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Get all credentials for a user.
     */
    public List<WebAuthnCredentialInfo> findCredentialsByUserId(Long userId) {
        String sql = """
            SELECT c.id, c.label, c.created, c.last_used, c.transports,
                   c.backup_eligible, c.backup_state, c.enabled
            FROM webauthn_user_credential c
            JOIN webauthn_user_entity wue ON c.user_entity_id = wue.id
            WHERE wue.user_account_id = ? AND c.enabled = true
            ORDER BY c.created DESC
            """;

        return jdbcTemplate.query(sql, this::mapCredentialInfo, userId);
    }

    /**
     * Check if user has any passkeys.
     */
    public boolean hasCredentials(Long userId) {
        String sql = """
            SELECT COUNT(*)
            FROM webauthn_user_credential c
            JOIN webauthn_user_entity wue ON c.user_entity_id = wue.id
            WHERE wue.user_account_id = ? AND c.enabled = true
            """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count != null && count > 0;
    }

    /**
     * Count enabled credentials for user (used for last-credential protection).
     */
    public long countEnabledCredentials(Long userId) {
        String sql = """
            SELECT COUNT(*)
            FROM webauthn_user_credential c
            JOIN webauthn_user_entity wue ON c.user_entity_id = wue.id
            WHERE wue.user_account_id = ? AND c.enabled = true
            """;

        Long count = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return count != null ? count : 0L;
    }

    /**
     * Rename a credential.
     */
    @Transactional
    public int renameCredential(String credentialId, String newLabel, Long userId) {
        String sql = """
            UPDATE webauthn_user_credential c
            SET c.label = ?
            WHERE c.id = ?
            AND EXISTS (
                SELECT 1 FROM webauthn_user_entity wue
                WHERE wue.id = c.user_entity_id
                AND wue.user_account_id = ?
            )
            """;

        int updated = jdbcTemplate.update(sql, newLabel, credentialId, userId);
        if (updated > 0) {
            log.info("Renamed credential {} to '{}' for user {}", credentialId, newLabel, userId);
        }
        return updated;
    }

    /**
     * Delete (disable) a credential.
     */
    @Transactional
    public int deleteCredential(String credentialId, Long userId) {
        String sql = """
            UPDATE webauthn_user_credential c
            SET c.enabled = false
            WHERE c.id = ?
            AND EXISTS (
                SELECT 1 FROM webauthn_user_entity wue
                WHERE wue.id = c.user_entity_id
                AND wue.user_account_id = ?
            )
            """;

        int updated = jdbcTemplate.update(sql, credentialId, userId);
        if (updated > 0) {
            log.info("Disabled credential {} for user {}", credentialId, userId);
        }
        return updated;
    }

    /**
     * Map ResultSet to WebAuthnCredentialInfo.
     */
    private WebAuthnCredentialInfo mapCredentialInfo(ResultSet rs, int rowNum)
            throws SQLException {
        return WebAuthnCredentialInfo.builder()
            .id(rs.getString("id"))
            .label(rs.getString("label"))
            .created(rs.getTimestamp("created").toInstant())
            .lastUsed(rs.getTimestamp("last_used") != null ?
                rs.getTimestamp("last_used").toInstant() : null)
            .transports(rs.getString("transports"))
            .backupEligible(rs.getBoolean("backup_eligible"))
            .backupState(rs.getBoolean("backup_state"))
            .enabled(rs.getBoolean("enabled"))
            .build();
    }
}
```

---

## Service Layer

### WebAuthnCredentialManagementService.java

Service for credential management operations (list, rename, delete).

```java
package com.digitalsanctuary.spring.user.service;

import com.digitalsanctuary.spring.user.dto.WebAuthnCredentialInfo;
import com.digitalsanctuary.spring.user.exception.WebAuthnException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnCredentialQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing WebAuthn credentials.
 * Handles credential listing, renaming, and deletion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebAuthnCredentialManagementService {

    private final WebAuthnCredentialQueryRepository credentialQueryRepository;

    /**
     * Get all credentials for a user.
     */
    public List<WebAuthnCredentialInfo> getUserCredentials(User user) {
        return credentialQueryRepository.findCredentialsByUserId(user.getId());
    }

    /**
     * Check if user has any passkeys.
     */
    public boolean hasCredentials(User user) {
        return credentialQueryRepository.hasCredentials(user.getId());
    }

    /**
     * Rename a credential.
     */
    @Transactional
    public void renameCredential(String credentialId, String newLabel, User user)
            throws WebAuthnException {
        validateLabel(newLabel);

        int updated = credentialQueryRepository.renameCredential(
            credentialId, newLabel, user.getId());

        if (updated == 0) {
            throw new WebAuthnException("Credential not found or access denied");
        }

        log.info("User {} renamed credential {}", user.getEmail(), credentialId);
    }

    /**
     * Delete a credential with last-credential protection.
     */
    @Transactional
    public void deleteCredential(String credentialId, User user) throws WebAuthnException {
        // Check if this is the last credential and user has no password
        long enabledCount = credentialQueryRepository.countEnabledCredentials(user.getId());

        if (enabledCount == 1 && (user.getPassword() == null || user.getPassword().isEmpty())) {
            throw new WebAuthnException(
                "Cannot delete last passkey. User would be locked out. " +
                "Please add a password or another passkey first."
            );
        }

        int updated = credentialQueryRepository.deleteCredential(credentialId, user.getId());

        if (updated == 0) {
            throw new WebAuthnException("Credential not found or access denied");
        }

        log.info("User {} deleted credential {}", user.getEmail(), credentialId);
    }

    /**
     * Validate credential label.
     */
    private void validateLabel(String label) throws WebAuthnException {
        if (label == null || label.trim().isEmpty()) {
            throw new WebAuthnException("Credential label cannot be empty");
        }
        if (label.length() > 255) {
            throw new WebAuthnException("Credential label too long (max 255 characters)");
        }
    }
}
```

### WebAuthnException.java

Custom exception for WebAuthn operations.

```java
package com.digitalsanctuary.spring.user.exception;

/**
 * Exception thrown for WebAuthn-related errors.
 */
public class WebAuthnException extends Exception {

    public WebAuthnException(String message) {
        super(message);
    }

    public WebAuthnException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

## Security Configuration

### WebSecurityConfig.java

Update security configuration to enable WebAuthn.

```java
package com.digitalsanctuary.spring.user.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.webauthn.management.UserCredentialRepository;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Spring Security configuration with WebAuthn support.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(WebAuthnConfigProperties.class)
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final WebAuthnConfigProperties webAuthnProperties;
    private final UserCredentialRepository userCredentialRepository;
    private final PublicKeyCredentialUserEntityRepository userEntityRepository;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Authorization rules
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints
                .requestMatchers(
                    "/",
                    "/login",
                    "/register",
                    "/user/registration",
                    "/user/resetPassword",
                    "/user/savePassword",
                    "/error",
                    "/css/**",
                    "/js/**",
                    "/images/**"
                ).permitAll()

                // WebAuthn authentication endpoints (public for login)
                .requestMatchers(
                    "/webauthn/authenticate/**",
                    "/login/webauthn"
                ).permitAll()

                // WebAuthn registration endpoints (require authentication)
                .requestMatchers(
                    "/webauthn/register/**"
                ).authenticated()

                // All other requests require authentication
                .anyRequest().authenticated()
            )

            // Traditional form login
            .formLogin(form -> form
                .loginPage("/user/login")
                .defaultSuccessUrl("/dashboard")
                .permitAll()
            )

            // OAuth2 login (if enabled)
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/user/login")
                .defaultSuccessUrl("/dashboard")
            )

            // WebAuthn (Passkey) support
            .webAuthn(webAuthn -> webAuthn
                .rpName(webAuthnProperties.getRpName())
                .rpId(webAuthnProperties.getRpId())
                .allowedOrigins(webAuthnProperties.getAllowedOrigins())
                // Wire in our repositories
                .userCredentialRepository(userCredentialRepository)
                .userEntityRepository(userEntityRepository)
            )

            // Logout
            .logout(logout -> logout
                .logoutUrl("/user/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }
}
```

### WebAuthnConfigProperties.java

Configuration properties for WebAuthn.

```java
package com.digitalsanctuary.spring.user.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * Configuration properties for WebAuthn.
 */
@Data
@ConfigurationProperties(prefix = "user.webauthn")
public class WebAuthnConfigProperties {

    /**
     * Relying Party ID (your domain).
     * Example: "example.com" or "localhost" for development.
     */
    private String rpId = "localhost";

    /**
     * Relying Party Name (display name shown to users).
     */
    private String rpName = "Spring User Framework";

    /**
     * Allowed origins for WebAuthn operations.
     * Must match the origin of your web application.
     */
    private Set<String> allowedOrigins = Set.of("https://localhost:8443");
}
```

---

## API Endpoints

### Spring Security Built-in Endpoints

Spring Security automatically provides these endpoints:

| Endpoint | Method | Purpose | Auth Required | CSRF Required |
|----------|--------|---------|---------------|---------------|
| `/webauthn/register/options` | POST | Get registration challenge | Yes | Yes |
| `/webauthn/register` | POST | Complete passkey registration | Yes | Yes |
| `/webauthn/authenticate/options` | POST | Get authentication challenge | No | Yes |
| `/login/webauthn` | POST | Complete passkey authentication | No | Yes |
| `/login` | GET | Default login page with passkey UI | No | No |

**You don't need to implement these** - Spring Security handles them automatically!

### Custom Credential Management Endpoints

WebAuthnManagementAPI.java - REST controller for credential management.

```java
package com.digitalsanctuary.spring.user.api;

import com.digitalsanctuary.spring.user.dto.GenericResponseDTO;
import com.digitalsanctuary.spring.user.dto.WebAuthnCredentialInfo;
import com.digitalsanctuary.spring.user.exception.WebAuthnException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.service.WebAuthnCredentialManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * REST API for WebAuthn credential management.
 */
@RestController
@RequestMapping("/user/webauthn")
@RequiredArgsConstructor
@Slf4j
public class WebAuthnManagementAPI {

    private final WebAuthnCredentialManagementService credentialManagementService;
    private final UserService userService;

    /**
     * Get user's registered passkeys.
     *
     * GET /user/webauthn/credentials
     */
    @GetMapping("/credentials")
    public ResponseEntity<List<WebAuthnCredentialInfo>> getCredentials(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findUserByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));

        List<WebAuthnCredentialInfo> credentials =
            credentialManagementService.getUserCredentials(user);

        return ResponseEntity.ok(credentials);
    }

    /**
     * Check if user has any passkeys.
     *
     * GET /user/webauthn/has-credentials
     */
    @GetMapping("/has-credentials")
    public ResponseEntity<Boolean> hasCredentials(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userService.findUserByEmail(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));

        boolean hasCredentials = credentialManagementService.hasCredentials(user);

        return ResponseEntity.ok(hasCredentials);
    }

    /**
     * Rename a passkey.
     *
     * PUT /user/webauthn/credentials/{id}/label
     */
    @PutMapping("/credentials/{id}/label")
    public ResponseEntity<GenericResponseDTO> renameCredential(
            @PathVariable String id,
            @RequestBody @Valid RenameCredentialRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            User user = userService.findUserByEmail(userDetails.getUsername())
                .orElseThrow(() -> new WebAuthnException("User not found"));

            credentialManagementService.renameCredential(id, request.label(), user);

            return ResponseEntity.ok(new GenericResponseDTO(
                "Passkey renamed successfully"
            ));

        } catch (WebAuthnException e) {
            log.error("Failed to rename credential: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(new GenericResponseDTO(e.getMessage()));
        }
    }

    /**
     * Delete a passkey.
     *
     * DELETE /user/webauthn/credentials/{id}
     */
    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<GenericResponseDTO> deleteCredential(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            User user = userService.findUserByEmail(userDetails.getUsername())
                .orElseThrow(() -> new WebAuthnException("User not found"));

            credentialManagementService.deleteCredential(id, user);

            return ResponseEntity.ok(new GenericResponseDTO(
                "Passkey deleted successfully"
            ));

        } catch (WebAuthnException e) {
            log.error("Failed to delete credential: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(new GenericResponseDTO(e.getMessage()));
        }
    }

    /**
     * Request DTO for renaming credential.
     */
    public record RenameCredentialRequest(@NotBlank String label) {}
}
```

---

## Frontend Integration

### Correct Endpoint Paths

**IMPORTANT:** Use the correct Spring Security endpoint paths:

**Registration:**
1. `POST /webauthn/register/options` - Get challenge
2. `POST /webauthn/register` - Submit credential

**Authentication:**
1. `POST /webauthn/authenticate/options` - Get challenge
2. `POST /login/webauthn` - Submit assertion

### JavaScript WebAuthn Integration

#### Registration Flow

```javascript
/**
 * Register a new passkey for authenticated user.
 * User must be already logged in!
 */
async function registerPasskey(credentialName = "My Passkey") {
    try {
        // 1. Request registration options (challenge) from Spring Security
        const optionsResponse = await fetch('/webauthn/register/options', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': getCsrfToken()  // Required!
            }
        });

        if (!optionsResponse.ok) {
            throw new Error('Failed to start registration');
        }

        const options = await optionsResponse.json();

        // 2. Convert base64url to ArrayBuffer
        options.challenge = base64urlToBuffer(options.challenge);
        options.user.id = base64urlToBuffer(options.user.id);

        if (options.excludeCredentials) {
            options.excludeCredentials = options.excludeCredentials.map(cred => ({
                ...cred,
                id: base64urlToBuffer(cred.id)
            }));
        }

        // 3. Call browser WebAuthn API
        const credential = await navigator.credentials.create({
            publicKey: options
        });

        if (!credential) {
            throw new Error('No credential returned from authenticator');
        }

        // 4. Convert credential to JSON for transmission
        const credentialJSON = {
            id: credential.id,
            rawId: bufferToBase64url(credential.rawId),
            type: credential.type,
            response: {
                clientDataJSON: bufferToBase64url(credential.response.clientDataJSON),
                attestationObject: bufferToBase64url(credential.response.attestationObject),
                transports: credential.response.getTransports?.() || []
            },
            clientExtensionResults: credential.getClientExtensionResults()
        };

        // 5. Send credential to Spring Security
        const finishResponse = await fetch('/webauthn/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': getCsrfToken()
            },
            body: JSON.stringify(credentialJSON)
        });

        if (!finishResponse.ok) {
            const error = await finishResponse.text();
            throw new Error(error || 'Registration failed');
        }

        // 6. Optionally set a friendly name
        if (credentialName && credentialName !== "My Passkey") {
            await setCredentialLabel(credential.id, credentialName);
        }

        alert('Passkey registered successfully!');
        location.reload();  // Refresh to show new passkey

    } catch (error) {
        console.error('Registration error:', error);
        alert('Failed to register passkey: ' + error.message);
    }
}

/**
 * Set friendly name for credential after registration.
 */
async function setCredentialLabel(credentialId, label) {
    await fetch(`/user/webauthn/credentials/${credentialId}/label`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': getCsrfToken()
        },
        body: JSON.stringify({ label })
    });
}
```

#### Authentication Flow

```javascript
/**
 * Authenticate with passkey (login).
 */
async function authenticateWithPasskey(username) {
    try {
        // 1. Request authentication options (challenge) from Spring Security
        const optionsResponse = await fetch('/webauthn/authenticate/options', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': getCsrfToken()
            },
            body: JSON.stringify({ username })
        });

        if (!optionsResponse.ok) {
            throw new Error('Failed to start authentication');
        }

        const options = await optionsResponse.json();

        // 2. Convert base64url to ArrayBuffer
        options.challenge = base64urlToBuffer(options.challenge);

        if (options.allowCredentials) {
            options.allowCredentials = options.allowCredentials.map(cred => ({
                ...cred,
                id: base64urlToBuffer(cred.id)
            }));
        }

        // 3. Call browser WebAuthn API
        const assertion = await navigator.credentials.get({
            publicKey: options
        });

        if (!assertion) {
            throw new Error('No assertion returned from authenticator');
        }

        // 4. Convert assertion to JSON for transmission
        const assertionJSON = {
            id: assertion.id,
            rawId: bufferToBase64url(assertion.rawId),
            type: assertion.type,
            response: {
                clientDataJSON: bufferToBase64url(assertion.response.clientDataJSON),
                authenticatorData: bufferToBase64url(assertion.response.authenticatorData),
                signature: bufferToBase64url(assertion.response.signature),
                userHandle: assertion.response.userHandle ?
                    bufferToBase64url(assertion.response.userHandle) : null
            },
            clientExtensionResults: assertion.getClientExtensionResults()
        };

        // 5. Send assertion to Spring Security
        const finishResponse = await fetch('/login/webauthn', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': getCsrfToken()
            },
            body: JSON.stringify(assertionJSON)
        });

        if (!finishResponse.ok) {
            const error = await finishResponse.text();
            throw new Error(error || 'Authentication failed');
        }

        // 6. Redirect to dashboard
        window.location.href = '/dashboard';

    } catch (error) {
        console.error('Authentication error:', error);
        alert('Failed to authenticate: ' + error.message);
    }
}

/**
 * Usernameless authentication (discoverable credentials).
 * Requires resident key support.
 */
async function authenticateUsernameless() {
    try {
        // Similar to above but without username in request body
        const optionsResponse = await fetch('/webauthn/authenticate/options', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': getCsrfToken()
            }
            // No body - usernameless
        });

        if (!optionsResponse.ok) {
            throw new Error('Failed to start usernameless authentication');
        }

        const options = await optionsResponse.json();

        // Convert challenge
        options.challenge = base64urlToBuffer(options.challenge);

        // Note: allowCredentials should be empty for usernameless
        const assertion = await navigator.credentials.get({
            publicKey: options,
            mediation: 'conditional'  // Browser autofill UI
        });

        if (!assertion) {
            throw new Error('No assertion returned');
        }

        // Convert and submit (same as above)
        const assertionJSON = {
            id: assertion.id,
            rawId: bufferToBase64url(assertion.rawId),
            type: assertion.type,
            response: {
                clientDataJSON: bufferToBase64url(assertion.response.clientDataJSON),
                authenticatorData: bufferToBase64url(assertion.response.authenticatorData),
                signature: bufferToBase64url(assertion.response.signature),
                userHandle: assertion.response.userHandle ?
                    bufferToBase64url(assertion.response.userHandle) : null
            }
        };

        const finishResponse = await fetch('/login/webauthn', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': getCsrfToken()
            },
            body: JSON.stringify(assertionJSON)
        });

        if (!finishResponse.ok) {
            throw new Error('Authentication failed');
        }

        window.location.href = '/dashboard';

    } catch (error) {
        console.error('Usernameless authentication error:', error);
        alert('Failed to authenticate: ' + error.message);
    }
}
```

#### Utility Functions

```javascript
/**
 * Convert base64url string to ArrayBuffer.
 */
function base64urlToBuffer(base64url) {
    const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
    const padLen = (4 - (base64.length % 4)) % 4;
    const padded = base64 + '='.repeat(padLen);
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
}

/**
 * Convert ArrayBuffer to base64url string.
 */
function bufferToBase64url(buffer) {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (const byte of bytes) {
        binary += String.fromCharCode(byte);
    }
    const base64 = btoa(binary);
    return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

/**
 * Get CSRF token from meta tag or cookie.
 */
function getCsrfToken() {
    // Try meta tag first
    const meta = document.querySelector('meta[name="_csrf"]');
    if (meta) {
        return meta.getAttribute('content');
    }

    // Try cookie
    const cookie = document.cookie.split('; ')
        .find(row => row.startsWith('XSRF-TOKEN='));
    if (cookie) {
        return cookie.split('=')[1];
    }

    console.warn('CSRF token not found');
    return '';
}

/**
 * Check if WebAuthn is supported in this browser.
 */
function isWebAuthnSupported() {
    return window.PublicKeyCredential !== undefined &&
           navigator.credentials !== undefined;
}

/**
 * Check if platform authenticator is available (TouchID, FaceID, Windows Hello).
 */
async function isPlatformAuthenticatorAvailable() {
    if (!isWebAuthnSupported()) {
        return false;
    }
    return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
}
```

#### Credential Management UI

```javascript
/**
 * Load and display user's passkeys.
 */
async function loadPasskeys() {
    try {
        const response = await fetch('/user/webauthn/credentials', {
            headers: {
                'X-CSRF-TOKEN': getCsrfToken()
            }
        });

        if (!response.ok) {
            throw new Error('Failed to load passkeys');
        }

        const credentials = await response.json();
        displayCredentials(credentials);

    } catch (error) {
        console.error('Failed to load passkeys:', error);
        alert('Failed to load passkeys: ' + error.message);
    }
}

/**
 * Display credentials in UI.
 */
function displayCredentials(credentials) {
    const container = document.getElementById('passkeys-list');

    if (credentials.length === 0) {
        container.innerHTML = '<p>No passkeys registered. <a href="#" onclick="registerPasskey()">Add your first passkey</a></p>';
        return;
    }

    container.innerHTML = credentials.map(cred => `
        <div class="passkey-item" data-id="${escapeHtml(cred.id)}">
            <div class="passkey-info">
                <strong>${escapeHtml(cred.label || 'Unnamed Passkey')}</strong>
                <small>Created: ${new Date(cred.created).toLocaleDateString()}</small>
                ${cred.lastUsed ?
                    `<small>Last used: ${new Date(cred.lastUsed).toLocaleDateString()}</small>`
                    : '<small>Never used</small>'}
                ${cred.backupEligible ?
                    '<span class="badge badge-success">Synced</span>' :
                    '<span class="badge badge-warning">Device-bound</span>'}
                ${!cred.enabled ?
                    '<span class="badge badge-danger">Disabled</span>' : ''}
            </div>
            <div class="passkey-actions">
                <button class="btn btn-sm btn-secondary" onclick="renamePasskey('${escapeHtml(cred.id)}')">
                    Rename
                </button>
                <button class="btn btn-sm btn-danger" onclick="deletePasskey('${escapeHtml(cred.id)}')">
                    Delete
                </button>
            </div>
        </div>
    `).join('');
}

/**
 * Rename a passkey.
 */
async function renamePasskey(credentialId) {
    const newLabel = prompt('Enter new name for this passkey:');
    if (!newLabel) return;

    try {
        const response = await fetch(`/user/webauthn/credentials/${credentialId}/label`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': getCsrfToken()
            },
            body: JSON.stringify({ label: newLabel })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to rename passkey');
        }

        alert('Passkey renamed successfully');
        loadPasskeys();  // Reload list

    } catch (error) {
        console.error('Failed to rename passkey:', error);
        alert('Failed to rename passkey: ' + error.message);
    }
}

/**
 * Delete a passkey with confirmation.
 */
async function deletePasskey(credentialId) {
    if (!confirm('Are you sure you want to delete this passkey? This action cannot be undone.')) {
        return;
    }

    try {
        const response = await fetch(`/user/webauthn/credentials/${credentialId}`, {
            method: 'DELETE',
            headers: {
                'X-CSRF-TOKEN': getCsrfToken()
            }
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to delete passkey');
        }

        alert('Passkey deleted successfully');
        loadPasskeys();  // Reload list

    } catch (error) {
        console.error('Failed to delete passkey:', error);
        alert('Failed to delete passkey: ' + error.message);
    }
}

/**
 * Escape HTML to prevent XSS.
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', async function() {
    // Check WebAuthn support
    if (!isWebAuthnSupported()) {
        console.warn('WebAuthn not supported in this browser');
        document.getElementById('passkey-warning')?.classList.remove('d-none');
        return;
    }

    // Check for platform authenticator
    const hasPlatformAuth = await isPlatformAuthenticatorAvailable();
    if (hasPlatformAuth) {
        console.log('Platform authenticator available (TouchID/FaceID/Windows Hello)');
    }

    // Load user's passkeys if on settings page
    if (document.getElementById('passkeys-list')) {
        loadPasskeys();
    }
});
```

---

## Dependencies

### build.gradle

**CRITICAL:** Add webauthn4j-core dependency!

```gradle
plugins {
    id 'org.springframework.boot' version '3.5.7'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'java'
}

group = 'com.digitalsanctuary'
version = '1.0.0'
sourceCompatibility = '21'

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot starters
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'

    // WebAuthn support - REQUIRED!
    // Spring Security 6.5 includes WebAuthn support but requires this library
    implementation 'com.webauthn4j:webauthn4j-core:0.29.7.RELEASE'

    // Database
    runtimeOnly 'com.h2database:h2'
    runtimeOnly 'org.postgresql:postgresql'
    runtimeOnly 'com.mysql:mysql-connector-j'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

**Note:** The `webauthn4j-core` dependency is **required** even though Spring Security 6.5 has native WebAuthn support. Spring Security uses WebAuthn4J internally.

---

## Configuration Properties

### application.properties

Add WebAuthn configuration and HTTPS setup.

```properties
# ==================== WebAuthn Configuration ====================

# Relying Party ID (your domain)
# For production: use your actual domain (e.g., "example.com")
# For development: use "localhost"
user.webauthn.rpId=localhost

# Relying Party Name (display name shown to users)
user.webauthn.rpName=Spring User Framework

# Allowed origins for WebAuthn operations
# Must match your application's origin exactly (including port)
# For production: https://example.com
# For development: https://localhost:8443
user.webauthn.allowedOrigins=https://localhost:8443

# ==================== HTTPS Configuration (REQUIRED) ====================

# WebAuthn REQUIRES HTTPS (browser-enforced security requirement)
# Browsers will NOT allow WebAuthn on HTTP except for localhost

# For development: Generate self-signed certificate
# Command: keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 \
#          -storetype PKCS12 -keystore src/main/resources/keystore.p12 \
#          -validity 3650 -dname "CN=localhost" \
#          -storepass changeit -keypass changeit

server.port=8443
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=localhost

# For production: Use proper SSL certificate from Let's Encrypt or commercial CA
# server.ssl.key-store=file:/path/to/production-cert.p12
# server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}

# ==================== Security Configuration ====================

# Enable Spring Security debugging (development only)
# logging.level.org.springframework.security=DEBUG

# CSRF protection (required for WebAuthn)
spring.security.csrf.enabled=true

# Session configuration
server.servlet.session.timeout=30m
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.same-site=strict
```

### Generate Development Certificate

```bash
# Run this command to generate self-signed certificate for development
keytool -genkeypair \
  -alias localhost \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore src/main/resources/keystore.p12 \
  -validity 3650 \
  -dname "CN=localhost,OU=Development,O=Spring User Framework,L=City,ST=State,C=US" \
  -storepass changeit \
  -keypass changeit
```

---

## Testing Strategy

### Unit Tests

#### WebAuthnCredentialManagementServiceTest.java

```java
package com.digitalsanctuary.spring.user.service;

import com.digitalsanctuary.spring.user.dto.WebAuthnCredentialInfo;
import com.digitalsanctuary.spring.user.exception.WebAuthnException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.WebAuthnCredentialQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebAuthnCredentialManagementServiceTest {

    @Mock
    private WebAuthnCredentialQueryRepository credentialQueryRepository;

    @InjectMocks
    private WebAuthnCredentialManagementService service;

    @Test
    void testGetUserCredentials() {
        User user = createTestUser();
        WebAuthnCredentialInfo cred = WebAuthnCredentialInfo.builder()
            .id("cred-123")
            .label("My Passkey")
            .enabled(true)
            .created(Instant.now())
            .build();

        when(credentialQueryRepository.findCredentialsByUserId(user.getId()))
            .thenReturn(List.of(cred));

        List<WebAuthnCredentialInfo> credentials = service.getUserCredentials(user);

        assertThat(credentials).hasSize(1);
        assertThat(credentials.get(0).getLabel()).isEqualTo("My Passkey");
    }

    @Test
    void testCannotDeleteLastPasskeyWithoutPassword() {
        User user = createTestUser();
        user.setPassword(null);  // No password

        when(credentialQueryRepository.countEnabledCredentials(user.getId()))
            .thenReturn(1L);

        assertThatThrownBy(() -> service.deleteCredential("cred-123", user))
            .isInstanceOf(WebAuthnException.class)
            .hasMessageContaining("Cannot delete last passkey");

        // Verify no deletion attempt was made
        verify(credentialQueryRepository, never()).deleteCredential(anyString(), anyLong());
    }

    @Test
    void testDeleteCredentialWithMultiplePasskeys() throws WebAuthnException {
        User user = createTestUser();

        when(credentialQueryRepository.countEnabledCredentials(user.getId()))
            .thenReturn(2L);  // Has 2 passkeys
        when(credentialQueryRepository.deleteCredential("cred-123", user.getId()))
            .thenReturn(1);

        service.deleteCredential("cred-123", user);

        verify(credentialQueryRepository).deleteCredential("cred-123", user.getId());
    }

    @Test
    void testRenameCredentialSuccess() throws WebAuthnException {
        User user = createTestUser();

        when(credentialQueryRepository.renameCredential("cred-123", "New Name", user.getId()))
            .thenReturn(1);

        service.renameCredential("cred-123", "New Name", user);

        verify(credentialQueryRepository).renameCredential("cred-123", "New Name", user.getId());
    }

    @Test
    void testRenameCredentialNotFound() {
        User user = createTestUser();

        when(credentialQueryRepository.renameCredential("cred-999", "New Name", user.getId()))
            .thenReturn(0);  // No rows updated

        assertThatThrownBy(() -> service.renameCredential("cred-999", "New Name", user))
            .isInstanceOf(WebAuthnException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void testValidateLabelEmpty() {
        User user = createTestUser();

        assertThatThrownBy(() -> service.renameCredential("cred-123", "", user))
            .isInstanceOf(WebAuthnException.class)
            .hasMessageContaining("cannot be empty");
    }

    @Test
    void testValidateLabelTooLong() {
        User user = createTestUser();
        String longLabel = "a".repeat(256);

        assertThatThrownBy(() -> service.renameCredential("cred-123", longLabel, user))
            .isInstanceOf(WebAuthnException.class)
            .hasMessageContaining("too long");
    }

    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPassword("password");
        return user;
    }
}
```

### Integration Tests

#### WebAuthnIntegrationTest.java

```java
package com.digitalsanctuary.spring.user.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WebAuthnIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "test@example.com")
    void testRegistrationOptionsRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/webauthn/register/options"))
            .andExpect(status().isOk());  // Authenticated user can request options
    }

    @Test
    void testRegistrationOptionsWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/webauthn/register/options"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testAuthenticationOptionsIsPublic() throws Exception {
        // Authentication options endpoint should be public (for login)
        mockMvc.perform(post("/webauthn/authenticate/options")
                .contentType("application/json")
                .content("{\"username\":\"test@example.com\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void testGetCredentialsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/user/webauthn/credentials"))
            .andExpect(status().isOk());
    }

    @Test
    void testGetCredentialsWithoutAuthReturns401() throws Exception {
        mockMvc.perform(get("/user/webauthn/credentials"))
            .andExpect(status().isUnauthorized());
    }
}
```

### End-to-End Tests

For full E2E testing with actual WebAuthn, use Spring Security's testing support or browser automation with virtual authenticators.

```java
package com.digitalsanctuary.spring.user.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebAuthnEndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testWebAuthnEndpointsAreAccessible() {
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/webauthn/authenticate/options",
            "{\"username\":\"test@example.com\"}",
            String.class
        );

        // Should return 200 or 4xx (bad request), not 404 (not found)
        assertThat(response.getStatusCode()).isIn(
            HttpStatus.OK,
            HttpStatus.BAD_REQUEST,
            HttpStatus.UNAUTHORIZED
        );
    }

    @Test
    void testLoginEndpointExists() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/login",
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

---

## Migration & Rollout Plan

### Timeline: 6-7 Weeks

**Week 1: Database & Configuration**
- ✅ Create database migration script (V1_1__add_webauthn_support.sql)
- ✅ Run migration on dev environment
- ✅ Run migration on staging environment
- ✅ Configure HTTPS for development (self-signed cert)
- ✅ Add webauthn4j-core dependency to build.gradle
- ✅ Add WebAuthn configuration properties
- ✅ Verify application starts successfully

**Week 2: Backend Development - Repositories**
- ✅ Configure Spring Security's built-in JDBC repositories
- ✅ Implement WebAuthnUserEntityBridge for User integration
- ✅ Implement WebAuthnCredentialQueryRepository
- ✅ Write unit tests for repositories
- ✅ Test anonymousUser edge cases

**Week 3: Backend Development - Services & Security**
- ✅ Implement WebAuthnCredentialManagementService
- ✅ Create WebAuthnManagementAPI controller
- ✅ Update WebSecurityConfig with .webAuthn()
- ✅ Add WebAuthnException class
- ✅ Write integration tests
- ✅ Verify signature_count updates correctly

**Week 4: Frontend Development - Registration**
- ✅ Add "Register Passkey" button to account settings
- ✅ Implement JavaScript registration flow
- ✅ Use correct endpoints (/webauthn/register/options, /webauthn/register)
- ✅ Test on Chrome, Safari, Firefox
- ✅ Add credential list UI
- ✅ Test with multiple authenticators (TouchID, YubiKey)

**Week 5: Frontend Development - Authentication**
- ✅ Add "Sign in with Passkey" button to login page
- ✅ Implement JavaScript authentication flow
- ✅ Use correct endpoint (/login/webauthn)
- ✅ Add credential management UI (rename, delete)
- ✅ Test usernameless authentication
- ✅ Handle error cases gracefully

**Week 6: Testing & QA**
- ✅ Cross-browser testing (Chrome, Safari, Firefox, Edge)
- ✅ Test multiple authenticator types
  - Platform: TouchID, FaceID, Windows Hello
  - Cross-platform: YubiKey, Google Titan
  - Synced: iCloud Keychain, Google Password Manager
- ✅ Security testing (HTTPS, CSRF, replay attacks)
- ✅ Performance testing
- ✅ Verify signature counter anti-cloning
- ✅ Test account lockout prevention
- ✅ Bug fixes

**Week 7: Beta & Deployment**
- ✅ Deploy to staging with production SSL certificate
- ✅ Beta release to test users (feature flag)
- ✅ Monitor metrics:
  - Registration success rate
  - Authentication success rate
  - Error rates
  - Performance metrics
- ✅ Gather user feedback
- ✅ Fix critical issues
- ✅ Create user documentation
- ✅ General availability release

### Success Criteria

| Metric | Target |
|--------|--------|
| Registration Success Rate | > 95% |
| Authentication Success Rate | > 98% |
| Browser Compatibility | Chrome, Safari, Firefox, Edge latest versions |
| Performance | < 2s registration, < 1s authentication |
| Error Rate | < 2% |
| User Satisfaction | Positive feedback from beta users |

---

## Security Considerations

### 1. HTTPS Requirement (CRITICAL)

**WebAuthn requires HTTPS.** Browsers enforce this (except for localhost in development).

**Development:**
```bash
# Generate self-signed certificate
keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore keystore.p12 -validity 3650 \
  -dname "CN=localhost" -storepass changeit
```

**Production:**
- Use Let's Encrypt or commercial SSL certificate
- Configure TLS 1.2+ with strong ciphers
- Enable HSTS headers: `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- Implement Certificate Pinning (optional)

### 2. Signature Counter (Anti-Cloning)

Spring Security's `JdbcUserCredentialRepository` automatically updates `signature_count` after each successful authentication.

**How it works:**
- Counter must always increase
- If counter decreases, authenticator may be cloned
- Spring Security rejects authentication if counter doesn't increase

**Verification:**
```sql
-- Check signature counter updates
SELECT credential_id, signature_count, last_used
FROM webauthn_user_credential
WHERE user_entity_id = ?
ORDER BY last_used DESC;
```

### 3. Challenge Management

Spring Security handles:
- ✅ Cryptographically secure random challenges (32+ bytes)
- ✅ Challenge timeout (default 5 minutes)
- ✅ One-time use (prevents replay attacks)
- ✅ Stored in HTTP session by default

**For stateless applications:**
Implement custom `PublicKeyCredentialCreationOptionsRepository` for distributed challenge storage (Redis, database).

### 4. CSRF Protection

**REQUIRED:** Keep CSRF protection enabled for WebAuthn endpoints.

```java
// All WebAuthn endpoints require CSRF token
http.csrf(csrf -> csrf.disable());  // DON'T DO THIS!
```

Include CSRF token in all POST requests:
```javascript
headers: {
    'X-CSRF-TOKEN': getCsrfToken()
}
```

### 5. Account Lockout Prevention

Implemented in `WebAuthnCredentialManagementService`:

```java
// Prevent deletion of last passkey if user has no password
if (enabledCount == 1 && user.getPassword() == null) {
    throw new WebAuthnException("Cannot delete last passkey");
}
```

**Best Practices:**
- Encourage users to register multiple passkeys on different devices
- Maintain password as backup authentication method
- Provide account recovery mechanism (email reset)

### 6. Origin Validation

Configure `allowedOrigins` correctly:

```properties
# Must match your application's origin exactly
user.webauthn.allowedOrigins=https://example.com,https://www.example.com
```

**Browser enforces:**
- RP ID must match domain
- Origin must be in allowedOrigins
- HTTPS required (except localhost)

### 7. Pre-Authentication Requirement

**Current Limitation:** Users must be authenticated before registering passkeys.

**Security implications:**
- ✅ Prevents unauthorized passkey registration
- ✅ Ensures user identity before credential binding
- ❌ Cannot use for initial registration (passwordless onboarding)

**Mitigation:** Document clearly that passkeys are an *enhancement* to existing authentication.

---

## Spring Boot 4.0 Migration Path

When migrating to Spring Boot 4.0 in the future, the changes are minimal.

### Required Changes

1. **Jackson 2 → Jackson 3** (Package imports)
2. **Test Configuration** (Add `@AutoConfigureMockMvc`)
3. **Null-Safety** (Optional - Add JSpecify annotations)

### What Stays the Same

- ✅ WebAuthn configuration (`.webAuthn()` DSL)
- ✅ Repository implementations (JDBC repositories)
- ✅ Database schema
- ✅ Service layer
- ✅ Frontend JavaScript
- ✅ API endpoints
- ✅ Overall architecture

**Estimated Migration Time:** 1-2 weeks

See [PASSKEY-SPRINGBOOT4-MIGRATION.md](PASSKEY-SPRINGBOOT4-MIGRATION.md) for detailed migration guide.

---

## Future Enhancements

### Phase 2 Features (Post-MVP)

1. **Conditional UI (Autofill Passkeys)**
   - Show passkeys in browser autofill
   - Streamlined UX for returning users
   - Requires WebAuthn Level 3 support

2. **Passwordless Registration**
   - Allow new user registration with passkey only
   - Requires Spring Security enhancement or custom implementation
   - Higher development effort

3. **Advanced Analytics**
   - Passkey adoption metrics dashboard
   - Authentication success rates by authenticator type
   - Geographic distribution of passkey usage
   - Device distribution analysis

4. **Account Recovery**
   - Passkey-based account recovery mechanism
   - Trusted device management
   - Social recovery (trusted contacts)

5. **Admin Dashboard**
   - View users with passkeys
   - Revoke credentials remotely
   - Audit passkey usage
   - Compliance reporting

6. **Mobile App Integration**
   - Native iOS/Android passkey support
   - Cross-device authentication (QR code flow)
   - Platform-specific optimizations

7. **Enterprise Features**
   - Attestation verification
   - FIDO Metadata Service integration
   - Authenticator allowlist/blocklist
   - Enterprise attestation support

---

## References

### Official Documentation
- [Spring Security Passkeys Documentation](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html)
- [Spring Security 6.4 Release Notes](https://spring.io/blog/2024/11/19/spring-security-6-4-goes-ga/)
- [WebAuthn4J Spring Security Reference](https://webauthn4j.github.io/webauthn4j-spring-security/en/)
- [W3C WebAuthn Specification](https://www.w3.org/TR/webauthn-3/)
- [FIDO Alliance](https://fidoalliance.org/)

### Tutorials & Guides
- [Baeldung: Integrating Passkeys into Spring Security](https://www.baeldung.com/spring-security-integrate-passkeys)
- [devgem.io: Implementing Passkey Registration with Spring Security](https://www.devgem.io/posts/implementing-passkey-registration-and-authentication-with-spring-security-and-webauthn4j)
- [WebAuthn.io Demo](https://webauthn.io/)
- [Auth0: WebAuthn and Passkeys for Java Developers](https://auth0.com/blog/webauthn-and-passkeys-for-java-developers/)

### Browser Support
- [Can I Use: WebAuthn](https://caniuse.com/webauthn)
- Chrome 67+ (Windows, macOS, Android)
- Safari 13+ (macOS), 14+ (iOS)
- Firefox 60+ (Windows, macOS, Linux)
- Edge 18+ (Windows)

---

## Document Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-30 | Initial | First draft with Yubico approach |
| 2.0 | 2025-11-30 | Revision | Switched to Spring Security native |
| 2.1 | 2025-11-30 | Corrected | Fixed critical issues from review |

### Changes in Version 2.1

- ✅ Added `webauthn4j-core:0.29.7.RELEASE` dependency (CRITICAL)
- ✅ Fixed authentication endpoint: `/webauthn/authenticate` → `/login/webauthn`
- ✅ Fixed registration endpoint path: added `/webauthn/register/options`
- ✅ Added "Critical Requirements & Limitations" section
- ✅ Documented pre-authentication requirement prominently
- ✅ Added anonymousUser null checks in repository
- ✅ Documented signature counter behavior
- ✅ Updated to hybrid repository approach (built-in + custom)
- ✅ Fixed version consistency to Spring Boot 3.5.7
- ✅ Enhanced frontend JavaScript with correct endpoint paths
- ✅ Added CSRF token handling throughout
- ✅ Improved error handling and edge cases

---

## Conclusion

This implementation plan leverages **Spring Security 6.5's native WebAuthn support**, providing:

- ✅ **Production-ready** JDBC persistence
- ✅ **Official Spring Security** support and maintenance
- ✅ **Simpler architecture** without custom filters
- ✅ **Faster implementation** (6-7 weeks)
- ✅ **Future-proof** for Spring Boot 4.0 migration

**Key Limitation:** Pre-authentication required for registration. Passkeys are an *enhancement* to existing authentication, not a replacement for initial registration.

**Estimated Timeline:** 6-7 weeks from start to production

**Next Steps:**
1. ✅ Review and approve this corrected plan
2. ✅ Set up development environment with HTTPS
3. ✅ Begin Week 1: Database migration and dependency setup
4. ✅ Proceed through phased implementation

---

**Document Version:** 2.1 (Corrected - Native Spring Security Implementation)
**Last Updated:** 2025-11-30
**Status:** Ready for Implementation

---

**End of Document**
