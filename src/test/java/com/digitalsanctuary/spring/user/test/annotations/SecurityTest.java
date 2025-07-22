package com.digitalsanctuary.spring.user.test.annotations;

import com.digitalsanctuary.spring.user.test.app.TestApplication;
import com.digitalsanctuary.spring.user.test.config.BaseTestConfiguration;
import com.digitalsanctuary.spring.user.test.config.SecurityTestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithSecurityContextTestExecutionListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;

import java.lang.annotation.*;

/**
 * Composite annotation for security-focused tests.
 * This annotation sets up the Spring Security test context with:
 * - Security test configuration
 * - Mock MVC with security support
 * - Security context test execution listener
 * - Test authentication utilities
 * 
 * Usage:
 * <pre>
 * @SecurityTest
 * @WithMockUser(roles = "ADMIN")
 * class AdminControllerSecurityTest {
 *     @Autowired
 *     private MockMvc mockMvc;
 *     
 *     // Security test methods
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@Import({BaseTestConfiguration.class, SecurityTestConfiguration.class})
@TestExecutionListeners(
    value = WithSecurityContextTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
public @interface SecurityTest {
    
    /**
     * Whether to print security debug information.
     * Useful for troubleshooting security configuration issues.
     */
    boolean debug() default false;
    
    /**
     * Whether to disable CSRF protection for tests.
     * Default is false to test with CSRF enabled.
     */
    boolean disableCsrf() default false;
}