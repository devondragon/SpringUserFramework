package com.digitalsanctuary.spring.user.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Provides a global advice for controllers to include LiveReload configuration details.
 * <p>
 * This advice will make the LiveReload port available to all controllers and views in the application. It is used on the layout.html template to
 * include the LiveReload script in dev and local environments.
 * </p>
 *
 * @author Devon Hillard
 */
@ControllerAdvice
public class LiveReloadGlobalControllerAdvice {

    /**
     * Flag to determine if the application is being accessed over HTTPS.
     */
    @Value("${spring.devtools.livereload.https:false}")
    private boolean isHttps;

    /**
     * Provides the appropriate LiveReload port based on the application's protocol.
     * <p>
     * If the application is running over HTTPS, the port will be {@code 35739}. Otherwise, it will be {@code 35729}.
     * </p>
     *
     * @return The appropriate LiveReload port.
     */
    @ModelAttribute("liveReloadPort")
    public int liveReloadPort() {
        return isHttps ? 35739 : 35729;
    }

}
