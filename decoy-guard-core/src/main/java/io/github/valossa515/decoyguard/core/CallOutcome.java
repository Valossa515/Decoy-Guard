package io.github.valossa515.decoyguard.core;

/**
 * How a call on a routed interface ended. Latency is observed per outcome,
 * because a rejected call is usually faster than an accepted one.
 */
public enum CallOutcome {
    RETURNED,
    THREW
}
