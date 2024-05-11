package com.digitalsanctuary.spring.user.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;

@TestConfiguration
@ComponentScan(basePackages = {
        "com.digitalsanctuary.spring.user.service.**",
        "com.digitalsanctuary.spring.user.mail.**",
        "com.digitalsanctuary.spring.user.persistence.model.**"
})
public class TestConfig {
}
