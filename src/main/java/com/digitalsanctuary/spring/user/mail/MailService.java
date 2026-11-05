package com.digitalsanctuary.spring.user.mail;

import java.time.Duration;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.scheduling.annotation.Async;
import org.thymeleaf.context.Context;
import lombok.extern.slf4j.Slf4j;

/**
 * The MailService provides outbound email sending services on top of the Spring mail framework, and leverages Thymeleaf templates for rich dynamic
 * emails.
 *
 * <p>Email is treated as optional: if no {@link JavaMailSender} bean is available (typically because {@code spring.mail.host} is not configured),
 * a single warning is logged at startup and all send operations silently no-op, so the application starts and runs normally with email-dependent
 * features degraded.</p>
 *
 * <p>This class is not component-scanned; it is contributed as a consumer-overridable {@code @Bean} by
 * {@code AuditMailAutoConfiguration} so a consumer can replace mail delivery by supplying their own {@link MailService}
 * (or subclass) bean, which the library's default then backs off from via {@code @ConditionalOnMissingBean}.</p>
 */
@Slf4j
public class MailService {

	private final ObjectProvider<JavaMailSender> mailSenderProvider;

	private final MailContentBuilder mailContentBuilder;

	/** Retries each send on {@link MailException}; see {@link #defaultRetryTemplate()}. */
	private final RetryTemplate retryTemplate;

	/** Resolved once at startup; null when JavaMailSender is not configured. */
	private JavaMailSender resolvedSender;

	/** The from address. */
	@Value("${user.mail.fromAddress}")
	private String fromAddress;

	/**
	 * Instantiates a new mail service.
	 *
	 * @param mailSenderProvider provider for the mail sender; may resolve to null when mail is not configured
	 * @param mailContentBuilder the mail content builder
	 */
	public MailService(ObjectProvider<JavaMailSender> mailSenderProvider, MailContentBuilder mailContentBuilder) {
		this(mailSenderProvider, mailContentBuilder, defaultRetryTemplate());
	}

	/**
	 * Instantiates a new mail service with a specific retry template. Package-private so tests can use a template without
	 * backoff delays.
	 *
	 * @param mailSenderProvider provider for the mail sender; may resolve to null when mail is not configured
	 * @param mailContentBuilder the mail content builder
	 * @param retryTemplate the retry template used for each send
	 */
	MailService(ObjectProvider<JavaMailSender> mailSenderProvider, MailContentBuilder mailContentBuilder,
			RetryTemplate retryTemplate) {
		this.mailSenderProvider = mailSenderProvider;
		this.mailContentBuilder = mailContentBuilder;
		this.retryTemplate = retryTemplate;
	}

	/**
	 * Resolves and caches the {@link JavaMailSender} once at startup. Logs a single warning when no sender is available so operators are informed
	 * without flooding logs during normal operation.
	 */
	@PostConstruct
	void init() {
		resolvedSender = mailSenderProvider.getIfAvailable();
		if (resolvedSender == null) {
			log.warn("JavaMailSender is not configured — email sending is disabled. Set 'spring.mail.host' to enable.");
		} else if (fromAddress == null || fromAddress.isBlank()) {
			log.warn("JavaMailSender is configured but 'user.mail.fromAddress' is not set — outbound emails will have no valid sender address. "
					+ "Set 'user.mail.fromAddress' to a valid from address.");
		}
	}

	/**
	 * Send a simple plain text email.
	 *
	 * @param to the to email address to send the mail to
	 * @param subject the subject of the email
	 * @param text the text to include as the email message body
	 */
	@Async("dsMailExecutor")
	public void sendSimpleMessage(String to, String subject, String text) {
		if (resolvedSender == null) {
			return;
		}
		log.debug("Attempting to send simple email to: {}", to);

		MimeMessagePreparator messagePreparator = mimeMessage -> {
			MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage);
			messageHelper.setFrom(fromAddress);
			messageHelper.setTo(to);
			messageHelper.setSubject(subject);
			messageHelper.setText(text, true);
		};

		try {
			sendWithRetry(messagePreparator);
			log.debug("Successfully sent simple email to: {}", to);
		} catch (MailException ex) {
			recoverSendSimpleMessage(ex, to, subject, text);
		}
	}

	/**
	 * Send a dynamic Thymeleaf template driven email.
	 *
	 * @param to the to email address to send the mail to
	 * @param subject the subject of the email
	 * @param variables a map of variables (key->value) to use in building the dynamic content via the template
	 * @param templatePath the file name, or path and name, for the Thymeleaf template to use to build the dynamic email
	 */
	@Async("dsMailExecutor")
	public void sendTemplateMessage(String to, String subject, Map<String, Object> variables, String templatePath) {
		if (resolvedSender == null) {
			return;
		}
		log.debug("Attempting to send template email to: {}, template: {}", to, templatePath);

		MimeMessagePreparator messagePreparator = mimeMessage -> {
			MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage);
			messageHelper.setFrom(fromAddress);
			messageHelper.setTo(to);
			messageHelper.setSubject(subject);
			Context context = new Context();
			context.setVariables(variables);
			String content = mailContentBuilder.build(templatePath, context);
			messageHelper.setText(content, true);
		};

		try {
			sendWithRetry(messagePreparator);
			log.debug("Successfully sent template email to: {}", to);
		} catch (MailException ex) {
			recoverSendTemplateMessage(ex, to, subject, variables, templatePath);
		}
	}

	/**
	 * Sends the message through the resolved sender, retrying on {@link MailException} according to the retry template.
	 *
	 * @param messagePreparator the preparator for the message to send
	 * @throws MailException the last mail failure, once every attempt has failed
	 */
	private void sendWithRetry(MimeMessagePreparator messagePreparator) {
		try {
			retryTemplate.execute(() -> {
				resolvedSender.send(messagePreparator);
				return null;
			});
		} catch (RetryException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof InterruptedException interrupted) {
				// Spring Framework 7.0.0 and 7.0.1 report an interrupt during backoff with the InterruptedException as the
				// cause and the earlier MailException failures suppressed (7.0.2 and later report the last MailException as
				// the cause instead). Keep the interrupt flag and hand the last mail failure to the caller's recover path.
				Thread.currentThread().interrupt();
				throw lastMailException(ex, interrupted);
			}
			// The policy only retries MailException, so any other failure stops the template on the first attempt.
			// Rethrow the original exception unchanged so it propagates exactly as it would without retry.
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException("Unexpected checked exception while sending mail", cause);
		}
	}

	/**
	 * Returns the most recent {@link MailException} recorded on an interrupted retry, or a {@link MailSendException} wrapping the
	 * interrupt when none was recorded.
	 *
	 * @param ex the retry exception thrown when the backoff was interrupted
	 * @param interrupted the interrupt that stopped the backoff
	 * @return the mail failure to pass to the recover method
	 */
	private static MailException lastMailException(RetryException ex, InterruptedException interrupted) {
		Throwable[] suppressed = ex.getSuppressed();
		for (int i = suppressed.length - 1; i >= 0; i--) {
			if (suppressed[i] instanceof MailException mailException) {
				return mailException;
			}
		}
		return new MailSendException("Mail send interrupted during retry backoff", interrupted);
	}

	/**
	 * Builds the default retry template: 3 attempts in total (the first send plus 2 retries) for {@link MailException}, with
	 * exponential backoff starting at 1 second and doubling.
	 *
	 * @return the default mail retry template
	 */
	static RetryTemplate defaultRetryTemplate() {
		return new RetryTemplate(RetryPolicy.builder()
				.includes(MailException.class)
				.maxRetries(2)
				.delay(Duration.ofSeconds(1))
				.multiplier(2)
				.build());
	}

	/**
	 * Recovery method for sendSimpleMessage when all retry attempts are exhausted. Logs the failure and suppresses the
	 * exception. Subclasses may override this to change how an undeliverable message is handled.
	 *
	 * @param ex the exception that caused the failure
	 * @param to the to email address
	 * @param subject the subject
	 * @param text the text
	 */
	public void recoverSendSimpleMessage(MailException ex, String to, String subject, String text) {
		log.error("Failed to send simple email to {} after all retry attempts. Subject: '{}'. Error: {}",
				  to, subject, ex.getMessage());
	}

	/**
	 * Recovery method for sendTemplateMessage when all retry attempts are exhausted. Logs the failure and suppresses the
	 * exception. Subclasses may override this to change how an undeliverable message is handled.
	 *
	 * @param ex the exception that caused the failure
	 * @param to the to email address
	 * @param subject the subject
	 * @param variables the template variables
	 * @param templatePath the template path
	 */
	public void recoverSendTemplateMessage(MailException ex, String to, String subject,
										   Map<String, Object> variables, String templatePath) {
		log.error("Failed to send template email to {} after all retry attempts. Subject: '{}', Template: '{}'. Error: {}",
				  to, subject, templatePath, ex.getMessage());
	}
}
