package com.digitalsanctuary.spring.user;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Registers the library's own message bundle ({@value #LIBRARY_BASENAME}) additively, without overriding the consuming application's
 * {@code spring.messages.basename}.
 *
 * <p>
 * Previously the library shipped a hardcoded {@code spring.messages.basename} default property, which clobbered the consumer's configured (or
 * conventional default) message bundle. This post-processor instead reads the existing {@code spring.messages.basename} from the environment (falling
 * back to Spring Boot's conventional default of {@code messages} when unset) and appends the library bundle to it, de-duplicated, with the library
 * bundle placed LAST so consumer message keys win on collisions.
 * </p>
 */
public class MessageSourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** The {@code spring.messages.basename} property key. */
    public static final String BASENAME_PROPERTY = "spring.messages.basename";

    /** Spring Boot's conventional default basename used when the consumer has not configured one. */
    public static final String DEFAULT_BASENAME = "messages";

    /** The library's own message bundle, appended last so consumer keys win on collisions. */
    public static final String LIBRARY_BASENAME = "messages/dsspringusermessages";

    /** Name of the property source this post-processor contributes. */
    private static final String PROPERTY_SOURCE_NAME = "dsSpringUserMessageBasename";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String existing = environment.getProperty(BASENAME_PROPERTY);
        String merged = mergeBasename(existing);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(BASENAME_PROPERTY, merged)));
    }

    /**
     * Merges the existing basename value (or Spring Boot's default when unset) with the library bundle, preserving order, de-duplicating, and placing the
     * library bundle last.
     *
     * @param existing the current {@code spring.messages.basename} value, or {@code null}/blank if unset
     * @return the merged, comma-joined basename list with {@value #LIBRARY_BASENAME} appended last
     */
    public static String mergeBasename(String existing) {
        Set<String> basenames = new LinkedHashSet<>();
        String base = StringUtils.hasText(existing) ? existing : DEFAULT_BASENAME;
        for (String name : base.split(",")) {
            String trimmed = name.trim();
            if (StringUtils.hasText(trimmed)) {
                basenames.add(trimmed);
            }
        }
        // Ensure the library bundle is last so consumer keys win on collisions.
        basenames.remove(LIBRARY_BASENAME);
        basenames.add(LIBRARY_BASENAME);
        return String.join(",", basenames);
    }

    @Override
    public int getOrder() {
        // Run late so any consumer-supplied basename (from application.properties/yml) is already visible in the environment.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
