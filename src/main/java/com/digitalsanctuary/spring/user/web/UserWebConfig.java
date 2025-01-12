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
     * The global user model opt in flag. This flag determines whether the user object is added to the model for all requests. If set to false, the
     * default, then the user object will be added to all requests unless the Controller or Controller method has the {@code @ExcludeUserFromModel}
     * annotation. If set to true, then the user object will only be added to the model if the Controller or Controller method has the
     * {@code @IncludeUserInModel} annotation.
     */
    private boolean globalUserModelOptIn;

}
