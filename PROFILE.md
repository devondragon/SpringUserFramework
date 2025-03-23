# User Profile Extension Framework

This guide explains how to leverage and extend the user profile system in Spring User Framework to create rich, application-specific user data models.

## Table of Contents
- [User Profile Extension Framework](#user-profile-extension-framework)
  - [Table of Contents](#table-of-contents)
  - [Overview](#overview)
  - [When to Use Profile Extensions](#when-to-use-profile-extensions)
  - [Core Components](#core-components)
  - [Implementation Guide](#implementation-guide)
    - [Step 1: Create Your Custom User Profile](#step-1-create-your-custom-user-profile)
    - [Step 2: Create a Profile Repository](#step-2-create-a-profile-repository)
    - [Step 3: Implement a Profile Service](#step-3-implement-a-profile-service)
    - [Step 4: Create a Session Profile Manager](#step-4-create-a-session-profile-manager)
    - [Step 5: Implement an Authentication Listener](#step-5-implement-an-authentication-listener)
  - [Usage Examples](#usage-examples)
    - [Accessing Profile Data in Controllers](#accessing-profile-data-in-controllers)
    - [Using Profiles in Views](#using-profiles-in-views)
    - [Profile-Based Authorization](#profile-based-authorization)
  - [Advanced Customizations](#advanced-customizations)
    - [Custom Profile Initialization](#custom-profile-initialization)
    - [Additional Event Handling](#additional-event-handling)
    - [Profile Migration Strategies](#profile-migration-strategies)
  - [Troubleshooting](#troubleshooting)

## Overview

The Spring User Framework provides an extensible user profile system that allows you to:

1. **Store application-specific user data** beyond the core authentication details
2. **Access profile information throughout the application** via session-scoped components
3. **Automatically load profiles during authentication** with minimal configuration
4. **Keep user-related data organized** in a type-safe, structured manner

This system is built on Spring's dependency injection, JPA persistence, and session management capabilities, making it seamlessly integrated with your Spring Boot application.

## When to Use Profile Extensions

Consider extending the profile system when you need to:

- Store user preferences, settings, or application-specific data
- Track user activity or state across sessions
- Associate domain-specific entities with users (e.g., subscriptions, permissions)
- Implement features requiring additional user properties beyond authentication

If your application only needs basic authentication without user-specific data, you may not need to implement these extensions.

## Core Components

The profile extension framework consists of these key components:

1. **`BaseUserProfile`**: The JPA entity base class that links to the core `User` entity
2. **`UserProfileService<T>`**: Interface for retrieving and managing profile objects
3. **`BaseSessionProfile<T>`**: Session-scoped container that holds the current user's profile
4. **`BaseAuthenticationListener<T>`**: Loads the profile on successful authentication

All components use generics to ensure type safety throughout your application.

## Implementation Guide

### Step 1: Create Your Custom User Profile

Create a JPA entity that extends `BaseUserProfile`:

```java
@Entity
@Table(name = "app_user_profile")
@Data
@EqualsAndHashCode(callSuper = true)
public class AppUserProfile extends BaseUserProfile {
    // Add your application-specific fields

    private String displayName;

    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    private boolean notificationsEnabled;

    @OneToMany(mappedBy = "userProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserPreference> preferences = new ArrayList<>();

    // Domain-specific methods
    public void addPreference(UserPreference preference) {
        preferences.add(preference);
        preference.setUserProfile(this);
    }

    public boolean hasPreference(String key) {
        return preferences.stream()
            .anyMatch(p -> p.getKey().equals(key));
    }
}
```

The `BaseUserProfile` class already provides:
- An ID field that maps to the User ID
- A one-to-one relationship with the User entity
- Common fields like lastAccessed and locale

### Step 2: Create a Profile Repository

Create a repository interface for your profile entity:

```java
public interface AppUserProfileRepository extends JpaRepository<AppUserProfile, Long> {
    Optional<AppUserProfile> findByUserId(Long userId);
}
```

### Step 3: Implement a Profile Service

Implement the `UserProfileService` interface to manage your profile entity:

```java
@Service
@Transactional
@RequiredArgsConstructor
public class AppUserProfileService implements UserProfileService<AppUserProfile> {

    private final AppUserProfileRepository profileRepository;
    private final UserRepository userRepository;

    @Override
    public AppUserProfile getOrCreateProfile(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        return profileRepository.findByUserId(user.getId())
            .orElseGet(() -> createAndSaveProfile(user));
    }

    @Override
    public AppUserProfile updateProfile(AppUserProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Profile must not be null");
        }
        return profileRepository.save(profile);
    }

    private AppUserProfile createAndSaveProfile(User user) {
        User managedUser = userRepository.findById(user.getId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        AppUserProfile profile = new AppUserProfile();
        profile.setUser(managedUser);

        // Set default values for new profiles
        profile.setDisplayName(user.getFirstName() + " " + user.getLastName());
        profile.setAccountType(AccountType.BASIC);
        profile.setNotificationsEnabled(true);

        return profileRepository.save(profile);
    }

    // Additional application-specific methods
    public void upgradeAccount(AppUserProfile profile, AccountType newType) {
        profile.setAccountType(newType);
        profileRepository.save(profile);
    }
}
```

### Step 4: Create a Session Profile Manager

Create a session-scoped component to access the current user's profile:

```java
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AppSessionProfile extends BaseSessionProfile<AppUserProfile> {

    // Add custom accessor methods for your application
    public String getDisplayName() {
        return getUserProfile() != null ? getUserProfile().getDisplayName() : null;
    }

    public boolean isNotificationsEnabled() {
        return getUserProfile() != null && getUserProfile().isNotificationsEnabled();
    }

    public AccountType getAccountType() {
        return getUserProfile() != null ? getUserProfile().getAccountType() : null;
    }

    public boolean isPremiumUser() {
        return getUserProfile() != null &&
               getUserProfile().getAccountType() == AccountType.PREMIUM;
    }
}
```

### Step 5: Implement an Authentication Listener

Create a listener to load profiles during authentication:

```java
@Component
public class AppAuthenticationListener extends BaseAuthenticationListener<AppUserProfile> {

    public AppAuthenticationListener(AppSessionProfile sessionProfile,
                                   AppUserProfileService profileService) {
        super(sessionProfile, profileService);
    }

    // Optionally override event handling methods
}
```

That's it! With these components in place, your application will automatically:
1. Load the user's profile upon successful authentication
2. Store the profile in the session for easy access
3. Allow you to read and update profile data throughout your application

## Usage Examples

### Accessing Profile Data in Controllers

```java
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final AppSessionProfile sessionProfile;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Access profile data
        model.addAttribute("displayName", sessionProfile.getDisplayName());
        model.addAttribute("isPremium", sessionProfile.isPremiumUser());

        // Access the underlying User object if needed
        User user = sessionProfile.getUser();

        // Use the full profile object
        AppUserProfile profile = sessionProfile.getUserProfile();

        return "dashboard";
    }
}
```

### Using Profiles in Views

In Thymeleaf templates, you can directly access the session profile:

```html
<!-- With SessionProfile automatically added to model -->
<div th:if="${appSessionProfile.premiumUser}">
    <p>Welcome, Premium Member <span th:text="${appSessionProfile.displayName}">User</span>!</p>
    <!-- Premium-only content -->
</div>

<!-- Or using sec:authorize -->
<div sec:authorize="@appSessionProfile.isPremiumUser()">
    <!-- Premium-only content -->
</div>
```

### Profile-Based Authorization

You can use profile data for fine-grained authorization:

```java
@PreAuthorize("@appSessionProfile.isPremiumUser()")
@GetMapping("/premium-content")
public String premiumContent() {
    return "premium/content";
}
```

## Advanced Customizations

### Custom Profile Initialization

Override the `getOrCreateProfile` method to implement custom initialization logic:

```java
@Override
public AppUserProfile getOrCreateProfile(User user) {
    return profileRepository.findByUserId(user.getId())
        .orElseGet(() -> {
            AppUserProfile profile = new AppUserProfile();
            profile.setUser(user);

            // Apply business logic for new profiles
            if (user.getEmail().endsWith("@company.com")) {
                profile.setAccountType(AccountType.INTERNAL);
            }

            // Set up default preferences
            UserPreference theme = new UserPreference();
            theme.setKey("theme");
            theme.setValue("light");
            profile.addPreference(theme);

            return profileRepository.save(profile);
        });
}
```

### Additional Event Handling

You can handle more authentication-related events by adding methods to your listener:

```java
@Component
public class ExtendedAuthListener extends BaseAuthenticationListener<AppUserProfile> {

    private final LoginAttemptService loginAttemptService;

    public ExtendedAuthListener(
            AppSessionProfile sessionProfile,
            AppUserProfileService profileService,
            LoginAttemptService loginAttemptService) {
        super(sessionProfile, profileService);
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent event) {
        // Handle logout, e.g., update last logout timestamp
        if (event.getAuthentication().getPrincipal() instanceof DSUserDetails) {
            User user = ((DSUserDetails) event.getAuthentication().getPrincipal()).getUser();
            AppUserProfile profile = profileService.getOrCreateProfile(user);
            profile.setLastLogout(new Date());
            profileService.updateProfile(profile);
        }
    }
}
```

### Profile Migration Strategies

If you need to migrate or update existing profiles:

```java
@Service
@RequiredArgsConstructor
public class ProfileMigrationService {

    private final AppUserProfileRepository profileRepository;

    @Transactional
    @Scheduled(fixedRate = 86400000) // Daily
    public void migrateProfilesToNewSchema() {
        List<AppUserProfile> profiles = profileRepository.findAll();
        for (AppUserProfile profile : profiles) {
            // Perform migration logic
            if (profile.getAccountType() == null) {
                profile.setAccountType(AccountType.BASIC);
            }

            // Initialize new fields with default values
            if (profile.getPreferences().isEmpty()) {
                UserPreference defaultPref = new UserPreference();
                defaultPref.setKey("notifications");
                defaultPref.setValue("true");
                profile.addPreference(defaultPref);
            }
        }
        profileRepository.saveAll(profiles);
    }
}
```

## Troubleshooting

**Profile Not Loading After Authentication**
- Ensure your `AuthenticationListener` is properly registered as a Spring bean
- Verify that Spring Security is configured to use the framework's authentication provider
- Check that your transaction boundaries are correctly defined

**Session Profile Returns Null**
- Make sure the session scoping is correctly configured
- Ensure authentication events are being fired
- Check for circular dependencies in your profile service

**Missing Profile Data**
- Verify that profile initialization logic correctly sets default values
- Check that database schema updates include new fields
- Review transaction isolation levels if concurrent updates are possible

For more complex issues, enable debug logging:

```yaml
logging:
  level:
    com.digitalsanctuary.spring.user.profile: DEBUG
    com.example.myapp.profile: DEBUG
```

---

This framework provides a flexible foundation for managing user-specific data in your application. By extending these base components, you can create a rich user experience while maintaining clean separation of concerns and leveraging Spring's powerful features.

For a complete working example, refer to the [Spring User Framework Demo Application](https://github.com/devondragon/SpringUserFrameworkDemoApp).
