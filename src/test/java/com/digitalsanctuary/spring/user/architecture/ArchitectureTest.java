package com.digitalsanctuary.spring.user.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * Architectural invariants enforced via ArchUnit. These rules document and protect the layering and conventions of the
 * library. Every rule here reflects a currently-true property of the codebase; do not add aspirational rules that fail.
 *
 * <p>
 * Analysis is scoped to the production package only ({@link ImportOption.DoNotIncludeTests}), so test fixtures and
 * scaffolding never affect the results.
 */
@AnalyzeClasses(packages = "com.digitalsanctuary.spring.user", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    /**
     * Persistence (JPA entities and repositories) is the lowest layer and must not reach upward into web, API,
     * controller, or service code.
     */
    @ArchTest
    static final ArchRule persistenceDoesNotDependOnUpperLayers = noClasses().that()
            .resideInAPackage("com.digitalsanctuary.spring.user.persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.digitalsanctuary.spring.user.api..", "com.digitalsanctuary.spring.user.controller..",
                    "com.digitalsanctuary.spring.user.service..")
            .because("persistence is the lowest layer and must not depend on web/API/service layers");

    /**
     * The service layer holds business logic and must not depend on the web-facing API or MVC controller layers.
     * Dependencies flow inward: api/controller may use service, never the reverse.
     */
    @ArchTest
    static final ArchRule serviceDoesNotDependOnWebLayers = noClasses().that()
            .resideInAPackage("com.digitalsanctuary.spring.user.service..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.digitalsanctuary.spring.user.api..", "com.digitalsanctuary.spring.user.controller..")
            .because("services contain business logic and must not depend on web-facing layers");

    /**
     * The library uses SLF4J (typically via Lombok's {@code @Slf4j}) for all logging and must not print to the
     * standard streams.
     */
    @ArchTest
    static final ArchRule noAccessToStandardStreams = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    /**
     * The library standardizes on SLF4J and must not use {@code java.util.logging} directly.
     */
    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
