package com.digitalsanctuary.spring.user.profile;

import com.digitalsanctuary.spring.user.persistence.model.User;

/**
 * Service interface for managing user profiles. This interface defines the core operations for retrieving, creating, and updating user profiles that
 * extend the base profile functionality.
 *
 * <p>
 * Implementations of this interface handle the persistence and business logic for user profiles, providing a standardized way to manage extended user
 * data beyond the core {@link User} entity.
 * </p>
 *
 * Example implementation: {@code @Service public class CustomUserProfileService implements UserProfileService<CustomUserProfile> { private final
 * CustomUserProfileRepository profileRepository;
 *
 * @Override public CustomUserProfile getOrCreateProfile(User user) { return profileRepository.findByUserId(user.getId()) .orElseGet(() -> {
 * CustomUserProfile profile = new CustomUserProfile(); profile.setUser(user); return profileRepository.save(profile); }); }
 *
 * @Override public CustomUserProfile updateProfile(CustomUserProfile profile) { return profileRepository.save(profile); } } }
 *
 * @param <T> the type of user profile to manage, must extend BaseUserProfile
 * @see BaseUserProfile
 * @see User
 */
public interface UserProfileService<T extends BaseUserProfile> {

    /**
     * Retrieves an existing profile for the given user or creates a new one if none exists. This method ensures that every user has an associated
     * profile.
     *
     * <p>
     * Implementations should:
     * </p>
     * <ul>
     * <li>Check if a profile exists for the user</li>
     * <li>Create a new profile if none exists</li>
     * <li>Initialize any required default values for new profiles</li>
     * <li>Persist the profile if newly created</li>
     * </ul>
     *
     * @param user the user to get or create a profile for
     * @return the existing or newly created profile
     * @throws IllegalArgumentException if user is null
     * @throws RuntimeException if profile creation or retrieval fails
     */
    T getOrCreateProfile(User user);

    /**
     * Updates an existing user profile with new information.
     *
     * <p>
     * Implementations should:
     * </p>
     * <ul>
     * <li>Validate the profile data before updating</li>
     * <li>Persist the changes to the data store</li>
     * <li>Return the updated profile instance</li>
     * </ul>
     *
     * @param profile the profile to update
     * @return the updated profile
     * @throws IllegalArgumentException if profile is null or invalid
     * @throws RuntimeException if profile update fails
     */
    T updateProfile(T profile);
}
