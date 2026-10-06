package com.rakshith.claimstriage.agent;

import com.rakshith.claimstriage.domain.DecisionSource;

/** Something that can propose a triage decision for a claim using the tools in a {@link TriageToolbox}. */
public interface DecisionEngine {

    TriageDraft decide(TriageToolbox toolbox);

    DecisionSource source();

    /** Model name for LLM engines, or a short label for non-LLM engines. */
    String modelName();
}
