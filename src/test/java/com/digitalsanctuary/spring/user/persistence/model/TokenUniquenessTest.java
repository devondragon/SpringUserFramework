package com.digitalsanctuary.spring.user.persistence.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import com.digitalsanctuary.spring.user.persistence.repository.PasswordResetTokenRepository;
import com.digitalsanctuary.spring.user.persistence.repository.VerificationTokenRepository;
import com.digitalsanctuary.spring.user.test.annotations.DatabaseTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

/**
 * Database-slice tests verifying that the UNIQUE + NOT NULL constraint on the {@code token} column
 * of {@link PasswordResetToken} and {@link VerificationToken} is enforced by the schema. Two tokens
 * with the same value must not coexist in either table.
 */
@DatabaseTest
class TokenUniquenessTest {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email) {
        User user = UserTestDataBuilder.aUser().withId(null).withEmail(email).build();
        return entityManager.persistAndFlush(user);
    }

    @Test
    void shouldRejectDuplicateTokenValueWhenPasswordResetTokenIsFlushed() {
        User user1 = persistUser("prt-unique1@test.com");
        User user2 = persistUser("prt-unique2@test.com");
        String duplicateToken = "duplicate-hash-value-prt";

        PasswordResetToken first = new PasswordResetToken(duplicateToken, user1);
        passwordResetTokenRepository.saveAndFlush(first);

        PasswordResetToken second = new PasswordResetToken(duplicateToken, user2);

        assertThatThrownBy(() -> passwordResetTokenRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicateTokenValueWhenVerificationTokenIsFlushed() {
        User user1 = persistUser("vt-unique1@test.com");
        User user2 = persistUser("vt-unique2@test.com");
        String duplicateToken = "duplicate-hash-value-vt";

        VerificationToken first = new VerificationToken(duplicateToken, user1);
        verificationTokenRepository.saveAndFlush(first);

        VerificationToken second = new VerificationToken(duplicateToken, user2);

        assertThatThrownBy(() -> verificationTokenRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
