package com.digitalsanctuary.spring.user.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.digitalsanctuary.spring.user.test.annotations.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = "user.dev.auto-login-enabled=false")
@DisplayName("Dev Login Disabled Integration Tests")
class DevLoginDisabledTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("should NOT register DevLoginController when disabled")
    void shouldNotRegisterDevLoginControllerBean() {
        assertThat(applicationContext.getBeanNamesForType(DevLoginController.class)).isEmpty();
    }

    @Test
    @DisplayName("should NOT register DevLoginStartupWarning when disabled")
    void shouldNotRegisterDevLoginStartupWarningBean() {
        assertThat(applicationContext.getBeanNamesForType(DevLoginStartupWarning.class)).isEmpty();
    }

    @Test
    @DisplayName("should not allow access to dev login endpoint when disabled")
    void shouldNotAllowAccessToDevLoginEndpoint() throws Exception {
        // When disabled, the /dev/** paths are not added to the unprotected list,
        // so with defaultAction=deny they require authentication (302 redirect to login)
        mockMvc.perform(get("/dev/login-as/user@test.com")).andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("should not allow access to dev users endpoint when disabled")
    void shouldNotAllowAccessToDevUsersEndpoint() throws Exception {
        mockMvc.perform(get("/dev/users")).andExpect(status().is3xxRedirection());
    }
}
