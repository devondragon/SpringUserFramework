package com.digitalsanctuary.spring.user.api;

import static com.digitalsanctuary.spring.user.api.helper.ApiTestHelper.buildUrlEncodedFormEntity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

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
import com.digitalsanctuary.spring.user.dto.PasswordDto;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.jdbc.Jdbc;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.test.annotations.IntegrationTest;
import com.digitalsanctuary.spring.user.test.fixtures.TestFixtures;
import com.digitalsanctuary.spring.user.api.provider.ApiTestUpdatePasswordArgumentsProvider;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Disabled;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

@Disabled("Temporarily disabled - requires specific database setup that conflicts with current test infrastructure")
@IntegrationTest
public class UserApiTest {
    private static final String URL = "/user";

    @Autowired
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    private static final UserDto baseTestUser = ApiTestData.BASE_TEST_USER;

    @AfterAll
    public static void afterAll() {
        Jdbc.deleteTestUser(baseTestUser);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder);
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

    /**
     * Tests the update password functionality with valid and invalid password combinations.
     *
     * @param argumentsHolder Contains test data for password updates (valid/invalid scenarios)  
     * @throws Exception if any error occurs during test execution
     */
    @ParameterizedTest
    @ArgumentsSource(ApiTestUpdatePasswordArgumentsProvider.class)
    @Order(3)
    public void updatePassword(ApiTestArgumentsHolder argumentsHolder) throws Exception {
        // Register and login test user first
        login(baseTestUser);

        PasswordDto passwordDto = argumentsHolder.getPasswordDto();

        ResultActions action = perform(MockMvcRequestBuilders.post(URL + "/updatePassword")
                .with(oauth2Login().oauth2User(createTestOAuth2User()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(buildUrlEncodedFormEntity(passwordDto)));

        if (argumentsHolder.getStatus() == DataStatus.VALID) {
            action.andExpect(status().isOk());
        }
        if (argumentsHolder.getStatus() == DataStatus.INVALID) {
            action.andExpect(status().isBadRequest());
        }

        MockHttpServletResponse actual = action.andReturn().getResponse();
        Response expected = argumentsHolder.getResponse();
        AssertionsHelper.compareResponses(actual, expected);
    }


    protected void login(UserDto userDto) {
        User user;
        if ((user = userService.findUserByEmail(userDto.getEmail())) == null) {
            user = userService.registerNewUserAccount(userDto);
        }
        userService.authWithoutPassword(user);
    }

    /**
     * Creates a test OAuth2 user for authentication in tests.
     */
    private OAuth2User createTestOAuth2User() {
        return TestFixtures.OAuth2.customUser(baseTestUser.getEmail(), "Test User", "test-user-123");
    }


}
