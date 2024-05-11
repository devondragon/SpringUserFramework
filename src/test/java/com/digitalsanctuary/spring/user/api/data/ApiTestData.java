package com.digitalsanctuary.spring.user.api.data;

import com.digitalsanctuary.spring.user.dto.UserDto;

public class ApiTestData {

    public static UserDto getUserDto() {
        UserDto userDto = new UserDto();
        userDto.setFirstName("testApiUser");
        userDto.setLastName("userApiTest");
        userDto.setEmail("testApiUser@bk.com");
        userDto.setPassword("testApiUserPassword");
        userDto.setMatchingPassword(userDto.getPassword());
        return userDto;
    }

    public static RegistrationResponse successRegistration() {
        return new RegistrationResponse(
                true, 0,
                "/user/registration-pending-verification.html",
                new String[]{"Registration Successful!"}, null
        );
    }

    public static RegistrationResponse userAlreadyExist() {
        return new RegistrationResponse(
                false, 2, null,
                new String[]{"An account already exists for the email address"}, null
        );
    }
}
