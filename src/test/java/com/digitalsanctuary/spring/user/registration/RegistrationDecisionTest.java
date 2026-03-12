package com.digitalsanctuary.spring.user.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationDecision Tests")
class RegistrationDecisionTest {

    @Test
    @DisplayName("Should create allow decision with allowed=true and null reason")
    void shouldCreateAllowDecision() {
        RegistrationDecision decision = RegistrationDecision.allow();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isNull();
    }

    @Test
    @DisplayName("Should create deny decision with allowed=false and given reason")
    void shouldCreateDenyDecision() {
        RegistrationDecision decision = RegistrationDecision.deny("Invite code required");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("Invite code required");
    }
}
