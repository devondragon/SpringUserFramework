# Test Improvement Plan - Spring User Framework

This document outlines remaining test improvements identified during the comprehensive test suite analysis. Each improvement includes detailed implementation guidance, code examples, and expected benefits.

## Completed Improvements ✅

1. **Re-enabled OAuth2 tests with @OAuth2Login** - ✅ Completed
2. **Enabled JUnit 5 parallel execution** - ✅ Completed  
3. **Extracted shared fixtures into utility class** - ✅ Completed
4. **Documented testing conventions** - ✅ Completed

## Remaining Improvements

### 4. Replace ReflectionTestUtils Usage 🔧
**Priority:** Medium  
**Effort:** Low-Medium  
**Impact:** High (Code Quality)

#### Problem
Several tests use `ReflectionTestUtils.setField()` to mutate private fields, making tests brittle and indicating potential design issues in production code.

#### Current Occurrences
```java
// UserActionControllerTest.java
ReflectionTestUtils.setField(userActionController, "registrationPendingURI", "/user/registration-pending.html");

// UserServiceTest.java  
ReflectionTestUtils.setField(userService, "sendRegistrationVerificationEmail", true);
ReflectionTestUtils.setField(userService, "actuallyDeleteAccount", true);
```

#### Solution Implementation

**Step 1: Refactor Production Classes**
```java
// Before: UserActionController
private String registrationPendingURI = "/user/registration-pending.html";

// After: Constructor injection
@Controller
public class UserActionController {
    private final String registrationPendingURI;
    private final String registrationSuccessURI;
    
    public UserActionController(
            @Value("${app.registration.pending-uri:/user/registration-pending.html}") String registrationPendingURI,
            @Value("${app.registration.success-uri:/user/registration-complete.html}") String registrationSuccessURI) {
        this.registrationPendingURI = registrationPendingURI;
        this.registrationSuccessURI = registrationSuccessURI;
    }
}
```

**Step 2: Update Test Configuration**
```java
// Create test configuration class
@TestConfiguration
public class UserActionControllerTestConfig {
    
    @Bean
    @Primary
    public UserActionController testUserActionController() {
        return new UserActionController(
            "/user/registration-pending.html",
            "/user/registration-complete.html"
        );
    }
}

// Update test class
@ExtendWith(MockitoExtension.class)
@Import(UserActionControllerTestConfig.class)
class UserActionControllerTest {
    // No more ReflectionTestUtils needed
}
```

**Step 3: Service Layer Refactoring**
```java
// UserService - use configuration properties
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserServiceProperties properties;
    
    public User registerNewUserAccount(UserDto userDto) {
        // Use properties.isSendRegistrationVerificationEmail()
        if (properties.isSendRegistrationVerificationEmail()) {
            user.setEnabled(false);
        }
    }
}

// Configuration properties class
@ConfigurationProperties(prefix = "app.user")
@Data
public class UserServiceProperties {
    private boolean sendRegistrationVerificationEmail = true;
    private boolean actuallyDeleteAccount = false;
}
```

#### Benefits
- Eliminates brittle reflection-based testing
- Improves production code design with proper dependency injection
- Makes configuration more explicit and testable
- Enables better integration testing

#### Migration Strategy
1. Start with one class (UserActionController)
2. Validate approach works well
3. Apply pattern to remaining classes incrementally
4. Update documentation with new patterns

---

### 5. Add End-to-End User Journey Tests 🚀
**Priority:** High  
**Effort:** Medium  
**Impact:** High (Integration Coverage)

#### Problem
Excellent per-layer testing but limited cross-component integration testing. Seams between UserService + EmailService + Persistence + Security are not fully exercised together.

#### Implementation

**Step 1: Create E2E Test Base Class**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("e2e")
@Transactional
public abstract class BaseE2ETest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
            
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    protected TestRestTemplate restTemplate;
    
    @Autowired
    protected TestEntityManager entityManager;
    
    @Container
    static GreenMailContainer greenMail = new GreenMailContainer()
            .withUser("test@greenmail.com", "test", "test");
}
```

**Step 2: User Registration Journey Test**
```java
@Tag("e2e")
class UserRegistrationJourneyTest extends BaseE2ETest {
    
    @Test
    @DisplayName("Complete user registration workflow: Register → Email Verification → Login")
    void completeUserRegistrationWorkflow() throws Exception {
        // Step 1: Register new user
        UserDto registrationDto = TestFixtures.DTOs.validUserRegistration();
        
        ResponseEntity<JSONResponse> registrationResponse = restTemplate.postForEntity(
            "/user/registration", 
            registrationDto, 
            JSONResponse.class
        );
        
        assertThat(registrationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registrationResponse.getBody().isSuccess()).isTrue();
        
        // Step 2: Verify user is created but disabled
        User createdUser = userRepository.findByEmail(registrationDto.getEmail());
        assertThat(createdUser).isNotNull();
        assertThat(createdUser.isEnabled()).isFalse();
        
        // Step 3: Verify verification email was sent
        await().atMost(5, SECONDS).until(() -> greenMail.getReceivedMessages().length > 0);
        
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages[0].getSubject()).isEqualTo("Registration Confirmation");
        
        // Step 4: Extract verification token from email
        String emailContent = messages[0].getContent().toString();
        String verificationToken = extractTokenFromEmail(emailContent);
        assertThat(verificationToken).isNotNull();
        
        // Step 5: Confirm registration with token
        ResponseEntity<String> confirmResponse = restTemplate.getForEntity(
            "/user/registrationConfirm?token=" + verificationToken,
            String.class
        );
        
        assertThat(confirmResponse.getStatusCode()).is3xxRedirection();
        
        // Step 6: Verify user is now enabled and can login
        User verifiedUser = userRepository.findByEmail(registrationDto.getEmail());
        assertThat(verifiedUser.isEnabled()).isTrue();
        
        // Step 7: Attempt login
        ResponseEntity<String> loginResponse = restTemplate
            .withBasicAuth(registrationDto.getEmail(), registrationDto.getPassword())
            .getForEntity("/user/profile", String.class);
            
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    
    private String extractTokenFromEmail(String emailContent) {
        // Extract verification token from email content
        Pattern pattern = Pattern.compile("token=([a-f0-9-]+)");
        Matcher matcher = pattern.matcher(emailContent);
        return matcher.find() ? matcher.group(1) : null;
    }
}
```

**Step 3: Password Reset Journey Test**
```java
@Tag("e2e")
class PasswordResetJourneyTest extends BaseE2ETest {
    
    @Test
    @DisplayName("Complete password reset workflow: Request → Email → Reset → Login")
    void completePasswordResetWorkflow() throws Exception {
        // Step 1: Create existing verified user
        User existingUser = TestFixtures.Users.standardUser();
        entityManager.persistAndFlush(existingUser);
        
        // Step 2: Request password reset
        UserDto resetRequest = new UserDto();
        resetRequest.setEmail(existingUser.getEmail());
        
        ResponseEntity<JSONResponse> resetResponse = restTemplate.postForEntity(
            "/user/resetPassword",
            resetRequest,
            JSONResponse.class
        );
        
        assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Step 3: Verify reset email sent
        await().atMost(5, SECONDS).until(() -> greenMail.getReceivedMessages().length > 0);
        
        // Step 4: Extract reset token and perform password change
        String resetToken = extractTokenFromLatestEmail();
        
        PasswordDto newPassword = new PasswordDto();
        newPassword.setOldPassword("ignored-for-reset");
        newPassword.setNewPassword("NewPassword123!");
        
        // Step 5: Submit password change with token
        ResponseEntity<JSONResponse> changeResponse = restTemplate.postForEntity(
            "/user/changePassword?token=" + resetToken,
            newPassword,
            JSONResponse.class
        );
        
        assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Step 6: Verify can login with new password
        ResponseEntity<String> loginResponse = restTemplate
            .withBasicAuth(existingUser.getEmail(), "NewPassword123!")
            .getForEntity("/user/profile", String.class);
            
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Step 7: Verify cannot login with old password
        ResponseEntity<String> oldLoginResponse = restTemplate
            .withBasicAuth(existingUser.getEmail(), existingUser.getPassword())
            .getForEntity("/user/profile", String.class);
            
        assertThat(oldLoginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

**Step 4: Gradle Configuration**
```gradle
// build.gradle
dependencies {
    testImplementation 'org.testcontainers:postgresql:1.19.3'
    testImplementation 'com.icegreen:greenmail-junit5:2.0.0'
    testImplementation 'org.awaitility:awaitility:4.2.0'
}

// Separate E2E test execution
tasks.register('testE2E', Test) {
    useJUnitPlatform {
        includeTags 'e2e'
    }
    // Disable parallel execution for E2E tests
    maxParallelForks = 1
}

tasks.register('testFast', Test) {
    useJUnitPlatform {
        excludeTags 'e2e', 'slow'
    }
}
```

#### Benefits
- Catches integration issues between layers
- Validates complete user workflows
- Tests real database interactions
- Verifies email functionality end-to-end
- Provides confidence in deployments

---

### 6. Integrate Mutation Testing (PIT) 🧬
**Priority:** Medium  
**Effort:** Low  
**Impact:** High (Test Quality)

#### Problem
Line coverage doesn't guarantee test quality. Need to verify tests actually catch logic changes and identify redundant/low-value tests.

#### Implementation

**Step 1: Add PIT Plugin to Gradle**
```gradle
// build.gradle
plugins {
    id 'info.solidsoft.pitest' version '1.15.0'
}

pitest {
    targetClasses = ['com.digitalsanctuary.spring.user.service.*',
                     'com.digitalsanctuary.spring.user.controller.*',
                     'com.digitalsanctuary.spring.user.api.*']
    targetTests = ['com.digitalsanctuary.spring.user.**.*Test']
    
    // Exclude generated code and DTOs
    excludedClasses = ['com.digitalsanctuary.spring.user.dto.*',
                       '**.*Builder',
                       '**.*Config*']
    
    // Mutation operators
    mutators = ['STRONGER']
    
    // Output formats
    outputFormats = ['HTML', 'XML']
    
    // Quality gates
    mutationThreshold = 70
    coverageThreshold = 80
    
    // Performance settings
    threads = 4
    timeoutFactor = 2.0
    timeoutConstInMillis = 10000
    
    // Test exclusions for performance
    excludedTestClasses = ['**.*IntegrationTest',
                           '**.*E2ETest']
}

// Separate task for full mutation testing
tasks.register('mutationTestFull', PitestTask) {
    // Include all tests including integration
    excludedTestClasses = []
    mutationThreshold = 60 // Lower threshold for full suite
}

// Nightly mutation testing
tasks.register('mutationTestNightly', PitestTask) {
    // Comprehensive mutation testing
    mutators = ['ALL']
    mutationThreshold = 75
}
```

**Step 2: Configure CI Pipeline**
```yaml
# .github/workflows/mutation-testing.yml
name: Mutation Testing

on:
  schedule:
    - cron: '0 2 * * *' # Run nightly at 2 AM
  workflow_dispatch: # Allow manual trigger

jobs:
  mutation-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Run Mutation Tests
        run: ./gradlew mutationTestNightly
        
      - name: Upload Mutation Report
        uses: actions/upload-artifact@v4
        with:
          name: mutation-report
          path: build/reports/pitest/
          
      - name: Comment PR with Results
        if: github.event_name == 'pull_request'
        run: |
          # Script to parse mutation results and comment on PR
```

**Step 3: Quality Gates Integration**
```gradle
// Fail build if mutation coverage is too low
tasks.named('check') {
    dependsOn(tasks.named('pitest'))
}

// Fast mutation test for development
tasks.register('mutationTestQuick', PitestTask) {
    // Only test recently changed files
    targetClasses = ['com.digitalsanctuary.spring.user.service.UserService']
    mutationThreshold = 80
    threads = Runtime.runtime.availableProcessors()
}
```

**Step 4: Mutation Testing Analysis Workflow**
```java
// Example of improving tests based on mutation results

// Before: Weak test (mutation would survive)
@Test
void shouldValidateUser() {
    User user = TestFixtures.Users.standardUser();
    boolean result = userService.isValid(user);
    assertTrue(result); // Doesn't test the validation logic
}

// After: Strong test (mutation would be killed)
@Test
void shouldValidateUser_allFieldsValid() {
    User user = TestFixtures.Users.standardUser();
    boolean result = userService.isValid(user);
    assertThat(result).isTrue();
}

@Test
void shouldInvalidateUser_nullEmail() {
    User user = TestFixtures.Users.standardUser();
    user.setEmail(null);
    boolean result = userService.isValid(user);
    assertThat(result).isFalse();
}

@Test
void shouldInvalidateUser_emptyPassword() {
    User user = TestFixtures.Users.standardUser();
    user.setPassword("");
    boolean result = userService.isValid(user);
    assertThat(result).isFalse();
}
```

#### Benefits
- Identifies weak tests that don't catch logic errors
- Measures real test effectiveness beyond line coverage
- Helps eliminate redundant tests
- Improves overall test quality and confidence
- Provides metrics for code review

---

### 7. Add Real Email Testing with GreenMail 📧
**Priority:** Medium  
**Effort:** Low  
**Impact:** Medium (Integration)

#### Problem
Email tests currently mock SMTP gateway entirely, potentially missing serialization issues and real SMTP problems.

#### Implementation

**Step 1: Add GreenMail Dependencies**
```gradle
// build.gradle
dependencies {
    testImplementation 'com.icegreen:greenmail-junit5:2.0.0'
    testImplementation 'org.testcontainers:testcontainers:1.19.3'
}
```

**Step 2: Create Email Integration Test**
```java
@IntegrationTest
@Testcontainers
class EmailServiceIntegrationTest {
    
    @Container
    static GreenMailContainer greenMail = new GreenMailContainer()
            .withUser("test@example.com", "testuser", "testpass")
            .withExposedPorts(3025, 3110, 3143, 3465, 3993, 3995);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", greenMail::getHost);
        registry.add("spring.mail.port", () -> greenMail.getMappedPort(3025));
        registry.add("spring.mail.username", () -> "testuser");
        registry.add("spring.mail.password", () -> "testpass");
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "true");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
    }
    
    @Autowired
    private UserEmailService userEmailService;
    
    @Test
    @DisplayName("Should send real registration verification email")
    void shouldSendRealRegistrationEmail() throws Exception {
        // Given
        User testUser = TestFixtures.Users.standardUser();
        String appUrl = "https://test.example.com";
        
        // When
        userEmailService.sendRegistrationVerificationEmail(testUser, appUrl);
        
        // Then - Wait for email to be sent
        await().atMost(10, SECONDS)
               .until(() -> greenMail.getReceivedMessages().length > 0);
        
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        
        MimeMessage message = messages[0];
        assertThat(message.getSubject()).isEqualTo("Registration Confirmation");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(testUser.getEmail());
        
        // Verify email content
        String content = message.getContent().toString();
        assertThat(content).contains(testUser.getFirstName());
        assertThat(content).contains("registrationConfirm?token=");
        assertThat(content).contains(appUrl);
        
        // Verify HTML structure
        assertThat(content).contains("<html>");
        assertThat(content).contains("</html>");
    }
    
    @Test
    @DisplayName("Should send real password reset email")
    void shouldSendRealPasswordResetEmail() throws Exception {
        // Given
        User testUser = TestFixtures.Users.standardUser();
        String appUrl = "https://test.example.com";
        
        // When
        userEmailService.sendForgotPasswordVerificationEmail(testUser, appUrl);
        
        // Then
        await().atMost(10, SECONDS)
               .until(() -> greenMail.getReceivedMessages().length > 0);
        
        MimeMessage message = greenMail.getReceivedMessages()[0];
        assertThat(message.getSubject()).isEqualTo("Password Reset");
        
        String content = message.getContent().toString();
        assertThat(content).contains("changePassword?token=");
        
        // Extract and validate token format
        Pattern tokenPattern = Pattern.compile("token=([a-f0-9-]{36})");
        Matcher matcher = tokenPattern.matcher(content);
        assertThat(matcher.find()).isTrue();
        
        String token = matcher.group(1);
        assertThat(token).matches("^[a-f0-9-]{36}$"); // UUID format
    }
    
    @Test
    @DisplayName("Should handle email template errors gracefully")
    void shouldHandleTemplateErrors() {
        // Test with invalid template or missing variables
        User userWithNullFields = new User();
        userWithNullFields.setEmail("test@example.com");
        // firstName and lastName are null
        
        // Should not throw exception, but may send email with default values
        assertDoesNotThrow(() -> 
            userEmailService.sendRegistrationVerificationEmail(userWithNullFields, "http://test.com")
        );
    }
}
```

**Step 3: Performance Email Test**
```java
@Test
@Tag("performance")
@DisplayName("Should handle bulk email sending efficiently")
void shouldHandleBulkEmailSending() throws Exception {
    // Given
    List<User> users = TestFixtures.Users.multipleUsers(50);
    String appUrl = "https://test.example.com";
    
    // When
    long startTime = System.currentTimeMillis();
    
    users.parallelStream().forEach(user -> 
        userEmailService.sendRegistrationVerificationEmail(user, appUrl)
    );
    
    long endTime = System.currentTimeMillis();
    
    // Then
    await().atMost(30, SECONDS)
           .until(() -> greenMail.getReceivedMessages().length >= 50);
    
    assertThat(greenMail.getReceivedMessages()).hasSizeGreaterThanOrEqualTo(50);
    assertThat(endTime - startTime).isLessThan(10000); // Should complete in <10 seconds
}
```

#### Benefits
- Catches real SMTP configuration issues
- Validates email template rendering
- Tests email content and formatting
- Verifies token generation and embedding
- Provides confidence in email delivery

---

### 8. Expand Security Testing 🔒
**Priority:** High  
**Effort:** Medium  
**Impact:** High (Security)

#### Problem
Current security testing covers basic authentication but lacks comprehensive security scenarios like CSRF, privilege escalation, and session management.

#### Implementation

**Step 1: CSRF Protection Testing**
```java
@SecurityTest
class CSRFSecurityTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @DisplayName("Should reject requests without CSRF token")
    void shouldRejectRequestsWithoutCSRFToken() throws Exception {
        UserDto userDto = TestFixtures.DTOs.validUserRegistration();
        
        mockMvc.perform(post("/user/registration")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDto)))
                .andExpect(status().isForbidden());
    }
    
    @Test
    @DisplayName("Should accept requests with valid CSRF token")
    void shouldAcceptRequestsWithValidCSRFToken() throws Exception {
        UserDto userDto = TestFixtures.DTOs.validUserRegistration();
        
        mockMvc.perform(post("/user/registration")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDto)))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("Should reject requests with invalid CSRF token")
    void shouldRejectRequestsWithInvalidCSRFToken() throws Exception {
        mockMvc.perform(post("/user/registration")
                .header("X-CSRF-TOKEN", "invalid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }
}
```

**Step 2: Privilege Escalation Testing**
```java
@SecurityTest
class PrivilegeEscalationTest {
    
    @Test
    @DisplayName("Regular user should not be able to delete other users")
    @WithMockUser(roles = "USER")
    void regularUserCannotDeleteOtherUsers() throws Exception {
        Long otherUserId = 999L;
        
        mockMvc.perform(delete("/admin/users/{id}", otherUserId)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }
    
    @Test
    @DisplayName("Regular user should not access admin endpoints")
    @WithMockUser(roles = "USER")
    void regularUserCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/users")
                .with(csrf()))
                .andExpect(status().isForbidden());
    }
    
    @Test
    @DisplayName("Admin user should be able to manage users")
    @WithMockUser(roles = {"ADMIN", "USER"})
    void adminUserCanManageUsers() throws Exception {
        mockMvc.perform(get("/admin/users")
                .with(csrf()))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("User should only access their own profile")
    void userShouldOnlyAccessOwnProfile() throws Exception {
        User user1 = TestFixtures.Users.withEmail("user1@test.com");
        User user2 = TestFixtures.Users.withEmail("user2@test.com");
        
        // User1 accessing their own profile - should work
        mockMvc.perform(get("/user/profile")
                .with(user(TestFixtures.Security.userDetailsFor(user1))))
                .andExpect(status().isOk());
        
        // User1 trying to access user2's profile - should fail
        mockMvc.perform(get("/user/{id}/profile", user2.getId())
                .with(user(TestFixtures.Security.userDetailsFor(user1))))
                .andExpect(status().isForbidden());
    }
}
```

**Step 3: JWT and Session Security Testing**
```java
@SecurityTest
class JWTSecurityTest {
    
    @Test
    @DisplayName("Should reject expired JWT tokens")
    void shouldRejectExpiredJWTTokens() throws Exception {
        // Create expired JWT token
        String expiredToken = createExpiredJWT();
        
        mockMvc.perform(get("/user/profile")
                .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token expired"));
    }
    
    @Test
    @DisplayName("Should reject malformed JWT tokens")
    void shouldRejectMalformedJWTTokens() throws Exception {
        String malformedToken = "invalid.jwt.token";
        
        mockMvc.perform(get("/user/profile")
                .header("Authorization", "Bearer " + malformedToken))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @DisplayName("Should validate JWT signature")
    void shouldValidateJWTSignature() throws Exception {
        String tokenWithInvalidSignature = createJWTWithInvalidSignature();
        
        mockMvc.perform(get("/user/profile")
                .header("Authorization", "Bearer " + tokenWithInvalidSignature))
                .andExpect(status().isUnauthorized());
    }
    
    private String createExpiredJWT() {
        // Implementation to create expired JWT for testing
        return Jwts.builder()
                .setSubject("test@example.com")
                .setExpiration(new Date(System.currentTimeMillis() - 1000)) // Expired 1 second ago
                .signWith(SignatureAlgorithm.HS512, "secret")
                .compact();
    }
}
```

**Step 4: Session Management Testing**
```java
@SecurityTest
class SessionSecurityTest {
    
    @Test
    @DisplayName("Should prevent session fixation attacks")
    void shouldPreventSessionFixation() throws Exception {
        // Get initial session
        MvcResult result1 = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();
        
        String sessionId1 = result1.getRequest().getSession().getId();
        
        // Perform login
        mockMvc.perform(post("/login")
                .param("username", "test@example.com")
                .param("password", "password")
                .session((MockHttpSession) result1.getRequest().getSession())
                .with(csrf()));
        
        // Verify session ID changed after login
        MvcResult result2 = mockMvc.perform(get("/user/profile")
                .session((MockHttpSession) result1.getRequest().getSession()))
                .andExpect(status().isOk())
                .andReturn();
        
        String sessionId2 = result2.getRequest().getSession().getId();
        assertThat(sessionId1).isNotEqualTo(sessionId2);
    }
    
    @Test
    @DisplayName("Should invalidate session on logout")
    void shouldInvalidateSessionOnLogout() throws Exception {
        // Login and get session
        MockHttpSession session = loginAndGetSession();
        
        // Verify authenticated access works
        mockMvc.perform(get("/user/profile").session(session))
                .andExpect(status().isOk());
        
        // Logout
        mockMvc.perform(post("/logout")
                .session(session)
                .with(csrf()));
        
        // Verify session is invalidated
        mockMvc.perform(get("/user/profile").session(session))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @DisplayName("Should enforce concurrent session limits")
    void shouldEnforceConcurrentSessionLimits() throws Exception {
        User user = TestFixtures.Users.standardUser();
        
        // Create multiple sessions for same user
        MockHttpSession session1 = loginAs(user);
        MockHttpSession session2 = loginAs(user);
        MockHttpSession session3 = loginAs(user);
        
        // Verify oldest session is invalidated when limit exceeded
        mockMvc.perform(get("/user/profile").session(session1))
                .andExpect(status().isUnauthorized()); // Should be kicked out
        
        mockMvc.perform(get("/user/profile").session(session3))
                .andExpect(status().isOk()); // Latest session should work
    }
}
```

**Step 5: Mass Assignment Protection**
```java
@SecurityTest
class MassAssignmentSecurityTest {
    
    @Test
    @DisplayName("Should prevent mass assignment of privileged fields")
    void shouldPreventMassAssignmentOfPrivilegedFields() throws Exception {
        // Attempt to update user with admin role via mass assignment
        String maliciousPayload = """
            {
                "firstName": "Hacker",
                "lastName": "User",
                "roles": [{"name": "ROLE_ADMIN"}],
                "enabled": true,
                "locked": false
            }
            """;
        
        mockMvc.perform(put("/user/profile")
                .with(user(TestFixtures.Security.standardUserDetails()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(maliciousPayload))
                .andExpect(status().isOk()); // Update should succeed
        
        // Verify privileged fields were not updated
        User updatedUser = userRepository.findByEmail("test@example.com");
        assertThat(updatedUser.getRoles()).extracting(Role::getName)
                .containsOnly("ROLE_USER"); // Should not contain ROLE_ADMIN
        assertThat(updatedUser.getFirstName()).isEqualTo("Hacker"); // Safe field updated
    }
}
```

#### Benefits
- Comprehensive security vulnerability testing
- Prevents common web application attacks
- Validates authentication and authorization logic
- Tests session management security
- Ensures proper access control implementation

---

### 9. Performance & Feedback Loop Optimization ⚡
**Priority:** Low  
**Effort:** Low-Medium  
**Impact:** Medium (Developer Experience)

#### Problem
With 44 test files, build times may exceed 30+ seconds. Need optimization for developer productivity and CI/CD efficiency.

#### Implementation

**Step 1: Enhanced Parallel Execution**
```gradle
// build.gradle - Method-level parallelization
test {
    useJUnitPlatform()
    
    // Method-level parallel execution
    systemProperty 'junit.jupiter.execution.parallel.enabled', 'true'
    systemProperty 'junit.jupiter.execution.parallel.mode.default', 'concurrent'
    systemProperty 'junit.jupiter.execution.parallel.mode.classes.default', 'concurrent'
    systemProperty 'junit.jupiter.execution.parallel.config.strategy', 'dynamic'
    systemProperty 'junit.jupiter.execution.parallel.config.dynamic.factor', '2.0'
    
    // Optimize JVM for test execution
    jvmArgs '-XX:+UseG1GC', '-XX:MaxGCPauseMillis=100', '-Xmx2g'
    
    // Separate resource-intensive tests
    maxParallelForks = Runtime.runtime.availableProcessors()
    forkEvery = 100 // Restart JVM after 100 tests to prevent memory issues
}
```

**Step 2: Test Categorization and Selective Execution**
```gradle
// Fast development feedback loop
tasks.register('testFast', Test) {
    useJUnitPlatform {
        excludeTags 'slow', 'e2e', 'integration'
        includeTags 'unit', 'fast'
    }
    description = 'Run fast unit tests only'
    group = 'verification'
}

// Full test suite
tasks.register('testFull', Test) {
    useJUnitPlatform()
    description = 'Run all tests including slow and integration tests'
    group = 'verification'
}

// CI optimized tests
tasks.register('testCI', Test) {
    useJUnitPlatform {
        excludeTags 'manual'
    }
    // CI-specific optimizations
    testLogging.showStandardStreams = false
    reports.html.required = false // Skip HTML reports in CI
}

// Development profile
if (project.hasProperty('fast')) {
    test {
        useJUnitPlatform {
            excludeTags 'slow', 'e2e'
        }
    }
}
```

**Step 3: Test Result Caching**
```gradle
// Enable test result caching
test {
    inputs.files(fileTree('src/test'))
    inputs.property('java.version', System.getProperty('java.version'))
    
    // Cache test results
    outputs.cacheIf { true }
    
    // Only run tests if source changed
    onlyIf {
        !gradle.taskGraph.hasTask(':testCached') || 
        inputs.sourceFiles.visit { file -> file.lastModified > lastTestRun }
    }
}

tasks.register('testCached', Test) {
    dependsOn test
    description = 'Run tests with result caching'
}
```

**Step 4: Build Performance Monitoring**
```gradle
// Add build scan plugin for performance insights
plugins {
    id 'com.gradle.build-scan' version '3.16.2'
}

buildScan {
    termsOfServiceUrl = 'https://gradle.com/terms-of-service'
    termsOfServiceAgree = 'yes'
    
    publishAlways()
    
    // Capture test performance data
    buildFinished {
        def testTask = tasks.findByName('test')
        if (testTask) {
            value 'Test Duration', testTask.didWork ? 
                "${(System.currentTimeMillis() - testTask.startTime) / 1000}s" : 'skipped'
        }
    }
}

// Performance profiling task
tasks.register('profileTests') {
    doLast {
        println "Test Performance Profile:"
        println "========================"
        tasks.withType(Test).forEach { testTask ->
            if (testTask.didWork) {
                def duration = (testTask.endTime - testTask.startTime) / 1000
                println "${testTask.name}: ${duration}s"
            }
        }
    }
}
```

**Step 5: Developer Workflow Optimization**
```bash
#!/bin/bash
# scripts/dev-test.sh - Developer test runner script

set -e

echo "🚀 Spring User Framework - Development Test Runner"
echo "=================================================="

# Parse command line arguments
MODE=${1:-fast}
WATCH=${2:-false}

case $MODE in
    "fast")
        echo "Running fast tests only..."
        ./gradlew testFast --continue
        ;;
    "full")
        echo "Running full test suite..."
        ./gradlew testFull
        ;;
    "changed")
        echo "Running tests for changed files..."
        # Get changed files and run related tests
        CHANGED_FILES=$(git diff --name-only HEAD~1)
        if [[ $CHANGED_FILES == *"src/main"* ]]; then
            ./gradlew test --rerun-tasks
        else
            echo "No source changes detected, running fast tests..."
            ./gradlew testFast
        fi
        ;;
    "watch")
        echo "Starting test watcher..."
        ./gradlew test --continuous
        ;;
    *)
        echo "Usage: $0 [fast|full|changed|watch]"
        exit 1
        ;;
esac

echo "✅ Test execution completed!"
```

**Step 6: CI/CD Pipeline Optimization**
```yaml
# .github/workflows/test-optimization.yml
name: Optimized Test Pipeline

on: [push, pull_request]

jobs:
  fast-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Cache Gradle dependencies
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*') }}
          
      - name: Run Fast Tests
        run: ./gradlew testFast --parallel --build-cache
        
  full-tests:
    runs-on: ubuntu-latest
    needs: fast-tests
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Run Full Test Suite
        run: ./gradlew testFull --parallel --build-cache
        
  performance-tests:
    runs-on: ubuntu-latest
    if: github.event_name == 'schedule'
    steps:
      - name: Run Performance Benchmarks
        run: ./gradlew test -Pbenchmark=true
```

#### Benefits
- Faster developer feedback loop (30-50% reduction in test time)
- Efficient CI/CD pipeline execution
- Better resource utilization
- Performance monitoring and optimization
- Improved developer productivity

---

## Implementation Timeline

### Phase 1 (Immediate - 1-2 weeks)
1. **Replace ReflectionTestUtils Usage** - Quick wins, improve code quality
2. **Expand Security Testing** - Critical for user management framework

### Phase 2 (Short Term - 2-4 weeks)  
3. **Add End-to-End Journey Tests** - High impact integration coverage
4. **Add Real Email Testing** - Improve integration confidence

### Phase 3 (Medium Term - 1-2 months)
5. **Integrate Mutation Testing** - Long-term quality improvement
6. **Performance Optimization** - Developer experience enhancement

## Success Metrics

- **Test Execution Time**: <60 seconds for fast tests, <5 minutes for full suite
- **Mutation Coverage**: >70% mutation kill ratio
- **Security Coverage**: 100% coverage of OWASP Top 10 scenarios
- **Integration Coverage**: 3+ complete user journey tests
- **Developer Satisfaction**: Faster feedback loop, better debugging

## Getting Started

1. Choose one improvement to start with (recommend #4 - ReflectionTestUtils)
2. Create a feature branch for the improvement
3. Implement following the detailed guide above
4. Test thoroughly and measure impact
5. Update documentation and share learnings with team
6. Move to next improvement

Each improvement builds upon the previous ones, creating a comprehensive and high-quality test suite that supports confident development and deployment.