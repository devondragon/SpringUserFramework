package com.digitalsanctuary.spring.user.audit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import com.digitalsanctuary.spring.user.UserConfiguration;
import com.digitalsanctuary.spring.user.mail.MailContentBuilder;
import com.digitalsanctuary.spring.user.mail.MailService;

/**
 * Auto-configuration that contributes the library's two consumer-overridable extension-point beans for audit logging and email delivery: the default
 * {@link AuditLogWriter} (a {@link FileAuditLogWriter}) and the default {@link MailService}.
 *
 * <p>
 * Both beans are guarded by {@link ConditionalOnMissingBean}, so a consuming application can fully replace either of them simply by defining their own
 * bean of the same type. A consumer can route audit events to a database, REST endpoint, or SIEM by supplying their own {@link AuditLogWriter}, and can
 * replace mail delivery by supplying their own {@link MailService} (typically a subclass). When the consumer defines no such bean, the library's default
 * applies and behavior is unchanged.
 * </p>
 *
 * <p>
 * These beans live on an {@code @AutoConfiguration} class &mdash; rather than as a component-scanned {@code @Component}/{@code @Service} &mdash; precisely
 * because {@code @ConditionalOnMissingBean} is only reliable on auto-configuration classes, which are guaranteed to load AFTER user-defined bean
 * definitions. Placing the conditional on a component-scanned stereotype would evaluate it too early and could suppress the consumer's override or cause a
 * bean-definition conflict (the H8 finding). This mirrors {@link com.digitalsanctuary.spring.user.security.UserSecurityBeansAutoConfiguration}.
 * </p>
 *
 * <p>
 * Bean-method-produced instances retain full Spring lifecycle support: {@link FileAuditLogWriter}'s {@code @PostConstruct setup()} /
 * {@code @PreDestroy cleanup()} and {@link MailService}'s {@code @PostConstruct init()}, {@code @Value} {@code fromAddress} field injection, and
 * {@code @Async}/{@code @Retryable} AOP proxying all still apply to {@code @Bean}-produced objects.
 * </p>
 */
@AutoConfiguration(after = UserConfiguration.class)
public class AuditMailAutoConfiguration {

    /**
     * Creates the library's default {@link AuditLogWriter}, a {@link FileAuditLogWriter} that writes pipe-delimited audit events to a log file. Backs
     * off entirely if the consuming application defines its own {@link AuditLogWriter}.
     *
     * <p>
     * Gated by {@code user.audit.logEvents} (default {@code true}): when audit logging is disabled the writer bean is not created at all, which in turn
     * lets {@link FileAuditLogFlushScheduler} back off too. Runtime write paths ({@link AuditEventListener}) already short-circuit on the same flag, so
     * this gate simply avoids creating an unused file-handle-owning bean.
     * </p>
     *
     * @param auditConfig the audit configuration properties
     * @return the default {@link FileAuditLogWriter}
     */
    @Bean
    @ConditionalOnMissingBean(AuditLogWriter.class)
    @ConditionalOnProperty(name = "user.audit.logEvents", havingValue = "true", matchIfMissing = true)
    public FileAuditLogWriter fileAuditLogWriter(AuditConfig auditConfig) {
        return new FileAuditLogWriter(auditConfig);
    }

    /**
     * Creates the {@link FileAuditLogFlushScheduler} that periodically flushes the {@link FileAuditLogWriter} buffer to disk.
     *
     * <p>
     * Only created when the library's {@link FileAuditLogWriter} is present ({@link ConditionalOnBean}) &mdash; so it backs off cleanly when a consumer
     * replaces the writer with their own {@link AuditLogWriter} &mdash; and only when audit logging is enabled and flush-on-write is disabled, because
     * immediate flushing makes the scheduler unnecessary. Declared after {@link #fileAuditLogWriter(AuditConfig)} so {@code @ConditionalOnBean} reliably
     * observes the writer.
     * </p>
     *
     * @param fileAuditLogWriter the library's file audit log writer
     * @return the flush scheduler
     */
    @Bean
    @ConditionalOnBean(FileAuditLogWriter.class)
    @ConditionalOnExpression("${user.audit.logEvents:true} && !${user.audit.flushOnWrite:true}")
    public FileAuditLogFlushScheduler fileAuditLogFlushScheduler(FileAuditLogWriter fileAuditLogWriter) {
        return new FileAuditLogFlushScheduler(fileAuditLogWriter);
    }

    /**
     * Creates the library's default {@link MailService}. Backs off entirely if the consuming application defines its own {@link MailService} (or a
     * subclass), keeping the concrete type so existing injectors are unaffected.
     *
     * @param mailSenderProvider provider for the mail sender; may resolve to null when mail is not configured
     * @param mailContentBuilder the mail content builder
     * @return the default {@link MailService}
     */
    @Bean
    @ConditionalOnMissingBean(MailService.class)
    public MailService mailService(ObjectProvider<JavaMailSender> mailSenderProvider, MailContentBuilder mailContentBuilder) {
        return new MailService(mailSenderProvider, mailContentBuilder);
    }
}
