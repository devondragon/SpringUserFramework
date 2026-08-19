package com.digitalsanctuary.spring.user.security;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import com.digitalsanctuary.spring.user.roles.RolesAndPrivilegesConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rejects role and privilege names that collide with Spring Security's factor authorities ({@code FACTOR_*}).
 *
 * <p>
 * Spring Security records how a user authenticated as a {@code FactorGrantedAuthority} carrying an issue time, and
 * resolves a required factor by taking the <em>first</em> authority whose string matches, then inspecting that one. A
 * plain granted authority that happens to be named {@code FACTOR_WEBAUTHN} therefore behaves as a counterfeit factor,
 * and because {@code user.roles-and-privileges} authorities are loaded from the database ahead of the stamped one, the
 * counterfeit always wins:
 * </p>
 * <ul>
 * <li><b>MFA enforcement</b> ({@code user.mfa.enabled=true}) checks only that the authority is present, so the
 * counterfeit satisfies the requirement outright and the user reaches protected endpoints without ever completing that
 * factor. This is an authentication bypass for the affected deployment.</li>
 * <li><b>Step-up</b> additionally checks the issue time. The counterfeit has none, so it is treated as expired and
 * shadows the genuine factor behind it: the gate denies immediately after a real ceremony, and the operation can never
 * be performed. This fails closed, but the symptom is opaque.</li>
 * </ul>
 *
 * <p>
 * The check therefore runs whether or not either feature is switched on. It fails startup when MFA or step-up is
 * enabled, since the consequence is a security hole or an unusable feature, and logs an error otherwise, since the
 * names are inert today but will break either feature the moment it is turned on.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class FactorAuthorityNameValidator {

    /** Prefix Spring Security reserves for authentication-factor authorities. */
    static final String FACTOR_PREFIX = "FACTOR_";

    private final RolesAndPrivilegesConfig rolesAndPrivilegesConfig;
    private final MfaConfigProperties mfaConfigProperties;
    private final StepUpConfigProperties stepUpConfigProperties;

    /**
     * Runs the check at startup.
     *
     * @throws IllegalStateException if a configured role or privilege name starts with {@code FACTOR_} while MFA or
     *         step-up is enabled
     */
    @PostConstruct
    public void validateAuthorityNames() {
        List<String> offenders = findOffendingNames();
        if (offenders.isEmpty()) {
            return;
        }

        String message = "user.roles-and-privileges declares " + offenders + ", which collide with Spring Security's reserved "
                + FACTOR_PREFIX + "* authentication-factor authorities. A granted authority with such a name is indistinguishable "
                + "from a real factor by name: it satisfies MFA enforcement without the factor ever being completed, and it shadows "
                + "the genuine factor in a step-up freshness check. Rename these roles/privileges.";

        if (mfaConfigProperties.isEnabled() || stepUpConfigProperties.isEnabled()) {
            throw new IllegalStateException(message);
        }
        log.error("{} They are inert while both user.mfa.enabled and user.security.stepUp.enabled are false, and startup will fail "
                + "once either is turned on.", message);
    }

    /**
     * Exposes the offending names for tests and diagnostics.
     *
     * @return the configured role and privilege names that collide with the reserved prefix, in sorted order
     */
    public List<String> findOffendingNames() {
        Set<String> offenders = new TreeSet<>();
        rolesAndPrivilegesConfig.getRolesAndPrivileges().forEach((role, privileges) -> Stream
                .concat(Stream.of(role), privileges == null ? Stream.<String>empty() : privileges.stream())
                .filter(name -> name != null && name.toUpperCase().startsWith(FACTOR_PREFIX)).forEach(offenders::add));
        return List.copyOf(offenders);
    }
}
