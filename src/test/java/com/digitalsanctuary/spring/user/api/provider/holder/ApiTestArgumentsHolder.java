package com.digitalsanctuary.spring.user.api.provider.holder;

import com.digitalsanctuary.spring.user.api.data.DataStatus;
import com.digitalsanctuary.spring.user.api.data.Response;
import com.digitalsanctuary.spring.user.dto.LockAccountDto;
import com.digitalsanctuary.spring.user.dto.PasswordDto;
import com.digitalsanctuary.spring.user.dto.UserDto;

public class ApiTestArgumentsHolder {

    private UserDto userDto;
    private PasswordDto passwordDto;
    private LockAccountDto lockAccountDto;
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

    public ApiTestArgumentsHolder(LockAccountDto lockAccountDto, DataStatus status, Response response) {
        this.lockAccountDto = lockAccountDto;
        this.status = status;
        this.response = response;
    }

    public UserDto getUserDto() {
        return userDto;
    }

    public DataStatus getStatus() {
        return status;
    }

    public LockAccountDto getLockAccountDto() {
        return lockAccountDto;
    }

    public Response getResponse() {
        return response;
    }

    public PasswordDto getPasswordDto() {
        return passwordDto;
    }
}
