# Spring User Framework - Test Improvement Plan

## Executive Summary

This document outlines a comprehensive test improvement plan for the Spring User Framework library. Currently, only 27% of services have test coverage, with critical gaps in security, configuration, and integration testing. This plan provides a structured approach to achieve 80%+ test coverage while creating a reliable safety net for AI-assisted development.

## Current State Analysis

### Test Coverage Metrics
- **Services**: 3 out of 11+ tested (27%)
- **Configuration Classes**: 0% coverage
- **Security Components**: 0% coverage
- **Event Handling**: 0% coverage
- **Audit System**: 0% coverage
- **Scheduled Jobs**: 0% coverage

### Critical Issues
1. `UserServiceTest` is disabled due to OAuth2 dependency issues
2. Heavy reliance on mocking without integration tests
3. No test application contexts for library testing
4. Missing test infrastructure for complex components

## Implementation Phases

### Phase 1: Test Infrastructure (Week 1-2)

#### 1.1 Create Test Application Contexts
```java
// src/test/java/com/digitalsanctuary/spring/user/test/app/TestApplication.java
@SpringBootApplication
@Import(UserConfiguration.class)
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
```

#### 1.2 Test Configuration Classes
- [ ] Create `BaseTestConfiguration` for common test beans
- [ ] Create `SecurityTestConfiguration` for security testing
- [ ] Create `OAuth2TestConfiguration` for OAuth2 mocking
- [ ] Create `DatabaseTestConfiguration` for H2 setup

#### 1.3 Test Data Builders
- [ ] `UserTestDataBuilder` - fluent API for User entities
- [ ] `RoleTestDataBuilder` - role and privilege setup
- [ ] `TokenTestDataBuilder` - verification and password tokens
- [ ] `OAuth2TestDataBuilder` - OAuth2 authentication objects

#### 1.4 Custom Test Annotations
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public @interface IntegrationTest {}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MockitoExtension.class)
public @interface ServiceTest {}
```

### Phase 2: Fix Critical Test Issues (Week 2-3)

#### 2.1 Fix UserServiceTest OAuth2 Issues
- [ ] Implement WireMock-based OAuth2 provider mocks
- [ ] Create test-specific OAuth2 configurations
- [ ] Re-enable and update UserServiceTest
- [ ] Add comprehensive OAuth2 service tests

**Implementation Steps:**
1. Add WireMock dependency to build.gradle
2. Create `MockOAuth2Server` test utility
3. Update test configuration to use mock servers
4. Refactor UserServiceTest to use new infrastructure

#### 2.2 Security Configuration Tests
- [ ] Test security filter chain configuration
- [ ] Verify endpoint protection rules
- [ ] Test CSRF configuration
- [ ] Test authentication mechanisms
- [ ] Test role hierarchy

**Test Classes to Create:**
- `WebSecurityConfigTest`
- `SecurityFilterChainTest`
- `AuthenticationTest`
- `AuthorizationTest`

### Phase 3: Service Layer Testing (Week 3-4)

#### 3.1 Untested Services Priority List
1. **AuthorityService** - Critical for security
2. **DSUserDetailsService** - Core authentication
3. **DSOAuth2UserService** - OAuth2 integration
4. **LoginHelperService** - Authentication support
5. **UserEmailService** - User communication
6. **LoginSuccessService** - Post-login handling
7. **LogoutSuccessService** - Session management
8. **DSOidcUserService** - OIDC support

#### 3.2 Test Implementation Pattern
For each service:
- [ ] Unit tests with mocked dependencies
- [ ] Integration tests with real Spring context
- [ ] Edge case and error handling tests
- [ ] Performance tests for critical paths

### Phase 4: Configuration and Properties Testing (Week 4-5)

#### 4.1 Configuration Properties Tests
- [ ] `AuditConfig` validation tests
- [ ] `RolesAndPrivilegesConfig` tests
- [ ] `WebSecurityConfig` property binding tests
- [ ] Property validation and defaults

#### 4.2 Auto-Configuration Tests
- [ ] Test conditional bean creation
- [ ] Test property-based configuration
- [ ] Test configuration precedence

### Phase 5: Event and Async Testing (Week 5-6)

#### 5.1 Event Handling Tests
- [ ] `OnRegistrationCompleteEvent` tests
- [ ] `UserPreDeleteEvent` tests
- [ ] `AuthenticationEventListener` tests
- [ ] Event publication verification

#### 5.2 Async and Scheduled Job Tests
- [ ] `ExpiredTokenCleanJob` scheduling tests
- [ ] Async email sending tests
- [ ] Scheduled audit log flushing tests

**Test Utilities:**
```java
@TestConfiguration
public class AsyncTestConfig {
    @Bean
    public TaskExecutor testTaskExecutor() {
        return new SyncTaskExecutor(); // Make async sync for testing
    }
}
```

### Phase 6: Integration Testing (Week 6-7)

#### 6.1 API Integration Tests
- [ ] Full user registration flow
- [ ] Password reset flow
- [ ] OAuth2 login flow
- [ ] User profile management

#### 6.2 Database Integration Tests
- [ ] Repository layer tests with @DataJpaTest
- [ ] Transaction boundary tests
- [ ] Database migration tests

#### 6.3 Mail Integration Tests
- [ ] Email template rendering
- [ ] Mail sending with GreenMail
- [ ] Error handling and retries

### Phase 7: Advanced Testing (Week 7-8)

#### 7.1 Architecture Tests with ArchUnit
```java
@Test
void services_should_be_annotated_with_service() {
    JavaClasses classes = new ClassFileImporter()
        .importPackages("com.digitalsanctuary.spring.user");
    
    ArchRuleDefinition.classes()
        .that().resideInPackage("..service..")
        .and().areNotInterfaces()
        .should().beAnnotatedWith(Service.class)
        .check(classes);
}
```

#### 7.2 Contract Testing
- [ ] API contract tests with Spring Cloud Contract
- [ ] Event contract tests
- [ ] Database schema contract tests

#### 7.3 Performance Testing
- [ ] Password hashing performance benchmarks
- [ ] Bulk user operations tests
- [ ] Concurrent login stress tests

## Testing Standards and Guidelines

### Test Naming Convention
```
methodName_givenCondition_expectedBehavior()

Example:
registerNewUserAccount_givenExistingEmail_throwsUserAlreadyExistException()
```

### Test Structure Pattern
```java
@Test
void testMethodName() {
    // Given - Setup test data and mocks
    User existingUser = UserTestDataBuilder.aUser()
        .withEmail("existing@example.com")
        .build();
    
    // When - Execute the method under test
    var result = userService.registerNewUserAccount(userDto);
    
    // Then - Assert the expected outcome
    assertThat(result)
        .isNotNull()
        .satisfies(user -> {
            assertThat(user.getEmail()).isEqualTo(userDto.getEmail());
            assertThat(user.isEnabled()).isFalse();
        });
}
```

### Documentation Requirements
- Each test class must have JavaDoc explaining its purpose
- Complex test scenarios require inline comments
- Test data builders must document available options
- Integration tests must document external dependencies

## Tooling and Dependencies

### Required Dependencies (build.gradle)
```gradle
testImplementation 'org.testcontainers:testcontainers:1.19.3'
testImplementation 'org.testcontainers:mariadb:1.19.3'
testImplementation 'com.github.tomakehurst:wiremock:3.0.1'
testImplementation 'com.tngtech.archunit:archunit-junit5:1.2.0'
testImplementation 'org.assertj:assertj-core:3.24.2'
testImplementation 'io.rest-assured:rest-assured:5.3.2'
testImplementation 'com.icegreen:greenmail:2.0.0'
testImplementation 'org.awaitility:awaitility:4.2.0'
```

### CI/CD Integration
```yaml
# .github/workflows/test.yml
name: Test Suite
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [17, 21]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
      - run: ./gradlew test
      - uses: codecov/codecov-action@v3
```

## Success Metrics

### Coverage Goals
- **Line Coverage**: 80% minimum, 90% target
- **Branch Coverage**: 75% minimum, 85% target
- **Service Layer**: 95% coverage required
- **Critical Paths**: 100% coverage required

### Quality Metrics
- **Test Execution Time**: < 5 minutes for full suite
- **Unit Test Speed**: < 100ms per test
- **Integration Test Speed**: < 5s per test
- **Flaky Test Rate**: < 1%

### AI-Readiness Checklist
- [ ] Clear test naming conventions
- [ ] Comprehensive test data builders
- [ ] Well-documented test scenarios
- [ ] Fast feedback loops
- [ ] Descriptive assertion messages
- [ ] Parameterized test examples

## Risk Mitigation

### Potential Risks
1. **OAuth2 Mocking Complexity**: Use established patterns from Spring Security test
2. **File System Dependencies**: Use temporary directories and cleanup
3. **Database State**: Use @DirtiesContext and transaction rollback
4. **Async Testing**: Use Awaitility for reliable async assertions
5. **Test Data Coupling**: Use builders to isolate test data creation

### Rollback Plan
- Each phase is independently deployable
- Tests are added without modifying production code
- Gradual rollout with feature flags for new test configurations
- Maintain backward compatibility with existing tests

## Timeline and Resources

### 8-Week Implementation Schedule
- **Weeks 1-2**: Infrastructure and tooling setup
- **Weeks 2-3**: Fix critical issues and OAuth2
- **Weeks 3-4**: Service layer testing
- **Weeks 4-5**: Configuration and properties
- **Weeks 5-6**: Events and async testing
- **Weeks 6-7**: Integration testing
- **Weeks 7-8**: Advanced testing and polish

### Resource Requirements
- 1 Senior Developer (full-time)
- 1 Junior Developer (part-time support)
- Code review from security team
- CI/CD pipeline updates

## Conclusion

This comprehensive test plan will transform the Spring User Framework into a robust, well-tested library suitable for AI-assisted development. By following this structured approach, we'll achieve high test coverage while creating maintainable, fast, and reliable tests that serve as both safety net and documentation.

The investment in test infrastructure will pay dividends through reduced bugs, faster development cycles, and increased confidence when using AI tools to extend the framework.