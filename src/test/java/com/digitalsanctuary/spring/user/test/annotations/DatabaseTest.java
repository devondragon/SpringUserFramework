package com.digitalsanctuary.spring.user.test.annotations;

import com.digitalsanctuary.spring.user.test.config.DatabaseTestConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.*;

/**
 * Composite annotation for JPA repository tests.
 * This annotation provides a slice test focused on JPA components with:
 * - In-memory H2 database
 * - Entity scanning
 * - Transaction management
 * - Repository configuration
 * 
 * Usage:
 * <pre>
 * @DatabaseTest
 * class UserRepositoryTest {
 *     @Autowired
 *     private UserRepository userRepository;
 *     
 *     // Repository test methods
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(DatabaseTestConfiguration.class)
@Transactional
public @interface DatabaseTest {
    
    /**
     * Whether to show SQL statements in the test output.
     * Default is false to reduce noise.
     */
    boolean showSql() default false;
    
    /**
     * Whether to rollback transactions after each test.
     * Default is true for test isolation.
     */
    boolean rollback() default true;
}