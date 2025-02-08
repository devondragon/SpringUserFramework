package com.digitalsanctuary.spring.user.service;

import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 *
 * This class is an implementation of the OAuth2UserService interface that is used to handle Oidc logins for a Spring Security application. It
 * provides methods to handle successful Oidc logins, register new users with Oidc accounts, update existing users with new Oidc information,
 * and retrieve user information from an OidcUser object. This service is used in conjunction with Spring Security's OAuth2LoginConfigurer to enable
 * Oidc login functionality for a web application. The OAuth2LoginConfigurer configures Spring Security to authenticate users with an Oidc
 * provider, and uses this service to handle the authentication process and retrieve user information from the provider. This class is annotated with
 * the @Service annotation to indicate that it is a Spring service that should be automatically detected and instantiated by the Spring container.
 *
 * @see org.springframework.security.oauth2.client.registration.ClientRegistration
 * @see OAuth2UserService
 * @see OAuth2UserRequest
 * @see OAuth2User
 * @see org.springframework.security.oauth2.core.user.DefaultOAuth2User
 * @see org.springframework.security.oauth2.core.user.OAuth2UserAuthority
 * @see org.springframework.security.core.userdetails.UserDetails
 * @see org.springframework.security.core.userdetails.User
 * @see User
 * @see UserRepository
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DSOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    /** The user repository. */
    private final UserRepository userRepository;

    OidcUserService defaultOidcUserService = new OidcUserService();

    /**
     *
     * Handles a successful Oidc login. If the user is already registered, updates their account with any new information from the Oidc provider.
     * If the user is not already registered, creates a new user account with the information from the Oidc provider and saves it to the database.
     *
     * @param registrationId The registration ID for the Oidc provider.
     * @param oidcUser The OidcUser object containing information about the authenticated user.
     * @return A User object representing the authenticated user.
     */
    public User handleOidcLoginSuccess(String registrationId, OidcUser oidcUser) {
        User user = null;
        if (registrationId.equalsIgnoreCase("keycloak")) {
            user = getUserFromKeycloakOidc2User(oidcUser);
        } else {
            log.error("Sorry! Login with " + registrationId + " is not supported yet.");
            throw new OAuth2AuthenticationException(new OAuth2Error("Login Exception"),
                    "Sorry! Login with " + registrationId + " is not supported yet.");
        }
        if (user == null) {
            log.error("handleOidcLoginSuccess: user is null");
            throw new OAuth2AuthenticationException(new OAuth2Error("Login Exception"),
                    "Sorry! An error occurred while processing your login request.");
        }
        log.debug("handleOidcLoginSuccess: looking up user with email: {}", user.getEmail());
        User existingUser = userRepository.findByEmail(user.getEmail());
        log.debug("handleOidcLoginSuccess: existingUser: {}", existingUser);
        if (existingUser != null && registrationId != null) {
            log.debug("handleOidcLoginSuccess: existingUser.getProvider(): {}", existingUser.getProvider());
            // If the user is already registered with a different auth provider (OAuth2 or Local), throw an exception.
            if (!existingUser.getProvider().toString().equals(registrationId.toUpperCase())) {
                log.debug("handleOidcLoginSuccess: ERROR! existingUser.getProvider(): {}", existingUser.getProvider());
                throw new OAuth2AuthenticationException(new OAuth2Error("User Registered With Alternate Provider"),
                        "Looks like you're signed up with your " + existingUser.getProvider() + " account. Please use your "
                                + existingUser.getProvider() + " account to log in.");
            }
            existingUser = updateExistingUser(existingUser, user);
            return userRepository.save(existingUser);
        } else {
            log.debug("handleOidcLoginSuccess: registering new user with email: {}", user.getEmail());
            user = registerNewOidcUser(registrationId, user);
            return user;
        }
    }

    /**
     *
     * Registers a new user with an Oidc account. Creates a new user account with the information from the Oidc provider and saves it to the
     * database.
     *
     * @param registrationId The registration ID for the Oidc provider.
     * @param user The User object representing the authenticated user.
     * @return A User object representing the newly registered user.
     */
    private User registerNewOidcUser(String registrationId, User user) {
        User.Provider provider = User.Provider.valueOf(registrationId.toUpperCase());
        user.setProvider(provider);
        // user.setRoles(Collections.singletonList(roleRepository.findByName(RoleName.ROLE_USER)));
        // We will trust OAuth2 providers to provide us with a verified email address.
        user.setEnabled(true);
        return userRepository.save(user);
    }

    /**
     *
     * Updates an existing user's account with any new information from the Oidc provider.
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
     * Retrieves user information from a Keycloak OidcUser object.
     *
     * @param principal The OidcUser object containing information about the authenticated user.
     * @return A User object representing the authenticated user.
     */
    public User getUserFromKeycloakOidc2User(OidcUser principal) {
        log.debug("Getting user info from Keycloak Oidc provider with principal: {}", principal);
        if (principal == null) {
            return null;
        }
        log.debug("Principal attributes: {}", principal.getAttributes());
        User user = new User();
/*        user.setEmail(principal.getAttribute("email"));
        user.setFirstName(principal.getAttribute("given_name"));
        user.setLastName(principal.getAttribute("family_name"));*/
        user.setEmail(principal.getEmail());
        user.setFirstName(principal.getGivenName());
        user.setLastName(principal.getFamilyName());
        user.setProvider(User.Provider.KEYCLOAK);
        return user;
    }


    /**
     *
     * Loads user information from an Oidc provider and creates a UserDetails object representing the authenticated user.
     *
     * @param userRequest The OidcUserRequest object containing information about the user request.
     * @return A UserDetails object representing the authenticated user.
     * @throws OAuth2AuthenticationException If there is an error authenticating the user with the OAuth2 provider.
     */
    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        log.debug("Loading user from OAuth2 provider with userRequest: {}", userRequest);
        OidcUser user = defaultOidcUserService.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.debug("registrationId: " + registrationId);
        User dbUser = handleOidcLoginSuccess(registrationId, user);
        DSUserDetails dsUserDetails = DSUserDetails.builder()
                .user(dbUser)
                .oidcUserInfo(user.getUserInfo())
                .oidcIdToken(user.getIdToken())
                .grantedAuthorities(user.getAuthorities())
                .build();
        return dsUserDetails;
    }
}
