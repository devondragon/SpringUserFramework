package com.digitalsanctuary.spring.user.profile.session;

import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import com.digitalsanctuary.spring.user.profile.BaseUserProfile;
import com.digitalsanctuary.spring.user.profile.UserProfileService;
import com.digitalsanctuary.spring.user.service.DSUserDetails;

/**
 * Base authentication listener that handles successful user authentication events by loading or creating the appropriate user profile and storing it
 * in the session. This class provides the core functionality for maintaining user profile state across the application session.
 *
 * <p>
 * This listener automatically responds to successful interactive authentication events (like form login) by retrieving or creating a user profile via
 * the {@link UserProfileService} and storing it in the session-scoped {@link BaseSessionProfile}.
 * </p>
 *
 * <p>
 * Example implementation:
 * </p>
 *
 * <pre>
* {@code
 * @Component
 * public class CustomAuthenticationListener extends BaseAuthenticationListener<CustomUserProfile> {
 *     public CustomAuthenticationListener(CustomSessionProfile sessionProfile, CustomUserProfileService profileService) {
 *         super(sessionProfile, profileService);
 *     }
 * }
 * }</pre>
 *
 * @param <T> the type of user profile, must extend BaseUserProfile
 *
 * @see BaseSessionProfile
 * @see UserProfileService
 * @see InteractiveAuthenticationSuccessEvent
 * @see DSUserDetails
 */
@Component
public abstract class BaseAuthenticationListener<T extends BaseUserProfile> implements ApplicationListener<InteractiveAuthenticationSuccessEvent> {

    /** The session profile manager for storing user profile data. */
    private final BaseSessionProfile<T> sessionProfile;

    /** The service for retrieving or creating user profiles. */
    private final UserProfileService<T> profileService;

    /**
     * Constructs a new BaseAuthenticationListener with the specified session profile and profile service.
     *
     * @param sessionProfile the session-scoped profile manager
     * @param profileService the service for managing user profiles
     * @throws IllegalArgumentException if either parameter is null
     */
    protected BaseAuthenticationListener(BaseSessionProfile<T> sessionProfile, UserProfileService<T> profileService) {
        if (sessionProfile == null || profileService == null) {
            throw new IllegalArgumentException("Session profile and profile service must not be null");
        }
        this.sessionProfile = sessionProfile;
        this.profileService = profileService;
    }

    /**
     * Handles successful authentication events by loading or creating the user's profile and storing it in the session.
     *
     * <p>
     * This method is automatically called by Spring's event system when a user successfully authenticates. It checks if the authentication principal
     * is a {@link DSUserDetails} instance, and if so, retrieves or creates the associated profile and stores it in the session.
     * </p>
     *
     * @param event the authentication success event
     * @throws IllegalStateException if the authentication details are invalid or missing
     */
    @Override
    public void onApplicationEvent(InteractiveAuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof DSUserDetails) {
            DSUserDetails userDetails = (DSUserDetails) event.getAuthentication().getPrincipal();
            T profile = profileService.getOrCreateProfile(userDetails.getUser());
            sessionProfile.setUserProfile(profile);
        }
    }
}
