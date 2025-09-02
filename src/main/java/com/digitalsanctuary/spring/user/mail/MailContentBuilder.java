package com.digitalsanctuary.spring.user.mail;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * The MailContentBuilder service renders Thymeleaf templates as rich emails, making use of the full templating engine for both HTML rendering and
 * dynamic content handling.
 * 
 * <p><strong>Required Dependency:</strong> This service requires Thymeleaf to be on the classpath and a TemplateEngine bean to be available.
 * Add the following dependency to your project:</p>
 * 
 * <pre>{@code
 * <dependency>
 *     <groupId>org.springframework.boot</groupId>
 *     <artifactId>spring-boot-starter-thymeleaf</artifactId>
 * </dependency>
 * }</pre>
 */
@Service
public class MailContentBuilder {

	/** The template engine. */
	private final TemplateEngine templateEngine;

	/**
	 * Instantiates a new mail content builder.
	 *
	 * @param templateEngine the template engine - provided by spring-boot-starter-thymeleaf
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if TemplateEngine bean is not available
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
