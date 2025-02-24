package com.digitalsanctuary.spring.user;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * {@code UserAutoConfigurationRegistrar} dynamically registers the base package of this library with Spring Boot to ensure that its entities,
 * repositories, and other Spring-managed components are properly detected and included in the application context.
 *
 * <p>
 * This class is designed to simplify the integration of the library into Spring Boot applications by automatically registering the library's base
 * package (<i>com.digitalsanctuary.spring.user</i>) for component scanning. It ensures that:
 * <ul>
 * <li>The library's repositories and entities are discovered and configured correctly.</li>
 * <li>The consuming application retains its ability to automatically detect its own repositories and entities.</li>
 * </ul>
 *
 * <p>
 * This approach avoids the need for the consuming application to manually specify the library's base package or manage complex configuration,
 * reducing setup effort and minimizing potential errors.
 *
 * <p>
 * <b>Note:</b> This solution leverages {@link AutoConfigurationPackages#register} to dynamically register the library's package during the
 * auto-configuration phase, ensuring compatibility with Spring Boot's component scanning and auto-configuration mechanisms.
 */
public class UserAutoConfigurationRegistrar implements ImportBeanDefinitionRegistrar {

    /**
     * Registers the library's base package (<i>com.digitalsanctuary.spring.user</i>) with the Spring application context to enable automatic
     * detection of entities, repositories, and other components provided by the library.
     *
     * @param importingClassMetadata metadata of the class that imports this registrar
     * @param registry the bean definition registry used to register the base package
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // Register the top-level package for the library
        AutoConfigurationPackages.register(registry, "com.digitalsanctuary.spring.user");
    }
}
