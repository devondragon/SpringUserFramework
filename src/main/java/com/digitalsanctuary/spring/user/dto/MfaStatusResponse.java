package com.digitalsanctuary.spring.user.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Response DTO for the MFA status endpoint.
 * <p>
 * Provides information about the MFA state of the current user session, including which factors are required, which
 * have been satisfied, and which are still missing. This enables the UI to display the appropriate MFA challenge pages.
 * </p>
 *
 * @see com.digitalsanctuary.spring.user.api.MfaAPI
 */
@Value
@Builder
public class MfaStatusResponse {

	/** Whether MFA is enabled on the server. */
	boolean mfaEnabled;

	/** The list of required factor names (e.g., PASSWORD, WEBAUTHN). */
	List<String> requiredFactors;

	/** The list of factor names that the current session has satisfied. */
	List<String> satisfiedFactors;

	/** The list of factor names that the current session has not yet satisfied. */
	List<String> missingFactors;

	/** Whether the current session has satisfied all required factors. */
	boolean fullyAuthenticated;
}
