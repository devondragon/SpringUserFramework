package com.digitalsanctuary.spring.user.api.provider;

import com.digitalsanctuary.spring.user.api.data.RegistrationResponse;
import com.digitalsanctuary.spring.user.dto.UserDto;

public class ApiTestArgumentsHolder {

    public enum UserStatus {
        NEW,
        EXIST
    }

    private final UserDto userDto;
    private final UserStatus status;
    private final RegistrationResponse response;


    public ApiTestArgumentsHolder(UserDto userDto, UserStatus status, RegistrationResponse response) {
        this.userDto = userDto;
        this.status = status;
        this.response = response;
    }

    public UserDto getUserDto() {
        return userDto;
    }

    public UserStatus getStatus() {
        return status;
    }

    public RegistrationResponse getResponse() {
        return response;
    }
}
