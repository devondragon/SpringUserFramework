/**
 * This package contains service classes for the DigitalSanctuary Spring User framework.
 *
 * <p>
 * The services in this package provide core functionalities related to user management, including user registration, email verification, login
 * attempts tracking, and user-related operations. These services interact with the persistence layer to perform CRUD operations and other business
 * logic.
 * </p>
 *
 * <h2>Classes:</h2>
 * <ul>
 * <li>{@link com.digitalsanctuary.spring.user.service.UserService} - Provides user-related operations such as registration, password management, and
 * user retrieval.</li>
 * <li>{@link com.digitalsanctuary.spring.user.service.UserEmailService} - Handles email-related operations for users, including sending verification
 * and password reset emails.</li>
 * <li>{@link com.digitalsanctuary.spring.user.service.UserVerificationService} - Manages user verification processes, including token generation and
 * validation.</li>
 * <li>{@link com.digitalsanctuary.spring.user.service.LoginAttemptService} - Tracks login attempts and manages account lockout policies to prevent
 * brute-force attacks.</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <p>
 * These services are typically used by controllers and other components to perform user-related operations. They encapsulate the business logic and
 * interact with the persistence layer to ensure data consistency and integrity.
 * </p>
 *
 * <h2>Example:</h2>
 * 
 * <pre>
 * {@code
 * @Autowired
 * private UserService userService;
 *
 * public void registerUser(UserDto userDto) {
 *     userService.registerNewUserAccount(userDto);
 * }
 * }
 * </pre>
 *
 * <h2>Dependencies:</h2>
 * <p>
 * The services in this package depend on the persistence layer (repositories) and may also interact with other services and utilities within the
 * application.
 * </p>
 *
 * @see com.digitalsanctuary.spring.user.service.UserService
 * @see com.digitalsanctuary.spring.user.service.UserEmailService
 * @see com.digitalsanctuary.spring.user.service.UserVerificationService
 * @see com.digitalsanctuary.spring.user.service.LoginAttemptService
 */
package com.digitalsanctuary.spring.user.service;
