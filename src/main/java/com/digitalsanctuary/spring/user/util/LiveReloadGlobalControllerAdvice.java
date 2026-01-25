package com.digitalsanctuary.spring.user.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Global controller advice that provides LiveReload configuration to all views.
 * <p>
 * This advice makes the LiveReload port available as a model attribute to all controllers,
 * enabling the layout template to include the LiveReload script in development environments.
 * The port is determined based on whether HTTPS is enabled.
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
