package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.service.LogoutSuccessService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.digitalsanctuary.spring.user.test.annotations.SecurityTest;

/**
 * Integration test verifying that the {@link LogoutSuccessService} audit-publishing handler is wired into the
 * security filter chain and that logout still redirects to the configured {@code logoutSuccessURI}.
 */
@SecurityTest
@Import(LogoutAuditIntegrationTest.RealAuthenticationProviderConfig.class)
class LogoutAuditIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    LogoutSuccessHandler logoutSuccessHandler;

    @Value("${user.security.logoutSuccessURI}")
    String logoutSuccessURI;

    @Test
    void logoutHandlerIsTheLogoutSuccessService() {
        // The configured logout success handler must be the audit-publishing LogoutSuccessService.
        assertThat(logoutSuccessHandler).isInstanceOf(LogoutSuccessService.class);
    }

    @Test
    void logoutPublishesAuditEventAndRedirectsToLogoutSuccessUri() throws Exception {
        // Given an authenticated session (SecurityTestConfiguration provides user@test.com / "password").
        MvcResult loginResult = mockMvc.perform(formLogin("/user/login").user("username", "user@test.com").password("password"))
                .andReturn();
        HttpSession session = loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        Mockito.clearInvocations(eventPublisher);

        // When the user logs out (POST to the configured logout URL with the authenticated session and a CSRF token).
        mockMvc.perform(post("/user/logout").session((org.springframework.mock.web.MockHttpSession) session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(logoutSuccessURI));

        // Then a Logout audit event must have been published by the wired LogoutSuccessService.
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(eventPublisher, Mockito.atLeastOnce()).publishEvent(eventCaptor.capture());

        boolean publishedLogoutAudit = eventCaptor.getAllValues().stream().filter(e -> e instanceof AuditEvent)
                .map(e -> (AuditEvent) e).anyMatch(e -> "Logout".equals(e.getAction()));
        assertThat(publishedLogoutAudit).as("a Logout AuditEvent should be published on logout").isTrue();
    }

    /**
     * Provides a real {@link DaoAuthenticationProvider} so that form login with a
     * {@code UsernamePasswordAuthenticationToken} can succeed in the security test slice.
     */
    @TestConfiguration
    static class RealAuthenticationProviderConfig {

        @Bean
        @Primary
        AuthenticationManager testFormLoginAuthenticationManager(UserDetailsService userDetailsService) {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
            provider.setPasswordEncoder(PasswordEncoderFactories.createDelegatingPasswordEncoder());
            return new ProviderManager(provider);
        }
    }
}
