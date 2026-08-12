package com.digitalsanctuary.spring.user.captcha;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Exposes the CAPTCHA public site key as the {@code captchaSiteKey} model attribute, so consuming
 * applications can render the CAPTCHA widget in their own templates without re-plumbing the key.
 * Registered only when {@code user.security.captcha.enabled=true}.
 *
 * <p>
 * The {@code annotations = Controller.class} selector is resolved with a meta-annotation search, so
 * this advice applies to {@code @Controller} types <em>and</em> to {@code @RestController} types
 * (which are meta-annotated {@code @Controller}). Response bodies are unaffected either way — the
 * model is ignored for {@code @ResponseBody} handlers — but {@link CaptchaService#siteKey()} is
 * called once per request across the whole MVC surface, which is why that method is documented as
 * needing to be a cached or configured lookup rather than I/O.
 * </p>
 */
@Slf4j
@ConditionalOnProperty(name = "user.security.captcha.enabled", havingValue = "true")
@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class CaptchaSiteKeyControllerAdvice {

    private final ObjectProvider<CaptchaService> captchaServiceProvider;

    /**
     * The CAPTCHA public site key, or null when no provider is available, none is configured, or
     * the provider fails to supply one.
     *
     * @return the site key for widget rendering
     */
    @ModelAttribute("captchaSiteKey")
    public String captchaSiteKey() {
        CaptchaService captchaService = captchaServiceProvider.getIfAvailable();
        if (captchaService == null) {
            return null;
        }
        try {
            return captchaService.siteKey().orElse(null);
        } catch (RuntimeException e) {
            // This advice runs on every @Controller request, so a throwing consumer-supplied
            // provider would otherwise break every MVC page in the application, not just the
            // CAPTCHA-bearing ones. The site key is a display concern, not a security gate:
            // degrading to a missing widget is safe (the interceptor still rejects tokenless
            // POSTs), breaking all page rendering is not.
            log.error("CaptchaService {} threw while supplying the site key. Rendering without it.",
                    captchaService.getClass().getName(), e);
            return null;
        }
    }
}
