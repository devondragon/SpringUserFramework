package com.digitalsanctuary.spring.user.captcha;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import lombok.RequiredArgsConstructor;

/**
 * Exposes the CAPTCHA public site key to all MVC page controllers as the {@code captchaSiteKey}
 * model attribute, so consuming applications can render the CAPTCHA widget in their own templates
 * without re-plumbing the key. Registered only when {@code user.security.captcha.enabled=true}, and
 * targeted at {@code @Controller} classes so REST responses are unaffected.
 */
@ConditionalOnProperty(name = "user.security.captcha.enabled", havingValue = "true")
@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class CaptchaSiteKeyControllerAdvice {

    private final ObjectProvider<CaptchaService> captchaServiceProvider;

    /**
     * The CAPTCHA public site key, or null when no provider is available.
     *
     * @return the site key for widget rendering
     */
    @ModelAttribute("captchaSiteKey")
    public String captchaSiteKey() {
        CaptchaService captchaService = captchaServiceProvider.getIfAvailable();
        return captchaService != null ? captchaService.getSiteKey() : null;
    }
}
