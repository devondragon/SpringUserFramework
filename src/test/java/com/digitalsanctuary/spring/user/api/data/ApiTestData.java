package com.digitalsanctuary.spring.user.api.data;

import com.digitalsanctuary.spring.user.dto.LockAccountDto;
import com.digitalsanctuary.spring.user.dto.PasswordDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.service.DSUserDetails;

import java.util.Collections;

public class ApiTestData {

    public static final UserDto BASE_TEST_USER = getUserDto();
    public static final UserDto BASE_ADMIN_USER = getAdminUserDto();
    public static final DSUserDetails DEFAULT_DETAILS = new DSUserDetails(null, null);

    // Ensure no exceptions are thrown during static initialization
    static {
        try {
            // Initialize static fields safely
            // Example:
            // BASE_ADMIN_USER = new UserDto(...);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ApiTestData", e);
        }
    }

    public static PasswordDto getPasswordDto() {
        PasswordDto dto = new PasswordDto();
        dto.setNewPassword("newTestApiUserPassword");
        dto.setOldPassword("testApiUserPassword");
        return dto;
    }

    public static PasswordDto getInvalidPasswordDto() {
        PasswordDto dto = new PasswordDto();
        dto.setNewPassword("newTestApiUserPassword");
        dto.setOldPassword("invalidPassword");
        return dto;
    }

    public static UserDto getUserDto() {
        UserDto userDto = new UserDto();
        userDto.setFirstName("testApiUser");
        userDto.setLastName("userApiTest");
        userDto.setEmail("testApiUser@bk.com");
        userDto.setPassword("testApiUserPassword");
        userDto.setMatchingPassword(userDto.getPassword());
        return userDto;
    }

    public static UserDto getEmptyUserDto() {
        return new UserDto();
    }

    public static UserDto getAdminUserDto() {
        UserDto userDto = new UserDto();
        userDto.setFirstName("testApiAdmin");
        userDto.setLastName("userApiTest");
        userDto.setEmail("testApiAdmin@bk.com");
        userDto.setPassword("testApiAdminPassword");
        userDto.setMatchingPassword(userDto.getPassword());
        userDto.setRole(2);
        return userDto;
    }

    public static LockAccountDto getLockAccountDto() {
        LockAccountDto lockAccountDto = new LockAccountDto();
        lockAccountDto.setEmail("testApiUser@bk.com");
        return lockAccountDto;
    }

    public static LockAccountDto getEmptyLockAccountDto() {
        return new LockAccountDto();
    }

    public static LockAccountDto getLockAccountDtoForMissingUser() {
        LockAccountDto lockAccountDto = new LockAccountDto();
        lockAccountDto.setEmail("testRandom@bk.com");
        return lockAccountDto;
    }

    public static Response successRegistration() {
        return new Response(true, 0, "/user/registration-pending-verification.html", new String[] {"Registration Successful!"}, null);
    }

    public static Response userAlreadyExist() {
        return new Response(false, 2, null, new String[] {"An account already exists for the email address"}, null);
    }

    public static Response systemError() {
        return new Response(false, 5, null, new String[] {"System Error!"}, null);
    }

    public static Response resetPassword() {
        return new Response(true, null, "/user/forgot-password-pending-verification.html",
                new String[] {"If account exists, password reset email has been sent!"}, null);
    }

    public static Response userNotLogged() {
        return new Response(false, null, null, new String[] {"User Not Logged In!"}, null);

    }

    public static Response userUpdateSuccess() {
        return new Response(true, null, null, new String[] {"Your Profile Was Successfully Updated.<br /><br />"}, null);
    }

    public static Response passwordUpdateSuccess() {
        return new Response(true, 0, null, new String[] {"Your password has been successfully updated."}, null);
    }

    public static Response passwordUpdateFailry() {
        return new Response(false, 1, null, new String[] {"The old password is incorrect."}, null);
    }

    public static Response successDeleteAccount() {
        return new Response(true, null, null, new String[] {"Account Deleted"}, null);
    }

    public static Response deleteAccountFailry() {
        return new Response(false, 2, null, new String[] {"Error Occurred"}, null);
    }

    public static Response successLockAccount() {
        return new Response(true, null, null, new String[] {"Account Locked"}, null);
    }

    public static Response lockAccountFailry() {
        return new Response(false, null, null, new String[] {"User not found"}, null);
    }

    public static Response invalidBodyLockAccountFailry() {
        return new Response(false, 1, null, new String[] {"Email is required"}, null);
    }
}
