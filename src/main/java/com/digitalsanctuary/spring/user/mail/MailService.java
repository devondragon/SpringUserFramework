package com.digitalsanctuary.spring.user.mail;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

/**
 * The MailService provides outbound email sending services on top of the Spring mail framework, and leverages Thymeleaf
 * templates for rich dynamic emails.
 */
@Service
public class MailService {

	/** The logger. */
	Logger logger = LoggerFactory.getLogger(MailService.class);

	/** The mail sender. */
	private JavaMailSender mailSender;

	/** The from address. */
	@Value("${user.mail.fromAddress}")
	private String fromAddress;

	/** The mail content builder. */
	@Autowired
	MailContentBuilder mailContentBuilder;

	/**
	 * Instantiates a new mail service.
	 *
	 * @param mailSender
	 *            the mail sender
	 */
	@Autowired
	public MailService(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	/**
	 * Send a simple plain text email.
	 *
	 * @param to
	 *            the to email address to send the mail to
	 * @param subject
	 *            the subject of the email
	 * @param text
	 *            the text to include as the email message body
	 */
	public void sendSimpleMessage(String to, String subject, String text) {
		MimeMessagePreparator messagePreparator = mimeMessage -> {
			MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage);
			messageHelper.setFrom(fromAddress);
			messageHelper.setTo(to);
			messageHelper.setSubject(subject);
			messageHelper.setText(text, true);
		};

		try {
			mailSender.send(messagePreparator);
		} catch (MailException e) {
			logger.error("MailService.sendSimpleMessage:" + "Error!", e);
		}
	}

	/**
	 * Send a dynamic Thymeleaf template driven email.
	 *
	 * @param to
	 *            the to email address to send the mail to
	 * @param subject
	 *            the subject of the email
	 * @param variables
	 *            a map of variables (key->value) to use in building the dynamic content via the template
	 * @param templatePath
	 *            the file name, or path and name, for the Thymeleaf template to use to build the dynamic email
	 */
	public void sendTemplateMessage(String to, String subject, Map<String, Object> variables, String templatePath) {
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
			mailSender.send(messagePreparator);
		} catch (MailException e) {
			logger.error("MailService.sendTemplateMessage:" + "Error!", e);
		}
	}
}
