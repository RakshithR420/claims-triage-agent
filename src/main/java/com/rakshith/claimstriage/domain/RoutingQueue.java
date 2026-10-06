package com.rakshith.claimstriage.domain;

/**
 * Work queues a claim can be routed to. The escalation order (FAST_TRACK lowest,
 * SPECIAL_INVESTIGATIONS highest) is used by the guardrails to enforce "at least" rules.
 * DENIAL_REVIEW sits outside that ladder: coverage problems always go to a human.
 */
public enum RoutingQueue {
    FAST_TRACK(0),
    STANDARD_ADJUSTER(1),
    SENIOR_ADJUSTER(2),
    SPECIAL_INVESTIGATIONS(3),
    DENIAL_REVIEW(-1);

    private final int escalationLevel;

    RoutingQueue(int escalationLevel) {
        this.escalationLevel = escalationLevel;
    }

    public int escalationLevel() {
        return escalationLevel;
    }

    /** Returns the more escalated of the two queues (DENIAL_REVIEW is handled separately by callers). */
    public static RoutingQueue atLeast(RoutingQueue current, RoutingQueue floor) {
        if (current == null) return floor;
        return current.escalationLevel >= floor.escalationLevel ? current : floor;
    }
}
