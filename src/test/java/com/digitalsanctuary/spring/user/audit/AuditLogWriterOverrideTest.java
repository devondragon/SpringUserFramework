package com.digitalsanctuary.spring.user.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import com.digitalsanctuary.spring.user.mail.MailContentBuilder;

/**
 * Proves that the library's {@link AuditLogWriter} is genuinely replaceable by a consuming application.
 *
 * <p>
 * The library historically defined {@link FileAuditLogWriter} as an unconditional component-scanned {@code @Component}. A consumer that supplied their
 * own {@link AuditLogWriter} bean got a collision at startup. Following the H8/Task 3.3 lesson, the library's default writer now lives on an
 * {@code @AutoConfiguration} class guarded by {@code @ConditionalOnMissingBean(AuditLogWriter.class)}, which loads AFTER user-defined beans so the
 * consumer's override reliably wins.
 * </p>
 *
 * <p>
 * The test is deliberately isolated: it drives {@link AuditMailAutoConfiguration} directly through an {@link ApplicationContextRunner} with mock
 * collaborators, so it never boots the full JPA/security context (avoiding JPA-metamodel pollution across parallel integration contexts).
 * </p>
 */
@DisplayName("AuditLogWriter Override Tests")
class AuditLogWriterOverrideTest {

    /**
     * Drives the real {@link AuditMailAutoConfiguration}. An {@link AuditConfig} is supplied as a collaborator because {@link FileAuditLogWriter}
     * depends on it. Registered as an auto-configuration so {@code @ConditionalOnMissingBean} evaluates AFTER any consumer-supplied beans.
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("user.mail.fromAddress=test@example.com")
            .withBean(AuditConfig.class, AuditLogWriterOverrideTest::auditConfig)
            .withBean(MailContentBuilder.class, () -> mock(MailContentBuilder.class))
            .withBean("javaMailSender", JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withConfiguration(AutoConfigurations.of(AuditMailAutoConfiguration.class));

    private static AuditConfig auditConfig() {
        AuditConfig config = new AuditConfig();
        // logEvents=true so the default writer bean is created under the @ConditionalOnProperty gate.
        config.setLogEvents(true);
        config.setLogFilePath(System.getProperty("java.io.tmpdir") + "/audit-override-test.log");
        config.setFlushOnWrite(true);
        config.setFlushRate(1000);
        return config;
    }

    @Nested
    @DisplayName("Default behavior: library FileAuditLogWriter present when consumer supplies none")
    class Defaults {

        @Test
        @DisplayName("Library provides a FileAuditLogWriter by default")
        void libraryWriterPresentByDefault() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(AuditLogWriter.class);
                assertThat(context.getBean(AuditLogWriter.class)).isInstanceOf(FileAuditLogWriter.class);
            });
        }
    }

    @Nested
    @DisplayName("Override behavior: consumer AuditLogWriter wins")
    class Overrides {

        @Test
        @DisplayName("Consumer AuditLogWriter replaces the library's FileAuditLogWriter, which backs off")
        void consumerWriterWins() {
            contextRunner.withUserConfiguration(ConsumerAuditLogWriterConfig.class).run(context -> {
                assertThat(context).hasSingleBean(AuditLogWriter.class);
                AuditLogWriter active = context.getBean(AuditLogWriter.class);
                assertThat(active).as("consumer's writer must win").isSameAs(ConsumerAuditLogWriterConfig.CONSUMER_WRITER);
                assertThat(active).as("library FileAuditLogWriter must NOT be the active writer").isNotInstanceOf(FileAuditLogWriter.class);
                assertThat(context).as("library FileAuditLogWriter must back off entirely").doesNotHaveBean(FileAuditLogWriter.class);
            });
        }
    }

    @Nested
    @DisplayName("Disabled audit: context starts even though no writer bean exists (regression: Task 3.5)")
    class DisabledAudit {

        /**
         * Regression guard for the Task 3.5 bug. When {@code user.audit.logEvents=false} the library's
         * {@link FileAuditLogWriter} bean is suppressed by {@code @ConditionalOnProperty}. The unconditional
         * {@link AuditEventListener} must still start, because its {@link AuditLogWriter} dependency is now resolved
         * through an {@link org.springframework.beans.factory.ObjectProvider} rather than a hard constructor injection.
         * Before the fix this configuration threw {@code UnsatisfiedDependencyException} ("No qualifying bean of type
         * 'AuditLogWriter' available") at context startup.
         */
        @Test
        @DisplayName("Context starts with AuditEventListener present and logEvents=false (no UnsatisfiedDependencyException)")
        void contextStartsWhenAuditDisabledAndListenerPresent() {
            contextRunner.withPropertyValues("user.audit.logEvents=false")
                    .withUserConfiguration(AuditEventListenerConfig.class)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(AuditEventListener.class);
                        assertThat(context).as("library FileAuditLogWriter must not be created when logEvents=false")
                                .doesNotHaveBean(FileAuditLogWriter.class);
                        assertThat(context).as("no AuditLogWriter bean of any kind should exist")
                                .doesNotHaveBean(AuditLogWriter.class);
                    });
        }
    }

    @Nested
    @DisplayName("Annotation contract on the auto-configuration bean method")
    class AnnotationContract {

        @Test
        @DisplayName("fileAuditLogWriter() is @ConditionalOnMissingBean")
        void writerIsConditional() throws Exception {
            Method method = AuditMailAutoConfiguration.class.getMethod("fileAuditLogWriter", AuditConfig.class);
            assertThat(method.getAnnotation(ConditionalOnMissingBean.class)).isNotNull();
        }
    }

    // ---- Consumer-supplied stand-in configuration. Not @Configuration so the integration tests' component scan does not pick it up. ----

    static class ConsumerAuditLogWriterConfig {
        static final AuditLogWriter CONSUMER_WRITER = new CustomAuditLogWriter();

        @Bean
        AuditLogWriter consumerAuditLogWriter() {
            return CONSUMER_WRITER;
        }
    }

    /**
     * Registers the real {@link AuditEventListener} so the disabled-audit regression test can prove the context starts
     * even when no {@link AuditLogWriter} bean exists. Spring supplies the {@code ObjectProvider<AuditLogWriter>}
     * automatically; {@link AuditConfig} is provided by the shared context runner.
     */
    static class AuditEventListenerConfig {
        @Bean
        AuditEventListener auditEventListener(AuditConfig auditConfig,
                org.springframework.beans.factory.ObjectProvider<AuditLogWriter> auditLogWriterProvider) {
            return new AuditEventListener(auditConfig, auditLogWriterProvider);
        }
    }

    /**
     * A trivial custom {@link AuditLogWriter} that is NOT a {@link FileAuditLogWriter}, so the test can assert the consumer's instance wins.
     */
    static class CustomAuditLogWriter implements AuditLogWriter {
        @Override
        public void writeLog(AuditEvent event) {}

        @Override
        public void setup() {}

        @Override
        public void cleanup() {}
    }
}
