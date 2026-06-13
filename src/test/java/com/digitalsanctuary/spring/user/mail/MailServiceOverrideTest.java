package com.digitalsanctuary.spring.user.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import com.digitalsanctuary.spring.user.audit.AuditMailAutoConfiguration;

/**
 * Proves that the library's {@link MailService} is genuinely replaceable by its concrete type.
 *
 * <p>
 * Per the Task 3.5 scope, {@link MailService} keeps its concrete type (no interface is extracted) so existing injectors that depend on the concrete
 * type are unaffected. A consumer overrides mail delivery by supplying their own {@link MailService} (typically a subclass) bean; the library's default
 * then backs off via {@code @ConditionalOnMissingBean(MailService.class)}. Following the H8/Task 3.3 lesson, the default lives on an
 * {@code @AutoConfiguration} class (loading AFTER user beans) so the override reliably wins.
 * </p>
 *
 * <p>
 * The test is isolated: it drives {@link AuditMailAutoConfiguration} through an {@link ApplicationContextRunner} with mocked collaborators
 * ({@link MailContentBuilder} and an {@link ObjectProvider} of {@link JavaMailSender}), so it never boots the full JPA/security context.
 * </p>
 */
@DisplayName("MailService Override Tests")
class MailServiceOverrideTest {

    /**
     * Drives the real {@link AuditMailAutoConfiguration}. A {@link MailContentBuilder} and an empty {@link ObjectProvider} of {@link JavaMailSender} are
     * supplied because the {@link MailService} {@code @Bean} method depends on them. {@code user.mail.fromAddress} is set so the {@code @Value} field on
     * a default-created {@link MailService} resolves.
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("user.mail.fromAddress=test@example.com", "user.audit.logEvents=false")
            .withBean(MailContentBuilder.class, () -> mock(MailContentBuilder.class))
            .withBean("javaMailSender", JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withConfiguration(AutoConfigurations.of(AuditMailAutoConfiguration.class));

    @Nested
    @DisplayName("Default behavior: library MailService present when consumer supplies none")
    class Defaults {

        @Test
        @DisplayName("Library provides a MailService by default")
        void libraryMailServicePresentByDefault() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(MailService.class);
                // The library's default is exactly MailService (not a subclass).
                assertThat(context.getBean(MailService.class).getClass()).isEqualTo(MailService.class);
            });
        }
    }

    @Nested
    @DisplayName("Override behavior: consumer MailService wins")
    class Overrides {

        @Test
        @DisplayName("Consumer MailService subclass replaces the library's default")
        void consumerMailServiceWins() {
            contextRunner.withUserConfiguration(ConsumerMailServiceConfig.class).run(context -> {
                assertThat(context).hasSingleBean(MailService.class);
                MailService active = context.getBean(MailService.class);
                assertThat(active).as("consumer's mail service must win").isSameAs(ConsumerMailServiceConfig.CONSUMER_MAIL_SERVICE);
                assertThat(active).as("consumer's mail service is a subclass, proving it is not the library default").isInstanceOf(CustomMailService.class);
            });
        }
    }

    @Nested
    @DisplayName("Annotation contract on the auto-configuration bean method")
    class AnnotationContract {

        @Test
        @DisplayName("mailService() is @ConditionalOnMissingBean")
        void mailServiceIsConditional() throws Exception {
            Method method = AuditMailAutoConfiguration.class.getMethod("mailService", ObjectProvider.class, MailContentBuilder.class);
            assertThat(method.getAnnotation(ConditionalOnMissingBean.class)).isNotNull();
        }
    }

    // ---- Consumer-supplied stand-in configuration. Not @Configuration so the integration tests' component scan does not pick it up. ----

    static class ConsumerMailServiceConfig {
        static final MailService CONSUMER_MAIL_SERVICE = new CustomMailService();

        @Bean
        MailService consumerMailService() {
            return CONSUMER_MAIL_SERVICE;
        }
    }

    /**
     * A consumer subclass of {@link MailService}, proving a consumer override of the concrete type wins. Constructed with nulls because this stand-in is
     * never invoked to send mail in the test; {@code init()} is overridden to a no-op so the parent's {@code @PostConstruct} does not dereference the
     * null provider.
     */
    static class CustomMailService extends MailService {
        CustomMailService() {
            super(null, null);
        }

        @Override
        void init() {
            // no-op: avoids the parent @PostConstruct touching the null provider in this stand-in
        }
    }
}
