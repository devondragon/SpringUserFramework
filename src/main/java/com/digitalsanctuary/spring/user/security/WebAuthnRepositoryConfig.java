package com.digitalsanctuary.spring.user.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for WebAuthn repositories.
 *
 * <p>
 * Note: The {@code PublicKeyCredentialUserEntityRepository} bean is provided by
 * {@link com.digitalsanctuary.spring.user.persistence.repository.WebAuthnUserEntityBridge} which is marked
 * as {@code @Primary} and bridges Spring Security's WebAuthn user entities with the application's User model.
 * </p>
 */
@Slf4j
@Configuration
public class WebAuthnRepositoryConfig {

	/**
	 * <p>
	 * This repository handles credential CRUD operations including:
	 * </p>
	 * <ul>
	 * <li>save() - Store new credentials after registration to user_credentials table</li>
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
		log.info("Initializing WebAuthn UserCredentialRepository");
		return new JdbcUserCredentialRepository(jdbcTemplate);
	}
}
