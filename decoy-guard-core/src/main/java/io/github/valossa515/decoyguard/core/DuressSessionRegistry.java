package io.github.valossa515.decoyguard.core;

/**
 * SPI that remembers which sessions are in duress mode.
 * Production implementations should be backed by shared storage (Redis, database)
 * so that every instance of the application serves the same mode.
 */
public interface DuressSessionRegistry {

    /** Marks the session as DURESS. Idempotent: the first activation wins. */
    DuressContext activate(String sessionId, ActivationTrigger trigger);

    /** Returns the context for the session, NORMAL when the session was never activated. */
    DuressContext resolve(String sessionId);

    /** Ends duress mode for the session, for example after the bank confirms the user is safe. */
    void release(String sessionId);
}
