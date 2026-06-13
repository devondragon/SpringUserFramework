package com.digitalsanctuary.spring.user.persistence.model;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying that {@link User#toString()} does not leak sensitive material.
 *
 * <p>The {@code password} field holds the bcrypt hash and must never appear in log output, which is
 * commonly produced by toString(). This test guards the {@code @ToString.Exclude} annotation on that
 * field.</p>
 */
class UserToStringTest {

    @Test
    void shouldNotIncludePasswordHashWhenToStringCalled() {
        // Given a user with a (fake) password hash set
        User user = new User();
        user.setEmail("user@test.com");
        user.setPassword("SUPERSECRET_HASH");

        // When the user is rendered to a string (e.g. via a log statement)
        String rendered = user.toString();

        // Then the password hash must not be present
        assertThat(rendered).doesNotContain("SUPERSECRET_HASH");
    }
}
