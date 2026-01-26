package com.digitalsanctuary.spring.user.web;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.WebRequest;
import lombok.RequiredArgsConstructor;

/**
 * Controller advice that provides global model attributes across all MVC controllers.
 *
 * <p>This class handles automatic resolution of message keys from request parameters,
 * allowing controllers to redirect with a {@code messageKey} parameter that gets
 * automatically resolved to a localized message and added to the model.</p>
 *
 * <p>Usage example: Redirect to {@code /somepage?messageKey=user.created.success} and
 * the resolved message will be available as the {@code message} model attribute.</p>
 *
 * @author Devon Hillard
 * @see org.springframework.web.bind.annotation.ControllerAdvice
 * @see org.springframework.context.MessageSource
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
            // Use the messageKey itself as the default if no translation is found
            String message = messages.getMessage(messageKey, null, messageKey, locale);
            model.addAttribute("message", message);
        }
    }
}
