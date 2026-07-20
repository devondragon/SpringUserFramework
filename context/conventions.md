# Project Conventions

This file is read by ccmagic skills (`review`, `codex-review`, `pr-feedback`, `push`, `quick`) for project-specific guidance.

## Coding standards

- Language: Java 21+ (Spring Boot 4.0) / Java 17+ (Spring Boot 3.5)
- Build tool: Gradle (`./gradlew build`, `./gradlew check`)
- Style guide: 4-space indentation, alphabetical imports (no wildcards), JavaDoc on public classes/methods
- Test framework: JUnit 5 (parallel execution) with AssertJ fluent assertions, spring-security-test, Testcontainers, ArchUnit

## Patterns

- **This is a library, not an app.** All Spring Boot starters are `compileOnly` — never add them as `implementation` dependencies.
- Logging: SLF4J via Lombok `@Slf4j`
- DI: `@RequiredArgsConstructor` with `final` fields
- Test naming: `should[ExpectedBehavior]When[Condition]`
- Use custom test annotations (`@ServiceTest`, `@DatabaseTest`, `@IntegrationTest`, `@SecurityTest`, `@OAuth2Test`) instead of raw Spring annotations.
- Event-driven architecture — don't bypass application events.
- Do not manually edit version numbers; the release process handles versioning.

## What ccmagic skills check against

- `/ccmagic:review` and `/ccmagic:codex-review` flag deviations from documented rules here.
- `/ccmagic:push` respects commit message conventions noted here (Conventional Commits — see recent git history).

> Tip: run `/ccmagic:map-codebase` to auto-extract conventions from the codebase into `context/knowledge/CONVENTIONS.md`. Use this file for *team-decided* conventions; the knowledge file for *observed* patterns.
