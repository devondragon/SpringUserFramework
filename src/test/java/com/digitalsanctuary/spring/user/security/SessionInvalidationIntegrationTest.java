package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

import com.digitalsanctuary.spring.user.test.annotations.SecurityTest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;

@SecurityTest
@Import(SessionInvalidationIntegrationTest.RealAuthenticationProviderConfig.class)
class SessionInvalidationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SessionRegistry sessionRegistry;

    @Test
    void registryIsPopulatedAfterLogin() throws Exception {
        // SecurityTestConfiguration provides user@test.com / "password"
        var result = mockMvc.perform(formLogin("/user/login").user("username", "user@test.com").password("password"))
                .andExpect(authenticated())
                .andReturn();
        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // The registry must now know about at least one principal/session
        assertThat(sessionRegistry.getAllPrincipals()).isNotEmpty();
    }

    /**
     * Provides a real {@link DaoAuthenticationProvider} so that form login with a
     * {@code UsernamePasswordAuthenticationToken} can succeed in the security test slice. The default
     * {@code SecurityTestConfiguration} only registers a {@code TestingAuthenticationProvider}, which does not
     * support username/password tokens. The delegating password encoder understands the {@code {bcrypt}} prefix
     * used by the pre-built test users.
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
