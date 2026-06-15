package com.digitalsanctuary.spring.user.persistence.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import com.digitalsanctuary.spring.user.persistence.repository.PrivilegeRepository;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.test.annotations.DatabaseTest;

/**
 * Database-slice tests verifying that the UNIQUE + NOT NULL constraint on the {@code name} column of {@link Role} and
 * {@link Privilege} is enforced by the schema. Two roles (or two privileges) with the same name must not coexist.
 */
@DatabaseTest
class RolePrivilegeUniquenessTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PrivilegeRepository privilegeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldRejectDuplicateRoleNameWhenFlushed() {
        roleRepository.saveAndFlush(new Role("ROLE_DUPLICATE"));
        entityManager.clear();

        Role second = new Role("ROLE_DUPLICATE");

        assertThatThrownBy(() -> roleRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicatePrivilegeNameWhenFlushed() {
        privilegeRepository.saveAndFlush(new Privilege("DUPLICATE_PRIVILEGE"));
        entityManager.clear();

        Privilege second = new Privilege("DUPLICATE_PRIVILEGE");

        assertThatThrownBy(() -> privilegeRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
