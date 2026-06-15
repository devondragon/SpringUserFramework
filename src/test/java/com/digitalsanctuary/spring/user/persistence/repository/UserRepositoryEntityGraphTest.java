package com.digitalsanctuary.spring.user.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import com.digitalsanctuary.spring.user.persistence.model.Privilege;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.test.annotations.DatabaseTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import jakarta.persistence.EntityManager;

/**
 * Database-slice tests verifying that {@link UserRepository#findWithRolesByEmail(String)} eagerly loads the
 * User &rarr; roles &rarr; privileges graph via {@code @EntityGraph} in a bounded number of queries, while the plain
 * {@link UserRepository#findByEmail(String)} leaves the now-LAZY {@code User.roles} collection uninitialized. This is the
 * regression guard for the EAGER &rarr; LAZY switch on {@code User.roles}. ({@code Role.privileges} remains EAGER.)
 */
@DatabaseTest
class UserRepositoryEntityGraphTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private void persistUserWithRolesAndPrivileges(String email) {
        Privilege read = new Privilege("READ_PRIVILEGE");
        Privilege write = new Privilege("WRITE_PRIVILEGE");
        entityManager.persist(read);
        entityManager.persist(write);

        Role userRole = new Role("ROLE_USER");
        Set<Privilege> userPrivileges = new HashSet<>();
        userPrivileges.add(read);
        userRole.setPrivileges(userPrivileges);

        Role adminRole = new Role("ROLE_ADMIN");
        Set<Privilege> adminPrivileges = new HashSet<>();
        adminPrivileges.add(read);
        adminPrivileges.add(write);
        adminRole.setPrivileges(adminPrivileges);

        entityManager.persist(userRole);
        entityManager.persist(adminRole);

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        roles.add(adminRole);
        User user = UserTestDataBuilder.aUser().withId(null).withEmail(email).build();
        user.setRolesAsSet(roles);
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();
    }

    private Statistics statistics() {
        EntityManager em = entityManager.getEntityManager();
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        return stats;
    }

    @Test
    void shouldInitializeRolesAndPrivilegesWhenLoadedViaEntityGraphFinder() {
        persistUserWithRolesAndPrivileges("graph@test.com");

        User user = userRepository.findWithRolesByEmail("graph@test.com");

        // Detach so any later access would hit a closed session if the graph were not initialized.
        entityManager.getEntityManager().detach(user);

        assertThat(user).isNotNull();
        assertThat(Hibernate.isInitialized(user.getRolesAsSet())).isTrue();
        for (Role role : user.getRolesAsSet()) {
            assertThat(Hibernate.isInitialized(role.getPrivileges())).isTrue();
        }
    }

    @Test
    void shouldAccessRolesAndPrivilegesWithoutLazyInitializationExceptionAfterDetach() {
        persistUserWithRolesAndPrivileges("detached@test.com");

        User user = userRepository.findWithRolesByEmail("detached@test.com");
        entityManager.getEntityManager().detach(user);

        // Traversing the full graph on a detached entity must not throw LazyInitializationException.
        assertThatCode(() -> {
            for (Role role : user.getRolesAsSet()) {
                role.getName();
                for (Privilege privilege : role.getPrivileges()) {
                    privilege.getName();
                }
            }
        }).doesNotThrowAnyException();

        assertThat(user.getRolesAsSet())
                .extracting(Role::getName)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void shouldNotInitializeRolesWhenLoadedViaPlainFindByEmail() {
        persistUserWithRolesAndPrivileges("lazy@test.com");

        User user = userRepository.findByEmail("lazy@test.com");

        // The plain finder must leave roles uninitialized, proving the User.roles EAGER -> LAZY switch took effect.
        assertThat(Hibernate.isInitialized(user.getRolesAsSet())).isFalse();
    }

    @Test
    void shouldLoadRolesAndPrivilegesInBoundedQueryCountViaEntityGraphFinder() {
        persistUserWithRolesAndPrivileges("bounded@test.com");

        Statistics stats = statistics();
        stats.clear();

        User user = userRepository.findWithRolesByEmail("bounded@test.com");
        // Force full traversal to prove no additional (N+1) queries are issued for privileges.
        int privilegeCount = 0;
        for (Role role : user.getRolesAsSet()) {
            privilegeCount += role.getPrivileges().size();
        }

        assertThat(privilegeCount).isPositive();
        // A single entity-graph fetch should not degenerate into a per-role / per-privilege N+1 explosion.
        // Allow a small bound to tolerate join-table fetch strategy differences across Hibernate versions.
        assertThat(stats.getPrepareStatementCount())
                .as("EntityGraph finder should load roles + privileges in a bounded number of queries")
                .isLessThanOrEqualTo(3);
    }
}
