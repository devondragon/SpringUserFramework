package com.digitalsanctuary.spring.user.mail;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * The MailContentBuilder service renders Thymeleaf templates as rich emails, making use of the full templating engine for both HTML rendering and
 * dynamic content handling. .
 */
@Service
public class MailContentBuilder {

	/** The template engine. */
	private TemplateEngine templateEngine;

	/**
	 * Instantiates a new mail content builder.
	 *
	 * @param templateEngine the template engine
	 */
	public MailContentBuilder(TemplateEngine templateEngine) {
		this.templateEngine = templateEngine;
	}

	/**
	 * Builds the content output from a provided Thymeleaf template file path and name, and the Context to use for dynamic content and logic.
	 *
	 * @param pTemplateName the name, or path and name, for the Thymeleaf template to use.
	 * @param pContext the context
	 * @return the string of the output of processing the template. Typically HTML.
	 */
	public String build(String pTemplateName, Context pContext) {
		return templateEngine.process(pTemplateName, pContext);
	}

}
