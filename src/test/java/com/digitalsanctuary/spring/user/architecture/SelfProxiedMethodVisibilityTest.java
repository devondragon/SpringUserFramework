package com.digitalsanctuary.spring.user.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.digitalsanctuary.spring.user.gdpr.GdprDeletionService;
import com.digitalsanctuary.spring.user.service.UserEmailService;
import com.digitalsanctuary.spring.user.service.UserService;

/**
 * Structural guard for the Spring self-invocation proxy pattern used across the service layer.
 *
 * <p>
 * Several services split a slow, non-transactional step (e.g. bcrypt hashing) from the short DB write by routing the
 * write back through their own Spring proxy: {@code self.persistX(...)} where {@code self} is an
 * {@code @Lazy @Autowired} reference to the same bean. For that to work the target method <strong>must be
 * {@code public} or {@code protected}</strong>: Spring generates the CGLIB proxy subclass in a <em>different</em>
 * package, so it can only override (and therefore advise + route) {@code public}/{@code protected} methods. A
 * <em>package-private</em> target is not overridden, so {@code self.persistX(...)} executes the inherited body on the
 * proxy instance — whose {@code @Autowired} fields were never populated — yielding a {@link NullPointerException} (e.g.
 * {@code "this.userRepository" is null}) and silently dropping the transaction.
 * </p>
 *
 * <p>
 * This bug is <strong>version-dependent at runtime</strong>: it reproduces on some Spring Framework patch releases and
 * is masked on others, so a {@code @SpringBootTest} on the CI's pinned Spring version cannot be relied on to catch a
 * regression. This bytecode-level visibility check is version-independent and fails fast if any self-proxied method is
 * ever made package-private (or private) again.
 * </p>
 *
 * <p>
 * Note: this rule is intentionally scoped to the specific methods invoked via {@code self}. Other package-private
 * {@code @Transactional} helpers (e.g. {@code RolePrivilegeSetupService.getOrCreateRole}) are called via {@code this}
 * from within an already-transactional method and run in the caller's transaction, so they do not need to be proxied.
 * </p>
 */
@DisplayName("Self-proxied (@Lazy self) transactional methods must be proxyable (public/protected)")
class SelfProxiedMethodVisibilityTest {

    /**
     * Every method that is invoked through a {@code self} proxy reference in the production code. Keep this list in
     * sync with the {@code self.<method>(...)} call sites in the service/gdpr packages.
     */
    static List<Arguments> selfProxiedMethods() {
        return List.of(Arguments.of(UserService.class, "persistNewUserAccount"),
                Arguments.of(UserService.class, "persistChangedPassword"),
                Arguments.of(UserService.class, "persistInitialPassword"),
                Arguments.of(GdprDeletionService.class, "executeUserDeletion"),
                Arguments.of(UserEmailService.class, "createPasswordResetTokenForUser"));
    }

    @ParameterizedTest(name = "{0}#{1} is public or protected")
    @MethodSource("selfProxiedMethods")
    void selfInvokedMethodMustBeProxyable(final Class<?> declaringClass, final String methodName) {
        final List<Method> matches = Arrays.stream(declaringClass.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName)).toList();

        assertThat(matches).as("expected to find method %s#%s — has it been renamed or removed? Update this guard.",
                declaringClass.getSimpleName(), methodName).isNotEmpty();

        for (final Method method : matches) {
            final int modifiers = method.getModifiers();
            assertThat(Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers))
                    .as("%s#%s is invoked through the Spring self-proxy and MUST be public or protected; a "
                            + "package-private/private method is not overridden by the CGLIB proxy subclass (generated "
                            + "in a different package), so the self-invocation runs on the un-injected proxy instance "
                            + "and throws NPE while silently losing the transaction.",
                            declaringClass.getSimpleName(), methodName)
                    .isTrue();
        }
    }
}
