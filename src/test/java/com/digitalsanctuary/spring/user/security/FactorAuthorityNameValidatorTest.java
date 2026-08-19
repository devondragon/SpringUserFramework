package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.digitalsanctuary.spring.user.roles.RolesAndPrivilegesConfig;

/**
 * Tests for {@link FactorAuthorityNameValidator}.
 * <p>
 * A role or privilege named {@code FACTOR_*} is not a naming nit. MFA enforcement checks only that the authority is
 * present, so such a name satisfies a required factor the user never completed, and step-up's freshness check finds the
 * counterfeit first and denies even a genuine, just-completed ceremony.
 * </p>
 */
@DisplayName("FactorAuthorityNameValidator Tests")
class FactorAuthorityNameValidatorTest {

    @Test
    @DisplayName("should pass when no role or privilege uses the reserved prefix")
    void shouldPassForOrdinaryNames() {
        FactorAuthorityNameValidator validator = validator(Map.of("ROLE_USER", List.of("READ_PRIVILEGE")), false, false);

        assertThatCode(validator::validateAuthorityNames).doesNotThrowAnyException();
        assertThat(validator.findOffendingNames()).isEmpty();
    }

    @Test
    @DisplayName("should fail startup when a privilege uses the reserved prefix and MFA is enabled")
    void shouldFailWhenPrivilegeCollidesAndMfaEnabled() {
        FactorAuthorityNameValidator validator =
                validator(Map.of("ROLE_USER", List.of("READ_PRIVILEGE", "FACTOR_WEBAUTHN")), true, false);

        assertThatThrownBy(validator::validateAuthorityNames).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FACTOR_WEBAUTHN").hasMessageContaining("Rename");
    }

    @Test
    @DisplayName("should fail startup when a role uses the reserved prefix and step-up is enabled")
    void shouldFailWhenRoleCollidesAndStepUpEnabled() {
        FactorAuthorityNameValidator validator = validator(Map.of("FACTOR_PASSWORD", List.of("READ_PRIVILEGE")), false, true);

        assertThatThrownBy(validator::validateAuthorityNames).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FACTOR_PASSWORD");
    }

    @Test
    @DisplayName("should log rather than fail when neither feature is enabled")
    void shouldNotFailWhenBothFeaturesDisabled() {
        FactorAuthorityNameValidator validator = validator(Map.of("ROLE_USER", List.of("FACTOR_WEBAUTHN")), false, false);

        assertThatCode(validator::validateAuthorityNames).doesNotThrowAnyException();
        assertThat(validator.findOffendingNames()).containsExactly("FACTOR_WEBAUTHN");
    }

    @Test
    @DisplayName("should detect the reserved prefix regardless of case")
    void shouldDetectPrefixCaseInsensitively() {
        FactorAuthorityNameValidator validator = validator(Map.of("ROLE_USER", List.of("factor_webauthn")), true, false);

        assertThatThrownBy(validator::validateAuthorityNames).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should tolerate a role with no privileges")
    void shouldTolerateNullPrivileges() {
        RolesAndPrivilegesConfig config = new RolesAndPrivilegesConfig();
        config.getRolesAndPrivileges().put("ROLE_USER", null);

        assertThatCode(validator(config, false, false)::validateAuthorityNames).doesNotThrowAnyException();
    }

    private static FactorAuthorityNameValidator validator(Map<String, List<String>> rolesAndPrivileges, boolean mfaEnabled,
            boolean stepUpEnabled) {
        RolesAndPrivilegesConfig config = new RolesAndPrivilegesConfig();
        config.getRolesAndPrivileges().putAll(rolesAndPrivileges);
        return validator(config, mfaEnabled, stepUpEnabled);
    }

    private static FactorAuthorityNameValidator validator(RolesAndPrivilegesConfig config, boolean mfaEnabled, boolean stepUpEnabled) {
        MfaConfigProperties mfa = new MfaConfigProperties();
        mfa.setEnabled(mfaEnabled);
        StepUpConfigProperties stepUp = new StepUpConfigProperties();
        stepUp.setEnabled(stepUpEnabled);
        return new FactorAuthorityNameValidator(config, mfa, stepUp);
    }
}
