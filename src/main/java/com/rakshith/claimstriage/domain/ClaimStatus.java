package com.rakshith.claimstriage.domain;

public enum ClaimStatus {
    SUBMITTED,
    /** Triaged and auto-routed to the fast-track queue (no human sign-off needed to start work). */
    TRIAGED,
    /** Triaged, waiting for an adjuster to confirm or change the agent's routing. */
    PENDING_HUMAN_REVIEW,
    /** An adjuster confirmed or overrode the routing. */
    REVIEWED
}
