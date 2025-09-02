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

### 8. Add DTO validation annotations
- **Issue**: UserDto and PasswordDto lack bean validation
- **Fix**: Add @NotBlank, @Email, password constraints, create @ControllerAdvice

### 9. Fix CSRF property typo
- **Issue**: Property name contains odd "d" - 'disableCSRFdURIs'
- **Fix**: Rename to 'disableCSRFURIs'

### 10. Improve error message handling
- **Issue**: CustomOAuth2AuthenticationEntryPoint exposes exception details
- **Fix**: Use generic user messages, log details internally

### 11. Enhance IP detection
- **Issue**: Only honors X-Forwarded-For header
- **Fix**: Support X-Real-IP, CF-Connecting-IP, True-Client-IP

## Web/Security Config (Priority 3)

### 12. Fix property injection robustness
- **Issue**: Empty property yields list with empty string
- **Fix**: Filter empty strings from unprotectedURIs list

### 13. Configure role hierarchy for method security
- **Issue**: Method security doesn't use role hierarchy automatically
- **Fix**: Create MethodSecurityExpressionHandler bean with hierarchy

### 14. Replace System.out.println with SLF4J
- **Issue**: Using stdout instead of proper logging
- **Fix**: Update CustomOAuth2AuthenticationEntryPoint and TimeLogger

## Persistence & Domain (Priority 3)

### 15. Clean up User.roles type handling
- **Issue**: Mixed List/Set setters, defensive copying
- **Fix**: Standardize collection handling for JPA dirty checking

## Email & Templates (Priority 3)

### 16. Improve MailService error handling
- **Issue**: Exceptions only logged and swallowed
- **Fix**: Add Spring Retry mechanism or queue

### 17. Document Thymeleaf dependency
- **Issue**: Relies on optional TemplateEngine bean
- **Fix**: Document requirement prominently

## Audit Issues (Priority 4)

### 18. Improve audit log defaults
- **Issue**: Default path /opt/app/logs unlikely to be writable
- **Fix**: Use temp directory or auto-create with graceful failure

### 19. Document conditional flushing
- **Issue**: Complex conditional expression hard to understand
- **Fix**: Add clear documentation

## Build & Publishing (Priority 4)

### 20. Fix group coordinate mismatch
- **Issue**: group = 'com.digitalsanctuary.springuser' vs publishing 'com.digitalsanctuary'
- **Fix**: Align group with publishing coordinates

### 21. Dependency management consistency
- **Issue**: Mixed explicit versions and BOM usage
- **Fix**: Prefer Boot BOM for all Spring dependencies

### 22. Simplify test task configuration
- **Issue**: Overriding test task unusual for library
- **Fix**: Make testAll optional, restore standard test task

## UX & Behavior (Priority 4)

### 23. Document registration verification flow
- **Issue**: Auto-enable vs email verification unclear
- **Fix**: Add clear documentation

### 24. Make post-auth redirects configurable
- **Issue**: Forces alwaysUseDefaultTargetUrl(true), surprising UX
- **Fix**: Add configuration property

### 25. Make global model injection opt-in
- **Issue**: Adds user to all MVC views by default
- **Fix**: Make opt-in for REST-only apps

## Documentation

### 26. Create comprehensive getting started guide
- **Fix**: Document required dependencies, minimal properties, examples

## Notes
- All issues have been validated against the codebase
- Fixes should include appropriate tests
- Run ./gradlew check after each fix to ensure no regressions