package com.digitalsanctuary.spring.user.test.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StatementCountInspector}. These exercise the counter mechanism directly (no Spring context, no Hibernate) so the thread-local
 * reset/count semantics that isolate {@code UserRepositoryEntityGraphTest} from parallel-execution pollution are locked in independently of that one
 * consumer test (GH-337).
 */
class StatementCountInspectorTest {

    @Test
    void shouldReportZeroCountAfterReset() {
        StatementCountInspector inspector = new StatementCountInspector();
        inspector.inspect("select 1");

        StatementCountInspector.reset();

        assertThat(StatementCountInspector.getCount()).isZero();
    }

    @Test
    void shouldIncrementCountForEachInspectCall() {
        StatementCountInspector inspector = new StatementCountInspector();
        StatementCountInspector.reset();

        inspector.inspect("select 1");
        inspector.inspect("select 2");
        inspector.inspect("select 3");

        assertThat(StatementCountInspector.getCount()).isEqualTo(3);
    }

    @Test
    void shouldReturnSqlUnchangedWhenInspecting() {
        StatementCountInspector inspector = new StatementCountInspector();
        String sql = "select * from users where email = ?";

        assertThat(inspector.inspect(sql)).isEqualTo(sql);
    }

    /**
     * The core property behind the GH-337 fix: one thread's counting — including its {@link StatementCountInspector#reset()} — must not perturb
     * another thread's count. Two threads are interleaved so that thread B fully resets and counts <em>after</em> thread A has counted but
     * <em>before</em> thread A reads its final value; thread A must still observe only its own statements.
     */
    @Test
    void shouldIsolateCountsBetweenThreads() throws InterruptedException {
        StatementCountInspector inspector = new StatementCountInspector();
        CountDownLatch threadAHasCounted = new CountDownLatch(1);
        CountDownLatch threadBIsDone = new CountDownLatch(1);
        AtomicInteger threadAFinalCount = new AtomicInteger(-1);
        AtomicInteger threadBFinalCount = new AtomicInteger(-1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread threadA = new Thread(() -> {
            try {
                StatementCountInspector.reset();
                inspector.inspect("a1");
                inspector.inspect("a2");
                inspector.inspect("a3");
                threadAHasCounted.countDown();
                assertThat(threadBIsDone.await(5, TimeUnit.SECONDS)).isTrue();
                // Thread B has since reset and counted on its own thread; A's count must be untouched.
                threadAFinalCount.set(StatementCountInspector.getCount());
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });

        Thread threadB = new Thread(() -> {
            try {
                assertThat(threadAHasCounted.await(5, TimeUnit.SECONDS)).isTrue();
                StatementCountInspector.reset();
                inspector.inspect("b1");
                inspector.inspect("b2");
                threadBFinalCount.set(StatementCountInspector.getCount());
                threadBIsDone.countDown();
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
                threadBIsDone.countDown();
            }
        });

        threadA.start();
        threadB.start();
        threadA.join(TimeUnit.SECONDS.toMillis(10));
        threadB.join(TimeUnit.SECONDS.toMillis(10));

        assertThat(failure.get()).isNull();
        assertThat(threadAFinalCount.get()).as("thread A must see only its own 3 statements").isEqualTo(3);
        assertThat(threadBFinalCount.get()).as("thread B must see only its own 2 statements").isEqualTo(2);
    }
}
