package com.digitalsanctuary.spring.user.dto;

import lombok.Data;

/**
 * A lock account dto. This object is used for locking a user account.
 */
@Data
public class LockAccountDto {

    /** The user's email */
    private String email;
}
