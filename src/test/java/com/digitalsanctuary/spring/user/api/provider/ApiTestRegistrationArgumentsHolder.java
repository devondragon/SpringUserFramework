package com.digitalsanctuary.spring.user.api.provider;

import com.digitalsanctuary.spring.user.api.data.RegistrationResponse;
import com.digitalsanctuary.spring.user.dto.UserDto;

public class ApiTestRegistrationArgumentsHolder {

    public enum DataStatus {
        NEW,
        EXIST,
        INVALID
    }

    private final UserDto userDto;
    private final DataStatus status;
    private final RegistrationResponse response;


    public ApiTestRegistrationArgumentsHolder(UserDto userDto, DataStatus status, RegistrationResponse response) {
        this.userDto = userDto;
        this.status = status;
        this.response = response;
    }

    public UserDto getUserDto() {
        return userDto;
    }

    public DataStatus getStatus() {
        return status;
    }

    public RegistrationResponse getResponse() {
        return response;
    }
}
