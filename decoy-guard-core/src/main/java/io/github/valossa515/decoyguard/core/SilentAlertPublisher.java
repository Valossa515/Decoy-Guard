package io.github.valossa515.decoyguard.core;

/**
 * SPI that delivers {@link SilentAlert}s. Implementations must not block the request
 * that triggered the alert for long, since response time is observable by the attacker.
 */
@FunctionalInterface
public interface SilentAlertPublisher {

    void publish(SilentAlert alert);
}
