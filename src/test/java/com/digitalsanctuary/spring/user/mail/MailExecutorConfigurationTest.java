package com.digitalsanctuary.spring.user.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Verifies the dedicated bounded {@code dsMailExecutor} so an SMTP stall on the mail retry/backoff path cannot starve the shared default async
 * executor that the rest of the library's {@code @Async} work relies on.
 *
 * <p>
 * The bean-presence assertions run against a minimal {@link ApplicationContextRunner} that imports only {@link MailExecutorConfiguration}, so the test
 * never boots the full JPA/security context and avoids JPA-metamodel pollution.
 * </p>
 */
@DisplayName("Mail Executor Configuration Tests")
class MailExecutorConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Nested
    @DisplayName("Bean presence and bounded configuration")
    class BeanPresence {

        @Test
        @DisplayName("dsMailExecutor bean exists and is a ThreadPoolTaskExecutor")
        void dsMailExecutorBeanExists() {
            contextRunner.withUserConfiguration(MailExecutorConfiguration.class).run(context -> {
                assertThat(context).hasBean("dsMailExecutor");
                assertThat(context.getBean("dsMailExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
            });
        }

        @Test
        @DisplayName("dsMailExecutor is bounded with CallerRunsPolicy backpressure")
        void dsMailExecutorIsBounded() {
            contextRunner.withUserConfiguration(MailExecutorConfiguration.class).run(context -> {
                ThreadPoolTaskExecutor executor = context.getBean("dsMailExecutor", ThreadPoolTaskExecutor.class);
                assertThat(executor.getCorePoolSize()).isEqualTo(2);
                assertThat(executor.getMaxPoolSize()).isEqualTo(4);
                assertThat(executor.getQueueCapacity()).isEqualTo(50);
                assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                        .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
            });
        }
    }

    @Nested
    @DisplayName("Override behavior")
    class Override {

        @Test
        @DisplayName("Consumer can override dsMailExecutor by name")
        void consumerCanOverride() {
            ThreadPoolTaskExecutor custom = new ThreadPoolTaskExecutor();
            custom.initialize();
            contextRunner.withUserConfiguration(MailExecutorConfiguration.class)
                    .withBean("dsMailExecutor", ThreadPoolTaskExecutor.class, () -> custom)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ThreadPoolTaskExecutor.class);
                        assertThat(context.getBean("dsMailExecutor")).isSameAs(custom);
                    });
        }
    }

    @Nested
    @DisplayName("MailService @Async wiring")
    class AsyncWiring {

        @Test
        @DisplayName("sendSimpleMessage runs on the dsMailExecutor")
        void sendSimpleMessageQualified() throws Exception {
            Method method = MailService.class.getMethod("sendSimpleMessage", String.class, String.class, String.class);
            Async async = method.getAnnotation(Async.class);
            assertThat(async).isNotNull();
            assertThat(async.value()).isEqualTo("dsMailExecutor");
        }

        @Test
        @DisplayName("sendTemplateMessage runs on the dsMailExecutor")
        void sendTemplateMessageQualified() throws Exception {
            Method method = MailService.class.getMethod("sendTemplateMessage", String.class, String.class, java.util.Map.class, String.class);
            Async async = method.getAnnotation(Async.class);
            assertThat(async).isNotNull();
            assertThat(async.value()).isEqualTo("dsMailExecutor");
        }
    }
}
