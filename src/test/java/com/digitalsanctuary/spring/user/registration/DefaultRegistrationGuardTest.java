package com.digitalsanctuary.spring.user.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultRegistrationGuard Tests")
class DefaultRegistrationGuardTest {

    private final DefaultRegistrationGuard guard = new DefaultRegistrationGuard();

    @Test
    @DisplayName("Should allow registration for all source types")
    void shouldAlwaysAllow() {
        for (RegistrationSource source : RegistrationSource.values()) {
            RegistrationContext context = new RegistrationContext("user@example.com", source,
                    source == RegistrationSource.OAUTH2 ? "google" : null);

            RegistrationDecision decision = guard.evaluate(context);

            assertThat(decision.allowed()).as("Should allow %s registration", source).isTrue();
            assertThat(decision.reason()).as("Reason should be null for %s", source).isNull();
        }
    }
}
