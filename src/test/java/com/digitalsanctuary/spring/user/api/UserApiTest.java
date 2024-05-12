package com.digitalsanctuary.spring.user.api;

import com.digitalsanctuary.spring.user.api.data.RegistrationResponse;
import com.digitalsanctuary.spring.user.api.provider.ApiTestRegistrationArgumentsHolder;
import com.digitalsanctuary.spring.user.api.provider.ApiTestRegistrationArgumentsProvider;
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

import static com.digitalsanctuary.spring.user.api.helper.ApiTestHelper.buildUrlEncodedFormEntity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class UserApiTest extends BaseApiTest {
    private static final String URL = "/user/registration";

    private static UserDto testUser;

    @AfterAll
    public static void deleteTestUser() {
        Jdbc.deleteTestUser(testUser);
    }

    @ParameterizedTest
    @ArgumentsSource(ApiTestRegistrationArgumentsProvider.class)
    public void registerUserAccount(ApiTestRegistrationArgumentsHolder argumentsHolder) throws Exception {
        testUser = argumentsHolder.getUserDto();
        ResultActions action = perform(MockMvcRequestBuilders.post(URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(buildUrlEncodedFormEntity(testUser)));
        if (argumentsHolder.getStatus() == ApiTestRegistrationArgumentsHolder.UserStatus.NEW) {
            action.andExpect(status().isOk());
        }
        if (argumentsHolder.getStatus() == ApiTestRegistrationArgumentsHolder.UserStatus.EXIST) {
            action.andExpect(status().isConflict());
        }

        RegistrationResponse actual = JsonUtil.readValue(action.andReturn()
                .getResponse().getContentAsString(), RegistrationResponse.class);
        RegistrationResponse excepted = argumentsHolder.getResponse();
        Assertions.assertEquals(excepted, actual);
    }

}
