package com.digitalsanctuary.spring.user.test.annotations;

import com.digitalsanctuary.spring.user.test.app.TestApplication;
import com.digitalsanctuary.spring.user.test.config.BaseTestConfiguration;
import com.digitalsanctuary.spring.user.test.config.OAuth2TestConfiguration;
import com.digitalsanctuary.spring.user.test.config.SecurityTestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.*;

/**
 * Composite annotation for OAuth2/OIDC integration tests.
 * This annotation provides:
 * - Mock OAuth2 authorization servers
 * - Test OAuth2 user services
 * - Pre-configured OAuth2 clients
 * - Security context for OAuth2 testing
 * 
 * Usage:
 * <pre>
 * @OAuth2Test
 * class OAuth2LoginTest {
 *     @Autowired
 *     private MockMvc mockMvc;
 *     
 *     @Autowired
 *     private OAuth2TestTokenFactory tokenFactory;
 *     
 *     // OAuth2 test methods
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
    BaseTestConfiguration.class,
    SecurityTestConfiguration.class,
    OAuth2TestConfiguration.class
})
public @interface OAuth2Test {
    
    /**
     * OAuth2 providers to enable for testing.
     * Default includes common providers.
     */
    String[] providers() default {"google", "github", "oidc"};
    
    /**
     * Whether to enable OIDC (OpenID Connect) testing.
     * Default is true.
     */
    boolean enableOidc() default true;
}