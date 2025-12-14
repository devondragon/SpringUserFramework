package com.digitalsanctuary.spring.user.test.annotations;

import com.digitalsanctuary.spring.user.test.app.TestApplication;
import com.digitalsanctuary.spring.user.test.config.*;
import org.springframework.boot.data.jpa.test.autoconfigure.AutoConfigureDataJpa;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.*;

/**
 * Composite annotation for integration tests that require a full Spring context.
 * This annotation combines common integration test setup including:
 * - Full Spring Boot test context
 * - Mock MVC configuration
 * - Test profile activation
 * - Transaction management
 * - All test configurations
 * 
 * Usage:
 * <pre>
 * @IntegrationTest
 * class UserServiceIntegrationTest {
 *     // Test methods
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@AutoConfigureDataJpa
@ActiveProfiles("test")
@Transactional
@Import({
    BaseTestConfiguration.class,
    DatabaseTestConfiguration.class,
    SecurityTestConfiguration.class,
    OAuth2TestConfiguration.class,
    MockMailConfiguration.class
})
public @interface IntegrationTest {
    
    /**
     * Whether to rollback transactions after each test method.
     * Default is true to ensure test isolation.
     */
    boolean rollback() default true;
    
    /**
     * Additional Spring profiles to activate.
     */
    String[] additionalProfiles() default {};
}