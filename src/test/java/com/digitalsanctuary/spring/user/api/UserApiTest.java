package com.digitalsanctuary.spring.user.api;

import com.digitalsanctuary.spring.user.api.data.RegistrationResponse;
import com.digitalsanctuary.spring.user.api.provider.ApiTestArgumentsHolder;
import com.digitalsanctuary.spring.user.api.provider.ApiTestArgumentsProvider;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.json.JsonUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ui.jdbc.Jdbc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class UserApiTest extends BaseApiTest {
    private static final String URL = "/user/registration";

    private static UserDto testUser;

    @AfterAll
    public static void deleteTestUser() {
        Jdbc.deleteTestUser(testUser);
    }

    @ParameterizedTest
    @ArgumentsSource(ApiTestArgumentsProvider.class)
    //Integration tests not passing without @RequestBody, but UI tests not passing with @RequestBody
    public void registerUserAccount(ApiTestArgumentsHolder argumentsHolder) throws Exception {
        System.out.println("---- " + argumentsHolder.getStatus());
        testUser = argumentsHolder.getUserDto();
        ResultActions action = perform(MockMvcRequestBuilders.post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonUtil.writeValue(testUser)));
        if (argumentsHolder.getStatus() == ApiTestArgumentsHolder.UserStatus.NEW) {
            action.andExpect(status().isOk());
        }
        if (argumentsHolder.getStatus() == ApiTestArgumentsHolder.UserStatus.EXIST) {
            action.andExpect(status().isConflict());
        }

        RegistrationResponse actual = JsonUtil.readValue(action.andReturn()
                .getResponse().getContentAsString(), RegistrationResponse.class);
        RegistrationResponse excepted = argumentsHolder.getResponse();
        Assertions.assertEquals(excepted, actual);
    }

}
