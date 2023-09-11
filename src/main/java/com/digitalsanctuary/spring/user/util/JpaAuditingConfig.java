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

@Slf4j
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {
	@Bean
	public AuditorAware<User> auditorProvider() {
		return new AuditorAwareImpl();
	}

	private class AuditorAwareImpl implements AuditorAware<User> {
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
