# Spring User Framework - Issues to Fix

## High-Impact Issues (Priority 1)

### 1. Fix jar artifact naming mismatch ✅ COMPLETED
- **Issue**: Jar task sets archiveBaseName to 'ds-spring-ai-client' (copy/paste error)
- **Fix**: Change to 'ds-spring-user-framework' in build.gradle line 109
- **Status**: Fixed - changed archiveBaseName to correct value

### 2. Remove transitive runtime dependencies ✅ COMPLETED
- **Issue**: Library declares runtimeOnly dependencies that surprise consumers
- **Fix**: Move spring-boot-devtools, mariadb-java-client, postgresql to testRuntimeOnly
- **Status**: Fixed - moved all runtime dependencies to test scope

### 3. Fix JPA equals/hashCode anti-patterns ✅ COMPLETED
- **Issue**: Role and Privilege use @Data without excluding relationships (causes stack overflows)
- **Fix**: Add @EqualsAndHashCode.Exclude to collection fields, base on id only
- **Status**: Fixed - replaced @Data with explicit @EqualsAndHashCode(onlyExplicitlyIncluded = true) and @EqualsAndHashCode.Include on id fields

### 4. Fix audit log writer concurrency ✅ COMPLETED
- **Issue**: FileAuditLogWriter and scheduler access shared BufferedWriter without synchronization
- **Fix**: Add synchronized blocks to protect concurrent access
- **Status**: Fixed - added synchronized keyword to writeLog(), flushWriter(), setup(), and cleanup() methods

### 5. Fix registration email base URL ✅ COMPLETED
- **Issue**: UserAPI.publishRegistrationEvent uses request.getContextPath() (broken links)
- **Fix**: Use UserUtils.getAppUrl(request) like other flows
- **Status**: Fixed - updated publishRegistrationEvent to use UserUtils.getAppUrl(request) for complete URL construction

### 6. Configure security remember-me properly ✅ COMPLETED
- **Issue**: Uses random key per startup, invalidates on restart
- **Fix**: Make opt-in with explicit key configuration
- **Status**: Fixed - added user.security.rememberMe.enabled and user.security.rememberMe.key properties, remember-me is now only enabled when explicitly configured

### 7. Remove @Async from event classes ✅ COMPLETED
- **Issue**: @Async on POJOs has no effect (false impression)
- **Fix**: Remove from AuditEvent and OnRegistrationCompleteEvent classes
- **Status**: Fixed - removed @Async annotation and import from both AuditEvent and OnRegistrationCompleteEvent classes

## Security & API Issues (Priority 2)

### 8. Add DTO validation annotations ✅ COMPLETED
- **Issue**: UserDto and PasswordDto lack bean validation
- **Fix**: Add @NotBlank, @Email, password constraints, create @ControllerAdvice
- **Status**: Fixed - added validation annotations to UserDto and PasswordDto fields, created GlobalValidationExceptionHandler for consistent API error responses

### 9. Fix CSRF property typo ✅ COMPLETED
- **Issue**: Property name contains odd "d" - 'disableCSRFdURIs'
- **Fix**: Rename to 'disableCSRFURIs'
- **Status**: Fixed - corrected property name in WebSecurityConfig, configuration metadata, properties files, and test configuration

### 10. Improve error message handling ✅ COMPLETED
- **Issue**: CustomOAuth2AuthenticationEntryPoint exposes exception details
- **Fix**: Use generic user messages, log details internally
- **Status**: Fixed - replaced exposed exception messages with generic user-friendly messages, detailed exceptions logged internally for debugging, removed debug print statements

### 11. Enhance IP detection ✅ COMPLETED
- **Issue**: Only honors X-Forwarded-For header
- **Fix**: Support X-Real-IP, CF-Connecting-IP, True-Client-IP
- **Status**: Fixed - enhanced UserUtils.getClientIP() to check multiple headers in order of preference (X-Forwarded-For, X-Real-IP, CF-Connecting-IP, True-Client-IP) with proper validation and fallback

## Web/Security Config (Priority 3)

### 12. Fix property injection robustness ✅ COMPLETED
- **Issue**: Empty property yields list with empty string
- **Fix**: Filter empty strings from unprotectedURIs list
- **Status**: Fixed - replaced direct property splitting with helper methods that filter out empty/null values from all URI configuration properties (protectedURIs, unprotectedURIs, disableCSRFURIs)

### 13. Configure role hierarchy for method security ✅ COMPLETED
- **Issue**: Method security doesn't use role hierarchy automatically
- **Fix**: Create MethodSecurityExpressionHandler bean with hierarchy
- **Status**: Fixed - added methodSecurityExpressionHandler() bean to WebSecurityConfig that uses the existing role hierarchy

### 14. Replace System.out.println with SLF4J ✅ COMPLETED
- **Issue**: Using stdout instead of proper logging
- **Fix**: Update CustomOAuth2AuthenticationEntryPoint and TimeLogger
- **Status**: Fixed - replaced System.out.println in TimeLogger with proper SLF4J logging using a default logger when none is provided

## Persistence & Domain (Priority 3)

### 15. Clean up User.roles type handling ✅ COMPLETED
- **Issue**: Mixed List/Set setters, defensive copying
- **Fix**: Standardize collection handling for JPA dirty checking
- **Status**: Fixed - simplified collection handling to work directly with the underlying Set while maintaining backward compatibility, removed defensive copying that interfered with JPA dirty checking, added smart handling for when the same set object is passed to setters

## Email & Templates (Priority 3)

### 16. Improve MailService error handling ✅ COMPLETED
- **Issue**: Exceptions only logged and swallowed
- **Fix**: Add Spring Retry mechanism or queue
- **Status**: Fixed - added Spring Retry support with @Retryable annotations, exponential backoff (1s, 2s, 4s), and @Recover methods for graceful failure handling after all attempts are exhausted

### 17. Document Thymeleaf dependency ✅ COMPLETED
- **Issue**: Relies on optional TemplateEngine bean
- **Fix**: Document requirement prominently
- **Status**: Fixed - added prominent documentation in README.md Quick Start section with explicit Maven/Gradle dependencies, updated MailContentBuilder JavaDoc with dependency requirements and exception details, clarified TemplateEngine bean requirement

## Audit Issues (Priority 4)

### 18. Improve audit log defaults ✅ COMPLETED
- **Issue**: Default path /opt/app/logs unlikely to be writable
- **Fix**: Use temp directory or auto-create with graceful failure
- **Status**: Fixed - changed default path from `/opt/app/logs` to `./logs` (relative to app directory), added automatic fallback to system temp directory if primary path is not writable, added automatic directory creation, enhanced error handling and logging

### 19. Document conditional flushing ✅ COMPLETED
- **Issue**: Complex conditional expression hard to understand
- **Fix**: Add clear documentation
- **Status**: Fixed - added comprehensive JavaDoc documentation to FileAuditLogFlushScheduler explaining the complex conditional expression @ConditionalOnExpression("${user.audit.logEvents:true} && !${user.audit.flushOnWrite:true}"), clarified when the scheduler is active and why the conditional logic is structured this way

## Build & Publishing (Priority 4)

### 20. Fix group coordinate mismatch ✅ COMPLETED
- **Issue**: group = 'com.digitalsanctuary.springuser' vs publishing 'com.digitalsanctuary'
- **Fix**: Align group with publishing coordinates
- **Status**: Fixed - changed project group from 'com.digitalsanctuary.springuser' to 'com.digitalsanctuary' to match the Maven publishing coordinates

### 21. Dependency management consistency ✅ COMPLETED
- **Issue**: Mixed explicit versions and BOM usage
- **Fix**: Prefer Boot BOM for all Spring dependencies
- **Status**: Fixed - removed explicit versions from Spring Boot dependencies to use BOM-managed versions, eliminated duplicate spring-boot-starter-actuator dependency, standardized dependency declarations to rely on Spring Boot's dependency management

### 22. Simplify test task configuration ✅ COMPLETED
- **Issue**: Overriding test task unusual for library
- **Fix**: Make testAll optional, restore standard test task
- **Status**: Fixed - restored the standard test task to work normally with the default JDK, made testAll an optional task for when multi-JDK testing is desired, removed the unusual override that forced all tests to run with multiple JDKs

## UX & Behavior (Priority 4)

### 23. Document registration verification flow ✅ COMPLETED
- **Issue**: Auto-enable vs email verification unclear
- **Fix**: Add clear documentation
- **Status**: Fixed - added comprehensive documentation in README.md explaining the two registration modes (Auto-Enable vs Email Verification), their behaviors, configuration options, and when each mode is appropriate

### 24. Make post-auth redirects configurable ✅ COMPLETED
- **Issue**: Forces alwaysUseDefaultTargetUrl(true), surprising UX
- **Fix**: Add configuration property
- **Status**: Fixed - added user.security.alwaysUseDefaultTargetUrl configuration property (default: false) to control whether to always redirect to the configured success URL or respect saved requests for better UX. When false, users are redirected to the page they were trying to access before login

### 25. Make global model injection opt-in ✅ COMPLETED
- **Issue**: Adds user to all MVC views by default
- **Fix**: Make opt-in for REST-only apps
- **Status**: Fixed - kept user.web.globalUserModelOptIn default as false (global opt-out mode), added @IncludeUserInModel annotations to existing MVC controllers that need user in model, enhanced documentation to clarify behavior. Now by default, user is NOT added to views unless explicitly requested via annotation, making it suitable for REST-only apps. Added comprehensive comments to prevent future confusion about the naming.

## Documentation

### 26. Create comprehensive getting started guide ✅ COMPLETED
- **Fix**: Document required dependencies, minimal properties, examples
- **Status**: Fixed - created comprehensive Quick Start guide in README.md with step-by-step instructions including prerequisites, dependencies, database setup, email configuration, testing steps, customization options, and complete example configurations

## Notes
- All issues have been validated against the codebase
- Fixes should include appropriate tests
- Run ./gradlew check after each fix to ensure no regressions