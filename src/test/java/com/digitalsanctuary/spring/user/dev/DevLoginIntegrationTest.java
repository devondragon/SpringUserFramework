package com.digitalsanctuary.spring.user.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.test.annotations.IntegrationTest;

@IntegrationTest
@ActiveProfiles({"test", "local"})
@TestPropertySource(properties = "user.dev.auto-login-enabled=true")
@DisplayName("Dev Login Enabled Integration Tests")
class DevLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        User user = new User();
        user.setEmail("dev-test@test.com");
        user.setFirstName("Dev");
        user.setLastName("User");
        user.setEnabled(true);
        user.setPassword("encoded-password");
        userRepository.saveAndFlush(user);

        User disabledUser = new User();
        disabledUser.setEmail("disabled-dev@test.com");
        disabledUser.setFirstName("Disabled");
        disabledUser.setLastName("User");
        disabledUser.setEnabled(false);
        disabledUser.setPassword("encoded-password");
        userRepository.saveAndFlush(disabledUser);
    }

    @Test
    @DisplayName("should register DevLoginController bean when enabled with local profile")
    void shouldRegisterDevLoginControllerBean() {
        assertThat(applicationContext.getBeanNamesForType(DevLoginController.class)).isNotEmpty();
        assertThat(applicationContext.getBeanNamesForType(DevLoginStartupWarning.class)).isNotEmpty();
    }

    @Test
    @DisplayName("should expose dev login endpoint mappings")
    void shouldExposeDevLoginEndpointMappings() {
        Set<String> mappedPaths = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream()).collect(Collectors.toSet());
        assertThat(mappedPaths).contains("/dev/login-as/{email}");
        assertThat(mappedPaths).contains("/dev/users");
    }

    @Test
    @DisplayName("should allow unauthenticated access to dev login endpoint")
    void shouldAllowUnauthenticatedAccessToDevLogin() throws Exception {
        mockMvc.perform(get("/dev/login-as/dev-test@test.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("should return 404 for unknown user via dev login")
    void shouldReturn404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/dev/login-as/nobody@test.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("should return 403 for disabled user via dev login")
    void shouldReturn403ForDisabledUser() throws Exception {
        mockMvc.perform(get("/dev/login-as/disabled-dev@test.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("should list enabled users via dev users endpoint")
    void shouldListEnabledUsers() throws Exception {
        mockMvc.perform(get("/dev/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
