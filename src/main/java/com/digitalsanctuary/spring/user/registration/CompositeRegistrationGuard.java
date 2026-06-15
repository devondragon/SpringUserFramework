package com.digitalsanctuary.spring.user.registration;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * A {@link RegistrationGuard} that composes multiple delegate guards and evaluates them in order,
 * applying first-deny-wins semantics.
 *
 * <p>The delegates are consulted sequentially. The first delegate that returns a
 * {@link RegistrationDecision#deny(String) deny} decision short-circuits evaluation — subsequent
 * delegates are not consulted — and its reason is propagated. If every delegate allows (or there are
 * no delegates at all), the composite allows the registration.</p>
 *
 * <p>This enables layered policies, e.g. an invite-only guard <em>and</em> a domain-allowlist guard,
 * where any single denial blocks the registration. The order of evaluation follows the order of the
 * injected list; consumers can control it with Spring's {@code @Order}/{@link org.springframework.core.Ordered}.</p>
 *
 * <p><strong>Thread Safety:</strong> This class holds an immutable snapshot of its delegates and is
 * therefore thread-safe provided the delegates themselves are thread-safe (as required by the SPI).</p>
 *
 * @see RegistrationGuard
 * @see RegistrationGuardConfiguration
 */
@Slf4j
public class CompositeRegistrationGuard implements RegistrationGuard {

    /** The ordered, immutable list of delegate guards. Never {@code null}; may be empty. */
    private final List<RegistrationGuard> delegates;

    /**
     * Instantiates a new composite registration guard.
     *
     * @param delegates the ordered delegate guards to consult; a {@code null} list is treated as empty
     */
    public CompositeRegistrationGuard(final List<RegistrationGuard> delegates) {
        this.delegates = delegates == null ? List.of() : List.copyOf(delegates);
        log.debug("CompositeRegistrationGuard initialized with {} delegate guard(s)", this.delegates.size());
    }

    /**
     * Evaluates each delegate in order, returning the first denial encountered (first-deny-wins). If no
     * delegate denies, the registration is allowed.
     *
     * @param context the registration context describing the attempt
     * @return the first denying {@link RegistrationDecision}, or {@link RegistrationDecision#allow()} if
     *         all delegates allow
     * @throws IllegalStateException if any delegate returns {@code null}; the SPI contract requires a
     *         non-null decision, and silently treating {@code null} as "allow" would let a buggy guard
     *         fail open. Failing fast surfaces the bug at test/runtime instead.
     */
    @Override
    public RegistrationDecision evaluate(final RegistrationContext context) {
        for (RegistrationGuard delegate : delegates) {
            RegistrationDecision decision = delegate.evaluate(context);
            if (decision == null) {
                throw new IllegalStateException(
                        "RegistrationGuard " + delegate.getClass().getName() + " returned a null decision; "
                                + "guards must return a non-null RegistrationDecision (allow or deny).");
            }
            if (!decision.allowed()) {
                return decision;
            }
        }
        return RegistrationDecision.allow();
    }

    /**
     * Returns the delegate guards composed by this guard, in evaluation order.
     *
     * @return an immutable list of delegate guards
     */
    public List<RegistrationGuard> getDelegates() {
        return new ArrayList<>(delegates);
    }
}
