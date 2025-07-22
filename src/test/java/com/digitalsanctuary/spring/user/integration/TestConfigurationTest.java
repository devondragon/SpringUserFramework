package com.digitalsanctuary.spring.user.integration;

import com.digitalsanctuary.spring.user.test.app.TestApplication;
import com.digitalsanctuary.spring.user.test.config.BaseTestConfiguration;
import com.digitalsanctuary.spring.user.test.config.OAuth2TestConfiguration;
import com.digitalsanctuary.spring.user.test.config.SecurityTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify individual test configurations work correctly.
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
public class TestConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;
    
    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
    
    @Test
    void baseTestConfigurationBeansExist() {
        // Password encoder
        assertThat(applicationContext.getBean(PasswordEncoder.class)).isNotNull();
        
        // Test clock
        assertThat(applicationContext.getBean(Clock.class)).isNotNull();
    }
    
    @Test
    void securityTestConfigurationBeansExist() {
        // Test security context factory
        assertThat(applicationContext.getBean(SecurityTestConfiguration.TestSecurityContextFactory.class)).isNotNull();
    }
    
    @Test
    void oauth2TestConfigurationBeansExist() {
        // Client registration repository
        assertThat(applicationContext.getBean(ClientRegistrationRepository.class)).isNotNull();
        
        // OAuth2 token factory
        assertThat(applicationContext.getBean(OAuth2TestConfiguration.OAuth2TestTokenFactory.class)).isNotNull();
    }
}