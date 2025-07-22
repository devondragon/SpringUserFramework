package com.digitalsanctuary.spring.user.test.builders;

import com.digitalsanctuary.spring.user.persistence.model.PasswordResetToken;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.model.VerificationToken;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

/**
 * Fluent builder for creating test token entities (VerificationToken and PasswordResetToken).
 * This builder simplifies token creation for tests with various expiration states.
 * 
 * Example usage:
 * <pre>
 * VerificationToken validToken = TokenTestDataBuilder.aVerificationToken()
 *     .forUser(testUser)
 *     .expiringInMinutes(60)
 *     .build();
 * 
 * PasswordResetToken expiredToken = TokenTestDataBuilder.aPasswordResetToken()
 *     .forUser(testUser)
 *     .expired()
 *     .build();
 * </pre>
 */
public class TokenTestDataBuilder {
    
    /**
     * Builder for VerificationToken entities.
     */
    public static class VerificationTokenBuilder {
        private static long idCounter = 1L;
        
        private Long id;
        private String token;
        private User user;
        private Date expiryDate;
        
        private VerificationTokenBuilder() {
            this.id = idCounter++;
            this.token = UUID.randomUUID().toString();
            // Default: expires in 24 hours
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 24);
            this.expiryDate = cal.getTime();
        }
        
        public VerificationTokenBuilder withId(Long id) {
            this.id = id;
            return this;
        }
        
        public VerificationTokenBuilder withToken(String token) {
            this.token = token;
            return this;
        }
        
        public VerificationTokenBuilder forUser(User user) {
            this.user = user;
            return this;
        }
        
        public VerificationTokenBuilder withExpiryDate(Date expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }
        
        public VerificationTokenBuilder expiringInMinutes(int minutes) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, minutes);
            this.expiryDate = cal.getTime();
            return this;
        }
        
        public VerificationTokenBuilder expiringInHours(int hours) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, hours);
            this.expiryDate = cal.getTime();
            return this;
        }
        
        public VerificationTokenBuilder expiringInDays(int days) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, days);
            this.expiryDate = cal.getTime();
            return this;
        }
        
        public VerificationTokenBuilder expired() {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, -1); // Expired 1 hour ago
            this.expiryDate = cal.getTime();
            return this;
        }
        
        public VerificationTokenBuilder expiredDaysAgo(int days) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -days);
            this.expiryDate = cal.getTime();
            return this;
        }
        
        public VerificationTokenBuilder neverExpires() {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.YEAR, 100); // Expires in 100 years
            this.expiryDate = cal.getTime();
            return this;
        }
        
        public VerificationToken build() {
            if (user == null) {
                // Create a default user if none provided
                user = UserTestDataBuilder.aUser().build();
            }
            
            VerificationToken token = new VerificationToken();
            token.setId(id);
            token.setToken(this.token);
            token.setUser(user);
            token.setExpiryDate(expiryDate);
            
            return token;
        }
    }
    
    /**
     * Builder for PasswordResetToken entities.
     */
    public static class PasswordResetTokenBuilder {
        private static long idCounter = 1L;
        
        private Long id;
        private String token;
        private User user;
        private Date expiryDate;
        
        private PasswordResetTokenBuilder() {
            this.id = idCounter++;
            this.token = UUID.randomUUID().toString();
            // Default: expires in 1 hour
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 1);
            this.expiryDate = cal.getTime();
        }
        
        public PasswordResetTokenBuilder withId(Long id) {
            this.id = id;
            return this;
        }
        
        public PasswordResetTokenBuilder withToken(String token) {
            this.token = token;
            return this;
        }
        
        public PasswordResetTokenBuilder forUser(User user) {
            this.user = user;
            return this;
        }
        
        public PasswordResetTokenBuilder withExpiryDate(Date expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }
        
        public PasswordResetTokenBuilder expiringInMinutes(int minutes) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, minutes);
            this.expiryDate = cal.getTime();
            return this;
        }
        
        public PasswordResetTokenBuilder expiringInHours(int hours) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, hours);
            this.expiryDate = cal.getTime();
            return this;
        }
        
        public PasswordResetTokenBuilder expired() {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, -30); // Expired 30 minutes ago
            this.expiryDate = cal.getTime();
            return this;
        }
        
        public PasswordResetTokenBuilder expiredMinutesAgo(int minutes) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, -minutes);
            this.expiryDate = cal.getTime();
            return this;
        }
        
        public PasswordResetTokenBuilder justExpired() {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.SECOND, -1); // Expired 1 second ago
            this.expiryDate = cal.getTime();
            return this;
        }
        
        public PasswordResetToken build() {
            if (user == null) {
                // Create a default user if none provided
                user = UserTestDataBuilder.aUser().build();
            }
            
            PasswordResetToken token = new PasswordResetToken();
            token.setId(id);
            token.setToken(this.token);
            token.setUser(user);
            token.setExpiryDate(expiryDate);
            
            return token;
        }
    }
    
    /**
     * Creates a new VerificationToken builder.
     */
    public static VerificationTokenBuilder aVerificationToken() {
        return new VerificationTokenBuilder();
    }
    
    /**
     * Creates a new PasswordResetToken builder.
     */
    public static PasswordResetTokenBuilder aPasswordResetToken() {
        return new PasswordResetTokenBuilder();
    }
    
    /**
     * Creates a valid (non-expired) verification token.
     */
    public static VerificationTokenBuilder aValidVerificationToken() {
        return new VerificationTokenBuilder()
                .expiringInHours(24);
    }
    
    /**
     * Creates an expired verification token.
     */
    public static VerificationTokenBuilder anExpiredVerificationToken() {
        return new VerificationTokenBuilder()
                .expired();
    }
    
    /**
     * Creates a valid (non-expired) password reset token.
     */
    public static PasswordResetTokenBuilder aValidPasswordResetToken() {
        return new PasswordResetTokenBuilder()
                .expiringInHours(1);
    }
    
    /**
     * Creates an expired password reset token.
     */
    public static PasswordResetTokenBuilder anExpiredPasswordResetToken() {
        return new PasswordResetTokenBuilder()
                .expired();
    }
}