package io.github.valossa515.decoyguard.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings for response time parity between real and decoy calls.
 *
 * @param enabled         whether real calls are observed and decoy calls are held
 * @param windowSize      most recent real durations kept per routed method and outcome
 * @param minObservations observations needed before they replace the fallback range
 * @param fallbackMin     lower bound of the decoy duration while there are too few observations
 * @param fallbackMax     upper bound of that duration
 */
@ConfigurationProperties("decoy-guard.latency-shaping")
public record LatencyShapingProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("500") int windowSize,
        @DefaultValue("20") int minObservations,
        @DefaultValue("150ms") Duration fallbackMin,
        @DefaultValue("400ms") Duration fallbackMax) {

    public LatencyShapingProperties {
        if (windowSize < 1) {
            throw new IllegalArgumentException("decoy-guard.latency-shaping.window-size must be at least 1");
        }
        if (minObservations < 0) {
            throw new IllegalArgumentException("decoy-guard.latency-shaping.min-observations must not be negative");
        }
        if (fallbackMin.isNegative() || fallbackMin.compareTo(fallbackMax) > 0) {
            throw new IllegalArgumentException(
                    "decoy-guard.latency-shaping.fallback-min must not be negative or above fallback-max");
        }
    }
}
