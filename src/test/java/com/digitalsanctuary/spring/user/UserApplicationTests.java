package com.digitalsanctuary.spring.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@SpringBootApplication
class UserApplicationTests {
    @Test
    void contextLoads() {
        // This ensures the entire application context, including your library's configuration, is loaded.
    }
}
