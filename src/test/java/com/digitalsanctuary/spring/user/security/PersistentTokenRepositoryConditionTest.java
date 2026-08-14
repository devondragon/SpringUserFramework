package com.digitalsanctuary.spring.user.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.digitalsanctuary.spring.user.roles.RolesAndPrivilegesConfig;

/**
 * The persistent-token repository condition must relaxed-match every spelling of
 * {@code user.security.remember-me.use-persistent-tokens}: an exact-camelCase condition previously ignored the
 * kebab spelling advertised by the generated metadata, silently downgrading remember-me to hash-based tokens
 * (which {@code SessionInvalidationService} cannot revoke).
 */
@DisplayName("PersistentTokenRepository @ConditionalOnProperty spelling")
class PersistentTokenRepositoryConditionTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({UserSecurityConfigProperties.class, RememberMeConfigProperties.class,
            PasswordPolicyConfigProperties.class})
    static class PropertiesConfig {
    }

    // roleHierarchy() must produce a non-null bean for methodSecurityExpressionHandler's injection.
    private static RolesAndPrivilegesConfig roleConfig() {
        RolesAndPrivilegesConfig config = new RolesAndPrivilegesConfig();
        config.setRoleHierarchy(java.util.List.of("ROLE_ADMIN > ROLE_USER"));
        return config;
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            // Same attach SpringApplication performs, so relaxed @ConditionalOnProperty matching behaves as in a
            // real boot.
            .withInitializer(context -> ConfigurationPropertySources.attach(context.getEnvironment()))
            .withUserConfiguration(PropertiesConfig.class)
            .withBean(UserDetailsService.class, () -> mock(UserDetailsService.class))
            .withBean(RolesAndPrivilegesConfig.class, PersistentTokenRepositoryConditionTest::roleConfig)
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withConfiguration(AutoConfigurations.of(UserSecurityBeansAutoConfiguration.class));

    @Test
    void shouldNotCreateRepositoryWhenPersistentTokensUnset() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(PersistentTokenRepository.class));
    }

    @Test
    void shouldCreateRepositoryWhenKebabSpellingEnablesPersistentTokens() {
        contextRunner.withPropertyValues("user.security.remember-me.use-persistent-tokens=true")
                .run(context -> assertThat(context).hasSingleBean(PersistentTokenRepository.class));
    }

    @Test
    void shouldCreateRepositoryWhenCamelCaseSpellingEnablesPersistentTokens() {
        contextRunner.withPropertyValues("user.security.rememberMe.usePersistentTokens=true")
                .run(context -> assertThat(context).hasSingleBean(PersistentTokenRepository.class));
    }
}
