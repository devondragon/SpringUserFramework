package com.digitalsanctuary.spring.user.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for WebAuthn repositories.
 */
@Slf4j
@Configuration
public class WebAuthnRepositoryConfig {

	/**
	 * <p>
	 * This repository handles credential CRUD operations including:
	 * </p>
	 * <ul>
	 * <li>save() - Store new credentials after registration to webauthn_user_credential table</li>
	 * <li>findByCredentialId() - Look up credentials during authentication</li>
	 * <li>findByUserId() - Get all credentials for a user</li>
	 * <li>delete() - Remove credentials from database</li>
	 * </ul>
	 *
	 * @param jdbcTemplate for database operations
	 * @return the UserCredentialRepository instance
	 */
	@Bean
	public UserCredentialRepository userCredentialRepository(JdbcTemplate jdbcTemplate) {
		log.info("Initializing WebAuthn UserCredentialRepository (JDBC/Database)");
		return new JdbcUserCredentialRepository(jdbcTemplate);
	}

	/**
	 * <p>
	 * Manages mapping between WebAuthn user entities and app users. It handles:
	 * </p>
	 * <ul>
	 * <li>save() - Create or update user entities in webauthn_user_entity table</li>
	 * <li>findByUsername() - Look up user entities by username/email</li>
	 * <li>findById() - Look up user entities by WebAuthn user ID</li>
	 * </ul>
	 *
	 * @param jdbcTemplate for database operations
	 * @return the PublicKeyCredentialUserEntityRepository instance
	 */
	@Bean
	public PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(JdbcTemplate jdbcTemplate) {
		log.info("Initializing WebAuthn PublicKeyCredentialUserEntityRepository (JDBC/Database)");
		return new JdbcPublicKeyCredentialUserEntityRepository(jdbcTemplate);
	}
}
