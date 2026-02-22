package com.digitalsanctuary.spring.user.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.test.annotations.ServiceTest;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;
import com.digitalsanctuary.spring.user.util.JSONResponse;
import jakarta.servlet.http.HttpServletResponse;

@ServiceTest
class DevLoginControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DevLoginConfigProperties devLoginConfigProperties;

    @InjectMocks
    private DevLoginController devLoginController;

    private User enabledUser;
    private User disabledUser;

    @BeforeEach
    void setUp() {
        enabledUser = UserTestDataBuilder.aUser().withEmail("dev@test.com").verified().build();
        disabledUser = UserTestDataBuilder.aUser().withEmail("disabled@test.com").disabled().build();
    }

    @Test
    @DisplayName("loginAs - should authenticate and redirect for valid enabled user")
    void shouldAuthenticateAndRedirectWhenValidUser() throws Exception {
        // Given
        when(userService.findUserByEmail("dev@test.com")).thenReturn(enabledUser);
        when(devLoginConfigProperties.getLoginRedirectUrl()).thenReturn("/dashboard");
        HttpServletResponse response = mock(HttpServletResponse.class);

        // When
        ResponseEntity<JSONResponse> result = devLoginController.loginAs("dev@test.com", response);

        // Then
        verify(userService).authWithoutPassword(enabledUser);
        verify(response).sendRedirect("/dashboard");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("loginAs - should return 404 for unknown user")
    void shouldReturn404WhenUserNotFound() throws Exception {
        // Given
        when(userService.findUserByEmail("unknown@test.com")).thenReturn(null);
        HttpServletResponse response = mock(HttpServletResponse.class);

        // When
        ResponseEntity<JSONResponse> result = devLoginController.loginAs("unknown@test.com", response);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        verify(userService, never()).authWithoutPassword(any());
    }

    @Test
    @DisplayName("loginAs - should return 403 for disabled user")
    void shouldReturn403WhenUserDisabled() throws Exception {
        // Given
        when(userService.findUserByEmail("disabled@test.com")).thenReturn(disabledUser);
        HttpServletResponse response = mock(HttpServletResponse.class);

        // When
        ResponseEntity<JSONResponse> result = devLoginController.loginAs("disabled@test.com", response);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        verify(userService, never()).authWithoutPassword(any());
    }

    @Test
    @DisplayName("listUsers - should return enabled user emails")
    void shouldReturnEnabledUserEmails() {
        // Given
        List<User> allUsers = Arrays.asList(enabledUser, disabledUser);
        when(userRepository.findAll()).thenReturn(allUsers);

        // When
        ResponseEntity<JSONResponse> result = devLoginController.listUsers();

        // Then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> emails = (List<String>) result.getBody().getData();
        assertThat(emails).containsExactly("dev@test.com");
    }
}
