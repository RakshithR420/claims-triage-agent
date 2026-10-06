package com.rakshith.claimstriage.domain;

public enum Severity {
    LOW, MEDIUM, HIGH, CATASTROPHIC;

    public boolean atLeast(Severity other) {
        return this.ordinal() >= other.ordinal();
    }

    public static Severity max(Severity a, Severity b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
