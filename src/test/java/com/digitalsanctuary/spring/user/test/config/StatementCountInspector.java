package com.digitalsanctuary.spring.user.test.config;

import java.io.Serializable;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * A Hibernate {@link StatementInspector} that counts prepared JDBC statements on a <em>per-thread</em> basis.
 *
 * <p>
 * The test suite runs with JUnit 5 parallel execution, and {@code @DataJpaTest} classes share a single cached Spring context (and therefore a
 * single {@code SessionFactory} and its {@code SessionFactory}-global {@code Statistics} object). Reading
 * {@code Statistics#getPrepareStatementCount()} to guard against N+1 query explosions is therefore polluted by statements issued from tests running
 * concurrently on other threads: the counter is neither session-scoped nor transactional, so {@code @DataJpaTest} rollback does not isolate it.
 *
 * <p>
 * Because each parallel test executes on its own thread and JDBC connection, this inspector records its count in a {@link ThreadLocal}, yielding a
 * stable, per-test measurement that is immune to concurrent pollution. A single instance is registered on the shared test {@code SessionFactory} via
 * the {@code hibernate.session_factory.statement_inspector} property (see {@code application-test.properties}); the counter state is {@code static} so
 * the inspecting instance created by Hibernate and the assertions in a test share the same per-thread counter.
 *
 * <p>
 * Usage: call {@link #reset()} immediately before the code under measurement, then assert on {@link #getCount()}.
 */
public final class StatementCountInspector implements StatementInspector, Serializable {

    private static final long serialVersionUID = 1L;

    private static final ThreadLocal<Integer> COUNT = ThreadLocal.withInitial(() -> 0);

    /**
     * Resets the calling thread's statement counter to zero.
     */
    public static void reset() {
        COUNT.set(0);
    }

    /**
     * Returns the number of prepared statements inspected on the calling thread since the last {@link #reset()}.
     *
     * @return the per-thread prepared-statement count
     */
    public static int getCount() {
        return COUNT.get();
    }

    @Override
    public String inspect(String sql) {
        COUNT.set(COUNT.get() + 1);
        return sql;
    }
}
