# Testing Guide - Spring User Framework

This document provides comprehensive guidance for testing within the Spring User Framework project.

## Table of Contents

- [Testing Strategy](#testing-strategy)
- [Test Categories](#test-categories)
- [Custom Test Annotations](#custom-test-annotations)
- [Test Execution](#test-execution)
- [Test Data Management](#test-data-management)
- [Best Practices](#best-practices)
- [Common Patterns](#common-patterns)
- [Troubleshooting](#troubleshooting)

## Testing Strategy

Our testing approach follows a multi-layered strategy:

1. **Unit Tests** - Fast, isolated tests for individual components
2. **Integration Tests** - Tests that verify component interactions
3. **API Tests** - End-to-end tests for REST endpoints
4. **Security Tests** - Authentication and authorization verification

### Test Coverage Goals

- **Service Layer**: Comprehensive unit testing with mocking
- **Controller Layer**: API testing with MockMvc
- **Security Layer**: Authentication and authorization scenarios
- **Data Layer**: Repository testing with test databases

## Test Categories

### Custom Test Annotations

The project uses custom annotations to categorize and configure tests:

#### `@ServiceTest`
- **Purpose**: Unit tests for service layer components
- **Configuration**:
  - Mockito extension enabled
  - Test profile activated
  - Base test configuration imported
- **Usage**: Fast unit tests with mocked dependencies

```java
@ServiceTest
class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    // Test methods
}
```

#### `@IntegrationTest`
- **Purpose**: Full Spring context integration tests
- **Configuration**:
  - Complete Spring Boot test context
  - MockMvc configuration
  - Transaction management
  - All test configurations imported
- **Usage**: End-to-end workflow testing

```java
@IntegrationTest
class UserRegistrationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    // Integration test methods
}
```

#### `@SecurityTest`
- **Purpose**: Security-focused testing
- **Configuration**: Security test configuration
- **Usage**: Authentication/authorization testing

#### `@DatabaseTest`
- **Purpose**: Repository and data layer testing
- **Configuration**: Database test configuration
- **Usage**: JPA repository testing

#### `@OAuth2Test`
- **Purpose**: OAuth2 integration testing
- **Configuration**: OAuth2 mock configuration
- **Usage**: Social login testing

## Test Execution

### Running Tests

#### All Tests
```bash
./gradlew test
```

#### Specific JDK Versions
```bash
./gradlew testJdk17    # Run with JDK 17
./gradlew testJdk21    # Run with JDK 21
./gradlew testAll      # Run with both JDK versions
```

#### Test Categories
```bash
# Run only unit tests (ServiceTest)
./gradlew test --tests "*ServiceTest"

# Run only integration tests
./gradlew test --tests "*IntegrationTest"

# Run specific test class
./gradlew test --tests "UserServiceTest"
```

### Parallel Execution

Tests are configured to run in parallel for improved performance:

- **Enabled**: JUnit 5 parallel execution
- **Strategy**: Dynamic thread allocation based on CPU cores
- **Configuration**: `junit-platform.properties`

#### Performance Settings

```properties
# Parallel execution enabled
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent

# Dynamic thread allocation
junit.jupiter.execution.parallel.config.strategy=dynamic
```

#### Expected Runtime
- **Unit Tests**: ~30-60 seconds
- **Integration Tests**: ~2-5 minutes
- **All Tests**: ~5-10 minutes (with parallelization)

### Test Tags

Use JUnit 5 tags for selective test execution:

```java
@Test
@Tag("fast")
void quickTest() { /* ... */ }

@Test
@Tag("slow")
@Tag("integration")
void comprehensiveTest() { /* ... */ }
```

```bash
# Run only fast tests
./gradlew test --tests "*" -Dgroups="fast"

# Exclude slow tests
./gradlew test --tests "*" -DexcludedGroups="slow"
```

## Test Data Management

### TestFixtures Utility

The centralized `TestFixtures` class provides consistent test data:

```java
// Standard test entities
User user = TestFixtures.Users.standardUser();
User admin = TestFixtures.Users.adminUser();
User locked = TestFixtures.Users.lockedUser();

// DTOs for API testing
UserDto registration = TestFixtures.DTOs.validUserRegistration();
PasswordDto passwordUpdate = TestFixtures.DTOs.validPasswordUpdate();

// OAuth2 test users
OAuth2User googleUser = TestFixtures.OAuth2.googleUser();
OAuth2User githubUser = TestFixtures.OAuth2.githubUser();

// Security contexts
DSUserDetails userDetails = TestFixtures.Security.standardUserDetails();

// Test scenarios
TestFixtures.Scenarios.UserRegistration scenario = new TestFixtures.Scenarios.UserRegistration();
```

### Test Data Builders

For custom test data, use the fluent builders:

```java
User customUser = UserTestDataBuilder.aUser()
    .withEmail("custom@test.com")
    .withFirstName("Custom")
    .withLastName("User")
    .withRole("ROLE_ADMIN")
    .verified()
    .build();

Role customRole = RoleTestDataBuilder.aRole()
    .withName("ROLE_CUSTOM")
    .withPrivilege("CUSTOM_PRIVILEGE")
    .build();
```

### Database State Management

- **Unit Tests**: Use mocked repositories
- **Integration Tests**: Transactional rollback ensures clean state
- **Test Isolation**: Each test starts with a clean database state

## Best Practices

### Test Structure

Follow the **Arrange-Act-Assert** pattern:

```java
@Test
void shouldUpdateUserProfile() {
    // Arrange (Given)
    User user = TestFixtures.Users.standardUser();
    UserDto updateDto = TestFixtures.DTOs.profileUpdate();
    when(userRepository.save(any())).thenReturn(user);

    // Act (When)
    User updatedUser = userService.updateProfile(user, updateDto);

    // Assert (Then)
    assertThat(updatedUser.getFirstName()).isEqualTo("Updated");
    verify(userRepository).save(user);
}
```

### Naming Conventions

- **Test Classes**: `[ComponentName]Test` (e.g., `UserServiceTest`)
- **Test Methods**: `should[ExpectedBehavior]When[Condition]` or `[methodName]_[condition]_[expectedResult]`
- **Nested Classes**: `@DisplayName` with descriptive names

### Assertions

Use AssertJ for fluent assertions:

```java
// Good
assertThat(user.getEmail()).isEqualTo("test@example.com");
assertThat(users).hasSize(3)
              .extracting(User::getEmail)
              .containsExactly("user1@test.com", "user2@test.com", "user3@test.com");

// Avoid
assertEquals("test@example.com", user.getEmail());
```

### Mock Verification

Use ArgumentCaptors for complex verification:

```java
@Test
void shouldPublishAuditEvent() {
    // Act
    userService.registerUser(userDto);

    // Assert
    ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());

    AuditEvent event = eventCaptor.getValue();
    assertThat(event.getAction()).isEqualTo("Registration");
    assertThat(event.getActionStatus()).isEqualTo("Success");
}
```

## Common Patterns

### OAuth2 Testing

```java
@Test
void shouldAuthenticateWithOAuth2() throws Exception {
    mockMvc.perform(post("/api/secure-endpoint")
            .with(oauth2Login().oauth2User(TestFixtures.OAuth2.googleUser())))
            .andExpect(status().isOk());
}
```

### Security Testing

```java
@Test
void shouldRequireAuthentication() throws Exception {
    mockMvc.perform(get("/user/profile"))
            .andExpect(status().isUnauthorized());
}

@Test
void shouldAllowAccessForValidUser() throws Exception {
    mockMvc.perform(get("/user/profile")
            .with(user(TestFixtures.Security.standardUserDetails())))
            .andExpect(status().isOk());
}
```

### Email Testing

```java
@Test
void shouldSendVerificationEmail() {
    // Act
    userEmailService.sendRegistrationVerificationEmail(user, appUrl);

    // Assert
    ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(mailService).sendTemplateMessage(
        eq(user.getEmail()),
        eq("Registration Confirmation"),
        variablesCaptor.capture(),
        eq("mail/registration-token.html")
    );

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat(variables).containsKeys("token", "user", "confirmationUrl");
}
```

### Exception Testing

```java
@Test
void shouldThrowExceptionForDuplicateEmail() {
    // Given
    when(userRepository.findByEmail("test@example.com")).thenReturn(existingUser);

    // When & Then
    assertThatThrownBy(() -> userService.registerUser(userDto))
        .isInstanceOf(UserAlreadyExistException.class)
        .hasMessageContaining("email address");
}
```

## Troubleshooting

### Common Issues

#### OAuth2 Test Failures
**Problem**: OAuth2 tests failing with dependency issues
**Solution**: Ensure `@IntegrationTest` annotation is used and Spring Security is properly configured

#### Parallel Execution Issues
**Problem**: Tests failing when run in parallel
**Solution**:
- Check for shared state between tests
- Use `@Tag("sequential")` for tests that must run sequentially
- Ensure proper test isolation

#### MockMvc Security Issues
**Problem**: Security context not properly configured
**Solution**:
- Use `@SecurityTest` annotation
- Apply `springSecurity()` to MockMvc setup
- Use `@WithMockUser` or `oauth2Login()` for authentication

#### Database State Issues
**Problem**: Tests affecting each other's database state
**Solution**:
- Ensure `@Transactional` is applied to integration tests
- Use `@Sql` annotations for specific test data setup
- Check test isolation with `@DirtiesContext` if needed

### Performance Optimization

1. **Use Unit Tests**: Prefer fast unit tests over integration tests
2. **Parallel Execution**: Ensure tests are thread-safe for parallel execution
3. **Test Data**: Use lightweight test fixtures instead of full database setup
4. **Mocking**: Mock external dependencies to avoid network calls

### Debugging Tests

```bash
# Run with debug logging
./gradlew test -Dlogging.level.org.springframework.security=DEBUG

# Run single test with full output
./gradlew test --tests "UserServiceTest.shouldRegisterUser" --info

# Debug test execution
./gradlew test --debug-jvm
```

## Conclusion

This testing framework provides comprehensive coverage while maintaining fast execution times. The combination of custom annotations, centralized fixtures, and parallel execution ensures both developer productivity and code quality.

For questions or improvements to this testing strategy, please refer to the project documentation or create an issue in the repository.
