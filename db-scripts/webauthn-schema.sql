-- =====================================================
-- Spring Security WebAuthn Schema for MariaDB/MySQL
-- =====================================================
-- This script creates tables for WebAuthn (Passkey) authentication support.
-- Compatible with MariaDB 10.3+ and MySQL 8.0+
--
-- Part of issue #153: Add Passkey Support for SpringUserFramework

-- Sequence structure
DROP SEQUENCE IF EXISTS `webauthn_user_entity_seq`;
CREATE SEQUENCE `webauthn_user_entity_seq` START WITH 1 INCREMENT BY 50 CACHE 1000 ENGINE=InnoDB;

-- =====================================================
-- Table: webauthn_user_entity
-- =====================================================
-- Maps Spring Security's WebAuthn user entity to application users.
-- This table links the WebAuthn authentication system to the existing user_account table.

DROP TABLE IF EXISTS `webauthn_user_entity`;
CREATE TABLE `webauthn_user_entity` (
  `id` BIGINT(20) NOT NULL,
  `name` VARCHAR(255) NOT NULL COMMENT 'User email (username)',
  `user_id` BLOB NOT NULL COMMENT 'Base64-encoded User ID',
  `display_name` VARCHAR(255) NOT NULL COMMENT 'User full name for display',
  `user_account_id` BIGINT(20) DEFAULT NULL COMMENT 'FK to user_account table',
  `created_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_webauthn_user_entity_name` (`name`),
  UNIQUE KEY `uk_webauthn_user_entity_user_id` (`user_id`(255)),
  KEY `idx_webauthn_user_entity_name` (`name`),
  KEY `idx_webauthn_user_account_id` (`user_account_id`),
  CONSTRAINT `fk_webauthn_user_account`
    FOREIGN KEY (`user_account_id`)
    REFERENCES `user_account` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
COMMENT='WebAuthn user entities mapped to application users';

-- =====================================================
-- Table: webauthn_user_credential
-- =====================================================
-- Stores WebAuthn credentials (public keys) for users.
-- Each user can have multiple credentials (e.g., phone, security key, laptop).

DROP TABLE IF EXISTS `webauthn_user_credential`;
CREATE TABLE `webauthn_user_credential` (
  `id` VARCHAR(255) NOT NULL COMMENT 'Primary key (UUID string)',
  `user_entity_id` BIGINT(20) NOT NULL COMMENT 'FK to webauthn_user_entity',
  `credential_id` BLOB NOT NULL COMMENT 'Base64-encoded credential ID from authenticator',
  `public_key` BLOB NOT NULL COMMENT 'COSE-encoded public key',
  `signature_count` BIGINT(20) NOT NULL DEFAULT 0 COMMENT 'Counter to detect cloned authenticators',
  `uv_initialized` BIT(1) NOT NULL DEFAULT 0 COMMENT 'User verification performed during registration',
  `transports` VARCHAR(255) DEFAULT NULL COMMENT 'Supported transports: usb, nfc, ble, internal',
  `backup_eligible` BIT(1) DEFAULT 0 COMMENT 'Credential can be synced (iCloud Keychain, etc.)',
  `backup_state` BIT(1) DEFAULT 0 COMMENT 'Credential is currently backed up',
  `attestation_object` BLOB DEFAULT NULL COMMENT 'Attestation data from registration',
  `attestation_client_data_json` BLOB DEFAULT NULL COMMENT 'Client data JSON from registration',
  `label` VARCHAR(255) DEFAULT NULL COMMENT 'User-friendly name (e.g., "My iPhone", "YubiKey")',
  `created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_used` TIMESTAMP NULL DEFAULT NULL,
  `enabled` BIT(1) NOT NULL DEFAULT 1 COMMENT 'Soft delete flag',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_webauthn_credential_id` (`credential_id`(255)),
  KEY `idx_webauthn_credential_user_entity` (`user_entity_id`),
  KEY `idx_webauthn_credential_id` (`credential_id`(255)),
  KEY `idx_webauthn_credential_enabled` (`enabled`),
  KEY `idx_webauthn_credential_last_used` (`last_used`),
  KEY `idx_webauthn_credential_created` (`created`),
  CONSTRAINT `fk_webauthn_credential_user_entity`
    FOREIGN KEY (`user_entity_id`)
    REFERENCES `webauthn_user_entity` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
COMMENT='WebAuthn credentials (public keys) for passkey authentication';

-- =====================================================
-- Notes:
-- =====================================================
-- 1. The signature_count is automatically updated by Spring Security after each authentication
--    to prevent cloned authenticator detection
-- 2. The enabled flag allows soft deletion of credentials without removing from database
-- 3. The user_account_id link in webauthn_user_entity allows efficient queries between
--    the WebAuthn system and your existing User entity
-- 4. Credentials are automatically deleted when the associated user is deleted (CASCADE)
-- 5. BLOB fields store binary data (credential IDs, public keys) as base64-decoded bytes
