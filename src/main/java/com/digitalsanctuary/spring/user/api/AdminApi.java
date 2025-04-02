package com.digitalsanctuary.spring.user.api;

import com.digitalsanctuary.spring.user.dto.LockAccountDto;
import com.digitalsanctuary.spring.user.service.UserService;
import com.digitalsanctuary.spring.user.util.JSONResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.digitalsanctuary.spring.user.util.ResponseUtil.buildErrorResponse;
import static com.digitalsanctuary.spring.user.util.ResponseUtil.buildSuccessResponse;

/**
 * REST controller for managing admin-related operations. This class handles locking of user account.
 */

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping(path = "/admin", produces = "application/json")
public class AdminApi {
    private final UserService userService;

    /**
     * Toggle Lock status for a user account.
     *
     * @param lockAccountDto the DTO containing the email of the user to be locked
     * @return a ResponseEntity containing a JSONResponse stating that user account has been locked
     * */
    @PostMapping("/lockAccount")
    @PreAuthorize("hasAuthority('ADMIN_PRIVILEGE')")
    public ResponseEntity<JSONResponse> lockAccount(@Valid @RequestBody LockAccountDto lockAccountDto) {
        log.info("AdminApi.lockAccount: called with email: {}", lockAccountDto.getEmail());
        try {
            validateDto(lockAccountDto);
            userService.lockAccount(lockAccountDto.getEmail());
            return buildSuccessResponse("User account locked successfully", null);
        } catch (UsernameNotFoundException e) {
            log.warn("AdminApi.lockAccount: user not found: {}", lockAccountDto.getEmail());
            return buildErrorResponse("User not found", 2, HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            log.warn("AdminApi.lockAccount: invalid argument: {}", e.getMessage());
            return buildErrorResponse(e.getMessage(), 1, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/unlockAccount")
    @PreAuthorize("hasAuthority('ADMIN_PRIVILEGE')")
    public ResponseEntity<JSONResponse> unlockAccount(@Valid @RequestBody LockAccountDto lockAccountDto) {
        log.info("AdminApi.unlockAccount: called with email: {}", lockAccountDto.getEmail());
        try {
            validateDto(lockAccountDto);
            userService.unlockAccount(lockAccountDto.getEmail());
            return buildSuccessResponse("User account unlocked successfully", null);
        } catch (UsernameNotFoundException e) {
            log.warn("AdminApi.unlockAccount: user not found: {}", lockAccountDto.getEmail());
            return buildErrorResponse("User not found", 2, HttpStatus.NOT_FOUND);
        } catch (IllegalArgumentException e) {
            log.warn("AdminApi.unlockAccount: invalid argument: {}", e.getMessage());
            return buildErrorResponse(e.getMessage(), 1, HttpStatus.BAD_REQUEST);
        }
    }


    private void validateDto(LockAccountDto dto) {
        if(dto.getEmail() == null || dto.getEmail().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
    }
}
