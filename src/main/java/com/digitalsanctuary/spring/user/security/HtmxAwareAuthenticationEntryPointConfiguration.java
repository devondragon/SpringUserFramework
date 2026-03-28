package com.digitalsanctuary.spring.user.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration for the {@link AuthenticationEntryPoint}.
 *
 * <p>Registers an {@link HtmxAwareAuthenticationEntryPoint} that wraps the appropriate inner entry point
 * (form-login or OAuth2) when no custom {@link AuthenticationEntryPoint} bean is defined by the consuming
 * application.</p>
 *
 * <p>Consuming applications can override this by defining their own {@link AuthenticationEntryPoint} bean:</p>
 * <pre>{@code
 * @Bean
 * public AuthenticationEntryPoint myCustomEntryPoint() {
 *     return new MyCustomAuthenticationEntryPoint();
 * }
 * }</pre>
 */
@Slf4j
@Configuration
public class HtmxAwareAuthenticationEntryPointConfiguration {

    @Value("${user.security.loginPageURI}")
    private String loginPageURI;

    @Value("${spring.security.oauth2.enabled:false}")
    private boolean oauth2Enabled;

    /**
     * Creates the default {@link AuthenticationEntryPoint} bean. This bean is only registered when no custom
     * {@link AuthenticationEntryPoint} bean is provided by the consuming application.
     *
     * @return an {@link HtmxAwareAuthenticationEntryPoint} wrapping the appropriate inner entry point
     */
    @Bean
    @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
    public AuthenticationEntryPoint authenticationEntryPoint() {
        AuthenticationEntryPoint inner;
        if (oauth2Enabled) {
            inner = new CustomOAuth2AuthenticationEntryPoint(null, loginPageURI);
            log.info("Configuring HtmxAwareAuthenticationEntryPoint wrapping CustomOAuth2AuthenticationEntryPoint");
        } else {
            inner = new LoginUrlAuthenticationEntryPoint(loginPageURI);
            log.info("Configuring HtmxAwareAuthenticationEntryPoint wrapping LoginUrlAuthenticationEntryPoint");
        }
        return new HtmxAwareAuthenticationEntryPoint(inner, loginPageURI);
    }
}
