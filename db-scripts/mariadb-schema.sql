-- Simplified schema for `springuser`

-- Sequence structure
DROP SEQUENCE IF EXISTS `password_reset_token_seq`;
CREATE SEQUENCE `password_reset_token_seq` START WITH 1 INCREMENT BY 50 CACHE 1000 ENGINE=InnoDB;

DROP SEQUENCE IF EXISTS `privilege_seq`;
CREATE SEQUENCE `privilege_seq` START WITH 1 INCREMENT BY 50 CACHE 1000 ENGINE=InnoDB;

DROP SEQUENCE IF EXISTS `role_seq`;
CREATE SEQUENCE `role_seq` START WITH 1 INCREMENT BY 50 CACHE 1000 ENGINE=InnoDB;

DROP SEQUENCE IF EXISTS `user_account_seq`;
CREATE SEQUENCE `user_account_seq` START WITH 1 INCREMENT BY 50 CACHE 1000 ENGINE=InnoDB;

DROP SEQUENCE IF EXISTS `verification_token_seq`;
CREATE SEQUENCE `verification_token_seq` START WITH 1 INCREMENT BY 50 CACHE 1000 ENGINE=InnoDB;

-- Table structure
DROP TABLE IF EXISTS `password_reset_token`;
CREATE TABLE `password_reset_token` (
  `id` BIGINT(20) NOT NULL,
  `expiry_date` DATETIME(6) DEFAULT NULL,
  `token` VARCHAR(255) DEFAULT NULL,
  `user_id` BIGINT(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKns9q9f0f318uaoxiqn6lka9ux` (`user_id`),
  CONSTRAINT `FKns9q9f0f318uaoxiqn6lka9ux` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `privilege`;
CREATE TABLE `privilege` (
  `id` BIGINT(20) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `name` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
  `id` BIGINT(20) NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `name` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `roles_privileges`;
CREATE TABLE `roles_privileges` (
  `role_id` BIGINT(20) NOT NULL,
  `privilege_id` BIGINT(20) NOT NULL,
  PRIMARY KEY (`role_id`, `privilege_id`),
  KEY `FK5yjwxw2gvfyu76j3rgqwo685u` (`privilege_id`),
  CONSTRAINT `FK5yjwxw2gvfyu76j3rgqwo685u` FOREIGN KEY (`privilege_id`) REFERENCES `privilege` (`id`),
  CONSTRAINT `FK9h2vewsqh8luhfq71xokh4who` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `user_account`;
CREATE TABLE `user_account` (
  `id` BIGINT(20) NOT NULL,
  `email` VARCHAR(255) NOT NULL,
  `enabled` BIT(1) NOT NULL,
  `first_name` VARCHAR(255) DEFAULT NULL,
  `last_activity_date` DATETIME(6) DEFAULT NULL,
  `last_name` VARCHAR(255) DEFAULT NULL,
  `locked` BIT(1) NOT NULL,
  `password` VARCHAR(60) DEFAULT NULL,
  `provider` ENUM('LOCAL','FACEBOOK','GOOGLE','APPLE','KEYCLOAK') DEFAULT NULL,
  `registration_date` DATETIME(6) DEFAULT NULL,
  `failed_login_attempts` INT(11) NOT NULL,
  `locked_date` DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_hl02wv5hym99ys465woijmfib` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `users_roles`;
CREATE TABLE `users_roles` (
  `user_id` BIGINT(20) NOT NULL,
  `role_id` BIGINT(20) NOT NULL,
  KEY `FKt4v0rrweyk393bdgt107vdx0x` (`role_id`),
  KEY `FKci4mdvg1fmo9eqmwno1y9o0fa` (`user_id`),
  CONSTRAINT `FKci4mdvg1fmo9eqmwno1y9o0fa` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`),
  CONSTRAINT `FKt4v0rrweyk393bdgt107vdx0x` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `verification_token`;
CREATE TABLE `verification_token` (
  `id` BIGINT(20) NOT NULL,
  `expiry_date` DATETIME(6) DEFAULT NULL,
  `token` VARCHAR(255) DEFAULT NULL,
  `user_id` BIGINT(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_VERIFY_USER` (`user_id`),
  CONSTRAINT `FK_VERIFY_USER` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
