package com.digitalsanctuary.spring.user.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CompositeRegistrationGuard} verifying ordered, first-deny-wins composition.
 */
@DisplayName("CompositeRegistrationGuard Tests")
class CompositeRegistrationGuardTest {

    private static final RegistrationContext CONTEXT =
            new RegistrationContext("user@example.com", RegistrationSource.FORM, null);

    @Nested
    @DisplayName("Cardinality")
    class Cardinality {

        @Test
        @DisplayName("Zero custom guards (only default) allows")
        void zeroGuardsAllows() {
            CompositeRegistrationGuard composite = new CompositeRegistrationGuard(List.of());

            assertThat(composite.evaluate(CONTEXT).allowed()).isTrue();
        }

        @Test
        @DisplayName("Null delegate list is treated as empty and allows")
        void nullDelegatesAllows() {
            CompositeRegistrationGuard composite = new CompositeRegistrationGuard(null);

            assertThat(composite.evaluate(CONTEXT).allowed()).isTrue();
        }

        @Test
        @DisplayName("Single allowing guard allows")
        void singleAllowingGuardAllows() {
            RegistrationGuard guard = ctx -> RegistrationDecision.allow();
            CompositeRegistrationGuard composite = new CompositeRegistrationGuard(List.of(guard));

            assertThat(composite.evaluate(CONTEXT).allowed()).isTrue();
        }

        @Test
        @DisplayName("Single denying guard denies and propagates reason")
        void singleDenyingGuardDenies() {
            RegistrationGuard guard = ctx -> RegistrationDecision.deny("nope");
            CompositeRegistrationGuard composite = new CompositeRegistrationGuard(List.of(guard));

            RegistrationDecision decision = composite.evaluate(CONTEXT);

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.reason()).isEqualTo("nope");
        }
    }

    @Nested
    @DisplayName("Ordering (first-deny-wins)")
    class Ordering {

        @Test
        @DisplayName("All-allow guards proceed (allow)")
        void allAllowProceeds() {
            RegistrationGuard first = mock(RegistrationGuard.class);
            RegistrationGuard second = mock(RegistrationGuard.class);
            when(first.evaluate(CONTEXT)).thenReturn(RegistrationDecision.allow());
            when(second.evaluate(CONTEXT)).thenReturn(RegistrationDecision.allow());

            CompositeRegistrationGuard composite = new CompositeRegistrationGuard(List.of(first, second));

            assertThat(composite.evaluate(CONTEXT).allowed()).isTrue();
            verify(first).evaluate(CONTEXT);
            verify(second).evaluate(CONTEXT);
        }

        @Test
        @DisplayName("First guard denies: second is NOT consulted; first reason wins")
        void firstDenyShortCircuits() {
            RegistrationGuard first = mock(RegistrationGuard.class);
            RegistrationGuard second = mock(RegistrationGuard.class);
            when(first.evaluate(CONTEXT)).thenReturn(RegistrationDecision.deny("first denied"));

            CompositeRegistrationGuard composite = new CompositeRegistrationGuard(List.of(first, second));

            RegistrationDecision decision = composite.evaluate(CONTEXT);

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.reason()).isEqualTo("first denied");
            verify(first).evaluate(CONTEXT);
            // Short-circuit: the second guard must never be consulted once the first denies.
            verify(second, never()).evaluate(CONTEXT);
        }

        @Test
        @DisplayName("Second guard denies when first allows; second reason propagates")
        void secondDenyAfterFirstAllow() {
            RegistrationGuard first = mock(RegistrationGuard.class);
            RegistrationGuard second = mock(RegistrationGuard.class);
            when(first.evaluate(CONTEXT)).thenReturn(RegistrationDecision.allow());
            when(second.evaluate(CONTEXT)).thenReturn(RegistrationDecision.deny("second denied"));

            CompositeRegistrationGuard composite = new CompositeRegistrationGuard(List.of(first, second));

            RegistrationDecision decision = composite.evaluate(CONTEXT);

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.reason()).isEqualTo("second denied");
            verify(first).evaluate(CONTEXT);
            verify(second).evaluate(CONTEXT);
        }
    }

    @Nested
    @DisplayName("Null decisions (fail-fast)")
    class NullDecisions {

        @Test
        @DisplayName("A delegate returning null throws IllegalStateException instead of failing open")
        void nullDecisionThrows() {
            RegistrationGuard nullReturning = ctx -> null;
            CompositeRegistrationGuard composite = new CompositeRegistrationGuard(List.of(nullReturning));

            assertThatThrownBy(() -> composite.evaluate(CONTEXT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("null decision");
        }

        @Test
        @DisplayName("A later guard returning null still throws even after earlier guards allow")
        void nullDecisionAfterAllowThrows() {
            RegistrationGuard first = mock(RegistrationGuard.class);
            RegistrationGuard second = mock(RegistrationGuard.class);
            when(first.evaluate(CONTEXT)).thenReturn(RegistrationDecision.allow());
            when(second.evaluate(CONTEXT)).thenReturn(null);

            CompositeRegistrationGuard composite = new CompositeRegistrationGuard(List.of(first, second));

            assertThatThrownBy(() -> composite.evaluate(CONTEXT))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("getDelegates returns the composed guards in order")
    void getDelegatesReturnsGuardsInOrder() {
        RegistrationGuard first = ctx -> RegistrationDecision.allow();
        RegistrationGuard second = ctx -> RegistrationDecision.allow();

        CompositeRegistrationGuard composite = new CompositeRegistrationGuard(List.of(first, second));

        assertThat(composite.getDelegates()).containsExactly(first, second);
    }
}
