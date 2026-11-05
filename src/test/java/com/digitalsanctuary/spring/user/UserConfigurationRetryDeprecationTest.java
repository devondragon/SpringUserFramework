package com.digitalsanctuary.spring.user;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

/**
 * Verifies that {@link UserConfiguration} warns at startup when the deprecated {@code user.retry.enabled} property is still set.
 */
@DisplayName("UserConfiguration user.retry.enabled Deprecation Tests")
// Isolated from other test classes: when a Spring Boot context starts, LogbackLoggingSystem calls LoggerContext.reset(),
// which detaches every appender, including this class's ListAppender. Within the class, assertions also filter by thread.
@Isolated
class UserConfigurationRetryDeprecationTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(UserConfiguration.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("Should warn when user.retry.enabled is set")
    void shouldWarnWhenDeprecatedRetryPropertyIsSet() {
        MockEnvironment environment = new MockEnvironment().withProperty("user.retry.enabled", "false");

        new UserConfiguration(environment).onStartup();

        assertThat(warnings()).singleElement().asString().contains("user.retry.enabled").contains("@EnableRetry");
    }

    @Test
    @DisplayName("Should not warn when user.retry.enabled is not set")
    void shouldNotWarnWhenDeprecatedRetryPropertyIsAbsent() {
        new UserConfiguration(new MockEnvironment()).onStartup();

        assertThat(warnings()).isEmpty();
    }

    private List<String> warnings() {
        // UserConfiguration's logger is JVM-global and this suite runs tests in parallel (see junit-platform.properties), so a
        // Spring context booting in another test can log into this appender. onStartup() runs synchronously here, so keep only
        // events from the current test thread. Snapshot first (List.copyOf) so a concurrent append cannot break iteration.
        String testThread = Thread.currentThread().getName();
        return List.copyOf(listAppender.list).stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .filter(event -> testThread.equals(event.getThreadName()))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
