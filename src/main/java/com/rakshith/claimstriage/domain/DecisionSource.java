package com.rakshith.claimstriage.domain;

public enum DecisionSource {
    /** LLM agent decision (after guardrails). */
    LLM_AGENT,
    /** Deterministic rules engine (LLM disabled or LLM failed and we fell back). */
    RULES_ENGINE,
    /** Human adjuster review. */
    HUMAN
}
