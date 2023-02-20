package com.digitalsanctuary.spring.user.controller;

import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * The Main Page Controller for pages outside of the actual User Management Framework.
 */
@Slf4j
@Controller
public class PageController {

	@Autowired
	private MessageSource messages;

	/**
	 * Home Page.
	 *
	 * @return the string
	 */
	@GetMapping({"/", "/index.html"})
	public String index(final HttpServletRequest request, final ModelMap model, @RequestParam("messageKey") final Optional<String> messageKey) {
		log.debug("PageController.index: called with messageKey={}", messageKey.orElse(""));

		// If there is a messageKey GET param, we'll map that into a locale specific message and add that to the model
		Locale locale = request.getLocale();
		messageKey.ifPresent(key -> {
			String message = messages.getMessage(key, null, locale);
			model.addAttribute("message", message);
		});
		return "index";
	}

	/**
	 * An example Protected page
	 *
	 * @return the string
	 */
	@GetMapping("/protected.html")
	public String protectedPage() {
		return "protected";
	}

	/**
	 * An example Unprotected page.
	 *
	 * @return the string
	 */
	@GetMapping("/unprotected.html")
	public String unprotectedPage() {
		return "unprotected";
	}

}
