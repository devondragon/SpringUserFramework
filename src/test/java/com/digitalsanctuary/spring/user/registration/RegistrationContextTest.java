package com.digitalsanctuary.spring.user.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RegistrationContext Tests")
class RegistrationContextTest {

    @Test
    @DisplayName("Should reject null source in RegistrationContext")
    void shouldRejectNullSource() {
        assertThatThrownBy(() -> new RegistrationContext("user@example.com", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("source must not be null");
    }

    @Test
    @DisplayName("Should accept valid context with all fields")
    void shouldAcceptValidContext() {
        RegistrationContext context = new RegistrationContext("user@example.com", RegistrationSource.OAUTH2, "google");

        assertThat(context.email()).isEqualTo("user@example.com");
        assertThat(context.source()).isEqualTo(RegistrationSource.OAUTH2);
        assertThat(context.providerName()).isEqualTo("google");
    }

    @Test
    @DisplayName("Should accept null email and null providerName")
    void shouldAcceptNullEmailAndProvider() {
        RegistrationContext context = new RegistrationContext(null, RegistrationSource.FORM, null);

        assertThat(context.email()).isNull();
        assertThat(context.source()).isEqualTo(RegistrationSource.FORM);
        assertThat(context.providerName()).isNull();
    }
}
