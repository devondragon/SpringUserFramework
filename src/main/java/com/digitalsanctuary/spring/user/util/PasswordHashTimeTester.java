package com.digitalsanctuary.spring.user.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The PasswordHashTimeTester class is a Spring Boot service class that tests the time it takes to hash a password. This class is used to test the
 * performance of the password hashing algorithm and provide feedback on the security and usability trade-offs of the password hashing configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordHashTimeTester {

    /** The password encoder. */
    private final PasswordEncoder passwordEncoder;

    /** The test hash time flag. */
    @Value("${user.security.testHashTime}")
    private boolean testHashTime = true;

    /**
     * Tests the time it takes to hash a password. This method is called when the application starts and tests the performance of the password hashing
     * algorithm. The results are logged to provide feedback on the security and usability trade-offs of the password hashing configuration.
     */
    @Async
    @EventListener(ApplicationStartedEvent.class)
    public void testHashTime() {
        if (testHashTime) {
            int runs = 5;
            long totalTime = 0;
            String password = "password";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < runs; i++) {
                long startTime = System.currentTimeMillis();
                String encodedPassword = passwordEncoder.encode(password);
                sb.append(encodedPassword); // Prevents the JVM from optimizing away the password hashing
                long endTime = System.currentTimeMillis();
                long duration = (endTime - startTime);
                totalTime += duration;
            }
            sb.toString(); // Prevents the JVM from optimizing away the password hashing

            long averageTime = totalTime / runs;
            log.info(
                    "Hashed password in {} ms. To balance security with usability, this should be reasonably close to 1000 ms. You can adjust the user.security.bcryptStrength property to increase or decrease the password hashing iterations",
                    averageTime);
        }
    }
}
