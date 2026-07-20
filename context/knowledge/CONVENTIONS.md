# Coding Conventions

> Verified by reading actual source in `src/main/java` and `src/test/java`, not just restating CLAUDE.md. Where the real codebase diverges from what CLAUDE.md claims, that's called out explicitly below.

## Naming Conventions
- **Files**: One public type per file, file name matches the public class/interface/enum/annotation name exactly (e.g. `UserService.java`, `PasswordMatches.java`, `ServiceTest.java`). Package-level docs live in `package-info.java` (e.g. `src/main/java/com/digitalsanctuary/spring/user/service/package-info.java`, `src/main/java/com/digitalsanctuary/spring/user/dto/package-info.java`).
- **Classes**: `UpperCamelCase` nouns. Suffix conveys role: `*Service` (`UserService`, `GdprDeletionService`), `*Controller` (`UserActionController`, `UserPageController`), `*API` (`UserAPI`, `GdprAPI` — all-caps "API" acronym, not `Api`), `*Repository` (`UserRepository`), `*Event` (`UserDeletedEvent`, `OnRegistrationCompleteEvent`), `*Listener` (`RegistrationListener`, `AuthenticationEventListener`), `*Exception` (`UserAlreadyExistException`), `*Dto`/`*DTO` (both casings coexist: `UserDto.java` vs `AuditEventDTO.java`, `GdprExportDTO.java`), `*Config`/`*Configuration` (`GdprConfig`, `MfaConfiguration`), `*AutoConfiguration` for Spring Boot auto-config entry points (`UserSecurityBeansAutoConfiguration`).
- **Methods**: `lowerCamelCase`, verb-led (`registerNewUserAccount`, `findUserByEmail`, `changeUserPassword`, `deleteOrDisableUser`). Boolean-returning methods use `is`/`has` prefixes (`isDataExported`, `emailExists`... note `emailExists` breaks the `is/has` prefix pattern, so it's not universal). Private helper methods are typically verb phrases scoped to the class (`authenticateUser`, `storeSecurityContextInSession` in `UserService`).
- **Variables**: `lowerCamelCase`, descriptive, rarely abbreviated (`userRepository`, `passwordTokenRepository`, `registrationPendingURI`). Fields injected via constructor are `private final` (occasionally `public final`, e.g. `UserService.userEmailService`/`userVerificationService` are `public final` rather than `private final` — a deliberate but inconsistent exception, see `src/main/java/com/digitalsanctuary/spring/user/service/UserService.java:228-232`).
- **Constants**: `UPPER_SNAKE_CASE` on `private static final` (or `public static final`) fields, e.g. `USER_ROLE_NAME`, `AUTH_MESSAGE_PREFIX` (`UserActionController.java:43`), `ERROR_CODE_REGISTRATION_DENIED` (`UserAPI.java`), `EXPORT_FORMAT_VERSION`, `DEFAULT_ACTION_DENY`. `serialVersionUID` is the lower-camel exception mandated by the `Serializable` contract.

## Code Style
- **Indentation**: CLAUDE.md doesn't document this, and there is no `.editorconfig`, checkstyle, or spotless config in the repo (`build.gradle` has no formatting plugin). In practice the codebase is **mixed**: roughly 65 of 158 main source files are tab-indented (legacy code — e.g. `UserService.java`, `User.java`, `WebSecurityConfig.java`, most of `persistence/model` and `persistence/repository`, `UserActionController.java`) while the rest use 4-space indentation. Newer/actively-touched code strongly favors 4 spaces: e.g. `GlobalValidationExceptionHandler.java`, `AuditEventDTO.java`, `UserDeletedEvent.java`, and the test suite (122 of 148 test files are space-indented vs. only 25 tab-indented; test annotation/config classes under `test/config` and `test/annotations` are all 4-space). No file mixes both styles internally. Treat "4 spaces" as the convention for new code, but expect tabs when editing older files — match the existing file's style rather than reformatting it wholesale.
- **Imports**: No wildcard imports as the general rule, with a couple of verified exceptions: `import jakarta.persistence.*;` in `src/main/java/com/digitalsanctuary/spring/user/persistence/model/User.java:12`, and `import java.lang.annotation.*;` used in every custom test annotation (`ServiceTest.java`, `DatabaseTest.java`, `IntegrationTest.java`, `OAuth2Test.java`, `SecurityTest.java`) plus static wildcard imports for Mockito/MockMvc in several tests (`import static org.mockito.Mockito.*;` in `RegistrationListenerTest.java`, `UserActionControllerTest.java`, etc. — 21 files total use a wildcard). Within an import block, imports are alphabetically sorted by fully-qualified name. Grouping is typically `java.*` → `org.springframework.*` → `com.digitalsanctuary.*` → `jakarta.*` → `lombok.*`, usually with **no blank lines** between groups (e.g. `UserActionController.java:3-24`), though some files add blank lines between groups (e.g. `GlobalValidationExceptionHandler.java:3-22`, `BaseTestConfiguration.java`) and a few test annotation classes place `com.digitalsanctuary` before `org.springframework` (`DatabaseTest.java`), breaking strict group ordering. So: alphabetical-within-group is consistent; group order and blank-line separation are not strictly enforced.
- **Braces**: K&R / Egyptian style — opening brace on the same line as the declaration, no exceptions observed (`public class UserService {`, `if (event.isUserEnabled()) {`).
- **Line Length**: Not enforced by tooling (no checkstyle/spotless found), but long Javadoc and log lines are frequently hand-wrapped around ~120 columns (e.g. `WebSecurityConfig.java`, `UserAPI.java` warning message split across 4 string-concatenated lines at `UserAPI.java:124-128`).

## Error Handling
- Custom checked/unchecked exceptions live in `src/main/java/com/digitalsanctuary/spring/user/exceptions/` (`UserAlreadyExistException`, `InvalidOldPasswordException`, `OAuth2AuthenticationProcessingException`, `WebAuthnException` and its subtypes `WebAuthnAccountLockedException`, `WebAuthnReauthenticationException`, `WebAuthnUserNotFoundException`). Each follows a `RuntimeException` boilerplate with 4 constructors (no-arg, message, message+cause, cause) and a Javadoc'd `serialVersionUID`, e.g. `UserAlreadyExistException.java:6-49`.
- Services catch specific low-level exceptions and translate them to domain exceptions rather than letting infra exceptions leak, with the reasoning documented inline:
  ```java
  // UserService.java:396-411
  try {
      User saved = userRepository.save(user);
      savePasswordHistory(saved, saved.getPassword());
      return saved;
  } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
      log.debug("UserService.persistNewUserAccount: concurrent registration detected for email {}: {}",
              user.getEmail(), e.getClass().getSimpleName());
      throw new UserAlreadyExistException(
              "There is an account with that email address: " + user.getEmail());
  }
  ```
- Centralized validation error translation via `@ControllerAdvice` **scoped** to the library's own controllers (not global) — `GlobalValidationExceptionHandler` (`src/main/java/com/digitalsanctuary/spring/user/exceptions/GlobalValidationExceptionHandler.java`) uses `@ControllerAdvice(assignableTypes = {UserAPI.class, GdprAPI.class, MfaAPI.class, UserActionController.class, UserPageController.class})`, handles `MethodArgumentNotValidException` and `ConstraintViolationException`, and returns a structured `ResponseEntity<Map<String,Object>>` body (`success`/`code`/`message`/`errors`) via `ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)`. `WebAuthnManagementAPI` deliberately has its own separate advice (`WebAuthnManagementAPIAdvice`) to avoid overlapping `@ExceptionHandler`s.
- Logging accompanies most catch blocks (`log.warn`, `log.debug`) before rethrowing/translating — exceptions are rarely swallowed silently.

## Testing Patterns
- **Location**: `src/test/java/com/digitalsanctuary/spring/user/...`, mirroring the main package structure (`service/`, `controller/`, `security/`, `gdpr/`, `audit/`, `listener/`, `registration/`, plus test-only packages `test/annotations/`, `test/config/`, `test/fixtures/`, `test/builders/`, `architecture/`, `json/`, `unit/`).
- **Naming**: Predominantly `should[ExpectedBehavior]When[Condition]` (191 matches for the full `should...When...` shape, 448 methods start with `should` overall), e.g. `shouldReturn401WhenHtmxRequestReceived`, `shouldAllowFormRegistrationWhenGuardAllows` (`HtmxAwareAuthenticationEntryPointTest`, various security tests). This is **not universal**: many tests instead use `@DisplayName` + a plain camelCase method name (e.g. `GdprDeletionServiceTest.java:76-96` — `throwsException_whenUserIsNull()`, `successfullyDeletesUser()`, `publishesUserPreDeleteEvent_beforeDeletion()`), sometimes with an underscore separating the assertion from the "when" clause. Test classes are frequently organized with `@Nested` inner classes named after the method under test (e.g. `class DeleteUser { ... }`) each carrying its own `@DisplayName`.
- **Framework**: JUnit 5 (`org.junit.jupiter.api.Test`, `@BeforeEach`, `@Nested`, `@DisplayName`) + Mockito (`@Mock`, `@InjectMocks`, `ArgumentCaptor`) + AssertJ for assertions + Spring Boot Test slices (`@DataJpaTest`, `@SpringBootTest` via the custom composite annotations below).
- **Custom annotations** (`src/test/java/com/digitalsanctuary/spring/user/test/annotations/`): `@ServiceTest` (Mockito-only unit tests, imports `BaseTestConfiguration`, `@ActiveProfiles("test")`), `@DatabaseTest` (JPA slice test: `@DataJpaTest` + `@AutoConfigureTestDatabase` + `DatabaseTestConfiguration` + `@Transactional`), `@IntegrationTest`, `@SecurityTest`, `@OAuth2Test` — all confirmed present and used as composite/meta-annotations with their own configurable attributes (e.g. `ServiceTest.strictStubbing()`, `DatabaseTest.showSql()`/`rollback()`).
- **Test config classes** (`src/test/java/com/digitalsanctuary/spring/user/test/config/`): `BaseTestConfiguration`, `SecurityTestConfiguration`, `OAuth2TestConfiguration`, `MockMailConfiguration`, `DatabaseTestConfiguration` all present, plus `StatementCountInspector` (a query-count assertion helper). `BaseTestConfiguration` provides shared `@Bean`s: a fast `BCryptPasswordEncoder(4)`, a spy `ApplicationEventPublisher`, a fixed `Clock`, `Locale.US`, and a static-property-setting `TestPropertySourcesConfigurer`.
- **Patterns**: AssertJ is dominant — 1189 occurrences of `assertThat(` across the test tree vs. 73 total legacy JUnit-style assertions (`assertEquals` 25, `assertTrue` 42, `assertFalse` 6, `assertNotNull` 0). Fluent chains are typical:
  ```java
  // GdprDeletionServiceTest.java:79-96
  assertThatThrownBy(() -> gdprDeletionService.deleteUser(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("User cannot be null");
  ...
  assertThat(result.isSuccess()).isTrue();
  verify(userRepository).delete(testUser);
  ```
  Given/When/Then is expressed via comments (`// Given`, `// When`, `// Then`) rather than a BDD framework. Mockito stubbing uses `when(...).thenReturn(...)`, verification via `verify(mock)`/`verify(mock, never())`, and `ArgumentCaptor` for asserting on published events/audit records. `UserTestDataBuilder` (`test/builders`) supplies a fluent builder for `User` fixtures (`UserTestDataBuilder.aVerifiedUser().withId(1L).withEmail(...).build()`).

## Documentation Style
- Public classes and most public methods carry Javadoc. Class-level docs on services are notably thorough — `UserService`'s class Javadoc (`UserService.java:62-158`) documents purpose, transactionality, a bulleted `<ul>` of dependencies, configuration flags, nested enums, and a full method index with `{@link #method(Args)}` cross-references, closed with `@author Devon Hillard`.
- Method Javadoc generally follows the classic tag style: a one-line summary, then `@param`, `@return`, and (less commonly) `@throws`, e.g.:
  ```java
  /**
   * Instantiates a new user already exist exception.
   *
   * @param message
   *            the message
   * @param cause
   *            the cause
   */
  public UserAlreadyExistException(final String message, final Throwable cause) { ... }
  ```
  (`UserAlreadyExistException.java:18-28` — note the wrapped-tag indentation style typical of tab-indented/legacy files). Newer space-indented files use the more compact single-line tag style instead, e.g. `GlobalValidationExceptionHandler.java:61-64`: `@param ex the MethodArgumentNotValidException` / `@return a ResponseEntity containing validation error details`.
- `@see` is used liberally to cross-link related types (`@see UserRepository`, `@see UserVerificationService`). HTML-ish Javadoc (`<p>`, `<ul><li>`) is common in class-level docs for prose explanations of non-obvious design decisions (e.g. the long rationale blocks in `UserService.java` around the `self`-proxy field, and in `UserDeletedEvent.java` around after-commit event delivery).
- Field-level Javadoc is common but terse, one-liners like `/** The user repository. */` above `private final UserRepository userRepository;` (`UserService.java:210-211`).

## Lombok Usage
- `lombok.config` at the repo root sets only `lombok.addLombokGeneratedAnnotation = true` (to suppress Javadoc-coverage warnings on generated code) — no other repo-wide Lombok config (no `lombok.equalsAndHashCode.doNotUseGetters`, etc.).
- `@Slf4j` is the near-universal logging annotation (60 files) — SLF4J logger obtained via Lombok rather than manual `LoggerFactory.getLogger(...)`.
- `@RequiredArgsConstructor` + `private final` fields is the standard DI pattern for services/controllers/listeners (41 files), e.g. `UserService`, `UserActionController`, `RegistrationListener` — all annotated `@Slf4j @RequiredArgsConstructor` plus their Spring stereotype (`@Service`, `@Controller`, `@Component`).
- `@Data` is used on simple DTOs (26 files, e.g. `UserDto`, combined with `@PasswordMatches` class-level validation and field-level `@ToString.Exclude` on the two password fields to keep secrets out of `toString()` — `UserDto.java:20-43`).
- `@Builder` (12 files) usually paired with `@Data @NoArgsConstructor @AllArgsConstructor` for DTOs that need both a fluent builder and Jackson-friendly no-arg construction, e.g. `AuditEventDTO.java:17-20`.
- `@Getter`/`@Setter` (12/9 files) and `@EqualsAndHashCode`/`@ToString` (11/13 files) are applied piecemeal to JPA entities instead of blanket `@Data` (entities avoid `@Data` because of JPA proxy/equality pitfalls) — e.g. `User.java:28-34` uses `@Getter @Setter @EqualsAndHashCode(onlyExplicitlyIncluded = true) @ToString @Entity`, with `@EqualsAndHashCode.Include` pinned only to the `id` field.
- Plain validators (e.g. `PasswordMatchesValidator`) and some config/registrar classes intentionally use **no Lombok** at all, relying on explicit constructors/getters — Lombok is a convenience, not mandatory everywhere.

## Common Idioms
- **Self-proxy for transactional boundary control**: `UserService` injects a `@Lazy` self-reference (`RegistrationGuard`-adjacent field, see `UserService.java:254-269`) so slow, non-transactional public entry points (bcrypt hashing) can delegate to short `@Transactional` persistence methods through the Spring proxy rather than via a self-invocation that would bypass `@Transactional`. The same pattern is documented in `GdprDeletionService` (`self` field, exercised directly in unit tests via `ReflectionTestUtils.setField(service, "self", service)`, see `GdprDeletionServiceTest.java:61-63`).
- **Deferred/after-commit event publishing**: domain events like `UserDeletedEvent` are published from a `TransactionSynchronization.afterCommit` callback so listeners never observe an uncommitted deletion (`UserDeletedEvent.java` Javadoc; implemented in `GdprDeletionService.java:176-216`), falling back to immediate publication when no transaction is active.
- **Optional for "may not exist" lookups**: repository/service lookup methods that can legitimately return nothing use `Optional<T>` return types, e.g. `UserService.getUserByPasswordResetToken(String): Optional<User>` and `findUserByID(long): Optional<User>` (`UserService.java:664`, `748`).
- **Builder-style test fixtures**: `UserTestDataBuilder.aVerifiedUser().withId(...).withEmail(...).build()` (`test/builders/UserTestDataBuilder.java`) mirrors the production `@Builder` idiom for constructing consistent test data.
- **Scoped `@ControllerAdvice`**: rather than a single global exception handler, advice classes are scoped via `assignableTypes` to just the library's own controllers, so consuming applications' controllers are unaffected (`GlobalValidationExceptionHandler.java:49`).
