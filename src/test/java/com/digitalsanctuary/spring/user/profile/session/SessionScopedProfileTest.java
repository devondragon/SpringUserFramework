package com.digitalsanctuary.spring.user.profile.session;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import com.digitalsanctuary.spring.user.profile.BaseUserProfile;

/**
 * Tests for the H7 fix: a concrete {@link BaseSessionProfile} subclass must be session-scoped so that one user's
 * profile does not leak across sessions.
 *
 * <p>
 * Two complementary proofs are provided:
 * </p>
 * <ul>
 * <li>A reflection assertion that the convenience meta-annotation {@link SessionScopedProfile} carries
 * {@code @Component} and a session-scoped {@code @Scope} with a {@code TARGET_CLASS} proxy.</li>
 * <li>A runtime proof, using a {@code session} scope backed by {@link SimpleThreadScope} (each thread acts as a
 * distinct "session"), that correctly scoped subclasses resolve to DISTINCT instances per session, while an
 * unscoped subclass falls into the singleton trap and SHARES a single instance across sessions.</li>
 * </ul>
 *
 * <p>
 * To avoid comparing the shared scoped proxy (which is the same object regardless of session), the test beans are
 * registered with {@link ScopedProxyMode#NO} and resolved on each session thread directly &mdash; with
 * {@link SimpleThreadScope} this returns the real per-session target instance, so identity comparison is meaningful.
 * The meta-annotation reflection test independently confirms the production scope/proxy configuration.
 * </p>
 */
@DisplayName("Session-scoped profile (H7) Tests")
class SessionScopedProfileTest {

    /** Minimal concrete profile type for the session profile to carry. */
    static class TestUserProfile extends BaseUserProfile {
    }

    /** Correctly scoped via the convenience meta-annotation (used by the reflection test). */
    @SessionScopedProfile
    static class MetaAnnotatedSessionProfile extends BaseSessionProfile<TestUserProfile> {
    }

    /** Correctly session-scoped, no proxy (so per-thread resolution returns the real target for identity checks). */
    @Component
    @Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.NO)
    static class ScopedNoProxySessionProfile extends BaseSessionProfile<TestUserProfile> {
    }

    /** INCORRECTLY scoped: only {@code @Component}, no {@code @Scope}. Demonstrates the singleton trap. */
    @Component
    static class UnscopedSessionProfile extends BaseSessionProfile<TestUserProfile> {
    }

    /**
     * Builds a context with a {@code session} scope backed by {@link SimpleThreadScope}, so each thread acts as a
     * distinct "session".
     */
    private AnnotationConfigApplicationContext sessionScopedContext(Class<?>... beanClasses) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerScope(WebApplicationContext.SCOPE_SESSION, new SimpleThreadScope());
        context.register(beanClasses);
        context.refresh();
        return context;
    }

    /** Resolves the bean on a fresh thread so that, with {@link SimpleThreadScope}, it represents a new session. */
    private <T> T resolveOnNewSession(AnnotationConfigApplicationContext context, Class<T> type) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<T> task = () -> context.getBean(type);
            return executor.submit(task).get();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("meta-annotation carries @Component and session @Scope with TARGET_CLASS proxy")
    void metaAnnotationCarriesCorrectScope() {
        // @Component is present (so it is a bean)
        assertThat(AnnotatedElementUtils.hasAnnotation(MetaAnnotatedSessionProfile.class, Component.class)).isTrue();

        // @Scope is present and configured for the session with a TARGET_CLASS proxy
        Scope scope = AnnotatedElementUtils.findMergedAnnotation(MetaAnnotatedSessionProfile.class, Scope.class);
        assertThat(scope).isNotNull();
        assertThat(scope.value()).isEqualTo(WebApplicationContext.SCOPE_SESSION);
        assertThat(scope.proxyMode()).isEqualTo(ScopedProxyMode.TARGET_CLASS);
    }

    @Test
    @DisplayName("correctly session-scoped subclass yields DISTINCT instances per session")
    void scopedProfileIsDistinctPerSession() throws Exception {
        try (AnnotationConfigApplicationContext context = sessionScopedContext(ScopedNoProxySessionProfile.class)) {
            ScopedNoProxySessionProfile sessionA = resolveOnNewSession(context, ScopedNoProxySessionProfile.class);
            ScopedNoProxySessionProfile sessionB = resolveOnNewSession(context, ScopedNoProxySessionProfile.class);

            assertThat(sessionA).isNotNull();
            assertThat(sessionB).isNotNull();
            assertThat(sessionA).isNotSameAs(sessionB);
        }
    }

    @Test
    @DisplayName("the singleton trap: an unscoped subclass SHARES one instance across sessions")
    void unscopedProfileIsSharedSingletonTrap() throws Exception {
        try (AnnotationConfigApplicationContext context = sessionScopedContext(UnscopedSessionProfile.class)) {
            UnscopedSessionProfile sessionA = resolveOnNewSession(context, UnscopedSessionProfile.class);
            UnscopedSessionProfile sessionB = resolveOnNewSession(context, UnscopedSessionProfile.class);

            // Same instance shared across sessions: this is the H7 vulnerability the fix warns against.
            assertThat(sessionA).isSameAs(sessionB);
        }
    }
}
