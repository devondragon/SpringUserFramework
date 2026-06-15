package com.digitalsanctuary.spring.user.mail;

import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Provides the dedicated, bounded {@code dsMailExecutor} used to run the library's mail-sending {@code @Async} work.
 *
 * <p>
 * {@link MailService}'s send methods combine {@code @Async} with {@code @Retryable} (3 attempts with exponential backoff). If those methods shared
 * Spring Boot's default application task executor, an SMTP outage could occupy that shared pool for seconds at a time per message and starve every
 * other {@code @Async} feature in the library (event publishing, listeners, etc.). To prevent that, mail runs on its own small, bounded pool with a
 * bounded queue and a {@link CallerRunsPolicy} rejection handler &mdash; so when the mail pool and queue are both saturated, the calling thread runs
 * the task itself, applying backpressure rather than dropping mail or letting the queue grow without bound.
 * </p>
 *
 * <p>
 * The pool uses fixed, conservative defaults (core 2, max 4, queue 50). A consuming application that needs different sizing can supply its own
 * {@code dsMailExecutor} bean; the library's default then backs off via {@link ConditionalOnMissingBean}.
 * </p>
 */
@Configuration
public class MailExecutorConfiguration {

    /**
     * Creates the dedicated, bounded executor for mail-sending {@code @Async} work. Bounded core/max pool sizes plus a bounded queue and a
     * {@link CallerRunsPolicy} rejection handler ensure an SMTP stall applies backpressure to the caller instead of starving the shared default
     * async executor or queueing mail without limit.
     *
     * @return the bounded {@link ThreadPoolTaskExecutor} for mail
     */
    @Bean("dsMailExecutor")
    @ConditionalOnMissingBean(name = "dsMailExecutor")
    public ThreadPoolTaskExecutor dsMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ds-mail-");
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
