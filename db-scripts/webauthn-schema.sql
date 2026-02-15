-- WebAuthn (Passkey) schema for Spring Security 7.0.2
-- These table names and column names must match Spring Security's
-- JdbcPublicKeyCredentialUserEntityRepository and JdbcUserCredentialRepository defaults.
--
-- IMPORTANT: Run the application first so Hibernate creates the user_account table,
-- then run this script.

-- Table: user_entities
-- Links WebAuthn user handles to application users.
-- Spring Security expects: id, name, display_name
-- We add: user_account_id (FK to user_account for efficient joins)
CREATE TABLE IF NOT EXISTS `user_entities` (
  `id` VARCHAR(255) NOT NULL COMMENT 'Base64url-encoded WebAuthn user handle',
  `name` VARCHAR(255) NOT NULL COMMENT 'Username (email)',
  `display_name` VARCHAR(255) NOT NULL COMMENT 'User full name for display',
  `user_account_id` BIGINT(20) DEFAULT NULL COMMENT 'FK to user_account table (custom column)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_entities_name` (`name`),
  KEY `idx_user_entities_user_account_id` (`user_account_id`),
  CONSTRAINT `fk_user_entities_user_account`
    FOREIGN KEY (`user_account_id`)
    REFERENCES `user_account` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
COMMENT='WebAuthn user entities mapped to application users';

-- Table: user_credentials
-- Stores WebAuthn credentials (public keys) for passkey authentication.
-- All columns match Spring Security's expected schema.
CREATE TABLE IF NOT EXISTS `user_credentials` (
  `credential_id` BLOB NOT NULL COMMENT 'Raw credential ID from authenticator',
  `user_entity_user_id` VARCHAR(255) NOT NULL COMMENT 'FK to user_entities.id',
  `public_key` BLOB NOT NULL COMMENT 'COSE-encoded public key',
  `signature_count` BIGINT(20) NOT NULL DEFAULT 0 COMMENT 'Counter to detect cloned authenticators',
  `uv_initialized` BIT(1) NOT NULL DEFAULT 0 COMMENT 'User verification performed during registration',
  `backup_eligible` BIT(1) DEFAULT 0 COMMENT 'Credential can be synced (iCloud Keychain, etc.)',
  `authenticator_transports` VARCHAR(255) DEFAULT NULL COMMENT 'Supported transports: usb, nfc, ble, internal',
  `public_key_credential_type` VARCHAR(255) DEFAULT NULL COMMENT 'Credential type (e.g. public-key)',
  `backup_state` BIT(1) DEFAULT 0 COMMENT 'Credential is currently backed up',
  `attestation_object` BLOB DEFAULT NULL COMMENT 'Attestation data from registration',
  `attestation_client_data_json` BLOB DEFAULT NULL COMMENT 'Client data JSON from registration',
  `created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_used` TIMESTAMP NULL DEFAULT NULL,
  `label` VARCHAR(255) DEFAULT NULL COMMENT 'User-friendly name (e.g., "My iPhone", "YubiKey")',
  PRIMARY KEY (`credential_id`(255)),
  KEY `idx_user_credentials_user_entity` (`user_entity_user_id`),
  KEY `idx_user_credentials_created` (`created`),
  KEY `idx_user_credentials_last_used` (`last_used`),
  CONSTRAINT `fk_user_credentials_user_entity`
    FOREIGN KEY (`user_entity_user_id`)
    REFERENCES `user_entities` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
COMMENT='WebAuthn credentials (public keys) for passkey authentication';
