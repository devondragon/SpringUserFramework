package com.digitalsanctuary.spring.user.api;

import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.digitalsanctuary.spring.user.audit.AuditEvent;
import com.digitalsanctuary.spring.user.dto.ConsentRequestDto;
import com.digitalsanctuary.spring.user.dto.GdprExportDTO;
import com.digitalsanctuary.spring.user.gdpr.ConsentAuditService;
import com.digitalsanctuary.spring.user.gdpr.ConsentRecord;
import com.digitalsanctuary.spring.user.gdpr.ConsentType;
import com.digitalsanctuary.spring.user.gdpr.GdprConfig;
import com.digitalsanctuary.spring.user.gdpr.GdprDeletionService;
import com.digitalsanctuary.spring.user.gdpr.GdprExportService;
import com.digitalsanctuary.spring.user.persistence.model.User;
import com.digitalsanctuary.spring.user.service.DSUserDetails;
import com.digitalsanctuary.spring.user.service.SessionInvalidationService;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.util.JSONResponse;
import com.digitalsanctuary.spring.user.util.UserUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * REST API controller for GDPR-related operations.
 *
 * <p>Provides JSON endpoints for GDPR compliance including:
 * <ul>
 *   <li>Data export (Right of Access)</li>
 *   <li>Account deletion (Right to be Forgotten)</li>
 *   <li>Consent management</li>
 * </ul>
 *
 * <p>All endpoints require authentication and return JSON responses.
 *
 * @author Devon Hillard
 * @see GdprExportService
 * @see GdprDeletionService
 * @see ConsentAuditService
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(path = "/user/gdpr", produces = "application/json")
public class GdprAPI {

    private final GdprConfig gdprConfig;
    private final GdprExportService gdprExportService;
    private final GdprDeletionService gdprDeletionService;
    private final ConsentAuditService consentAuditService;
    private final UserService userService;
    private final SessionInvalidationService sessionInvalidationService;
    private final MessageSource messages;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Exports all GDPR-relevant data for the authenticated user.
     *
     * <p>Returns a comprehensive export including:
     * <ul>
     *   <li>User account data</li>
     *   <li>Audit history</li>
     *   <li>Consent records</li>
     *   <li>Token metadata</li>
     *   <li>Data from registered GdprDataContributors</li>
     * </ul>
     *
     * @param userDetails the authenticated user
     * @param request the HTTP request
     * @return the complete data export as JSON
     */
    @GetMapping("/export")
    public ResponseEntity<JSONResponse> exportUserData(@AuthenticationPrincipal DSUserDetails userDetails,
                                                        HttpServletRequest request) {
        if (!gdprConfig.isEnabled()) {
            return buildNotFoundResponse();
        }

        User user = validateAndGetUser(userDetails);
        if (user == null) {
            return buildErrorResponse("User not authenticated", 1, HttpStatus.UNAUTHORIZED);
        }

        try {
            GdprExportDTO export = gdprExportService.exportUserData(user);
            logAuditEvent("GdprExport", "Success", "User data exported", user, request);

            return ResponseEntity.ok(JSONResponse.builder()
                    .success(true)
                    .code(0)
                    .message("Data export completed successfully")
                    .data(export)
                    .build());
        } catch (Exception e) {
            log.error("GdprAPI.exportUserData: Failed to export data for user {}: {}",
                    user.getId(), e.getMessage(), e);
            logAuditEvent("GdprExport", "Failure", e.getMessage(), user, request);
            return buildErrorResponse("Failed to export data", 5, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Requests deletion of the authenticated user's account.
     *
     * <p>If configured, exports user data before deletion and includes
     * it in the response. After deletion, the user is logged out.
     *
     * @param userDetails the authenticated user
     * @param request the HTTP request
     * @return deletion result, optionally including exported data
     */
    @PostMapping("/delete")
    public ResponseEntity<JSONResponse> deleteAccount(@AuthenticationPrincipal DSUserDetails userDetails,
                                                       HttpServletRequest request) {
        if (!gdprConfig.isEnabled()) {
            return buildNotFoundResponse();
        }

        User user = validateAndGetUser(userDetails);
        if (user == null) {
            return buildErrorResponse("User not authenticated", 1, HttpStatus.UNAUTHORIZED);
        }

        try {
            GdprDeletionService.DeletionResult result = gdprDeletionService.deleteUser(user);

            if (result.isSuccess()) {
                logAuditEvent("GdprDelete", "Success", "User account deleted", null, request);
                logoutUser(user, request);

                JSONResponse.JSONResponseBuilder responseBuilder = JSONResponse.builder()
                        .success(true)
                        .code(0)
                        .message(result.getMessage());

                if (result.getExportedData() != null) {
                    responseBuilder.data(result.getExportedData());
                }

                return ResponseEntity.ok(responseBuilder.build());
            } else {
                log.error("GdprAPI.deleteAccount: Deletion failed for user {}: {}",
                        user.getId(), result.getMessage());
                return buildErrorResponse(result.getMessage(), 2, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            log.error("GdprAPI.deleteAccount: Failed to delete account for user {}: {}",
                    user.getId(), e.getMessage(), e);
            logAuditEvent("GdprDelete", "Failure", e.getMessage(), user, request);
            return buildErrorResponse("Failed to delete account", 5, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Records a consent grant or withdrawal.
     *
     * @param userDetails the authenticated user
     * @param consentRequest the consent request details
     * @param request the HTTP request
     * @return the recorded consent details
     */
    @PostMapping("/consent")
    public ResponseEntity<JSONResponse> recordConsent(@AuthenticationPrincipal DSUserDetails userDetails,
                                                       @Valid @RequestBody ConsentRequestDto consentRequest,
                                                       HttpServletRequest request) {
        if (!gdprConfig.isEnabled() || !gdprConfig.isConsentTracking()) {
            return buildNotFoundResponse();
        }

        User user = validateAndGetUser(userDetails);
        if (user == null) {
            return buildErrorResponse("User not authenticated", 1, HttpStatus.UNAUTHORIZED);
        }

        // Validate custom type if needed
        if (consentRequest.getConsentType() == ConsentType.CUSTOM &&
                (consentRequest.getCustomType() == null || consentRequest.getCustomType().isBlank())) {
            return buildErrorResponse("Custom type is required when consent type is CUSTOM",
                    2, HttpStatus.BAD_REQUEST);
        }

        try {
            String method = consentRequest.getMethod() != null ? consentRequest.getMethod() : "api";
            ConsentRecord record;

            if (Boolean.TRUE.equals(consentRequest.getGrant())) {
                record = consentAuditService.recordConsentGranted(
                        user,
                        consentRequest.getConsentType(),
                        consentRequest.getCustomType(),
                        consentRequest.getPolicyVersion(),
                        method,
                        request
                );
            } else {
                record = consentAuditService.recordConsentWithdrawn(
                        user,
                        consentRequest.getConsentType(),
                        consentRequest.getCustomType(),
                        method,
                        request
                );
            }

            String action = Boolean.TRUE.equals(consentRequest.getGrant()) ? "granted" : "withdrawn";
            return ResponseEntity.ok(JSONResponse.builder()
                    .success(true)
                    .code(0)
                    .message("Consent " + action + " successfully")
                    .data(record)
                    .build());
        } catch (Exception e) {
            log.error("GdprAPI.recordConsent: Failed to record consent for user {}: {}",
                    user.getId(), e.getMessage(), e);
            return buildErrorResponse("Failed to record consent", 5, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Gets the current consent status for the authenticated user.
     *
     * @param userDetails the authenticated user
     * @param request the HTTP request
     * @return map of consent types to their current status
     */
    @GetMapping("/consent")
    public ResponseEntity<JSONResponse> getConsentStatus(@AuthenticationPrincipal DSUserDetails userDetails,
                                                          HttpServletRequest request) {
        if (!gdprConfig.isEnabled() || !gdprConfig.isConsentTracking()) {
            return buildNotFoundResponse();
        }

        User user = validateAndGetUser(userDetails);
        if (user == null) {
            return buildErrorResponse("User not authenticated", 1, HttpStatus.UNAUTHORIZED);
        }

        try {
            Map<String, ConsentAuditService.ConsentStatus> status = consentAuditService.getConsentStatus(user);

            return ResponseEntity.ok(JSONResponse.builder()
                    .success(true)
                    .code(0)
                    .message("Consent status retrieved successfully")
                    .data(status)
                    .build());
        } catch (Exception e) {
            log.error("GdprAPI.getConsentStatus: Failed to get consent status for user {}: {}",
                    user.getId(), e.getMessage(), e);
            return buildErrorResponse("Failed to get consent status", 5, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Validates the authenticated user and returns the User entity.
     */
    private User validateAndGetUser(DSUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            return null;
        }
        // Re-fetch from database to ensure attached entity
        return userService.findUserByEmail(userDetails.getUser().getEmail());
    }

    /**
     * Logs out the user from all sessions and clears the current security context.
     *
     * <p>This method invalidates ALL sessions for the user across all devices/browsers,
     * not just the current session. This is critical for GDPR account deletion to ensure
     * no orphaned sessions remain with references to the deleted user.
     *
     * @param user the user to log out (used for invalidating all sessions)
     * @param request the current HTTP request
     */
    private void logoutUser(User user, HttpServletRequest request) {
        try {
            // Invalidate all user sessions across all devices
            int invalidatedCount = sessionInvalidationService.invalidateUserSessions(user);
            log.debug("GdprAPI.logoutUser: Invalidated {} sessions for user {}", invalidatedCount, user.getId());

            // Clear current context and logout current session
            SecurityContextHolder.clearContext();
            request.logout();
        } catch (ServletException e) {
            log.warn("GdprAPI.logoutUser: Logout failed", e);
        }
    }

    /**
     * Logs an audit event.
     */
    private void logAuditEvent(String action, String status, String message, User user, HttpServletRequest request) {
        AuditEvent event = AuditEvent.builder()
                .source(this)
                .user(user)
                .sessionId(request.getSession().getId())
                .ipAddress(UserUtils.getClientIP(request))
                .userAgent(request.getHeader("User-Agent"))
                .action(action)
                .actionStatus(status)
                .message(message)
                .build();
        eventPublisher.publishEvent(event);
    }

    /**
     * Builds a 404 Not Found response (for when GDPR is disabled).
     */
    private ResponseEntity<JSONResponse> buildNotFoundResponse() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(JSONResponse.builder()
                        .success(false)
                        .code(404)
                        .message("GDPR features are not enabled")
                        .build());
    }

    /**
     * Builds an error response.
     */
    private ResponseEntity<JSONResponse> buildErrorResponse(String message, int code, HttpStatus status) {
        return ResponseEntity.status(status)
                .body(JSONResponse.builder()
                        .success(false)
                        .code(code)
                        .message(message)
                        .build());
    }

}
