package com.sismics.docs.core.constant;

/**
 * User registration request status.
 *
 * @author bgamard
 */
public enum UserRegistrationRequestStatus {
    /**
     * Request is pending approval.
     */
    PENDING,

    /**
     * Request has been approved.
     */
    APPROVED,

    /**
     * Request has been rejected.
     */
    REJECTED
}