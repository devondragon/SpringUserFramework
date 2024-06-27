package com.digitalsanctuary.spring.user.api.data;

import com.digitalsanctuary.spring.user.dto.PasswordDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.service.DSUserDetails;

public class ApiTestData {

    public static final UserDto BASE_TEST_USER = getUserDto();
    public static final DSUserDetails DEFAULT_DETAILS = new DSUserDetails(null, null);

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

    public static Response successRegistration() {
        return new Response(
                true, 0,
                "/user/registration-pending-verification.html",
                new String[]{"Registration Successful!"}, null
        );
    }
    public static Response userAlreadyExist() {
        return new Response(
                false, 2, null,
                new String[]{"An account already exists for the email address"}, null
        );
    }
    public static Response systemError() {
        return new Response(
                false, 5, null,
                new String[]{"System Error!"}, null
        );
    }
    public static Response resetPassword() {
        return new Response(true, null, "/user/forgot-password-pending-verification.html",
                new String[]{"If account exists, password reset email has been sent!"}, null
        );
    }
    public static Response userNotLogged() {
        return new Response(false, null, null,
                new String[]{"User Not Logged In!"}, null
        );

    }
    public static Response userUpdateSuccess() {
        return new Response(true, null, null,
                new String[]{"Your Profile Was Successfully Updated.<br /><br />"}, null
        );
    }
    public static Response passwordUpdateSuccess() {
        return new Response(true, 0, null,
                new String[]{"Password updated successfully"}, null
        );
    }

    public static Response passwordUpdateFailry() {
        return new Response(false, 1, null,
                new String[]{"Invalid Old Password"}, null
        );
    }
    public static Response successDeleteAccount() {
        return new Response(true, null, null,
                new String[]{"Account Deleted"}, null
        );
    }
    public static Response deleteAccountFailry() {
        return new Response(false, 2, null,
                new String[]{"Error Occurred"}, null
        );
    }
}
