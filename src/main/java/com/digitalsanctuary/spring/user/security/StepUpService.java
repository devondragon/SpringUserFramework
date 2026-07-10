package com.digitalsanctuary.spring.user.security;

import com.digitalsanctuary.spring.user.persistence.model.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * SPI for step-up (re-)authentication before a sensitive, credential-altering operation.
 *
 * <p>
 * The framework does not ship a step-up primitive of its own. A consuming application may provide a Spring bean
 * implementing this interface to require a fresh proof of presence/possession &mdash; e.g. a WebAuthn assertion, a TOTP
 * code, or a recent-authentication signal &mdash; before an operation that cannot verify a <em>current</em> credential.
 * The motivating case is setting an <em>initial</em> password on a passwordless (passkey-only) account via
 * {@code POST /user/setPassword} (SUF-02): there is no existing password to check, so without step-up a caller who
 * merely holds an authenticated session could add a durable password credential.
 * </p>
 *
 * <p>
 * Enforcement in {@code UserAPI.setPassword}:
 * </p>
 * <ul>
 * <li>If a {@code StepUpService} bean is present, it is invoked and must return {@code true} for the request to proceed.</li>
 * <li>If no bean is present, the endpoint is <strong>disabled by default</strong>; set
 * {@code user.security.allowInitialPasswordSetWithoutStepUp=true} to explicitly allow the session-only behavior.</li>
 * </ul>
 *
 * <p>
 * Implementations should be side-effect free with respect to the operation itself; they only decide whether the caller
 * has satisfied step-up. Return {@code false} (rather than throwing) to reject; the caller maps that to an HTTP 401.
 * </p>
 */
public interface StepUpService {

    /**
     * Decides whether the caller has satisfied step-up authentication for the given action.
     *
     * @param user the authenticated user the operation targets (never {@code null})
     * @param action a short, stable action identifier (e.g. {@code "set-password"})
     * @param request the current HTTP request, so the implementation can read a step-up assertion/token supplied by the
     *        client. Read it from request headers, query/form parameters, or a prior challenge stored in the session.
     *        Note: by the time this method runs the request <em>body</em> has already been consumed by the target
     *        endpoint's {@code @RequestBody} binding (e.g. {@code SetPasswordDto} on {@code POST /user/setPassword}), so
     *        {@code request.getInputStream()}/{@code request.getReader()} will be empty. Carry the proof in a header, a request
     *        parameter, or the session instead &mdash; or install a content-caching request filter if you must re-read
     *        the body.
     * @return {@code true} if step-up is satisfied and the operation may proceed; {@code false} to reject it
     */
    boolean isStepUpSatisfied(User user, String action, HttpServletRequest request);
}
