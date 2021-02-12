package com.digitalsanctuary.spring.user.util;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {
	public Logger logger = LoggerFactory.getLogger(this.getClass());

	@Bean
	AuditorAware<User> auditorProvider() {
		return new AuditorAwareImpl();
	}

	// This class is used for Spring JPA entities to get & populate createdBy and modifiedBy username strings
	private class AuditorAwareImpl implements AuditorAware<User> {
		@Override
		public Optional<User> getCurrentAuditor() {

			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			logger.debug("JpaAuditingConfig.AuditorAwareImpl.getCurrentAuditor:" + "Authentication: {}",
					authentication);

			if (authentication == null || !authentication.isAuthenticated()) {
				return null;
			}
			logger.debug("JpaAuditingConfig.AuditorAwareImpl.getCurrentAuditor:" + "Principal: {}",
					authentication.getPrincipal());
			if (authentication.getPrincipal() instanceof String) {
				// This happens when the principal is "anonymousUser"
				logger.info("JpaAuditingConfig.AuditorAwareImpl.getCurrentAuditor:"
						+ "principal is String: {}. Returning null.", authentication.getPrincipal());
				return null;
			}
			if (authentication.getPrincipal() instanceof DSUserDetails) {
				logger.debug("JpaAuditingConfig.AuditorAwareImpl.getCurrentAuditor:" + "principal is DSUserDetails.");
				return Optional.ofNullable(((DSUserDetails) authentication.getPrincipal()).getUser());
			}
			return null;
		}
	}
}