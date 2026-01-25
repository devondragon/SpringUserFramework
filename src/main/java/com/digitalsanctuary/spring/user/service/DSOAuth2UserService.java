package com.digitalsanctuary.spring.user.service;

import java.util.Arrays;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.RoleRepository;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 user service implementation for handling OAuth2 authentication (Google, Facebook).
 *
 * <p>Handles the OAuth2 login flow by processing user information from OAuth2 providers,
 * registering new users or updating existing ones, and creating the authentication principal.</p>
 *
 * <p>Supported providers: Google, Facebook. For OIDC providers like Keycloak, see {@link DSOidcUserService}.</p>
 *
 * @see OAuth2UserService
 * @see DSOidcUserService
 * @see DSUserDetails
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DSOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    /** The user repository. */
    private final UserRepository userRepository;

    /** The role repository. */
    private final RoleRepository roleRepository;

    private final LoginHelperService loginHelperService;



    /** The Event Publisher. */
    private final ApplicationEventPublisher eventPublisher;

    /** The user role name. */
    private static final String USER_ROLE_NAME = "ROLE_USER";

    DefaultOAuth2UserService defaultOAuth2UserService = new DefaultOAuth2UserService();

    /**
     *
     * Handles a successful OAuth2 login. If the user is already registered, updates their account with any new information from the OAuth2 provider.
     * If the user is not already registered, creates a new user account with the information from the OAuth2 provider and saves it to the database.
     *
     * @param registrationId The registration ID for the OAuth2 provider.
     * @param oAuth2User The OAuth2User object containing information about the authenticated user.
     * @return A User object representing the authenticated user.
     */
    public User handleOAuthLoginSuccess(String registrationId, OAuth2User oAuth2User) {
        User user = null;
        if (registrationId.equalsIgnoreCase("google")) {
            user = getUserFromGoogleOAuth2User(oAuth2User);
        } else if (registrationId.equalsIgnoreCase("facebook")) {
            user = getUserFromFacebookOAuth2User(oAuth2User);
        } else {
            log.error("Sorry! Login with {} is not supported yet.", registrationId);
            throw new OAuth2AuthenticationException(new OAuth2Error("Login Exception"),
                    "Sorry! Login with " + registrationId + " is not supported yet.");
        }
        if (user == null) {
            log.error("handleOAuthLoginSuccess: user is null");
            throw new OAuth2AuthenticationException(new OAuth2Error("Login Exception"),
                    "Sorry! An error occurred while processing your login request.");
        }
        // Validate that we have an email address from the OAuth2 provider
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            log.error("handleOAuthLoginSuccess: OAuth2 provider did not provide an email address");
            throw new OAuth2AuthenticationException(new OAuth2Error("Missing Email"),
                    "Unable to retrieve email address from " + registrationId + ". Please ensure you have granted email permissions.");
        }
        log.debug("handleOAuthLoginSuccess: looking up user with email: {}", user.getEmail());
        User existingUser = userRepository.findByEmail(user.getEmail().toLowerCase());
        log.debug("handleOAuthLoginSuccess: existingUser: {}", existingUser);
        if (existingUser != null && registrationId != null) {
            log.debug("handleOAuthLoginSuccess: existingUser.getProvider(): {}", existingUser.getProvider());
            // If the user is already registered with a different auth provider (OAuth2 or Local), throw an exception.
            if (!existingUser.getProvider().toString().equals(registrationId.toUpperCase())) {
                log.debug("handleOAuthLoginSuccess: ERROR! existingUser.getProvider(): {}", existingUser.getProvider());
                throw new OAuth2AuthenticationException(new OAuth2Error("User Registered With Alternate Provider"),
                        "Looks like you're signed up with your " + existingUser.getProvider() + " account. Please use your "
                                + existingUser.getProvider() + " account to log in.");
            }
            existingUser = updateExistingUser(existingUser, user);
            return userRepository.save(existingUser);
        } else {
            log.debug("handleOAuthLoginSuccess: registering new user with email: {}", user.getEmail());
            user = registerNewOAuthUser(registrationId, user);
            return user;
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
    @Transactional
    private User registerNewOAuthUser(String registrationId, User user) {
        User.Provider provider = User.Provider.valueOf(registrationId.toUpperCase());
        user.setProvider(provider);
        user.setRoles(Arrays.asList(roleRepository.findByName(USER_ROLE_NAME)));
        // We will trust OAuth2 providers to provide us with a verified email address.
        user.setEnabled(true);
        AuditEvent registrationAuditEvent = AuditEvent.builder().source(this).user(user).action("OAuth2 Registration Success").actionStatus("Success")
                .message("Registration Confirmed. User logged in.").build();

        eventPublisher.publishEvent(registrationAuditEvent);
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
     * Retrieves user information from a Google OAuth2User object.
     *
     * @param principal The OAuth2User object containing information about the authenticated user.
     * @return A User object representing the authenticated user.
     */
    public User getUserFromGoogleOAuth2User(OAuth2User principal) {
        log.debug("Getting user info from Google OAuth2 provider with principal: {}", principal);
        if (principal == null) {
            return null;
        }
        log.debug("Principal attributes: {}", principal.getAttributes());
        User user = new User();
        String email = principal.getAttribute("email");
        user.setEmail(email != null ? email.toLowerCase() : null);
        user.setFirstName(principal.getAttribute("given_name"));
        user.setLastName(principal.getAttribute("family_name"));
        user.setProvider(User.Provider.GOOGLE);
        return user;
    }

    /**
     * Retrieves user information from a Facebook OAuth2User object.
     *
     * @param principal The OAuth2User object containing information about the authenticated user.
     * @return A User object representing the authenticated user.
     */
    public User getUserFromFacebookOAuth2User(OAuth2User principal) {
        log.debug("Getting user info from Facebook OAuth2 provider with principal: {}", principal);
        if (principal == null) {
            return null;
        }
        log.debug("Principal attributes: {}", principal.getAttributes());
        User user = new User();
        String email = principal.getAttribute("email");
        user.setEmail(email != null ? email.toLowerCase() : null);
        String fullName = principal.getAttribute("name");
        if (fullName != null) {
            String[] names = fullName.split(" ");
            if (names.length > 0) {
                user.setFirstName(names[0]);
            }
            if (names.length > 1) {
                user.setLastName(names[names.length - 1]);
            }
        }
        user.setProvider(User.Provider.FACEBOOK);
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
        log.debug("registrationId: {}", registrationId);
        User dbUser = handleOAuthLoginSuccess(registrationId, user);
        DSUserDetails userDetails = loginHelperService.userLoginHelper(dbUser);
        return userDetails;
    }

}
