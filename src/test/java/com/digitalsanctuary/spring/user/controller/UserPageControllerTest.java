package com.digitalsanctuary.spring.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import com.digitalsanctuary.spring.user.dto.UserDto;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.test.builders.UserTestDataBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import jakarta.servlet.http.HttpSession;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPageController Tests")
class UserPageControllerTest {
    
    private MockMvc mockMvc;
    
    @InjectMocks
    private UserPageController userPageController;
    
    private User testUser;
    private DSUserDetails testUserDetails;
    
    @BeforeEach
    void setUp() {
        // Set field values using reflection
        ReflectionTestUtils.setField(userPageController, "facebookEnabled", true);
        ReflectionTestUtils.setField(userPageController, "googleEnabled", true);
        ReflectionTestUtils.setField(userPageController, "keycloakEnabled", false);
        
        // Build MockMvc with custom argument resolver for authentication principal
        mockMvc = MockMvcBuilders.standaloneSetup(userPageController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                        return parameter.getParameterType().equals(DSUserDetails.class);
                    }
                    
                    @Override
                    public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                            org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                            org.springframework.web.context.request.NativeWebRequest webRequest,
                            org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                        return testUserDetails;
                    }
                })
                .build();
        
        testUser = UserTestDataBuilder.aUser()
                .withId(1L)
                .withEmail("test@example.com")
                .withFirstName("Test")
                .withLastName("User")
                .enabled()
                .build();
        
        testUserDetails = new DSUserDetails(testUser);
    }
    
    @Nested
    @DisplayName("Login Page Tests")
    class LoginPageTests {
        
        @Test
        @DisplayName("Should display login page with OAuth providers")
        void login_returnsLoginViewWithProviders() throws Exception {
            mockMvc.perform(get("/user/login.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/login"))
                    .andExpect(model().attribute("googleEnabled", true))
                    .andExpect(model().attribute("facebookEnabled", true))
                    .andExpect(model().attribute("keycloakEnabled", false));
        }
        
        @Test
        @DisplayName("Should display login page with error message from session")
        void login_withSessionError_displaysErrorMessage() throws Exception {
            MvcResult result = mockMvc.perform(get("/user/login.html")
                    .sessionAttr("error.message", "Invalid credentials"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/login"))
                    .andExpect(model().attribute("errormessage", "Invalid credentials"))
                    .andReturn();
            
            // Verify session attribute was removed
            HttpSession session = result.getRequest().getSession();
            assertThat(session.getAttribute("error.message")).isNull();
        }
        
        @Test
        @DisplayName("Should handle authenticated user accessing login page")
        void login_authenticatedUser_returnsLoginView() throws Exception {
            mockMvc.perform(get("/user/login.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/login"));
        }
    }
    
    @Nested
    @DisplayName("Registration Page Tests")
    class RegistrationPageTests {
        
        @Test
        @DisplayName("Should display registration page with OAuth providers")
        void register_returnsRegisterViewWithProviders() throws Exception {
            mockMvc.perform(get("/user/register.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/register"))
                    .andExpect(model().attribute("googleEnabled", true))
                    .andExpect(model().attribute("facebookEnabled", true))
                    .andExpect(model().attribute("keycloakEnabled", false));
        }
        
        @Test
        @DisplayName("Should display registration page with error message from session")
        void register_withSessionError_displaysErrorMessage() throws Exception {
            MvcResult result = mockMvc.perform(get("/user/register.html")
                    .sessionAttr("error.message", "Email already exists"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/register"))
                    .andExpect(model().attribute("errormessage", "Email already exists"))
                    .andReturn();
            
            // Verify session attribute was removed
            HttpSession session = result.getRequest().getSession();
            assertThat(session.getAttribute("error.message")).isNull();
        }
    }
    
    @Nested
    @DisplayName("Static Page Tests")
    class StaticPageTests {
        
        @Test
        @DisplayName("Should return registration pending view")
        void registrationPending_returnsCorrectView() throws Exception {
            mockMvc.perform(get("/user/registration-pending-verification.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/registration-pending-verification"));
        }
        
        @Test
        @DisplayName("Should return registration complete view")
        void registrationComplete_returnsCorrectView() throws Exception {
            mockMvc.perform(get("/user/registration-complete.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/registration-complete"));
        }
        
        @Test
        @DisplayName("Should return request new verification email view")
        void requestNewVerificationEmail_returnsCorrectView() throws Exception {
            mockMvc.perform(get("/user/request-new-verification-email.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/request-new-verification-email"));
        }
        
        @Test
        @DisplayName("Should return forgot password view")
        void forgotPassword_returnsCorrectView() throws Exception {
            mockMvc.perform(get("/user/forgot-password.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/forgot-password"));
        }
        
        @Test
        @DisplayName("Should return forgot password pending view")
        void forgotPasswordPending_returnsCorrectView() throws Exception {
            mockMvc.perform(get("/user/forgot-password-pending-verification.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/forgot-password-pending-verification"));
        }
        
        @Test
        @DisplayName("Should return forgot password change view")
        void forgotPasswordChange_returnsCorrectView() throws Exception {
            mockMvc.perform(get("/user/forgot-password-change.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/forgot-password-change"));
        }
        
        @Test
        @DisplayName("Should return update password view")
        void updatePassword_returnsCorrectView() throws Exception {
            mockMvc.perform(get("/user/update-password.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/update-password"));
        }
        
        @Test
        @DisplayName("Should return delete account view")
        void deleteAccount_returnsCorrectView() throws Exception {
            mockMvc.perform(get("/user/delete-account.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/delete-account"));
        }
    }
    
    @Nested
    @DisplayName("Update User Page Tests")
    class UpdateUserPageTests {
        
        @Test
        @DisplayName("Should display update user page with user data")
        void updateUser_authenticatedUser_returnsViewWithUserData() throws Exception {
            mockMvc.perform(get("/user/update-user.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/update-user"))
                    .andExpect(model().attributeExists("user"));
            
            // Verify user data in model
            MvcResult result = mockMvc.perform(get("/user/update-user.html"))
                    .andReturn();
            
            UserDto userDto = (UserDto) result.getModelAndView().getModel().get("user");
            assertThat(userDto.getFirstName()).isEqualTo("Test");
            assertThat(userDto.getLastName()).isEqualTo("User");
        }
        
        @Test
        @DisplayName("Should handle unauthenticated user")
        void updateUser_unauthenticatedUser_returnsViewWithoutUserData() throws Exception {
            // Setup MockMvc with argument resolver that returns null for unauthenticated user
            MockMvc unauthenticatedMockMvc = MockMvcBuilders.standaloneSetup(userPageController)
                    .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                        @Override
                        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                            return parameter.getParameterType().equals(DSUserDetails.class);
                        }
                        
                        @Override
                        public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                org.springframework.web.context.request.NativeWebRequest webRequest,
                                org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                            return null; // Return null for unauthenticated user
                        }
                    })
                    .build();
            
            unauthenticatedMockMvc.perform(get("/user/update-user.html"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("user/update-user"))
                    .andExpect(model().attributeDoesNotExist("user"));
        }
    }
    
    @Nested
    @DisplayName("OAuth Provider Configuration Tests")
    class OAuthProviderTests {
        
        @Test
        @DisplayName("Should show only enabled OAuth providers")
        void oauthProviders_onlyEnabledProvidersShown() throws Exception {
            // Update configuration
            ReflectionTestUtils.setField(userPageController, "facebookEnabled", false);
            ReflectionTestUtils.setField(userPageController, "googleEnabled", true);
            ReflectionTestUtils.setField(userPageController, "keycloakEnabled", true);
            
            mockMvc.perform(get("/user/login.html"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("googleEnabled", true))
                    .andExpect(model().attribute("facebookEnabled", false))
                    .andExpect(model().attribute("keycloakEnabled", true));
        }
        
        @Test
        @DisplayName("Should handle all OAuth providers disabled")
        void oauthProviders_allDisabled_returnsViewWithAllDisabled() throws Exception {
            // Update configuration
            ReflectionTestUtils.setField(userPageController, "facebookEnabled", false);
            ReflectionTestUtils.setField(userPageController, "googleEnabled", false);
            ReflectionTestUtils.setField(userPageController, "keycloakEnabled", false);
            
            mockMvc.perform(get("/user/register.html"))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("googleEnabled", false))
                    .andExpect(model().attribute("facebookEnabled", false))
                    .andExpect(model().attribute("keycloakEnabled", false));
        }
    }
}