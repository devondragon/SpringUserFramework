package com.digitalsanctuary.spring.user.dev;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.persistence.repository.UserRepository;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.util.JSONResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Development-only controller providing quick login-as functionality.
 * <p>
 * This controller is only active when the "local" Spring profile is active AND
 * {@code user.dev.auto-login-enabled} is set to {@code true}. It allows developers
 * to quickly switch between user accounts without entering passwords.
 * </p>
 * <p>
 * <strong>SECURITY WARNING:</strong> This controller must NEVER be enabled in
 * production environments. It bypasses all password authentication.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
@Profile("local")
@ConditionalOnProperty(name = "user.dev.auto-login-enabled", havingValue = "true", matchIfMissing = false)
public class DevLoginController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final DevLoginConfigProperties devLoginConfigProperties;

    /**
     * Logs in as the specified user by email without requiring a password.
     * After successful authentication, returns a redirect response to the configured URL.
     *
     * @param email the email of the user to log in as
     * @return a redirect response on success, or an error response with 404/403 status
     */
    @GetMapping("/login-as/{email}")
    public ResponseEntity<JSONResponse> loginAs(@PathVariable String email) {
        log.warn("Dev login attempt for user: {}", email);

        User user = userService.findUserByEmail(email);
        if (user == null) {
            log.warn("Dev login failed: user not found for email: {}", email);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(JSONResponse.builder().success(false).message("User not found: " + email).code(404).build());
        }

        if (!user.isEnabled()) {
            log.warn("Dev login failed: user is disabled: {}", email);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(JSONResponse.builder().success(false).message("User is disabled: " + email).code(403)
                            .build());
        }

        userService.authWithoutPassword(user);
        log.warn("Dev login successful for user: {}", email);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", devLoginConfigProperties.getLoginRedirectUrl())
                .build();
    }

    /**
     * Lists all enabled user emails available for dev login.
     *
     * @return a JSONResponse containing the list of enabled user emails
     */
    @GetMapping("/users")
    public ResponseEntity<JSONResponse> listUsers() {
        List<String> enabledEmails = userRepository.findAllByEnabledTrue().stream()
                .map(User::getEmail)
                .toList();

        return ResponseEntity.ok(JSONResponse.builder().success(true)
                .message("Found " + enabledEmails.size() + " enabled users").data(enabledEmails).build());
    }
}
