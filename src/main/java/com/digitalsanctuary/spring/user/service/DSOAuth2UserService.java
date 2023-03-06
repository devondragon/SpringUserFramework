package com.digitalsanctuary.spring.user.service;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.exceptions.OAuth2AuthenticationProcessingException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * This class is an implementation of the OAuth2UserService interface that is used to handle OAuth2 logins for a Spring Security application. It
 * provides methods to handle successful OAuth2 logins, register new users with OAuth2 accounts, update existing users with new OAuth2 information,
 * and retrieve user information from an OAuth2User object. This service is used in conjunction with Spring Security's OAuth2LoginConfigurer to enable
 * OAuth2 login functionality for a web application. The OAuth2LoginConfigurer configures Spring Security to authenticate users with an OAuth2
 * provider, and uses this service to handle the authentication process and retrieve user information from the provider. This class is annotated with
 * the @Service annotation to indicate that it is a Spring service that should be automatically detected and instantiated by the Spring container.
 *
 * @see org.springframework.security.oauth2.client.registration.ClientRegistration
 * @see org.springframework.security.oauth2.client.userinfo.OAuth2UserService
 * @see org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
 * @see org.springframework.security.oauth2.core.user.OAuth2User
 * @see org.springframework.security.oauth2.core.user.DefaultOAuth2User
 * @see org.springframework.security.oauth2.core.user.OAuth2UserAuthority
 * @see org.springframework.security.core.userdetails.UserDetails
 * @see org.springframework.security.core.userdetails.User
 * @see com.digitalsanctuary.spring.user.persistence.model.User
 * @see com.digitalsanctuary.spring.user.persistence.repository.UserRepository
 * @see com.digitalsanctuary.spring.user.exceptions.OAuth2AuthenticationProcessingException
 */
@Slf4j
@Service
public class DSOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    /** The user repository. */
    UserRepository userRepository;

    DefaultOAuth2UserService defaultOAuth2UserService = new DefaultOAuth2UserService();

    public DSOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     *
     * Handles a successful OAuth2 login. If the user is already registered, updates their account with any new information from the OAuth2 provider.
     * If the user is not already registered, creates a new user account with the information from the OAuth2 provider and saves it to the database.
     *
     * @param registrationId The registration ID for the OAuth2 provider.
     * @param oAuth2User The OAuth2User object containing information about the authenticated user.
     * @return A User object representing the authenticated user.
     * @throws OAuth2AuthenticationProcessingException If the user is signed up with a different OAuth2 provider account than the one they are
     *         currently using to log in.
     */
    public User handleOAuthLoginSuccess(String registrationId, OAuth2User oAuth2User) {
        User user = getUserFromOAuth2User(oAuth2User);
        User existingUser = userRepository.findByEmail(user.getEmail());
        if (existingUser != null && registrationId != null) {
            if (!existingUser.getProvider().toString().equals(registrationId.toUpperCase())) {
                throw new OAuth2AuthenticationProcessingException("Looks like you're signed up with " + existingUser.getProvider()
                        + " account. Please use your " + existingUser.getProvider() + " account to login.");
            }
            existingUser = updateExistingUser(existingUser, user);
            return userRepository.save(existingUser);
        } else {
            user = registerNewOAuthUser(registrationId, user);
            return userRepository.save(user);
        }
    }

    /**
     *
     * Registers a new user with an OAuth2 account. Creates a new user account with the information from the OAuth2 provider and saves it to the
     * database.
     *
     * @param registrationId The registration ID for the OAuth2 provider.
     * @param user The User object representing the authenticated user.
     * @return A User object representing the newly registered user.
     */
    private User registerNewOAuthUser(String registrationId, User user) {
        User.Provider provider = User.Provider.valueOf(registrationId.toUpperCase());
        user.setProvider(provider);
        // user.setRoles(Collections.singletonList(roleRepository.findByName(RoleName.ROLE_USER)));

        return userRepository.save(user);
    }

    /**
     *
     * Updates an existing user's account with any new information from the OAuth2 provider.
     *
     * @param existingUser The existing User object representing the user to be updated.
     * @param user The User object representing the authenticated user.
     * @return The updated User object.
     */
    private User updateExistingUser(User existingUser, User user) {
        existingUser.setFirstName(user.getFirstName());
        existingUser.setLastName(user.getLastName());
        return existingUser;
    }

    /**
     *
     * Retrieves user information from an OAuth2User object.
     *
     * @param principal The OAuth2User object containing information about the authenticated user.
     * @return A User object representing the authenticated user.
     */
    public User getUserFromOAuth2User(OAuth2User principal) {
        log.debug("Getting user info from OAuth2 provider with principal: {}", principal);
        if (principal == null) {
            return null;
        }
        log.debug("Principal attributes: {}", principal.getAttributes());
        User user = new User();
        user.setEmail(principal.getAttribute("email"));
        user.setFirstName(principal.getAttribute("given_name"));
        user.setLastName(principal.getAttribute("family_name"));
        user.setProvider(principal.getAttribute("iss"));
        return user;
    }

    /**
     *
     * Loads user information from an OAuth2 provider and creates a UserDetails object representing the authenticated user.
     *
     * @param userRequest The OAuth2UserRequest object containing information about the user request.
     * @return A UserDetails object representing the authenticated user.
     * @throws OAuth2AuthenticationException If there is an error authenticating the user with the OAuth2 provider.
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.debug("Loading user from OAuth2 provider with userRequest: {}", userRequest);
        OAuth2User user = defaultOAuth2UserService.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.debug("registrationId: " + registrationId);
        User dbUser = handleOAuthLoginSuccess(registrationId, user);
        DSUserDetails dsUserDetails = new DSUserDetails(dbUser);
        return dsUserDetails;
    }

}
