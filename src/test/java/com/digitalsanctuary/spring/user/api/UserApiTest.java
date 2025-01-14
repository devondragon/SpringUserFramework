package com.digitalsanctuary.spring.user.api;

import static com.digitalsanctuary.spring.user.api.helper.ApiTestHelper.buildUrlEncodedFormEntity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.digitalsanctuary.spring.user.api.data.ApiTestData;
import com.digitalsanctuary.spring.user.api.data.DataStatus;
import com.digitalsanctuary.spring.user.api.data.Response;
import com.digitalsanctuary.spring.user.api.helper.AssertionsHelper;
import com.digitalsanctuary.spring.user.api.provider.ApiTestRegistrationArgumentsProvider;
import com.digitalsanctuary.spring.user.api.provider.holder.ApiTestArgumentsHolder;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.jdbc.Jdbc;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.UserService;

public class UserApiTest extends BaseApiTest {
    private static final String URL = "/user";

    @Autowired
    private UserService userService;

    private static final UserDto baseTestUser = ApiTestData.BASE_TEST_USER;

    @AfterAll
    public static void afterAll() {
        Jdbc.deleteTestUser(baseTestUser);
    }

    /**
     *
     * @param argumentsHolder
     * @throws Exception testing with three params: new user data, exist user data and invalid user data
     */
    @ParameterizedTest
    @ArgumentsSource(ApiTestRegistrationArgumentsProvider.class)
    @Order(1)
    // correctly run separately
    public void registerUserAccount(ApiTestArgumentsHolder argumentsHolder) throws Exception {
        ResultActions action = perform(MockMvcRequestBuilders.post(URL + "/registration").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(buildUrlEncodedFormEntity(argumentsHolder.getUserDto())));

        if (argumentsHolder.getStatus() == DataStatus.NEW) {
            action.andExpect(status().isOk());
        }
        if (argumentsHolder.getStatus() == DataStatus.EXIST) {
            action.andExpect(status().isConflict());
        }
        if (argumentsHolder.getStatus() == DataStatus.INVALID) {
            action.andExpect(status().is5xxServerError());
        }

        MockHttpServletResponse actual = action.andReturn().getResponse();
        Response excepted = argumentsHolder.getResponse();
        AssertionsHelper.compareResponses(actual, excepted);
    }

    @Test
    @Order(2)
    public void resetPassword() throws Exception {
        ResultActions action = perform(MockMvcRequestBuilders.post(URL + "/resetPassword").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(buildUrlEncodedFormEntity(baseTestUser))).andExpect(status().isOk());

        MockHttpServletResponse actual = action.andReturn().getResponse();
        Response excepted = ApiTestData.resetPassword();
        AssertionsHelper.compareResponses(actual, excepted);
    }



    protected void login(UserDto userDto) {
        User user;
        if ((user = userService.findUserByEmail(userDto.getEmail())) == null) {
            user = userService.registerNewUserAccount(userDto);
        }
        userService.authWithoutPassword(user);
    }


}
