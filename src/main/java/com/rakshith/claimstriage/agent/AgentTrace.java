package com.rakshith.claimstriage.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Collects every tool call made during one triage run, so it can be saved as an audit trail. */
public class AgentTrace {

    public record Step(int stepNo, String toolName, String input, String output, Instant at) {
    }

    private final String runId = UUID.randomUUID().toString();
    private final List<Step> steps = Collections.synchronizedList(new ArrayList<>());

    public void record(String toolName, String input, String output) {
        synchronized (steps) {
            steps.add(new Step(steps.size() + 1, toolName, input, output, Instant.now()));
        }
    }

    public String runId() {
        return runId;
    }

    public List<Step> steps() {
        synchronized (steps) {
            return List.copyOf(steps);
        }
    }

    public boolean calledTool(String toolName) {
        return steps().stream().anyMatch(s -> s.toolName().equals(toolName));
    }
}
