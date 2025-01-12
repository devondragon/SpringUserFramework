package com.digitalsanctuary.spring.user.web;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.WebRequest;
import lombok.RequiredArgsConstructor;

/**
 * Global advice to handle common model attributes across all controllers.
 */
@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class GlobalMessageControllerAdvice {
    private final MessageSource messages;

    /**
     * Adds a localized message to the model if a `messageKey` GET parameter is present.
     *
     * @param request the web request
     * @param model the model
     */
    @ModelAttribute
    public void addMessageFromKey(WebRequest request, org.springframework.ui.Model model) {
        // Retrieve the `messageKey` parameter from the request
        String messageKey = request.getParameter("messageKey");
        if (messageKey != null) {
            Locale locale = request.getLocale();
            String message = messages.getMessage(messageKey, null, locale);
            model.addAttribute("message", message);
        }
    }
}
