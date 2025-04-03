package com.digitalsanctuary.spring.user.api;

import com.digitalsanctuary.spring.user.api.data.ApiTestData;
import com.digitalsanctuary.spring.user.api.data.DataStatus;
import com.digitalsanctuary.spring.user.api.data.Response;
import com.digitalsanctuary.spring.user.api.helper.AssertionsHelper;
import com.digitalsanctuary.spring.user.api.provider.ApiTestAccountLockingArgumentsProvider;
import com.digitalsanctuary.spring.user.api.provider.holder.ApiTestArgumentsHolder;
import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.jdbc.Jdbc;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
// Disabling the test cases for now because of java.lang.NoClassDefFoundError: org/springframework/security/oauth2/core/user/OAuth2User
//@Disabled("Temporarily disabled due to OAuth2 dependency issues")
public class AdminApiTest extends BaseApiTest {
    private static final String URL = "/admin";
    private static final UserDto baseAdminUser = ApiTestData.BASE_ADMIN_USER;
    private static final UserDto baseTestUser = ApiTestData.BASE_TEST_USER;

    @AfterAll
    public static void afterAll() {
        Jdbc.deleteTestUser(baseAdminUser);
        Jdbc.deleteTestUser(baseTestUser);
    }

    /**
     *
     * @param argumentsHolder
     * @throws Exception testing with 3 params: existing email, non-existing email, no email
     */
    @ParameterizedTest
    @ArgumentsSource(ApiTestAccountLockingArgumentsProvider.class)
    public void toggleLockStatusOfUser(ApiTestArgumentsHolder argumentsHolder) throws Exception {
        ResultActions action = perform(MockMvcRequestBuilders.post(URL + "/lockAccount").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(String.valueOf(argumentsHolder.getLockAccountDto())));

        verifyResponse(action, argumentsHolder);
    }

    /**
     * Test unlocking a user account with different conditions (valid, not found, invalid).
     *
     * @param argumentsHolder Test data including valid, not found, and invalid cases.
     * @throws Exception when the test fails
     */
    @ParameterizedTest
    @ArgumentsSource(ApiTestAccountLockingArgumentsProvider.class)
    public void unlockUserAccount(ApiTestArgumentsHolder argumentsHolder) throws Exception {
        ResultActions action = perform(MockMvcRequestBuilders.post(URL + "/unlockAccount")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.valueOf(argumentsHolder.getLockAccountDto())));

        verifyResponse(action, argumentsHolder);
    }

    /**
     * Helper method to verify API responses based on test data.
     */
    private void verifyResponse(ResultActions action, ApiTestArgumentsHolder argumentsHolder) throws Exception {
        if (argumentsHolder.getStatus() == DataStatus.VALID) {
            action.andExpect(status().isOk());
        } else if (argumentsHolder.getStatus() == DataStatus.NOT_FOUND) {
            action.andExpect(status().isNotFound());
        } else if (argumentsHolder.getStatus() == DataStatus.INVALID) {
            action.andExpect(status().isBadRequest());
        }

        MockHttpServletResponse actual = action.andReturn().getResponse();
        Response expected = argumentsHolder.getResponse();
        AssertionsHelper.compareResponses(actual, expected);
    }
}
