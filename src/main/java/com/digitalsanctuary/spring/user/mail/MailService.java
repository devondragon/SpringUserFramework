package com.digitalsanctuary.spring.user.mail;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import lombok.extern.slf4j.Slf4j;

/**
 * The MailService provides outbound email sending services on top of the Spring mail framework, and leverages Thymeleaf templates for rich dynamic
 * emails.
 */
@Slf4j
@Service
public class MailService {

	/** The mail sender. */
	private final JavaMailSender mailSender;

	/** The mail content builder. */
	private final MailContentBuilder mailContentBuilder;

	/** The from address. */
	@Value("${user.mail.fromAddress}")
	private String fromAddress;

	/**
	 * Instantiates a new mail service.
	 *
	 * @param mailSender the mail sender
	 * @param mailContentBuilder the mail content builder
	 */
	public MailService(JavaMailSender mailSender, MailContentBuilder mailContentBuilder) {
		this.mailSender = mailSender;
		this.mailContentBuilder = mailContentBuilder;
	}

	/**
	 * Send a simple plain text email.
	 *
	 * @param to the to email address to send the mail to
	 * @param subject the subject of the email
	 * @param text the text to include as the email message body
	 */
	@Async
	@Retryable(retryFor = {MailException.class}, maxAttempts = 3, 
			   backoff = @Backoff(delay = 1000, multiplier = 2))
	public void sendSimpleMessage(String to, String subject, String text) {
		log.debug("Attempting to send simple email to: {}", to);
		
		MimeMessagePreparator messagePreparator = mimeMessage -> {
			MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage);
			messageHelper.setFrom(fromAddress);
			messageHelper.setTo(to);
			messageHelper.setSubject(subject);
			messageHelper.setText(text, true);
		};

		mailSender.send(messagePreparator);
		log.debug("Successfully sent simple email to: {}", to);
	}

	/**
	 * Send a dynamic Thymeleaf template driven email.
	 *
	 * @param to the to email address to send the mail to
	 * @param subject the subject of the email
	 * @param variables a map of variables (key->value) to use in building the dynamic content via the template
	 * @param templatePath the file name, or path and name, for the Thymeleaf template to use to build the dynamic email
	 */
	@Async
	@Retryable(retryFor = {MailException.class}, maxAttempts = 3, 
			   backoff = @Backoff(delay = 1000, multiplier = 2))
	public void sendTemplateMessage(String to, String subject, Map<String, Object> variables, String templatePath) {
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
		
		mailSender.send(messagePreparator);
		log.debug("Successfully sent template email to: {}", to);
	}

	/**
	 * Recovery method for sendSimpleMessage when all retry attempts are exhausted.
	 *
	 * @param ex the exception that caused the failure
	 * @param to the to email address
	 * @param subject the subject
	 * @param text the text
	 */
	@Recover
	public void recoverSendSimpleMessage(MailException ex, String to, String subject, String text) {
		log.error("Failed to send simple email to {} after all retry attempts. Subject: '{}'. Error: {}", 
				  to, subject, ex.getMessage());
	}

	/**
	 * Recovery method for sendTemplateMessage when all retry attempts are exhausted.
	 *
	 * @param ex the exception that caused the failure
	 * @param to the to email address
	 * @param subject the subject
	 * @param variables the template variables
	 * @param templatePath the template path
	 */
	@Recover
	public void recoverSendTemplateMessage(MailException ex, String to, String subject, 
										   Map<String, Object> variables, String templatePath) {
		log.error("Failed to send template email to {} after all retry attempts. Subject: '{}', Template: '{}'. Error: {}", 
				  to, subject, templatePath, ex.getMessage());
	}
}
