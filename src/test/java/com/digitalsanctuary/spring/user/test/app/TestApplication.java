package com.digitalsanctuary.spring.user.test.app;

import com.digitalsanctuary.spring.user.UserConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test application for Spring User Framework integration tests.
 * This application imports the UserConfiguration to test the library
 * in a real Spring Boot context.
 */
@SpringBootApplication
@Import(UserConfiguration.class)
@EntityScan(basePackages = "com.digitalsanctuary.spring.user.persistence.model")
@EnableJpaRepositories(basePackages = "com.digitalsanctuary.spring.user.persistence.repository")
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}