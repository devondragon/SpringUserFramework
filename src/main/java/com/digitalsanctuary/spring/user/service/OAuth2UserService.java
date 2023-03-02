package com.digitalsanctuary.spring.user.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import com.digitalsanctuary.spring.user.exceptions.OAuth2AuthenticationProcessingException;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OAuth2UserService {

    /** The user repository. */
    @Autowired
    UserRepository userRepository;

    public User handleOAuthLoginSuccess(String registrationId, OAuth2User oAuth2User) {
        User user = getUserFromOAuth2User(oAuth2User);
        User existingUser = userRepository.findByEmail(user.getEmail());
        if (existingUser != null) {
            if (!existingUser.getProvider().toString().equals(registrationId)) {
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

    private User registerNewOAuthUser(String registrationId, User user) {

        User.Provider provider = User.Provider.valueOf(registrationId.toUpperCase());
        user.setProvider(provider);
        // user.setProvider(registrationId);
        // user.setRoles(Collections.singletonList(roleRepository.findByName(RoleName.ROLE_USER)));

        return userRepository.save(user);
    }

    private User updateExistingUser(User existingUser, User user) {
        existingUser.setFirstName(user.getFirstName());
        existingUser.setLastName(user.getLastName());
        return existingUser;
    }

    /**
     * Gets the user from OAuth2 user.
     *
     * @param principal
     * @return
     *
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
        // user.setProviderId(principal.getAttribute("sub"));
        // user.setProviderToken(principal.getAttribute("access_token"));
        return user;
    }

}
