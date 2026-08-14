package com.digitalsanctuary.spring.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import com.digitalsanctuary.spring.user.security.UserSecurityConfigProperties;

@DisplayName("UserSecurityUriControllerAdvice")
class UserSecurityUriControllerAdviceTest {

    @Controller
    static class TestPageController {
        @GetMapping("/user-security-advice-test-page")
        public String page() {
            return "test";
        }
    }

    @Test
    void shouldExposeUserSecurityViewWithUrisAndCopyrightYear() throws Exception {
        UserSecurityConfigProperties props = new UserSecurityConfigProperties();
        UserSecurityUriControllerAdvice advice = new UserSecurityUriControllerAdvice(props, "2020");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestPageController())
                .setControllerAdvice(advice).build();

        mockMvc.perform(get("/user-security-advice-test-page")).andExpect(status().isOk())
                .andExpect(model().attributeExists("userSecurity"));

        UserSecurityUriView view = advice.userSecurity();
        assertThat(view.loginPageUri()).isEqualTo("/user/login.html");
        assertThat(view.copyrightFirstYear()).isEqualTo("2020");
    }

    @Test
    void viewMustNotExposeTheTokenHashSecret() {
        // The view is a fixed record of URIs + copyright; it has no accessor for secrets.
        for (var component : UserSecurityUriView.class.getRecordComponents()) {
            assertThat(component.getName()).doesNotContainIgnoringCase("secret");
        }
    }
}
