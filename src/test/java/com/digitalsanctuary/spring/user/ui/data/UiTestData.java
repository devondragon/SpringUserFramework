package com.digitalsanctuary.spring.user.ui.data;

import com.digitalsanctuary.spring.user.dto.UserDto;
public class UiTestData {

    public static final String ACCOUNT_EXIST_ERROR_MESSAGE = "An account for that username/email already exists. " +
            "Please enter a different email.";

    public static final String SUCCESS_SING_UP_MESSAGE = "Thank you for registering!";

    public static final String SUCCESS_RESET_PASSWORD_MESSAGE = "You should receive a password reset email shortly";

    public static final String TEST_USER_ENCODED_PASSWORD = "$2y$10$XIRn/npMMCGt21gpU6QAbeOAUSxj/C7A793YZe.a6AEvL0LhQwkqW";

    public static UserDto getUserDto() {
        UserDto userDto = new UserDto();
        userDto.setFirstName("testUiUser");
        userDto.setLastName("userUiTest");
        userDto.setEmail("testUiUser@bk.com");
        userDto.setPassword("testUiUserPassword");
        userDto.setMatchingPassword(userDto.getPassword());
        return userDto;
    }
}
