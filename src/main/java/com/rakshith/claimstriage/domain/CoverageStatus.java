package com.rakshith.claimstriage.domain;

public enum CoverageStatus {
    /** Loss type is covered and policy was in force on the loss date. */
    COVERED,
    /** Policy is in force but the loss type is not a covered peril. */
    NOT_COVERED,
    /** Policy was lapsed/cancelled or the loss date is outside the policy period. */
    POLICY_INACTIVE,
    /** Could not be determined automatically. */
    NEEDS_REVIEW
}
