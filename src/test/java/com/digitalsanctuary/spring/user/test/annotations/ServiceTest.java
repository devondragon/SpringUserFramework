package com.digitalsanctuary.spring.user.test.annotations;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.digitalsanctuary.spring.user.test.config.BaseTestConfiguration;

import java.lang.annotation.*;

/**
 * Composite annotation for service layer unit tests using Mockito.
 * This annotation is optimized for fast unit tests that don't require
 * a full Spring context.
 * 
 * Features:
 * - Mockito extension for @Mock and @InjectMocks support
 * - Base test configuration for common test beans
 * - Test profile activation
 * 
 * Usage:
 * <pre>
 * @ServiceTest
 * class UserServiceTest {
 *     @Mock
 *     private UserRepository userRepository;
 *     
 *     @InjectMocks
 *     private UserService userService;
 *     
 *     // Test methods
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@Import(BaseTestConfiguration.class)
public @interface ServiceTest {
    
    /**
     * Whether to enable strict stubbing for Mockito.
     * Default is true for better test quality.
     */
    boolean strictStubbing() default true;
}