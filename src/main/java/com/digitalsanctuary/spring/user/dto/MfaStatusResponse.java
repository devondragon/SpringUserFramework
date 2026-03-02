package com.digitalsanctuary.spring.user.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for the MFA status endpoint.
 * <p>
 * Provides information about the MFA state of the current user session, including which factors are required, which
 * have been satisfied, and which are still missing. This enables the UI to display the appropriate MFA challenge pages.
 * </p>
 *
 * @see com.digitalsanctuary.spring.user.api.MfaAPI
 */
@Data
@Builder
public class MfaStatusResponse {

	/** Whether MFA is enabled on the server. */
	private boolean mfaEnabled;

	/** The list of required factor names (e.g., PASSWORD, WEBAUTHN). */
	private List<String> requiredFactors;

	/** The list of factor names that the current session has satisfied. */
	private List<String> satisfiedFactors;

	/** The list of factor names that the current session has not yet satisfied. */
	private List<String> missingFactors;

	/** Whether the current session has satisfied all required factors. */
	private boolean fullyAuthenticated;
}
