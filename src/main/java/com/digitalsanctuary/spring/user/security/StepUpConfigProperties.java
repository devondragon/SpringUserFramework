package com.digitalsanctuary.spring.user.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Configuration properties for step-up (re-)authentication before credential-altering operations.
 *
 * <p>
 * Step-up is satisfied by an authentication factor that was issued recently: the user proves presence by re-running an
 * ordinary login ceremony (for {@code WEBAUTHN}, the passkey assertion at {@code /login/webauthn}) while already
 * logged in, which refreshes that factor's issue time. There is no separate step-up ceremony, endpoint, or token.
 * </p>
 *
 * <p>
 * The window is bound to the session and to time, not to a single operation: within {@code ttlSeconds} of a
 * ceremony, any credential-altering operation on that session is authorized. That is a deliberate trade-off; see
 * {@link DSFactorFreshnessStepUpService}.
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "user.security.step-up")
public class StepUpConfigProperties {

    /**
     * Factor names accepted in {@link #factors}, mapped to the Spring Security authority they require. Every
     * {@link FactorGrantedAuthority} constant is offered, but only factors the deployment actually issues can ever be
     * refreshed: requiring a factor no login flow produces makes the gated operations permanently unavailable.
     */
    static final Map<String, String> FACTOR_AUTHORITIES = Map.of(
            "WEBAUTHN", FactorGrantedAuthority.WEBAUTHN_AUTHORITY,
            "PASSWORD", FactorGrantedAuthority.PASSWORD_AUTHORITY,
            "OTT", FactorGrantedAuthority.OTT_AUTHORITY,
            "AUTHORIZATION_CODE", FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY,
            "SAML_RESPONSE", FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY,
            "CAS", FactorGrantedAuthority.CAS_AUTHORITY,
            "X509", FactorGrantedAuthority.X509_AUTHORITY,
            "BEARER", FactorGrantedAuthority.BEARER_AUTHORITY);

    /**
     * Whether the framework registers its built-in step-up service. When false (default), no {@code StepUpService} bean
     * is created and behavior is unchanged: {@code POST /user/setPassword} stays governed by
     * {@code user.security.allowInitialPasswordSetWithoutStepUp}, and passkey delete/rename keep their current-password
     * check with no additional requirement for passwordless accounts. A consumer-supplied {@code StepUpService} bean
     * still takes precedence when this is true.
     */
    private boolean enabled = false;

    /**
     * How recently the factor must have been issued, in seconds. The ceremony immediately precedes the operation, so
     * the default is deliberately short: a longer window widens the period in which an attacker sharing the session can
     * piggyback on the legitimate user's ceremony.
     */
    @Min(1)
    private int ttlSeconds = 120;

    /**
     * How recently the user must have authenticated, by any means, to register a new passkey. Separate from
     * {@link #ttlSeconds} and deliberately longer: the step-up ceremony immediately precedes the operation, whereas
     * enrollment usually follows a login, a look around the settings page, and a decision.
     *
     * <p>
     * The gate applies only while step-up is {@link #enabled}. It exists because enrolling a passkey is what turns a
     * stolen session into durable access: the credential outlives a password change, and asserting with it refreshes
     * {@code FACTOR_WEBAUTHN}, which would otherwise let an attacker satisfy step-up with an authenticator they
     * enrolled seconds earlier. Note the residual: within this window of a genuine login, a concurrent attacker on
     * the same session can still enroll.
     * </p>
     */
    @Min(1)
    private int enrollmentTtlSeconds = 600;

    /**
     * Factors that satisfy step-up, any one of which is sufficient. Values are the keys of
     * {@link #FACTOR_AUTHORITIES}; unknown values fail startup. Defaults to {@code WEBAUTHN} alone, which is the only
     * factor whose refresh reliably proves user presence: re-running an OAuth2 login, for instance, typically completes
     * with no user interaction when the identity provider session is still alive.
     */
    private List<String> factors = new ArrayList<>(List.of("WEBAUTHN"));
}
