package com.digitalsanctuary.spring.user.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import lombok.Data;

/**
 * The UserWebConfig class is a Spring Boot configuration class that provides properties for configuring the user web interface. This class is used to
 * define properties that control the behavior of the user web interface, such as whether the user object is added to the model for all requests.
 */
@Data
@Component
@PropertySource("classpath:config/dsspringuserconfig.properties")
@ConfigurationProperties(prefix = "user.web")
public class UserWebConfig {

    /**
     * Controls global user model injection behavior for MVC controllers.
     * 
     * <p><strong>When set to {@code true} (Global Opt-In Mode):</strong></p>
     * <ul>
     *   <li>User object is automatically added to ALL MVC view models by default</li>
     *   <li>Use {@code @ExcludeUserFromModel} annotation to skip specific controllers/methods</li>
     *   <li>Best for traditional MVC applications where most views need user data</li>
     * </ul>
     * 
     * <p><strong>When set to {@code false} (Global Opt-Out Mode - DEFAULT):</strong></p>
     * <ul>
     *   <li>User object is NOT added to any view models by default</li>
     *   <li>Use {@code @IncludeUserInModel} annotation to add user to specific controllers/methods</li>
     *   <li>Best for REST-only applications or when you want minimal overhead</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> This only affects MVC controllers that return views, not REST controllers 
     * that return {@code @ResponseBody} data.</p>
     * 
     * @apiNote Default value is {@code false} (opt-out mode) to be suitable for REST-only applications
     */
    private boolean globalUserModelOptIn;

}
