# Password Validation & History - Implementation Plan

## Executive Summary
This plan addresses 4 critical security issues in the password management system where validation can be bypassed.

---

## Priority 1: Critical Security Fixes

### Fix #1: Add Password Validation to `/updatePassword` Endpoint
**Status:** ❌ Currently allows weak passwords
**Impact:** HIGH - Logged-in users can set weak passwords
**Estimated Time:** 2 hours

#### Current Issue
The `/updatePassword` endpoint at [UserAPI.java:186-210](src/main/java/com/digitalsanctuary/spring/user/api/UserAPI.java#L186-L210) does not validate the new password against policy rules.

#### Implementation Steps

1. **Modify UserAPI.java - `/updatePassword` method** (30 min)
   - Location: `src/main/java/com/digitalsanctuary/spring/user/api/UserAPI.java:186`
   - Add password validation before calling `changeUserPassword()`
   - Return validation errors to user if any policy violations

   ```java
   @PostMapping("/updatePassword")
   public ResponseEntity<JSONResponse> updatePassword(@AuthenticationPrincipal DSUserDetails userDetails,
           @Valid @RequestBody PasswordDto passwordDto, HttpServletRequest request, Locale locale) {
       validateAuthenticatedUser(userDetails);
       User user = userDetails.getUser();

       try {
           // Verify old password
           if (!userService.checkIfValidOldPassword(user, passwordDto.getOldPassword())) {
               throw new InvalidOldPasswordException("Invalid old password");
           }

           // ✅ ADD: Validate new password against policy
           List<String> errors = passwordPolicyService.validate(
               user,
               passwordDto.getNewPassword(),
               user.getEmail(),
               locale
           );

           if (!errors.isEmpty()) {
               log.warn("Password validation failed for user {}: {}", user.getEmail(), errors);
               return buildErrorResponse(String.join(" ", errors), 1, HttpStatus.BAD_REQUEST);
           }

           // Save password (this also saves to history)
           userService.changeUserPassword(user, passwordDto.getNewPassword());
           logAuditEvent("PasswordUpdate", "Success", "User password updated", user, request);

           return buildSuccessResponse(messages.getMessage("message.update-password.success", null, locale), null);
       } catch (InvalidOldPasswordException ex) {
           logAuditEvent("PasswordUpdate", "Failure", "Invalid old password", user, request);
           return buildErrorResponse(messages.getMessage("message.update-password.invalid-old", null, locale), 1,
                   HttpStatus.BAD_REQUEST);
       } catch (Exception ex) {
           log.error("Unexpected error during password update.", ex);
           logAuditEvent("PasswordUpdate", "Failure", ex.getMessage(), user, request);
           return buildErrorResponse("System Error!", 5, HttpStatus.INTERNAL_SERVER_ERROR);
       }
   }
   ```

2. **Add Unit Tests** (45 min)
   - Location: `src/test/java/com/digitalsanctuary/spring/user/api/UserAPIUnitTest.java`
   - Test cases:
     - ✅ Valid password update succeeds
     - ✅ Password too short is rejected
     - ✅ Password missing required characters is rejected
     - ✅ Password matching history is rejected
     - ✅ Password too similar to email is rejected
     - ✅ Invalid old password is rejected

3. **Add Integration Tests** (45 min)
   - Location: `src/test/java/com/digitalsanctuary/spring/user/api/UserApiTest.java`
   - Test full flow: login → update password with weak password → verify rejection
   - Test full flow: login → update password with reused password → verify rejection
   - Test full flow: login → update password with valid password → verify success

#### Files to Modify
- `src/main/java/com/digitalsanctuary/spring/user/api/UserAPI.java`
- `src/test/java/com/digitalsanctuary/spring/user/api/UserAPIUnitTest.java`
- `src/test/java/com/digitalsanctuary/spring/user/api/UserApiTest.java`

#### Testing Checklist
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual test: Update password with weak password (should fail)
- [ ] Manual test: Update password with reused password (should fail)
- [ ] Manual test: Update password with valid password (should succeed)

---

### Fix #2: Implement Missing `/savePassword` Endpoint
**Status:** ❌ Endpoint missing - password reset flow incomplete
**Impact:** CRITICAL - Password reset doesn't work
**Estimated Time:** 3 hours

#### Current Issue
The forgot-password form (Demo App) posts to `/user/resetPassword` with a token and new password, but:
- The existing `/resetPassword` endpoint only sends the email (takes email, not token+password)
- No endpoint exists to actually save the new password after token validation
- Config references `/user/savePassword` in unprotected URIs but it's not implemented

#### Implementation Steps

1. **Create SavePasswordDto** (15 min)
   - Location: `src/main/java/com/digitalsanctuary/spring/user/dto/SavePasswordDto.java`
   - Fields: `token`, `newPassword`, `confirmPassword`
   - Add validation annotations

   ```java
   package com.digitalsanctuary.spring.user.dto;

   import jakarta.validation.constraints.NotEmpty;
   import jakarta.validation.constraints.NotNull;
   import lombok.Data;

   @Data
   public class SavePasswordDto {

       @NotNull
       @NotEmpty
       private String token;

       @NotNull
       @NotEmpty
       private String newPassword;

       @NotNull
       @NotEmpty
       private String confirmPassword;
   }
   ```

2. **Add `/savePassword` endpoint to UserAPI** (1 hour)
   - Location: `src/main/java/com/digitalsanctuary/spring/user/api/UserAPI.java`
   - Validate token is still valid
   - Get user by token
   - Validate new password against policy
   - Check passwords match
   - Save new password
   - Invalidate token

   ```java
   /**
    * Saves a new password after password reset token validation.
    * This endpoint is called from the password reset form after the user
    * clicks the link in their email and enters a new password.
    *
    * @param savePasswordDto DTO containing token and new password
    * @param request HTTP servlet request
    * @param locale locale for messages
    * @return ResponseEntity with success or error response
    */
   @PostMapping("/savePassword")
   public ResponseEntity<JSONResponse> savePassword(
           @Valid @RequestBody SavePasswordDto savePasswordDto,
           HttpServletRequest request,
           Locale locale) {

       try {
           // Validate passwords match
           if (!savePasswordDto.getNewPassword().equals(savePasswordDto.getConfirmPassword())) {
               return buildErrorResponse(
                   messages.getMessage("message.password.mismatch", null, locale),
                   1,
                   HttpStatus.BAD_REQUEST
               );
           }

           // Validate the reset token
           TokenValidationResult tokenResult = userService.validatePasswordResetToken(
               savePasswordDto.getToken()
           );

           if (tokenResult != TokenValidationResult.VALID) {
               String messageKey = "auth.message." + tokenResult.getValue();
               return buildErrorResponse(
                   messages.getMessage(messageKey, null, locale),
                   2,
                   HttpStatus.BAD_REQUEST
               );
           }

           // Get user by token
           Optional<User> userOptional = userService.getUserByPasswordResetToken(
               savePasswordDto.getToken()
           );

           if (userOptional.isEmpty()) {
               return buildErrorResponse(
                   messages.getMessage("auth.message.invalid", null, locale),
                   3,
                   HttpStatus.BAD_REQUEST
               );
           }

           User user = userOptional.get();

           // Validate new password against policy
           List<String> errors = passwordPolicyService.validate(
               user,
               savePasswordDto.getNewPassword(),
               user.getEmail(),
               locale
           );

           if (!errors.isEmpty()) {
               log.warn("Password validation failed during reset for user {}: {}",
                   user.getEmail(), errors);
               return buildErrorResponse(String.join(" ", errors), 4, HttpStatus.BAD_REQUEST);
           }

           // Save the new password
           userService.changeUserPassword(user, savePasswordDto.getNewPassword());

           // Delete the reset token (it's been used)
           PasswordResetToken token = userService.getPasswordResetToken(savePasswordDto.getToken());
           if (token != null) {
               // TODO: Add deletePasswordResetToken method to UserService
               // For now, the token will expire naturally
           }

           logAuditEvent("PasswordReset", "Success", "Password reset completed", user, request);

           return buildSuccessResponse(
               messages.getMessage("message.reset-password.success", null, locale),
               "/user/login.html"
           );

       } catch (Exception ex) {
           log.error("Unexpected error during password reset.", ex);
           logAuditEvent("PasswordReset", "Failure", ex.getMessage(), null, request);
           return buildErrorResponse("System Error!", 5, HttpStatus.INTERNAL_SERVER_ERROR);
       }
   }
   ```

3. **Add helper method to UserService** (15 min)
   - Location: `src/main/java/com/digitalsanctuary/spring/user/service/UserService.java`
   - Add method to delete password reset token after use

   ```java
   /**
    * Deletes a password reset token.
    *
    * @param token the token to delete
    */
   public void deletePasswordResetToken(String token) {
       PasswordResetToken resetToken = passwordTokenRepository.findByToken(token);
       if (resetToken != null) {
           passwordTokenRepository.delete(resetToken);
           log.debug("Deleted password reset token for user: {}", resetToken.getUser().getEmail());
       }
   }
   ```

4. **Update configuration** (5 min)
   - Verify `/user/savePassword` is in unprotected URIs list
   - Location: `src/main/resources/config/dsspringuserconfig.properties:57`
   - Already present: ✅

5. **Add message keys** (10 min)
   - Location: `src/main/resources/messages/dsspringusermessages.properties`
   - Add keys:
     ```properties
     message.password.mismatch=Passwords do not match
     message.reset-password.success=Your password has been successfully reset. You can now log in with your new password.
     ```

6. **Update Demo App Form** (15 min)
   - Location: `SpringUserFrameworkDemoApp/src/main/resources/templates/user/forgot-password-change.html:25`
   - Change form action from `/user/resetPassword` to `/user/savePassword`
   - Ensure field names match DTO: `token`, `newPassword`, `confirmPassword`

7. **Add Unit Tests** (45 min)
   - Location: `src/test/java/com/digitalsanctuary/spring/user/api/UserAPIUnitTest.java`
   - Test cases:
     - ✅ Valid token + valid password succeeds
     - ✅ Invalid token is rejected
     - ✅ Expired token is rejected
     - ✅ Weak password is rejected
     - ✅ Password in history is rejected
     - ✅ Passwords not matching is rejected

8. **Add Integration Tests** (45 min)
   - Location: Create new test file `src/test/java/com/digitalsanctuary/spring/user/api/PasswordResetFlowTest.java`
   - Test full flow:
     1. Request password reset
     2. Extract token from "email"
     3. Submit new password with token
     4. Verify password was changed
     5. Verify old password no longer works
     6. Verify new password works for login

#### Files to Create
- `src/main/java/com/digitalsanctuary/spring/user/dto/SavePasswordDto.java`
- `src/test/java/com/digitalsanctuary/spring/user/api/PasswordResetFlowTest.java`

#### Files to Modify
- `src/main/java/com/digitalsanctuary/spring/user/api/UserAPI.java`
- `src/main/java/com/digitalsanctuary/spring/user/service/UserService.java`
- `src/main/resources/messages/dsspringusermessages.properties`
- Demo App: `forgot-password-change.html` (form action)

#### Testing Checklist
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual test: Full password reset flow (request → email → click link → enter password → verify)
- [ ] Manual test: Try to reuse reset token (should fail)
- [ ] Manual test: Try to use expired token (should fail)
- [ ] Manual test: Try weak password in reset (should fail)

---

## Priority 2: Design & Architecture Fixes

### Fix #3: Make Password History Check Explicit for Registration
**Status:** ⚠️ Inconsistent - history not checked for new users
**Impact:** MEDIUM - Minor security gap, but acceptable for new users
**Estimated Time:** 1.5 hours

#### Current Issue
At [UserAPI.java:77](src/main/java/com/digitalsanctuary/spring/user/api/UserAPI.java#L77), registration passes `null` for user:
```java
List<String> errors = passwordPolicyService.validate(null, userDto.getPassword(), ...);
```

This bypasses history checking because [PasswordPolicyService.java:214-216](src/main/java/com/digitalsanctuary/spring/user/service/PasswordPolicyService.java#L214-L216) returns early if user is null.

#### Design Decision Options

**Option A: Accept Current Behavior (Recommended)**
- Document that password history only applies to existing users
- Add JavaDoc comments explaining this design decision
- Add unit test documenting this behavior

**Option B: Check History for New Users**
- Could check if ANY user in the system has used this password
- Privacy concern: exposes that password exists in system
- Not recommended for security/privacy reasons

**Option C: Split Validation Methods**
- Create separate methods: `validateNewUserPassword()` and `validateExistingUserPassword()`
- More explicit, but adds complexity

#### Implementation Steps (Option A - Recommended)

1. **Add Documentation** (30 min)
   - Update JavaDoc in `PasswordPolicyService.validate()`
   - Explain why user can be null for registration
   - Document that history checks only apply when user != null

2. **Add Test for New User Registration** (30 min)
   - Location: `src/test/java/com/digitalsanctuary/spring/user/service/PasswordPolicyServiceTest.java`
   - Test: `validate_skipsHistoryCheck_whenUserIsNull()`
   - Documents expected behavior

3. **Add Comment in UserAPI** (15 min)
   ```java
   // Note: Passing null for user during registration means password history
   // is not checked (new users have no history). This is intentional - only
   // existing users are checked against their own password history.
   List<String> errors = passwordPolicyService.validate(null, userDto.getPassword(), ...);
   ```

#### Files to Modify
- `src/main/java/com/digitalsanctuary/spring/user/service/PasswordPolicyService.java`
- `src/main/java/com/digitalsanctuary/spring/user/api/UserAPI.java`
- `src/test/java/com/digitalsanctuary/spring/user/service/PasswordPolicyServiceTest.java`

#### Testing Checklist
- [ ] Documentation is clear
- [ ] Test documents expected behavior
- [ ] Code review confirms design decision is acceptable

---

### Fix #4: Add Transaction Isolation for Password History Cleanup
**Status:** ⚠️ Race condition possible
**Impact:** LOW - Unlikely in practice
**Estimated Time:** 1 hour

#### Current Issue
The cleanup method at [UserService.java:305-319](src/main/java/com/digitalsanctuary/spring/user/service/UserService.java#L305-L319) is not atomic. If two threads change the same user's password concurrently, they could both fetch the same list and cause issues.

#### Implementation Steps

1. **Add @Transactional with Isolation Level** (15 min)
   - Location: `src/main/java/com/digitalsanctuary/spring/user/service/UserService.java:305`
   - Add SERIALIZABLE isolation to prevent concurrent modifications

   ```java
   @Transactional(isolation = Isolation.SERIALIZABLE)
   private void cleanUpPasswordHistory(User user) {
       if (user == null || historyCount <= 0) {
           return;
       }

       List<PasswordHistoryEntry> entries = passwordHistoryRepository.findByUserOrderByEntryDateDesc(user);
       int maxEntries = historyCount + 1;

       if (entries.size() > maxEntries) {
           List<PasswordHistoryEntry> toDelete = entries.subList(maxEntries, entries.size());
           passwordHistoryRepository.deleteAll(toDelete);
           log.debug("Cleaned up {} old password history entries for user: {}",
               toDelete.size(), user.getEmail());
       }
   }
   ```

2. **Alternative: Use Database-Level Constraint** (30 min)
   - Add trigger or stored procedure to maintain max N entries per user
   - More complex but handles concurrency at DB level
   - Skip this if transaction isolation is sufficient

3. **Add Concurrent Modification Test** (45 min)
   - Location: `src/test/java/com/digitalsanctuary/spring/user/service/UserServiceTest.java`
   - Test: Two threads changing password for same user simultaneously
   - Verify cleanup doesn't fail or cause data issues
   - Use CountDownLatch to coordinate threads

   ```java
   @Test
   void changeUserPassword_handlesLowRateConcurrentModification() throws Exception {
       // This test verifies that concurrent password changes don't cause issues
       // In practice, this is very unlikely as it requires the same user to
       // change their password from two different sessions simultaneously

       User user = new User();
       user.setEmail("test@example.com");
       user.setPassword(passwordEncoder.encode("OldPassword1!"));
       user = userRepository.save(user);

       CountDownLatch startLatch = new CountDownLatch(1);
       CountDownLatch doneLatch = new CountDownLatch(2);
       User finalUser = user;

       Runnable changePassword = () -> {
           try {
               startLatch.await();  // Wait for both threads to be ready
               userService.changeUserPassword(finalUser, "NewPassword" +
                   Thread.currentThread().getId() + "!");
           } catch (Exception e) {
               log.error("Error in thread", e);
           } finally {
               doneLatch.countDown();
           }
       };

       new Thread(changePassword).start();
       new Thread(changePassword).start();

       startLatch.countDown();  // Start both threads
       doneLatch.await(5, TimeUnit.SECONDS);  // Wait for completion

       // Verify: No exceptions thrown, password was changed
       List<PasswordHistoryEntry> history =
           passwordHistoryRepository.findByUserOrderByEntryDateDesc(finalUser);
       assertTrue(history.size() <= historyCount + 1,
           "Should not have more than max entries even with concurrent changes");
   }
   ```

#### Files to Modify
- `src/main/java/com/digitalsanctuary/spring/user/service/UserService.java`
- `src/test/java/com/digitalsanctuary/spring/user/service/UserServiceTest.java`

#### Testing Checklist
- [ ] Unit tests pass
- [ ] Concurrent modification test passes
- [ ] Performance impact acceptable (SERIALIZABLE is stricter)

---

## Implementation Order

### Phase 1: Critical Fixes (Day 1)
1. Fix #2: Implement `/savePassword` endpoint (3 hours)
   - Highest priority: Currently blocking password reset
   - Must be completed first

2. Fix #1: Add validation to `/updatePassword` (2 hours)
   - High priority: Security vulnerability
   - Straightforward implementation

**Total: 5 hours (1 day)**

### Phase 2: Refinements (Day 2)
3. Fix #3: Document password history behavior (1.5 hours)
   - Medium priority: Documentation/consistency
   - Low risk

4. Fix #4: Add transaction isolation (1 hour)
   - Low priority: Edge case
   - Nice to have

**Total: 2.5 hours (half day)**

---

## Testing Strategy

### Unit Tests
- Test each validation path independently
- Mock dependencies (PasswordPolicyService, UserService)
- Fast execution (< 1 second per test)

### Integration Tests
- Test full flows with real database (H2 in-memory)
- Test cross-component interactions
- Verify end-to-end behavior

### Manual Testing Checklist
- [ ] Register new user with weak password (should fail)
- [ ] Register new user with strong password (should succeed)
- [ ] Update password to weak password (should fail)
- [ ] Update password to reused password (should fail)
- [ ] Update password to valid password (should succeed)
- [ ] Request password reset
- [ ] Complete password reset with weak password (should fail)
- [ ] Complete password reset with valid password (should succeed)
- [ ] Try to reuse reset token (should fail)
- [ ] Verify password history is maintained correctly

---

## Rollback Plan

If issues are discovered after deployment:

1. **Configuration-based Disable**
   - Set `user.security.password.enabled=false` to disable validation
   - Allows system to continue functioning while fix is developed

2. **Endpoint-specific Rollback**
   - If `/savePassword` has issues, temporarily disable forgot-password feature
   - Users can still update passwords via `/updatePassword` when logged in

3. **Database Rollback**
   - Password history entries are append-only
   - Safe to rollback without data loss
   - Users may need to reset passwords again if rolled back mid-flow

---

## Success Criteria

### Functional Requirements Met
- ✅ All password changes validate against policy
- ✅ Password reset flow is complete and working
- ✅ Password history is enforced consistently
- ✅ No race conditions in concurrent scenarios

### Quality Requirements Met
- ✅ All unit tests pass (>95% coverage on modified code)
- ✅ All integration tests pass
- ✅ Manual testing scenarios pass
- ✅ No regression in existing functionality

### Documentation Requirements Met
- ✅ JavaDoc updated for all modified methods
- ✅ Design decisions documented
- ✅ Test cases document expected behavior

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Break existing password reset flow | Medium | High | Thorough testing of all password flows |
| Performance degradation from SERIALIZABLE isolation | Low | Low | Load testing, can reduce isolation if needed |
| Users frustrated by stricter validation | Medium | Low | Clear error messages, documentation |
| Missed edge case in validation | Low | Medium | Code review, extensive test coverage |

---

## Notes

- All changes are backwards compatible (additive only)
- No database schema changes required
- Configuration changes are minimal
- Can be deployed incrementally (Fix #2 → Fix #1 → Fix #3 → Fix #4)

---

**Created:** 2025-01-26
**Last Updated:** 2025-01-26
**Assigned To:** TBD
**Estimated Total Time:** 7.5 hours (1.5 days)
