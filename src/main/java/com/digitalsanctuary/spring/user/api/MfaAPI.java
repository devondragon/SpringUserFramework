package com.digitalsanctuary.spring.user.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.digitalsanctuary.spring.user.dto.MfaStatusResponse;
import com.digitalsanctuary.spring.user.security.MfaConfigProperties;
import com.digitalsanctuary.spring.user.util.JSONResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for Multi-Factor Authentication status.
 * <p>
 * Provides an endpoint for checking the MFA status of the current session. This is accessible to
 * partially-authenticated users so the UI can determine which factor challenge to show next.
 * </p>
 * <p>
 * This controller is only registered when MFA is enabled ({@code user.mfa.enabled=true}).
 * </p>
 */
@Slf4j
@RestController
@RequestMapping(path = "/user/mfa", produces = "application/json")
@ConditionalOnProperty(name = "user.mfa.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class MfaAPI {

	/**
	 * Mapping from user-facing factor names to Spring Security authority strings.
	 */
	private static final Map<String, String> FACTOR_AUTHORITY_MAP = Map.of(
			"PASSWORD", FactorGrantedAuthority.PASSWORD_AUTHORITY,
			"WEBAUTHN", FactorGrantedAuthority.WEBAUTHN_AUTHORITY);

	private final MfaConfigProperties mfaConfigProperties;

	/**
	 * Returns the MFA status for the current session.
	 * <p>
	 * Reports which factors are required, which have been satisfied, and which are still missing. This endpoint is
	 * accessible to partially-authenticated users (added to unprotected URIs when MFA is enabled).
	 * </p>
	 *
	 * @return a ResponseEntity containing the MFA status
	 */
	@GetMapping("/status")
	public ResponseEntity<JSONResponse> getMfaStatus() {
		List<String> requiredFactors = mfaConfigProperties.getFactors();
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		List<String> satisfiedFactors = new ArrayList<>();
		List<String> missingFactors = new ArrayList<>();

		if (authentication != null && authentication.isAuthenticated()) {
			Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

			for (String factor : requiredFactors) {
				String authorityString = FACTOR_AUTHORITY_MAP.get(factor.toUpperCase());
				if (authorityString != null && hasAuthority(authorities, authorityString)) {
					satisfiedFactors.add(factor);
				} else {
					missingFactors.add(factor);
				}
			}
		} else {
			missingFactors.addAll(requiredFactors);
		}

		MfaStatusResponse status = MfaStatusResponse.builder()
				.mfaEnabled(true)
				.requiredFactors(requiredFactors)
				.satisfiedFactors(satisfiedFactors)
				.missingFactors(missingFactors)
				.fullyAuthenticated(missingFactors.isEmpty())
				.build();

		return ResponseEntity.ok(JSONResponse.builder().success(true).data(status).build());
	}

	private boolean hasAuthority(Collection<? extends GrantedAuthority> authorities, String authorityString) {
		return authorities.stream().anyMatch(a -> authorityString.equals(a.getAuthority()));
	}
}
