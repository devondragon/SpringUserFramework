package com.digitalsanctuary.spring.user.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.repository.PrivilegeRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
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
        return validator(config, mfaEnabled, stepUpEnabled, List.of(), List.of());
    }

    private static FactorAuthorityNameValidator validator(RolesAndPrivilegesConfig config, boolean mfaEnabled,
            boolean stepUpEnabled, List<String> persistedRoles, List<String> persistedPrivileges) {
        MfaConfigProperties mfa = new MfaConfigProperties();
        mfa.setEnabled(mfaEnabled);
        StepUpConfigProperties stepUp = new StepUpConfigProperties();
        stepUp.setEnabled(stepUpEnabled);
        return new FactorAuthorityNameValidator(config, mfa, stepUp, provider(roleRows(persistedRoles)),
                provider(privilegeRows(persistedPrivileges)));
    }

    private static List<Role> roleRows(List<String> names) {
        return names.stream().map(name -> {
            Role role = new Role();
            role.setName(name);
            return role;
        }).toList();
    }

    private static List<Privilege> privilegeRows(List<String> names) {
        return names.stream().map(name -> {
            Privilege privilege = new Privilege();
            privilege.setName(name);
            return privilege;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(List<?> rows) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        Object repository = rows.isEmpty() ? null : repositoryReturning(rows);
        when(provider.getIfAvailable()).thenReturn((T) repository);
        return provider;
    }

    private static Object repositoryReturning(List<?> rows) {
        if (rows.get(0) instanceof Role) {
            RoleRepository repository = mock(RoleRepository.class);
            when(repository.findAll()).thenReturn((List<Role>) rows);
            return repository;
        }
        PrivilegeRepository repository = mock(PrivilegeRepository.class);
        when(repository.findAll()).thenReturn((List<Privilege>) rows);
        return repository;
    }

    @Test
    @DisplayName("should reject a reserved name persisted in the database but absent from configuration")
    void shouldRejectReservedNamePersistedButNotConfigured() {
        // RolePrivilegeSetupService never deletes, so a FACTOR_-prefixed row created under an earlier configuration
        // survives its removal from YAML and is still granted by AuthorityService. Checking configuration alone
        // reports clean while the counterfeit authority is live.
        FactorAuthorityNameValidator validator =
                validator(new RolesAndPrivilegesConfig(), true, false, List.of("FACTOR_WEBAUTHN"), List.of());

        assertThat(validator.findOffendingNames()).containsExactly("FACTOR_WEBAUTHN");
        assertThatThrownBy(validator::validateAuthorityNames).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("should reject a reserved privilege name persisted in the database")
    void shouldRejectReservedPrivilegePersisted() {
        FactorAuthorityNameValidator validator =
                validator(new RolesAndPrivilegesConfig(), false, true, List.of(), List.of("FACTOR_OTT"));

        assertThat(validator.findOffendingNames()).containsExactly("FACTOR_OTT");
    }
}
