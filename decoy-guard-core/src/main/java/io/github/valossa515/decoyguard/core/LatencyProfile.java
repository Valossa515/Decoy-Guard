package io.github.valossa515.decoyguard.core;

import java.time.Duration;
import java.util.Optional;

/**
 * Stores how long real calls take, so decoy calls can be held for a similar time.
 * The default keeps the observations in memory. Production deployments with several
 * instances may back this with shared storage.
 *
 * <p>Implementations must be thread safe and fast: {@link #record} runs on every real call.
 */
public interface LatencyProfile {

    /**
     * Records the duration of one real call.
     *
     * @param operation stable key of the routed method
     * @param outcome   whether the call returned or threw
     * @param elapsed   how long it took
     */
    void record(String operation, CallOutcome outcome, Duration elapsed);

    /**
     * Draws a duration that a real call of this operation and outcome could plausibly take.
     *
     * @return empty while there are too few observations to be representative
     */
    Optional<Duration> sample(String operation, CallOutcome outcome);
}
