package com.digitalsanctuary.spring.user.util;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration class for JPA Auditing.
 * <p>
 * Enables JPA Auditing and provides an implementation of {@link AuditorAware} to capture
 * the current auditor from the Spring Security context. This allows JPA entities using
 * {@code @CreatedBy} and {@code @LastModifiedBy} annotations to automatically track
 * which user created or modified them.
 * </p>
 */
@Slf4j
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

	/**
	 * Provides an implementation of AuditorAware to capture the current auditor.
	 *
	 * @return an instance of AuditorAware
	 */
	@Bean
	public AuditorAware<User> auditorProvider() {
		return new AuditorAwareImpl();
	}

	/**
	 * Implementation of AuditorAware to capture the current auditor.
	 */
	private class AuditorAwareImpl implements AuditorAware<User> {

		/**
		 * Returns the current auditor based on the authentication context.
		 *
		 * @return an Optional containing the current auditor, or an empty Optional if no auditor is available
		 */
		@Override
		public Optional<User> getCurrentAuditor() {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			log.debug("AuditorAwareImpl.getCurrentAuditor: Authentication: {}", authentication);

			if (authentication == null || !authentication.isAuthenticated()) {
				return Optional.empty();
			}

			log.debug("AuditorAwareImpl.getCurrentAuditor: Principal: {}", authentication.getPrincipal());

			if (authentication.getPrincipal() instanceof String) {
				log.info("AuditorAwareImpl.getCurrentAuditor: principal is String: {}. Returning empty.", authentication.getPrincipal());
				return Optional.empty();
			}

			if (authentication.getPrincipal() instanceof DSUserDetails) {
				log.debug("AuditorAwareImpl.getCurrentAuditor: principal is DSUserDetails.");
				return Optional.ofNullable(((DSUserDetails) authentication.getPrincipal()).getUser());
			}

			return Optional.empty();
		}
	}
}
