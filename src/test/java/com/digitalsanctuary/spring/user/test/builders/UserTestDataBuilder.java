package com.digitalsanctuary.spring.user.test.builders;

import com.digitalsanctuary.spring.user.persistence.model.Role;
import com.digitalsanctuary.spring.user.persistence.model.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

/**
 * Fluent builder for creating test User entities with sensible defaults.
 * This builder simplifies test data creation and ensures consistent test data.
 * 
 * Example usage:
 * <pre>
 * User testUser = UserTestDataBuilder.aUser()
 *     .withEmail("test@example.com")
 *     .withFirstName("John")
 *     .withLastName("Doe")
 *     .verified()
 *     .withRole("ROLE_ADMIN")
 *     .build();
 * </pre>
 */
public class UserTestDataBuilder {
    
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(4);
    private static long idCounter = 1L;
    
    private Long id;
    private String firstName = "Test";
    private String lastName = "User";
    private String email;
    private User.Provider provider = User.Provider.LOCAL;
    private String password = "password123";
    private boolean passwordEncoded = false;
    private boolean enabled = false;
    private Date registrationDate = new Date();
    private Date lastActivityDate = new Date();
    private int failedLoginAttempts = 0;
    private boolean locked = false;
    private Date lockedDate = null;
    private List<Role> roles = new ArrayList<>();

    private UserTestDataBuilder() {
        // Generate unique email if not set
        this.email = "user" + idCounter + "@test.com";
        this.id = idCounter++;
    }

    /**
     * Creates a new UserTestDataBuilder instance.
     */
    public static UserTestDataBuilder aUser() {
        return new UserTestDataBuilder();
    }

    /**
     * Creates a builder for a default verified user.
     */
    public static UserTestDataBuilder aVerifiedUser() {
        return new UserTestDataBuilder()
                .verified()
                .withRole("ROLE_USER");
    }

    /**
     * Creates a builder for a default admin user.
     */
    public static UserTestDataBuilder anAdminUser() {
        return new UserTestDataBuilder()
                .withEmail("admin@test.com")
                .withFirstName("Admin")
                .withLastName("User")
                .verified()
                .withRole("ROLE_ADMIN");
    }

    /**
     * Creates a builder for an unverified user.
     */
    public static UserTestDataBuilder anUnverifiedUser() {
        return new UserTestDataBuilder()
                .withEmail("unverified@test.com")
                .unverified()
                .withRole("ROLE_USER");
    }

    /**
     * Creates a builder for a locked user.
     */
    public static UserTestDataBuilder aLockedUser() {
        return new UserTestDataBuilder()
                .withEmail("locked@test.com")
                .locked()
                .withFailedLoginAttempts(5)
                .withRole("ROLE_USER");
    }

    public UserTestDataBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public UserTestDataBuilder withFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public UserTestDataBuilder withLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public UserTestDataBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public UserTestDataBuilder withProvider(User.Provider provider) {
        this.provider = provider;
        return this;
    }

    public UserTestDataBuilder withPassword(String password) {
        this.password = password;
        this.passwordEncoded = false;
        return this;
    }

    public UserTestDataBuilder withEncodedPassword(String encodedPassword) {
        this.password = encodedPassword;
        this.passwordEncoded = true;
        return this;
    }

    public UserTestDataBuilder enabled() {
        this.enabled = true;
        return this;
    }

    public UserTestDataBuilder disabled() {
        this.enabled = false;
        return this;
    }

    public UserTestDataBuilder verified() {
        this.enabled = true;
        return this;
    }

    public UserTestDataBuilder unverified() {
        this.enabled = false;
        return this;
    }

    public UserTestDataBuilder withRegistrationDate(Date registrationDate) {
        this.registrationDate = registrationDate;
        return this;
    }

    public UserTestDataBuilder registeredDaysAgo(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        this.registrationDate = cal.getTime();
        return this;
    }

    public UserTestDataBuilder withLastActivityDate(Date lastActivityDate) {
        this.lastActivityDate = lastActivityDate;
        return this;
    }

    public UserTestDataBuilder lastActiveDaysAgo(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        this.lastActivityDate = cal.getTime();
        return this;
    }

    public UserTestDataBuilder withFailedLoginAttempts(int attempts) {
        this.failedLoginAttempts = attempts;
        return this;
    }

    public UserTestDataBuilder locked() {
        this.locked = true;
        this.lockedDate = new Date();
        return this;
    }

    public UserTestDataBuilder unlocked() {
        this.locked = false;
        this.lockedDate = null;
        this.failedLoginAttempts = 0;
        return this;
    }

    public UserTestDataBuilder withLockedDate(Date lockedDate) {
        this.lockedDate = lockedDate;
        this.locked = (lockedDate != null);
        return this;
    }

    public UserTestDataBuilder withRole(String roleName) {
        Role role = new Role();
        role.setId((long) roleName.hashCode());
        role.setName(roleName);
        this.roles.add(role);
        return this;
    }

    public UserTestDataBuilder withRole(Role role) {
        this.roles.add(role);
        return this;
    }

    public UserTestDataBuilder withRoles(List<Role> roles) {
        this.roles = new ArrayList<>(roles);
        return this;
    }

    public UserTestDataBuilder withRoles(String... roleNames) {
        for (String roleName : roleNames) {
            withRole(roleName);
        }
        return this;
    }


    public UserTestDataBuilder fromOAuth2(User.Provider provider) {
        this.provider = provider;
        this.password = null; // OAuth2 users don't have local passwords
        this.passwordEncoded = true;
        return this;
    }

    /**
     * Builds the User entity with all configured values.
     */
    public User build() {
        User user = new User();
        user.setId(id);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setProvider(provider);
        
        // Handle password encoding
        if (password != null) {
            user.setPassword(passwordEncoded ? password : PASSWORD_ENCODER.encode(password));
        }
        
        user.setEnabled(enabled);
        user.setRegistrationDate(registrationDate);
        user.setLastActivityDate(lastActivityDate);
        user.setFailedLoginAttempts(failedLoginAttempts);
        user.setLocked(locked);
        user.setLockedDate(lockedDate);
        user.setRoles(roles);
        
        return user;
    }

    /**
     * Builds and returns a list containing the configured user.
     * Useful for methods expecting a list.
     */
    public List<User> buildAsList() {
        return Arrays.asList(build());
    }

    /**
     * Builds multiple users with incremented emails.
     */
    public List<User> buildMany(int count) {
        List<User> users = new ArrayList<>();
        String baseEmail = this.email;
        
        for (int i = 0; i < count; i++) {
            this.email = baseEmail.replace("@", i + "@");
            users.add(build());
            this.id = idCounter++;
        }
        
        this.email = baseEmail; // Reset
        return users;
    }
}