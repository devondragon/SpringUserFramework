package com.digitalsanctuary.spring.user.api;

import com.digitalsanctuary.spring.user.api.data.ApiTestData;
import com.digitalsanctuary.spring.user.api.data.DataStatus;
import com.digitalsanctuary.spring.user.api.data.Response;
import com.digitalsanctuary.spring.user.api.helper.AssertionsHelper;
import com.digitalsanctuary.spring.user.api.provider.ApiTestLockAccountArgumentsProvider;
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
@Disabled("Temporarily disabled due to OAuth2 dependency issues")
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
    @ArgumentsSource(ApiTestLockAccountArgumentsProvider.class)
    public void toggleLockStatusOfUser(ApiTestArgumentsHolder argumentsHolder) throws Exception {
        ResultActions action = perform(MockMvcRequestBuilders.post(URL + "/lock").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content(String.valueOf(argumentsHolder.getLockAccountDto())));

        if (argumentsHolder.getStatus() == DataStatus.VALID) {
            action.andExpect(status().isOk());
        }
        if (argumentsHolder.getStatus() == DataStatus.NOT_FOUND) {
            action.andExpect(status().isNotFound());
        }
        if (argumentsHolder.getStatus() == DataStatus.INVALID) {
            action.andExpect(status().isBadRequest());
        }

        MockHttpServletResponse actual = action.andReturn().getResponse();
        Response excepted = argumentsHolder.getResponse();
        AssertionsHelper.compareResponses(actual, excepted);
    }
}
