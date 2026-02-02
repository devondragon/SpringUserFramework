package com.digitalsanctuary.spring.user.dto;

import com.digitalsanctuary.spring.user.gdpr.ConsentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for consent grant/withdrawal requests.
 *
 * @see com.digitalsanctuary.spring.user.api.GdprAPI
 * @see com.digitalsanctuary.spring.user.gdpr.ConsentAuditService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRequestDto {

    /**
     * The type of consent being granted or withdrawn.
     */
    @NotNull(message = "Consent type is required")
    private ConsentType consentType;

    /**
     * For CUSTOM consent type, specifies the custom consent type name.
     * Required when consentType is CUSTOM.
     * Must contain only alphanumeric characters, underscores, and hyphens.
     */
    @Size(max = 100, message = "Custom type must not exceed 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Custom type can only contain letters, numbers, underscores, and hyphens")
    private String customType;

    /**
     * Optional version identifier for the policy document
     * (e.g., "privacy-policy-v1.2").
     */
    private String policyVersion;

    /**
     * Whether consent is being granted (true) or withdrawn (false).
     */
    @NotNull(message = "Grant flag is required")
    private Boolean grant;

    /**
     * The method by which consent is being given/withdrawn.
     * Common values: "web_form", "api", "checkbox".
     * Defaults to "api" if not specified.
     */
    private String method;

}
