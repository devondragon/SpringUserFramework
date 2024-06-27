package com.digitalsanctuary.spring.user.api.provider.holder;

import com.digitalsanctuary.spring.user.api.data.DataStatus;
import com.digitalsanctuary.spring.user.api.data.Response;
import com.digitalsanctuary.spring.user.dto.PasswordDto;
import com.digitalsanctuary.spring.user.dto.UserDto;

public class ApiTestArgumentsHolder {

    private UserDto userDto;
    private PasswordDto passwordDto;
    private final DataStatus status;
    private final Response response;


    public ApiTestArgumentsHolder(UserDto userDto, DataStatus status, Response response) {
        this.userDto = userDto;
        this.status = status;
        this.response = response;
    }

    public ApiTestArgumentsHolder(PasswordDto passwordDto, DataStatus status, Response response) {
        this.passwordDto = passwordDto;
        this.status = status;
        this.response = response;
    }

    public ApiTestArgumentsHolder(DataStatus status, Response response) {
        this.status = status;
        this.response = response;
    }

    public UserDto getUserDto() {
        return userDto;
    }

    public DataStatus getStatus() {
        return status;
    }

    public Response getResponse() {
        return response;
    }

    public PasswordDto getPasswordDto() {
        return passwordDto;
    }
}
