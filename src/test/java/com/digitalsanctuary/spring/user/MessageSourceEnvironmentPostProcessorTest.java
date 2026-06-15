package com.digitalsanctuary.spring.user;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;

/**
 * Verifies that {@link MessageSourceEnvironmentPostProcessor} registers the library's message bundle ADDITIVELY, preserving the consuming application's
 * own {@code spring.messages.basename} (or Spring Boot's conventional default) rather than overriding it.
 */
@DisplayName("MessageSourceEnvironmentPostProcessor Tests")
class MessageSourceEnvironmentPostProcessorTest {

    @Nested
    @DisplayName("Basename merge logic")
    class MergeLogic {

        @Test
        @DisplayName("should preserve Spring Boot default and append library bundle when basename is unset")
        void shouldPreserveDefaultWhenUnset() {
            assertThat(MessageSourceEnvironmentPostProcessor.mergeBasename(null)).isEqualTo("messages,messages/dsspringusermessages");
            assertThat(MessageSourceEnvironmentPostProcessor.mergeBasename("")).isEqualTo("messages,messages/dsspringusermessages");
            assertThat(MessageSourceEnvironmentPostProcessor.mergeBasename("   ")).isEqualTo("messages,messages/dsspringusermessages");
        }

        @Test
        @DisplayName("should preserve a single consumer basename and append library bundle last")
        void shouldPreserveSingleConsumerBasename() {
            assertThat(MessageSourceEnvironmentPostProcessor.mergeBasename("i18n/app"))
                    .isEqualTo("i18n/app,messages/dsspringusermessages");
        }

        @Test
        @DisplayName("should preserve multiple consumer basenames in order with library bundle last")
        void shouldPreserveMultipleConsumerBasenames() {
            assertThat(MessageSourceEnvironmentPostProcessor.mergeBasename("messages,i18n/labels"))
                    .isEqualTo("messages,i18n/labels,messages/dsspringusermessages");
        }

        @Test
        @DisplayName("should trim whitespace around consumer basenames")
        void shouldTrimWhitespace() {
            assertThat(MessageSourceEnvironmentPostProcessor.mergeBasename(" messages , i18n/app "))
                    .isEqualTo("messages,i18n/app,messages/dsspringusermessages");
        }

        @Test
        @DisplayName("should de-duplicate and keep the library bundle last if the consumer already lists it")
        void shouldDeduplicateLibraryBundle() {
            assertThat(MessageSourceEnvironmentPostProcessor.mergeBasename("messages/dsspringusermessages,messages"))
                    .isEqualTo("messages,messages/dsspringusermessages");
        }
    }

    @Nested
    @DisplayName("Additive registration through MessageSource")
    class AdditiveRegistration {

        // Boots only MessageSourceAutoConfiguration, then applies the merged basename the post-processor would produce for a consumer who points at
        // their own bundle (test-messages/consumer-bundle). Both the consumer key and the library key must resolve.
        private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MessageSourceAutoConfiguration.class))
                .withPropertyValues("spring.messages.basename="
                        + MessageSourceEnvironmentPostProcessor.mergeBasename("test-messages/consumer-bundle"));

        @Test
        @DisplayName("should resolve BOTH a consumer-bundle key AND a library-bundle key")
        void shouldResolveConsumerAndLibraryKeys() {
            contextRunner.run(context -> {
                MessageSource messageSource = context.getBean(MessageSource.class);
                assertThat(messageSource.getMessage("consumer.test.key", null, Locale.ENGLISH))
                        .as("consumer's own bundle must still resolve").isEqualTo("Consumer bundle value");
                assertThat(messageSource.getMessage("email.forgot-password.prompt", null, Locale.ENGLISH))
                        .as("library bundle must also resolve").isEqualTo("Reset your password");
            });
        }
    }
}
